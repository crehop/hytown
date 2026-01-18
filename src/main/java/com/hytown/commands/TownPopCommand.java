package com.hytown.commands;

import com.hytown.HyTown;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /townpop command - Admin command to view town populations.
 *
 * Usage:
 *   /townpop        - Show total population across all towns
 *   /townpop <town> - Show population of a specific town
 */
public class TownPopCommand extends AbstractPlayerCommand {

    private final HyTown plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color AQUA = new Color(85, 255, 255);

    public TownPopCommand(HyTown plugin) {
        super("townpop", "[Admin] View town population statistics - total residents, per-town breakdown. Usage: /townpop [town]");
        requirePermission("hytown.admin");
        setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    private String[] parseArgs(CommandContext ctx) {
        String input = ctx.getInputString().trim();
        if (input.isEmpty()) return new String[0];
        String[] allArgs = input.split("\\s+");
        if (allArgs.length > 0 && allArgs[0].equalsIgnoreCase("townpop")) {
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
        TownStorage townStorage = plugin.getTownStorage();

        if (args.length == 0) {
            // Show all towns population
            showAllTownsPopulation(playerData, townStorage);
        } else {
            // Show specific town population
            String townName = args[0];
            showTownPopulation(playerData, townStorage, townName);
        }
    }

    private void showAllTownsPopulation(PlayerRef playerData, TownStorage townStorage) {
        List<Town> towns = new ArrayList<>(townStorage.getAllTowns());

        if (towns.isEmpty()) {
            playerData.sendMessage(Message.raw("No towns exist yet.").color(GRAY));
            return;
        }

        // Sort by population (descending)
        towns.sort(Comparator.comparingInt(Town::getResidentCount).reversed());

        int totalPopulation = 0;
        int totalTowns = towns.size();

        playerData.sendMessage(Message.raw("========== Town Population ==========").color(GOLD));

        for (Town town : towns) {
            int pop = town.getResidentCount();
            totalPopulation += pop;

            String popStr = String.format("%-20s %d resident%s",
                    town.getName(), pop, pop == 1 ? "" : "s");
            playerData.sendMessage(Message.raw(popStr).color(WHITE));
        }

        playerData.sendMessage(Message.raw("=====================================").color(GOLD));
        playerData.sendMessage(Message.raw("Total: " + totalPopulation + " residents in " + totalTowns + " town" + (totalTowns == 1 ? "" : "s")).color(AQUA));

        // Show average
        if (totalTowns > 0) {
            double avg = (double) totalPopulation / totalTowns;
            playerData.sendMessage(Message.raw("Average: " + String.format("%.1f", avg) + " residents per town").color(GRAY));
        }
    }

    private void showTownPopulation(PlayerRef playerData, TownStorage townStorage, String townName) {
        Town town = townStorage.getTown(townName);

        if (town == null) {
            playerData.sendMessage(Message.raw("Town '" + townName + "' not found!").color(RED));
            return;
        }

        int population = town.getResidentCount();

        playerData.sendMessage(Message.raw("========== " + town.getName() + " ==========").color(GOLD));
        playerData.sendMessage(Message.raw("Population: " + population + " resident" + (population == 1 ? "" : "s")).color(WHITE));
        playerData.sendMessage(Message.raw("Mayor: " + town.getMayorName()).color(WHITE));

        // List all residents
        if (population > 0) {
            playerData.sendMessage(Message.raw("Residents:").color(GRAY));

            List<String> residentNames = new ArrayList<>(town.getResidentNames().values());
            residentNames.sort(String::compareToIgnoreCase);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < residentNames.size(); i++) {
                String name = residentNames.get(i);
                // Mark mayor with a star
                if (name.equals(town.getMayorName())) {
                    sb.append(name).append("*");
                } else {
                    sb.append(name);
                }

                if (i < residentNames.size() - 1) {
                    sb.append(", ");
                }

                // Send in chunks to avoid message too long
                if (sb.length() > 60) {
                    playerData.sendMessage(Message.raw("  " + sb.toString()).color(WHITE));
                    sb = new StringBuilder();
                }
            }

            if (sb.length() > 0) {
                playerData.sendMessage(Message.raw("  " + sb.toString()).color(WHITE));
            }
        }

        playerData.sendMessage(Message.raw("Claims: " + town.getClaimCount()).color(GRAY));
    }
}
