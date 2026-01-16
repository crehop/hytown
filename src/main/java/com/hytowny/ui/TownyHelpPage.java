package com.hytowny.ui;

import com.hytowny.HyTowny;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/**
 * UI Page displaying all Towny commands in a nice formatted view.
 * Similar to the MOTD plugin's UI.
 */
public class TownyHelpPage extends BasicCustomUIPage {

    private final HyTowny plugin;

    public TownyHelpPage(HyTowny plugin, PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.plugin = plugin;
    }

    @Override
    public void build(UICommandBuilder builder) {
        StringBuilder content = new StringBuilder();

        // Push content down with line returns
        content.append("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");

        // Town Commands
        content.append("=== Town Commands ===\n");
        content.append("/town new <name> - Create a new town\n");
        content.append("/town delete - Delete your town\n");
        content.append("/town claim - Claim the chunk you're standing on\n");
        content.append("/town unclaim - Unclaim the current chunk\n");
        content.append("/town add <player> - Invite a player to your town\n");
        content.append("/town kick <player> - Kick a player from your town\n");
        content.append("/town leave - Leave your current town\n");
        content.append("/town join <name> - Join an open town\n");
        content.append("/town info [name] - View town information\n");
        content.append("/town list - List all towns\n");
        content.append("/town spawn - Teleport to town spawn\n");
        content.append("/town setspawn - Set town spawn location\n");
        content.append("/town deposit <amount> - Deposit to town bank\n");
        content.append("/town withdraw <amount> - Withdraw from bank\n");
        content.append("/town toggle <setting> - Toggle town settings\n");
        content.append("/town rank <add|remove> <player> <rank>\n");
        content.append("/town online - Show online town members\n");
        content.append("/town here - Show town at your location\n");
        content.append("\n");

        // Resident Commands
        content.append("=== Resident Commands ===\n");
        content.append("/resident [player] - View resident info\n");
        content.append("\n");

        // Plot Commands
        content.append("=== Plot Commands ===\n");
        content.append("/plot claim - Claim a plot in your town\n");
        content.append("/plot unclaim - Release your plot\n");
        content.append("/plot forsale <price> - Set plot for sale\n");
        content.append("/plot notforsale - Remove from sale\n");
        content.append("/plot info - Show plot information\n");
        content.append("/plot set <perm> <on|off> - Set permissions\n");
        content.append("\n");

        // Personal Claim Commands
        content.append("=== Personal Claims ===\n");
        content.append("/claim claim - Claim current chunk\n");
        content.append("/claim unclaim - Unclaim current chunk\n");
        content.append("/claim trust <player> <level> - Add trust\n");
        content.append("/claim untrust <player> - Remove trust\n");
        content.append("/claim list - View your claims\n");
        content.append("/claim gui - Open claim map\n");
        content.append("\n");

        // Admin Commands
        content.append("=== Admin Commands ===\n");
        content.append("/townyadmin reload - Reload config\n");
        content.append("/townyadmin town <name> - Manage town\n");
        content.append("/townyadmin wild toggle - Toggle wild protection\n");
        content.append("/townyadmin wild sety <level> - Set Y threshold\n");
        content.append("/townyadmin save - Force save all data\n");
        content.append("/townyadmin debug - Show debug info\n");

        builder.append("Pages/BarterPage.ui");
        builder.set("#ShopTitle.Text", "HyTowny Help");
        builder.set("#RefreshTimer.Text", content.toString());
        builder.set("#TradeGridContainer.Visible", false);
    }

    /**
     * Open the help page for a player (from command with Ref).
     */
    public static void openFor(HyTowny plugin, Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = player.getPlayerRef();
        TownyHelpPage page = new TownyHelpPage(plugin, playerRef);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    /**
     * Open the help page for a player (from event).
     */
    public static void openFor(HyTowny plugin, Player player) {
        try {
            var world = player.getWorld();
            if (world == null) return;

            PlayerRef playerRef = player.getPlayerRef();
            var ref = playerRef.getReference();
            var store = world.getEntityStore().getStore();

            if (ref != null) {
                TownyHelpPage page = new TownyHelpPage(plugin, playerRef);
                player.getPageManager().openCustomPage(ref, store, page);
            }
        } catch (Exception ignored) {}
    }
}
