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
import java.util.UUID;

/**
 * /resident command - View resident information.
 */
public class ResidentCommand extends AbstractPlayerCommand {

    private final HyTown plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GRAY = new Color(170, 170, 170);

    public ResidentCommand(HyTown plugin) {
        super("resident", "View player's town status, rank, join date, and activity. Usage: /resident [player]");
        addAliases("res");
        setAllowsExtraArguments(true);
        requirePermission("hytown.use");
        this.plugin = plugin;
    }

    private String[] parseArgs(CommandContext ctx) {
        String input = ctx.getInputString().trim();
        if (input.isEmpty()) return new String[0];
        String[] allArgs = input.split("\\s+");
        if (allArgs.length > 0 && (allArgs[0].equalsIgnoreCase("resident") || allArgs[0].equalsIgnoreCase("res"))) {
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
        UUID playerId = playerData.getUuid();
        String playerName = playerData.getUsername();

        TownStorage townStorage = plugin.getTownStorage();

        if (args.length == 0) {
            showResidentInfo(playerData, playerId, playerName, townStorage);
        } else {
            String targetName = args[0];
            UUID targetId = null;
            String foundName = null;
            Town foundTown = null;

            for (Town town : townStorage.getAllTowns()) {
                for (var entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(targetName)) {
                        targetId = entry.getKey();
                        foundName = entry.getValue();
                        foundTown = town;
                        break;
                    }
                }
                if (targetId != null) break;
            }

            if (targetId != null) {
                showResidentInfoForPlayer(playerData, targetId, foundName, foundTown);
            } else {
                playerData.sendMessage(Message.raw("Player '" + targetName + "' not found!").color(RED));
            }
        }
    }

    private void showResidentInfo(PlayerRef playerData, UUID playerId, String playerName, TownStorage townStorage) {
        Town town = townStorage.getPlayerTown(playerId);

        playerData.sendMessage(Message.raw("========== Resident: " + playerName + " ==========").color(GOLD));

        if (town != null) {
            playerData.sendMessage(Message.raw("Town: " + town.getName()).color(WHITE));
            Town.TownRank rank = town.getRank(playerId);
            playerData.sendMessage(Message.raw("Rank: " + (rank != null ? rank.getDisplayName() : "None")).color(WHITE));
        } else {
            playerData.sendMessage(Message.raw("Town: None (Nomad)").color(GRAY));
        }

        var playtimeData = plugin.getPlaytimeManager().getPlaytime(playerId);
        if (playtimeData != null) {
            double hours = playtimeData.getTotalHoursWithCurrentSession();
            playerData.sendMessage(Message.raw("Playtime: " + String.format("%.1f", hours) + " hours").color(WHITE));
        }

        var claims = plugin.getClaimManager().getPlayerClaims(playerId);
        if (claims != null) {
            int claimCount = claims.getClaimCount();
            int maxClaims = plugin.getClaimManager().getMaxClaims(playerId);
            playerData.sendMessage(Message.raw("Personal Claims: " + claimCount + "/" + maxClaims).color(WHITE));
        }
    }

    private void showResidentInfoForPlayer(PlayerRef playerData, UUID targetId, String targetName, Town town) {
        playerData.sendMessage(Message.raw("========== Resident: " + targetName + " ==========").color(GOLD));

        if (town != null) {
            playerData.sendMessage(Message.raw("Town: " + town.getName()).color(WHITE));
            Town.TownRank rank = town.getRank(targetId);
            playerData.sendMessage(Message.raw("Rank: " + (rank != null ? rank.getDisplayName() : "Resident")).color(WHITE));
        } else {
            playerData.sendMessage(Message.raw("Town: None (Nomad)").color(GRAY));
        }
    }
}
