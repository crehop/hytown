package com.hytown.managers;

import com.hytown.config.PluginConfig;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.logger.HytaleLogger;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Manages daily town upkeep collection.
 * Upkeep is collected from town bank at a specific hour each day.
 */
public class UpkeepManager {

    private static final Color RED = new Color(255, 85, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color GREEN = new Color(85, 255, 85);
    private static final int GRACE_PERIOD_DAYS = 15;

    private final PluginConfig config;
    private final TownStorage townStorage;
    private final HytaleLogger logger;
    private int lastCheckedDay = -1;

    public UpkeepManager(PluginConfig config, TownStorage townStorage, HytaleLogger logger) {
        this.config = config;
        this.townStorage = townStorage;
        this.logger = logger;
    }

    /**
     * Check if upkeep should be collected (called periodically).
     * Collects upkeep once per day at the configured hour.
     */
    public void checkUpkeep() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        int currentDay = now.getDayOfYear();
        int currentHour = now.getHour();

        // Only collect once per day at the configured hour
        if (currentDay != lastCheckedDay && currentHour >= config.getTownUpkeepHour()) {
            lastCheckedDay = currentDay;
            collectAllUpkeep();
        }
    }

    /**
     * Collect upkeep from all towns.
     */
    public void collectAllUpkeep() {
        logger.atInfo().log("[Upkeep] Collecting daily upkeep from all towns...");

        // Create daily backup before collecting upkeep
        townStorage.createBackup();

        for (Town town : townStorage.getAllTowns()) {
            collectUpkeep(town);
        }

        logger.atInfo().log("[Upkeep] Upkeep collection complete.");
    }

    /**
     * Collect upkeep from a single town.
     */
    private void collectUpkeep(Town town) {
        double upkeep = calculateUpkeep(town);

        if (upkeep <= 0) {
            // No upkeep due (maybe upkeep is disabled)
            town.setLastUpkeepTime(System.currentTimeMillis());
            town.setMissedUpkeepDays(0);
            townStorage.saveTown(town);
            return;
        }

        if (town.withdraw(upkeep)) {
            // Successfully paid upkeep
            town.setLastUpkeepTime(System.currentTimeMillis());
            town.setMissedUpkeepDays(0);
            town.logUpkeep(upkeep, town.getClaimCount());
            townStorage.saveTown(town);
            logger.atInfo().log("[Upkeep] Town '%s' paid $%.2f upkeep", town.getName(), upkeep);

            // Notify online mayor
            notifyMayor(town, String.format("Daily upkeep of $%.2f collected from town bank.", upkeep), GREEN);
        } else {
            // Couldn't pay upkeep - accrue debt (negative balance)
            int missedDays = town.getMissedUpkeepDays() + 1;
            town.setMissedUpkeepDays(missedDays);
            // Make balance go negative to track debt
            double debt = upkeep - town.getBalance();
            town.setBalance(town.getBalance() - upkeep);  // Will go negative
            town.setLastUpkeepTime(System.currentTimeMillis());
            town.logUpkeep(upkeep, town.getClaimCount());  // Still log the upkeep
            townStorage.saveTown(town);

            logger.atWarning().log("[Upkeep] Town '%s' couldn't pay $%.2f upkeep (missed %d days, debt: $%.2f)",
                    town.getName(), upkeep, missedDays, Math.abs(town.getBalance()));

            // Notify mayor about missed payment
            int daysUntilDeletion = GRACE_PERIOD_DAYS - missedDays;
            String warning;
            if (daysUntilDeletion > 0) {
                warning = String.format(
                        "WARNING: Town is in debt! Balance: $%.2f. %d days until deletion!",
                        town.getBalance(), daysUntilDeletion
                );
            } else {
                warning = "CRITICAL: Town will be deleted for unpaid upkeep!";
            }
            notifyMayor(town, warning, RED);
            notifyAllResidents(town, warning, RED);

            // Delete town after grace period
            if (missedDays >= GRACE_PERIOD_DAYS) {
                logger.atWarning().log("[Upkeep] Deleting town '%s' for unpaid upkeep (%d days)",
                        town.getName(), missedDays);
                notifyAllResidents(town, "Your town has been deleted due to unpaid upkeep!", RED);
                townStorage.deleteTown(town.getName());
            }
        }
    }

