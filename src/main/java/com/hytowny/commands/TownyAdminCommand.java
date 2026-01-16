package com.hytowny.commands;

import com.hytowny.HyTowny;
import com.hytowny.data.Town;
import com.hytowny.data.TownStorage;
import com.hytowny.gui.TownAdminGui;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;

/**
 * /townyadmin command - Admin commands for managing towns.
 */
public class TownyAdminCommand extends AbstractPlayerCommand {

    private final HyTowny plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GRAY = new Color(170, 170, 170);

    public TownyAdminCommand(HyTowny plugin) {
        super("townyadmin", "Admin commands for town management");
        addAliases("ta");
        requirePermission("towny.admin");
        setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    private String[] parseArgs(CommandContext ctx) {
        String input = ctx.getInputString().trim();
        if (input.isEmpty()) return new String[0];
        String[] allArgs = input.split("\\s+");
        if (allArgs.length > 0 && (allArgs[0].equalsIgnoreCase("townyadmin") || allArgs[0].equalsIgnoreCase("ta"))) {
            String[] args = new String[allArgs.length - 1];
            System.arraycopy(allArgs, 1, args, 0, allArgs.length - 1);
            return args;
        }
        return allArgs;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> playerRef, @Nonnull PlayerRef playerData, @Nonnull World world) {

        String[] args = parseArgs(ctx);
        if (args.length == 0) {
            showHelp(playerData);
            return;
        }

        String action = args[0];
        String arg1 = args.length > 1 ? args[1] : null;
        String arg2 = args.length > 2 ? args[2] : null;
        String arg3 = args.length > 3 ? args[3] : null;

        switch (action.toLowerCase()) {
            case "gui", "menu" -> handleGui(store, playerRef, playerData, world);
            case "reload" -> handleReload(playerData);
            case "town" -> handleTown(playerData, arg1, arg2, arg3);
            case "wild" -> handleWild(playerData, arg1, arg2);
            case "debug" -> handleDebug(playerData);
            case "save" -> handleSave(playerData);
            case "set" -> handleSet(playerData, arg1, arg2);
            default -> showHelp(playerData);
        }
    }

    private void handleGui(Store<EntityStore> store, Ref<EntityStore> playerRef,
                           PlayerRef playerData, World world) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            TownAdminGui.openFor(plugin, player, playerData, world);
        }
    }

    private void handleReload(PlayerRef playerData) {
        plugin.getPluginConfig().reload();
        playerData.sendMessage(Message.raw("Configuration reloaded!").color(GREEN));
    }

    private void handleTown(PlayerRef playerData, String townName, String subAction, String arg) {
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townyadmin town <name> [action]").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTown(townName);

        if (town == null) {
            playerData.sendMessage(Message.raw("Town '" + townName + "' not found!").color(RED));
            return;
        }

        if (subAction == null || subAction.isEmpty()) {
            playerData.sendMessage(Message.raw("Town: " + town.getName()).color(GOLD));
            playerData.sendMessage(Message.raw("Mayor: " + town.getMayorName()).color(WHITE));
            playerData.sendMessage(Message.raw("Residents: " + town.getResidentCount()).color(WHITE));
            playerData.sendMessage(Message.raw("Claims: " + town.getClaimCount()).color(WHITE));
            playerData.sendMessage(Message.raw("Balance: $" + String.format("%.2f", town.getBalance())).color(WHITE));
            return;
        }

        switch (subAction.toLowerCase()) {
            case "delete" -> {
                townStorage.deleteTown(town.getName());
                playerData.sendMessage(Message.raw("Town '" + town.getName() + "' deleted!").color(GREEN));
            }
            case "kick" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townyadmin town " + townName + " kick <player>").color(RED));
                    return;
                }
                UUID targetId = null;
                String targetName = arg;
                for (var entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(arg)) {
                        targetId = entry.getKey();
                        targetName = entry.getValue();
                        break;
                    }
                }
                if (targetId == null) {
                    playerData.sendMessage(Message.raw(arg + " is not in this town!").color(RED));
                    return;
                }
                town.removeResident(targetId);
                townStorage.saveTown(town);
                townStorage.unindexPlayer(targetId);
                playerData.sendMessage(Message.raw("Removed " + targetName + " from " + town.getName()).color(GREEN));
            }
            case "setmayor" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townyadmin town " + townName + " setmayor <player>").color(RED));
                    return;
                }
                UUID newMayorId = null;
                String newMayorName = arg;
                for (var entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(arg)) {
                        newMayorId = entry.getKey();
                        newMayorName = entry.getValue();
                        break;
                    }
                }
                if (newMayorId == null) {
                    playerData.sendMessage(Message.raw(arg + " is not in this town!").color(RED));
                    return;
                }
                town.setMayor(newMayorId, newMayorName);
                townStorage.saveTown(town);
                playerData.sendMessage(Message.raw(newMayorName + " is now mayor of " + town.getName()).color(GREEN));
            }
            case "setbalance" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townyadmin town " + townName + " setbalance <amount>").color(RED));
                    return;
                }
                try {
                    double amount = Double.parseDouble(arg);
                    town.setBalance(amount);
                    townStorage.saveTown(town);
                    playerData.sendMessage(Message.raw("Set balance to $" + String.format("%.2f", amount)).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
                }
            }
            default -> playerData.sendMessage(Message.raw("Unknown action: " + subAction).color(RED));
        }
    }

    private void handleWild(PlayerRef playerData, String subAction, String arg) {
        if (subAction == null || subAction.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townyadmin wild <toggle|sety|info>").color(RED));
            return;
        }

        var config = plugin.getPluginConfig();

        switch (subAction.toLowerCase()) {
            case "toggle" -> {
                boolean current = config.isWildProtectionEnabled();
                config.setWildProtectionEnabled(!current);
                playerData.sendMessage(Message.raw("Wild protection " + (!current ? "ENABLED" : "DISABLED")).color(GREEN));
            }
            case "sety" -> {
                if (arg == null) {
                    playerData.sendMessage(Message.raw("Usage: /townyadmin wild sety <y-level>").color(RED));
                    return;
                }
                try {
                    int y = Integer.parseInt(arg);
                    config.setWildProtectionMinY(y);
                    playerData.sendMessage(Message.raw("Wild protection Y-level set to " + y).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid Y level!").color(RED));
                }
            }
            case "info" -> {
                playerData.sendMessage(Message.raw("========== Wild Protection ==========").color(GOLD));
                playerData.sendMessage(Message.raw("Enabled: " + config.isWildProtectionEnabled()).color(WHITE));
                playerData.sendMessage(Message.raw("Min Y-Level: " + config.getWildProtectionMinY()).color(WHITE));
                playerData.sendMessage(Message.raw("Destroy Below: " + config.isWildDestroyBelowAllowed()).color(WHITE));
                playerData.sendMessage(Message.raw("Build Below: " + config.isWildBuildBelowAllowed()).color(WHITE));
            }
            default -> playerData.sendMessage(Message.raw("Unknown action: " + subAction).color(RED));
        }
    }

    private void handleDebug(PlayerRef playerData) {
        TownStorage townStorage = plugin.getTownStorage();

        playerData.sendMessage(Message.raw("========== Towny Debug ==========").color(GOLD));
        playerData.sendMessage(Message.raw("Towns: " + townStorage.getTownCount()).color(WHITE));

        int totalClaims = 0, totalResidents = 0;
        for (Town town : townStorage.getAllTowns()) {
            totalClaims += town.getClaimCount();
            totalResidents += town.getResidentCount();
        }
        playerData.sendMessage(Message.raw("Total Claims: " + totalClaims).color(WHITE));
        playerData.sendMessage(Message.raw("Total Residents: " + totalResidents).color(WHITE));

        var config = plugin.getPluginConfig();
        playerData.sendMessage(Message.raw("Town Creation Cost: $" + config.getTownCreationCost()).color(GRAY));
        playerData.sendMessage(Message.raw("Wild Protection: " + config.isWildProtectionEnabled() +
                " (Y>" + config.getWildProtectionMinY() + ")").color(GRAY));
    }

    private void handleSave(PlayerRef playerData) {
        plugin.getTownStorage().saveAll();
        plugin.getClaimStorage().saveAll();
        playerData.sendMessage(Message.raw("All data saved!").color(GREEN));
    }

    private void handleSet(PlayerRef playerData, String setting, String value) {
        if (setting == null || setting.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /townyadmin set <towncost|claimcost|wildminy> <value>").color(RED));
            return;
        }

        var config = plugin.getPluginConfig();

        switch (setting.toLowerCase()) {
            case "towncost" -> {
                if (value == null) {
                    playerData.sendMessage(Message.raw("Town creation cost: $" + config.getTownCreationCost()).color(WHITE));
                    return;
                }
                try {
                    double cost = Double.parseDouble(value);
                    config.setTownCreationCost(cost);
                    playerData.sendMessage(Message.raw("Town creation cost set to $" + cost).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
                }
            }
            case "claimcost" -> {
                if (value == null) {
                    playerData.sendMessage(Message.raw("Town claim cost: $" + config.getTownClaimCost()).color(WHITE));
                    return;
                }
                try {
                    double cost = Double.parseDouble(value);
                    config.setTownClaimCost(cost);
                    playerData.sendMessage(Message.raw("Town claim cost set to $" + cost).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
                }
            }
            case "wildminy" -> {
                if (value == null) {
                    playerData.sendMessage(Message.raw("Wild protection min Y: " + config.getWildProtectionMinY()).color(WHITE));
                    return;
                }
                try {
                    int y = Integer.parseInt(value);
                    config.setWildProtectionMinY(y);
                    playerData.sendMessage(Message.raw("Wild protection min Y set to " + y).color(GREEN));
                } catch (NumberFormatException e) {
                    playerData.sendMessage(Message.raw("Invalid Y level!").color(RED));
                }
            }
            default -> playerData.sendMessage(Message.raw("Unknown setting: " + setting).color(RED));
        }
    }

    private void showHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("========== Towny Admin ==========").color(GOLD));
        playerData.sendMessage(Message.raw("/townyadmin gui - Open admin control panel").color(WHITE));
        playerData.sendMessage(Message.raw("/townyadmin reload - Reload config").color(WHITE));
        playerData.sendMessage(Message.raw("/townyadmin save - Force save data").color(WHITE));
        playerData.sendMessage(Message.raw("/townyadmin debug - Show debug info").color(WHITE));
        playerData.sendMessage(Message.raw("/townyadmin town <name> - View town").color(WHITE));
        playerData.sendMessage(Message.raw("/townyadmin town <name> delete").color(WHITE));
        playerData.sendMessage(Message.raw("/townyadmin wild <toggle|sety|info>").color(WHITE));
        playerData.sendMessage(Message.raw("/townyadmin set <setting> <value>").color(WHITE));
    }
}
