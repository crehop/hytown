package com.hytowny.gui;

import com.hytowny.HyTowny;
import com.hytowny.data.Town;
import com.hytowny.data.TownStorage;
import com.hytowny.data.TownTransaction;
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
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * GUI for viewing town transaction history.
 */
public class TownLogGui extends InteractiveCustomUIPage<TownLogGui.LogData> {

    private final HyTowny plugin;
    private final World world;
    private final Ref<EntityStore> playerEntityRef;
    private final Store<EntityStore> entityStore;

    private int currentPage = 1;
    private static final int ENTRIES_PER_PAGE = 15;

    public TownLogGui(@Nonnull PlayerRef playerRef, HyTowny plugin, World world,
                      Ref<EntityStore> playerEntityRef, Store<EntityStore> entityStore) {
        super(playerRef, CustomPageLifetime.CanDismiss, LogData.CODEC);
        this.plugin = plugin;
        this.world = world;
        this.playerEntityRef = playerEntityRef;
        this.entityStore = entityStore;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull LogData data) {
        super.handleDataEvent(ref, store, data);

        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            this.sendUpdate();
            return;
        }

        // Handle close button
        if (data.close != null) {
            this.close();
            return;
        }

        // Handle back button - go back to town menu
        if (data.back != null) {
            this.close();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                TownGui.openFor(plugin, player, playerEntityRef, entityStore, world);
            }
            return;
        }

        // Handle pagination
        if (data.action != null) {
            UUID playerId = playerRef.getUuid();
            TownStorage townStorage = plugin.getTownStorage();
            Town town = townStorage.getPlayerTown(playerId);

            if (town != null) {
                List<TownTransaction> transactions = town.getTransactionHistory();
                int totalPages = Math.max(1, (int) Math.ceil(transactions.size() / (double) ENTRIES_PER_PAGE));

                switch (data.action) {
                    case "prev" -> currentPage = Math.max(1, currentPage - 1);
                    case "next" -> currentPage = Math.min(totalPages, currentPage + 1);
                }
            }
        }

        this.sendUpdate();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/hycrown_HyTowny_LogMenu.ui");

        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        UUID playerId = playerRef.getUuid();
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            cmd.set("#TownName.Text", "No Town");
            cmd.set("#PageInfo.Text", "You are not in a town");
            return;
        }

        // Check permission
        if (!town.isAssistant(playerId)) {
            cmd.set("#TownName.Text", town.getName());
            cmd.set("#PageInfo.Text", "Only mayor and assistants can view the log");
            return;
        }

        List<TownTransaction> transactions = town.getTransactionHistory();
        int totalPages = Math.max(1, (int) Math.ceil(transactions.size() / (double) ENTRIES_PER_PAGE));
        currentPage = Math.max(1, Math.min(currentPage, totalPages));

        cmd.set("#TownName.Text", town.getName() + " - Transaction Log");
        cmd.set("#PageInfo.Text", "Page " + currentPage + "/" + totalPages + " (" + transactions.size() + " transactions)");

        // Calculate page range
        int start = (currentPage - 1) * ENTRIES_PER_PAGE;
        int end = Math.min(start + ENTRIES_PER_PAGE, transactions.size());

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm");

        // Fill entries
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
            int entryNum = i + 1;
            String entryId = "#Entry" + entryNum;

            if (start + i < end) {
                TownTransaction tx = transactions.get(start + i);
                String time = dateFormat.format(new Date(tx.getTimestamp()));
                String displayStr = tx.getDisplayString();

                // Color based on transaction type
                String color = switch (tx.getType()) {
                    case DEPOSIT, MEMBER_JOIN -> "#55ff55";
                    case WITHDRAW, UPKEEP, PLOT_PURCHASE, TOWN_CREATE -> "#ff5555";
                    case MEMBER_LEAVE, MEMBER_KICK -> "#ffff55";
                    default -> "#ffffff";
                };

                cmd.set(entryId + ".Text", "[" + time + "] " + displayStr);
                cmd.set(entryId + ".Style.TextColor", color);
                cmd.set(entryId + ".Visible", true);
            } else {
                cmd.set(entryId + ".Text", "");
                cmd.set(entryId + ".Visible", false);
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
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of("Back", "true"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Close", "true"), false);
    }

    /**
     * Open the log GUI for a player.
     */
    public static void openFor(HyTowny plugin, Player player, Ref<EntityStore> playerRef,
                                Store<EntityStore> store, World world) {
        var playerData = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerData != null) {
            TownLogGui gui = new TownLogGui(playerData, plugin, world, playerRef, store);
            player.getPageManager().pushPage(gui);
        }
    }

    /**
     * Data class for receiving UI events.
     */
    public static class LogData {
        public String close;
        public String back;
        public String action;

        public static final Codec<LogData> CODEC = BuilderCodec.of(LogData::new,
                KeyedCodec.optional("Close", Codec.STRING, d -> d.close, (d, v) -> d.close = v),
                KeyedCodec.optional("Back", Codec.STRING, d -> d.back, (d, v) -> d.back = v),
                KeyedCodec.optional("Action", Codec.STRING, d -> d.action, (d, v) -> d.action = v)
        );
    }
}
