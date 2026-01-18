package com.hytown.commands;

import com.hytown.HyTown;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /wilderness command - Admin command for wilderness debugging.
 *
 * Usage:
 *   /wilderness debug - Toggle debug mode to see block names when breaking
 */
public class WildernessCommand extends AbstractPlayerCommand {

    private final HyTown plugin;

    // Track players with debug mode enabled
    private static final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color AQUA = new Color(85, 255, 255);

    public WildernessCommand(HyTown plugin) {
        super("wilderness", "[Admin] Wilderness harvest debug commands. Usage: /wilderness debug");
        requirePermission("hytown.admin");
        setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    private String[] parseArgs(CommandContext ctx) {
        String input = ctx.getInputString().trim();
        if (input.isEmpty()) return new String[0];
        String[] allArgs = input.split("\\s+");
        if (allArgs.length > 0 && allArgs[0].equalsIgnoreCase("wilderness")) {
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

        if (args.length == 0) {
            showHelp(playerData);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "debug" -> toggleDebug(playerData, playerId);
            case "reload" -> reloadConfig(playerData);
            default -> showHelp(playerData);
        }
    }

    private void toggleDebug(PlayerRef playerData, UUID playerId) {
        if (debugPlayers.contains(playerId)) {
            debugPlayers.remove(playerId);
            playerData.sendMessage(Message.raw("Wilderness debug mode: OFF").color(RED));
            playerData.sendMessage(Message.raw("Block names will no longer be shown when breaking.").color(GRAY));
        } else {
            debugPlayers.add(playerId);
            playerData.sendMessage(Message.raw("Wilderness debug mode: ON").color(GREEN));
            playerData.sendMessage(Message.raw("Block names will be shown in chat when breaking blocks.").color(GRAY));
        }
    }

    private void reloadConfig(PlayerRef playerData) {
        plugin.reloadWildernessHarvestConfig();
        playerData.sendMessage(Message.raw("Wilderness harvest config reloaded!").color(GREEN));
    }

    private void showHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("========== Wilderness Commands ==========").color(GOLD));
        playerData.sendMessage(Message.raw("/wilderness debug - Toggle block name debug mode").color(AQUA));
        playerData.sendMessage(Message.raw("/wilderness reload - Reload wilderness_harvest.json").color(AQUA));
        playerData.sendMessage(Message.raw("=========================================").color(GOLD));
    }

    /**
     * Check if a player has debug mode enabled.
     */
    public static boolean isDebugEnabled(UUID playerId) {
        return debugPlayers.contains(playerId);
    }
}
