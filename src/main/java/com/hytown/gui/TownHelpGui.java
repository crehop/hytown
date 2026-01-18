package com.hytown.gui;

import com.hytown.HyTown;
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

    private final HyTown plugin;
    private final World world;
    private final Ref<EntityStore> playerEntityRef;
    private final Store<EntityStore> entityStore;
    private final boolean isAdmin;
    private final int currentPage;

    private static final int ENTRIES_PER_PAGE = 12;

    // Command entries: command, description
    private static final List<String[]> PLAYER_COMMANDS = List.of(
        // === TOWN COMMANDS ===
        new String[]{"--- TOWN COMMANDS ---", ""},
        new String[]{"/town gui", "Open the town management GUI interface"},
        new String[]{"/town help", "Open this help menu with all commands"},
        new String[]{"/town new <name>", "Create a new town (costs money, name 3-24 chars)"},
        new String[]{"/town delete", "Permanently delete your town (mayor only)"},
        new String[]{"/town claim", "Claim the current chunk for your town"},
        new String[]{"/town unclaim", "Unclaim current chunk (requires confirmation)"},
        new String[]{"/town invite <player>", "Invite an online player to your town"},
        new String[]{"/town deny <town>", "Deny an invite (blocks re-invites for 1 hour)"},
        new String[]{"/town join", "Accept pending invite (or join open town)"},
        new String[]{"/town kick <player>", "Kick a resident from your town"},
        new String[]{"/town leave", "Leave your current town"},
        new String[]{"/town info [town]", "View detailed town information"},
        new String[]{"/town list", "List all towns on the server"},
        new String[]{"/town spawn", "Teleport to your town's spawn point"},
        new String[]{"/town here", "Show who owns the chunk you're standing in"},
        new String[]{"/town online", "View which town members are online"},
        new String[]{"--- TOWN SETTINGS ---", ""},
        new String[]{"/town set spawn", "Set town spawn at your current location"},
        new String[]{"/town set mayor <player>", "Transfer mayor role to another resident"},
        new String[]{"/town board <message>", "Set town message (max 100 chars)"},
        new String[]{"/town toggle pvp", "Toggle PvP on/off in town territory"},
        new String[]{"/town toggle open", "Toggle if anyone can join without invite"},
        new String[]{"/town toggle fire", "Toggle fire spread in town"},
        new String[]{"/town toggle explosion", "Toggle explosion damage in town"},
        new String[]{"--- TOWN RANKS ---", ""},
        new String[]{"/town rank add <p> assistant", "Promote resident to assistant rank"},
        new String[]{"/town rank remove <p> assistant", "Demote assistant back to resident"},
        new String[]{"--- TOWN ECONOMY ---", ""},
        new String[]{"/town deposit <amount>", "Deposit money into town bank"},
        new String[]{"/town withdraw <amount>", "Withdraw money from town bank"},
        new String[]{"/town balance", "Check your balance and town balance"},
        new String[]{"/town log [page]", "View town transaction history"},
        // === PLOT COMMANDS ===
        new String[]{"--- PLOT COMMANDS ---", ""},
        new String[]{"/plot", "Open plot management GUI"},
        new String[]{"/plot claim", "Claim current plot for yourself (in town)"},
        new String[]{"/plot unclaim", "Unclaim your plot back to town"},
        new String[]{"/plot reset", "Reset plot settings to town defaults"},
        new String[]{"/plot info", "View current plot information"},
        new String[]{"/plot set <setting> <value>", "Change plot permission settings"},
        new String[]{"/plot forsale <price>", "Put your plot up for sale"},
        new String[]{"/plot notforsale", "Remove plot from sale"},
        // === PERSONAL CLAIM COMMANDS ===
        new String[]{"--- PERSONAL CLAIMS ---", ""},
        new String[]{"/claim", "Open personal claim GUI"},
        new String[]{"/claim claim", "Claim current chunk for yourself"},
        new String[]{"/claim unclaim", "Unclaim current personal chunk"},
        new String[]{"/claim unclaimall", "Unclaim ALL your personal chunks"},
        new String[]{"/claim list", "List all your claimed chunks"},
        new String[]{"/claim trust <p> [level]", "Trust player (build/container/access)"},
        new String[]{"/claim untrust <player>", "Remove trust from a player"},
        new String[]{"/claim trustlist", "List all players you've trusted"},
        new String[]{"/claim settings", "Open claim settings GUI"},
        new String[]{"/claim playtime", "Show playtime and available claim slots"},
        // === OTHER ===
        new String[]{"--- OTHER COMMANDS ---", ""},
        new String[]{"/resident [player]", "View player's town info and status"},
        new String[]{"/townhelp", "Open this help menu (aliases: /th, /thelp)"}
    );

    private static final List<String[]> ADMIN_COMMANDS = List.of(
        new String[]{"--- ADMIN COMMANDS ---", ""},
        new String[]{"/townadmin gui", "Open the admin control panel GUI"},
        new String[]{"/townadmin reload", "Reload all configuration files"},
        new String[]{"/townadmin save", "Force save all town/claim data"},
        new String[]{"/townadmin debug", "Show debug info (storage stats, etc)"},
        new String[]{"--- TOWN MANAGEMENT ---", ""},
        new String[]{"/townadmin town <name>", "View detailed info about a town"},
        new String[]{"/townadmin town <n> delete", "Force delete a town (no refund)"},
        new String[]{"/townadmin town <n> setbalance <$>", "Set a town's bank balance"},
        new String[]{"--- WILDERNESS ---", ""},
        new String[]{"/townadmin wild toggle", "Toggle wilderness harvest protection"},
        new String[]{"/townadmin wild sety <y>", "Set protected Y level for wild"},
        new String[]{"/townadmin wild info", "View wild protection settings"},
        new String[]{"--- CONFIG ---", ""},
        new String[]{"/townadmin set <key> <value>", "Change a config setting live"},
        new String[]{"/claim admin config", "View current config values"},
        new String[]{"/claim admin reload", "Reload personal claim config"},
        new String[]{"/claim admin gui", "Open admin chunk visualizer"},
        new String[]{"/townpop", "View all town population statistics"},
        new String[]{"/townpop <town>", "View specific town population"}
    );

    public TownHelpGui(@Nonnull PlayerRef playerRef, HyTown plugin, World world,
                       Ref<EntityStore> playerEntityRef, Store<EntityStore> entityStore, boolean isAdmin, int page) {
        super(playerRef, CustomPageLifetime.CanDismiss, HelpData.CODEC);
        this.plugin = plugin;
        this.world = world;
        this.playerEntityRef = playerEntityRef;
        this.entityStore = entityStore;
        this.isAdmin = isAdmin;
        this.currentPage = page;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull HelpData data) {
        super.handleDataEvent(ref, store, data);

        // Handle close button
        if (data.close != null) {
            this.close();
            return;
        }

        // Handle pagination - open new page
        if (data.action != null) {
            List<String[]> allCommands = getAllCommands();
            int totalPages = Math.max(1, (int) Math.ceil(allCommands.size() / (double) ENTRIES_PER_PAGE));

            int newPage = currentPage;
            switch (data.action) {
                case "prev" -> newPage = Math.max(1, currentPage - 1);
                case "next" -> newPage = Math.min(totalPages, currentPage + 1);
            }

            if (newPage != currentPage) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    final int targetPage = newPage;
                    world.execute(() -> {
                        openForDirectWithPage(plugin, player, playerEntityRef, entityStore, world, isAdmin, targetPage);
                    });
                }
            }
            return;
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
        cmd.append("Pages/hycrown_HyTown_HelpMenu.ui");

        List<String[]> allCommands = getAllCommands();
        int totalPages = Math.max(1, (int) Math.ceil(allCommands.size() / (double) ENTRIES_PER_PAGE));
        int page = Math.max(1, Math.min(currentPage, totalPages));

        String pageInfo = "Page " + page + "/" + totalPages + " (" + allCommands.size() + " commands)";
        if (isAdmin) {
            pageInfo += " [Admin View]";
        }
        cmd.set("#PageInfo.Text", pageInfo);

        // Calculate page range
        int start = (page - 1) * ENTRIES_PER_PAGE;
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

                // Section headers (start with "---") in gold
                boolean isHeader = entry[0].startsWith("---");
                // Admin commands (start with /townadmin or /claim admin) in yellow
                boolean isAdminCmd = entry[0].startsWith("/townadmin") || entry[0].startsWith("/claim admin") || entry[0].startsWith("/townpop");

                if (isHeader) {
                    cmd.set(cmdId + ".Style.TextColor", "#ffaa00");  // Gold for headers
                    cmd.set(descId + ".Style.TextColor", "#ffaa00");
                } else if (isAdminCmd) {
                    cmd.set(cmdId + ".Style.TextColor", "#ffff55");  // Yellow for admin
                    cmd.set(descId + ".Style.TextColor", "#ffff55");
                } else {
                    cmd.set(cmdId + ".Style.TextColor", "#55ff55");  // Green for commands
                    cmd.set(descId + ".Style.TextColor", "#ffffff");  // White for descriptions
                }
            } else {
                cmd.set(cmdId + ".Visible", false);
                cmd.set(descId + ".Visible", false);
            }
        }

        // Enable/disable pagination buttons
        cmd.set("#PrevButton.Visible", page > 1);
        cmd.set("#NextButton.Visible", page < totalPages);

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
    public static void openFor(HyTown plugin, Player player, Ref<EntityStore> playerRef,
                                Store<EntityStore> store, World world, boolean isAdmin) {
        openForWithPage(plugin, player, playerRef, store, world, isAdmin, 1);
    }

    /**
     * Open the help GUI for a player at a specific page.
     */
    public static void openForWithPage(HyTown plugin, Player player, Ref<EntityStore> playerRef,
                                Store<EntityStore> store, World world, boolean isAdmin, int page) {
        var playerData = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerData != null) {
            TownHelpGui gui = new TownHelpGui(playerData, plugin, world, playerRef, store, isAdmin, page);
            world.execute(() -> {
                player.getPageManager().openCustomPage(playerRef, store, gui);
            });
        }
    }

    /**
     * Open the help GUI directly without world.execute wrapper.
     */
    public static void openForDirectWithPage(HyTown plugin, Player player, Ref<EntityStore> playerRef,
                                Store<EntityStore> store, World world, boolean isAdmin, int page) {
        var playerData = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerData != null) {
            TownHelpGui gui = new TownHelpGui(playerData, plugin, world, playerRef, store, isAdmin, page);
            player.getPageManager().openCustomPage(playerRef, store, gui);
        }
    }

    /**
     * Data class for receiving UI events.
     */
    public static class HelpData {
        public String close;
        public String action;

        public static final BuilderCodec<HelpData> CODEC = BuilderCodec.<HelpData>builder(HelpData.class, HelpData::new)
                .addField(new KeyedCodec<>("Close", Codec.STRING),
                        (data, s) -> data.close = s,
                        data -> data.close)
                .addField(new KeyedCodec<>("Action", Codec.STRING),
                        (data, s) -> data.action = s,
                        data -> data.action)
                .build();
    }
}