    /**
     * Calculate upkeep for a town.
     */
    public double calculateUpkeep(Town town) {
        double base = config.getTownUpkeepBase();
        double perClaim = config.getTownUpkeepPerClaim();
        int claims = town.getClaimCount();

        return base + (perClaim * claims);
    }

    /**
     * Calculate how many days until town runs out of funds.
     */
    public int calculateDaysUntilBankrupt(Town town) {
        double upkeep = calculateUpkeep(town);
        if (upkeep <= 0) return -1; // Unlimited (no upkeep)

        double balance = town.getBalance();
        if (balance <= 0) return 0; // Already bankrupt

        return (int) (balance / upkeep);
    }

    /**
     * Notify the mayor of a town with a message.
     */
    private void notifyMayor(Town town, String message, Color color) {
        try {
            PlayerRef mayor = Universe.get().getPlayer(town.getMayorId());
            if (mayor != null) {
                mayor.sendMessage(Message.raw("[Town] " + message).color(color));
            }
        } catch (Exception e) {
            // Mayor not online, ignore
        }
    }

    /**
     * Notify all online residents of a town with a message.
     */
    private void notifyAllResidents(Town town, String message, Color color) {
        for (java.util.UUID residentId : town.getResidents()) {
            try {
                PlayerRef resident = Universe.get().getPlayer(residentId);
                if (resident != null) {
                    resident.sendMessage(Message.raw("[Town] " + message).color(color));
                }
            } catch (Exception e) {
                // Resident not online, ignore
            }
        }
    }

    /**
     * Check if a town is overdue on upkeep and return warning message if so.
     * Called when player logs in or enters town territory.
     */
    public String getOverdueWarning(Town town) {
        if (town == null) return null;

        int missedDays = town.getMissedUpkeepDays();
        if (missedDays > 0) {
            int daysUntilDeletion = GRACE_PERIOD_DAYS - missedDays;
            if (daysUntilDeletion <= 0) {
                return String.format("CRITICAL: Town '%s' will be deleted! Debt: $%.2f",
                        town.getName(), Math.abs(town.getBalance()));
            } else {
                return String.format("WARNING: Town '%s' is in debt ($%.2f)! %d days until deletion!",
                        town.getName(), Math.abs(town.getBalance()), daysUntilDeletion);
            }
        }
        return null;
    }

    /**
     * Check if a town is overdue.
     */
    public boolean isOverdue(Town town) {
        return town != null && town.getMissedUpkeepDays() > 0;
    }

    /**
     * Warn a player if their town is overdue on upkeep.
     */
    public void warnIfOverdue(java.util.UUID playerId) {
        Town town = townStorage.getPlayerTown(playerId);
        String warning = getOverdueWarning(town);
        if (warning != null) {
            try {
                PlayerRef player = Universe.get().getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(Message.raw("[Town] " + warning).color(RED));
                }
            } catch (Exception e) {
                // Player not online, ignore
            }
        }
    }

    /**
     * Format upkeep info for display.
     */
    public String formatUpkeepInfo(Town town) {
        double upkeep = calculateUpkeep(town);
        int daysLeft = calculateDaysUntilBankrupt(town);

        if (upkeep <= 0) {
            return "Upkeep: Free";
        }

        String daysText;
        if (daysLeft < 0) {
            daysText = "forever";
        } else if (daysLeft == 0) {
            daysText = "OVERDUE!";
        } else if (daysLeft == 1) {
            daysText = "1 day";
        } else {
            daysText = daysLeft + " days";
        }

        return String.format("$%.0f/day (%s)", upkeep, daysText);
    }
}
