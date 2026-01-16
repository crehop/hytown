package com.hytowny.gui;

import com.hytowny.HyTowny;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI for displaying town command help.
 */
public class TownHelpGui extends InteractiveCustomUIPage<TownHelpGui.HelpData> {

    private final HyTowny plugin;
    private final World world;
    private final Ref<EntityStore> playerEntityRef;
    private final Store<EntityStore> entityStore;
    private final boolean isAdmin;

    private int currentPage = 1;
    private static final int ENTRIES_PER_PAGE = 12;

    // Command entries: command, description
    private static final List<String[]> PLAYER_COMMANDS = List.of(
        new String[]{"/town gui", "Open town management GUI"},
        new String[]{"/town help", "Show this help menu"},
        new String[]{"/town new <name>", "Create a new town"},
        new String[]{"/town delete", "Delete your town (mayor only)"},
        new String[]{"/town claim", "Claim current chunk for town"},
        new String[]{"/town unclaim", "Unclaim current chunk"},
        new String[]{"/town add <player>", "Invite a player to your town"},
        new String[]{"/town kick <player>", "Kick a resident from town"},
        new String[]{"/town join <town>", "Join an open town"},
        new String[]{"/town leave", "Leave your current town"},
        new String[]{"/town info [town]", "View town information"},
        new String[]{"/town list", "List all towns"},
        new String[]{"/town spawn", "Teleport to town spawn"},
        new String[]{"/town set spawn", "Set town spawn (in your territory)"},
        new String[]{"/town set board <msg>", "Set town message board"},
        new String[]{"/town set mayor <player>", "Transfer mayor to another resident"},
        new String[]{"/town deposit <amount>", "Deposit money to town bank"},
        new String[]{"/town withdraw <amount>", "Withdraw from town bank"},
        new String[]{"/town balance", "Check your and town balance"},
        new String[]{"/town toggle pvp", "Toggle PvP in town"},
        new String[]{"/town toggle open", "Toggle if anyone can join"},
        new String[]{"/town toggle fire", "Toggle fire spread"},
        new String[]{"/town toggle explosion", "Toggle explosions"},
        new String[]{"/town rank add <p> assistant", "Promote to assistant"},
        new String[]{"/town rank remove <p> assistant", "Demote from assistant"},
        new String[]{"/town here", "Show who owns current chunk"},
        new String[]{"/town log [page]", "View transaction history"},
        new String[]{"/town online", "View online town members"}
    );

    private static final List<String[]> ADMIN_COMMANDS = List.of(
        new String[]{"/townyadmin gui", "Open admin control panel"},
        new String[]{"/townyadmin reload", "Reload configuration"},
        new String[]{"/townyadmin save", "Force save all data"},
        new String[]{"/townyadmin debug", "Show debug information"},
        new String[]{"/townyadmin town <name>", "View/manage a specific town"},
        new String[]{"/townyadmin town <n> delete", "Delete a town"},
        new String[]{"/townyadmin town <n> setbalance <$>", "Set town balance"},
        new String[]{"/townyadmin wild toggle", "Toggle wild protection"},
        new String[]{"/townyadmin wild sety <y>", "Set wild protected Y level"},
        new String[]{"/townyadmin wild info", "View wild protection info"},
        new String[]{"/townyadmin set <setting> <value>", "Change config setting"}
    );

    public TownHelpGui(@Nonnull PlayerRef playerRef, HyTowny plugin, World world,
                       Ref<EntityStore> playerEntityRef, Store<EntityStore> entityStore, boolean isAdmin) {
        super(playerRef, CustomPageLifetime.CanDismiss, HelpData.CODEC);
        this.plugin = plugin;
        this.world = world;
        this.playerEntityRef = playerEntityRef;
        this.entityStore = entityStore;
        this.isAdmin = isAdmin;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull HelpData data) {
        super.handleDataEvent(ref, store, data);

        // Handle close button
        if (data.close != null) {
            this.close();
            return;
        }

        // Handle pagination
        if (data.action != null) {
            List<String[]> allCommands = getAllCommands();
            int totalPages = Math.max(1, (int) Math.ceil(allCommands.size() / (double) ENTRIES_PER_PAGE));

            switch (data.action) {
                case "prev" -> currentPage = Math.max(1, currentPage - 1);
                case "next" -> currentPage = Math.min(totalPages, currentPage + 1);
            }
        }

        this.sendUpdate();
    }

    private List<String[]> getAllCommands() {
        List<String[]> all = new ArrayList<>(PLAYER_COMMANDS);
        if (isAdmin) {
            all.addAll(ADMIN_COMMANDS);
        }
        return all;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/hycrown_HyTowny_HelpMenu.ui");

        List<String[]> allCommands = getAllCommands();
        int totalPages = Math.max(1, (int) Math.ceil(allCommands.size() / (double) ENTRIES_PER_PAGE));
        currentPage = Math.max(1, Math.min(currentPage, totalPages));

        cmd.set("#Title.Text", "Town Commands Help");
        cmd.set("#PageInfo.Text", "Page " + currentPage + "/" + totalPages + " (" + allCommands.size() + " commands)");

        // Calculate page range
        int start = (currentPage - 1) * ENTRIES_PER_PAGE;
        int end = Math.min(start + ENTRIES_PER_PAGE, allCommands.size());

        // Fill entries
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
            int entryNum = i + 1;
            String cmdId = "#Cmd" + entryNum;
            String descId = "#Desc" + entryNum;

            if (start + i < end) {
                String[] entry = allCommands.get(start + i);
                cmd.set(cmdId + ".Text", entry[0]);
                cmd.set(descId + ".Text", entry[1]);
                cmd.set(cmdId + ".Visible", true);
                cmd.set(descId + ".Visible", true);

                // Admin commands in yellow
                boolean isAdminCmd = entry[0].startsWith("/townyadmin");
                cmd.set(cmdId + ".Style.TextColor", isAdminCmd ? "#ffff55" : "#55ff55");
                cmd.set(descId + ".Style.TextColor", isAdminCmd ? "#ffff55" : "#ffffff");
            } else {
                cmd.set(cmdId + ".Visible", false);
                cmd.set(descId + ".Visible", false);
            }
        }

        // Enable/disable pagination buttons
        cmd.set("#PrevButton.Visible", currentPage > 1);
        cmd.set("#NextButton.Visible", currentPage < totalPages);

        // Event bindings
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevButton",
                EventData.of("Action", "prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton",
                EventData.of("Action", "next"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Close", "true"), false);
    }

    /**
     * Open the help GUI for a player.
     */
    public static void openFor(HyTowny plugin, Player player, Ref<EntityStore> playerRef,
                                Store<EntityStore> store, World world, boolean isAdmin) {
        var playerData = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerData != null) {
            TownHelpGui gui = new TownHelpGui(playerData, plugin, world, playerRef, store, isAdmin);
            player.getPageManager().pushPage(gui);
        }
    }

    /**
     * Data class for receiving UI events.
     */
    public static class HelpData {
        public String close;
        public String action;

        public static final Codec<HelpData> CODEC = BuilderCodec.of(HelpData::new,
                KeyedCodec.optional("Close", Codec.STRING, d -> d.close, (d, v) -> d.close = v),
                KeyedCodec.optional("Action", Codec.STRING, d -> d.action, (d, v) -> d.action = v)
        );
    }
}
