package com.hytown.commands;

import com.hytown.HyTown;
import com.hytown.ui.TownyHelpPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;

/**
 * /townyhelp command - Opens the Towny help UI showing all available commands.
 *
 * Usage:
 * /townyhelp - Opens the help UI page
 * /th        - Alias for /townyhelp
 */
public class TownyHelpCommand extends AbstractPlayerCommand {

    private final HyTown plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GRAY = new Color(170, 170, 170);

    public TownyHelpCommand(HyTown plugin) {
        super("townyhelp", "Display Towny help menu");
        addAliases("th", "thelp", "hytown");
        requirePermission("hytown.use");
        this.plugin = plugin;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store,
                           Ref<EntityStore> playerRef, PlayerRef playerData, World world) {

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            playerData.sendMessage(Message.raw("Could not open help UI!").color(new Color(255, 85, 85)));
            return;
        }

        try {
            // Open the help UI page
            TownyHelpPage.openFor(plugin, player, playerRef, store);
        } catch (Exception e) {
            // Fallback to text-based help if UI fails
            showTextHelp(playerData);
        }
    }

    /**
     * Fallback text-based help display if UI cannot be opened.
     */
    private void showTextHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("========== HyTown Commands ==========").color(GOLD));
        playerData.sendMessage(Message.raw("").color(WHITE));

        playerData.sendMessage(Message.raw("--- Town Commands ---").color(GREEN));
        playerData.sendMessage(Message.raw("/town new <name> - Create a town").color(WHITE));
        playerData.sendMessage(Message.raw("/town claim - Claim chunk for town").color(WHITE));
        playerData.sendMessage(Message.raw("/town add <player> - Invite player").color(WHITE));
        playerData.sendMessage(Message.raw("/town info - View town info").color(WHITE));
        playerData.sendMessage(Message.raw("/town list - List all towns").color(WHITE));
        playerData.sendMessage(Message.raw("").color(WHITE));

        playerData.sendMessage(Message.raw("--- Resident & Plot ---").color(GREEN));
        playerData.sendMessage(Message.raw("/resident [player] - View resident").color(WHITE));
        playerData.sendMessage(Message.raw("/plot info - View plot info").color(WHITE));
        playerData.sendMessage(Message.raw("").color(WHITE));

        playerData.sendMessage(Message.raw("--- Personal Claims ---").color(GREEN));
        playerData.sendMessage(Message.raw("/claim claim - Claim chunk").color(WHITE));
        playerData.sendMessage(Message.raw("/claim trust - Trust players").color(WHITE));
        playerData.sendMessage(Message.raw("").color(WHITE));

        playerData.sendMessage(Message.raw("--- Admin ---").color(GREEN));
        playerData.sendMessage(Message.raw("/townyadmin - Admin commands").color(WHITE));
        playerData.sendMessage(Message.raw("").color(WHITE));

        playerData.sendMessage(Message.raw("Type each command for more options!").color(GRAY));
    }
}
