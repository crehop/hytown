package com.hytowny.gui;

import com.hycrown.hyconomy.HyConomy;
import com.hytowny.HyTowny;
import com.hytowny.data.Town;
import com.hytowny.data.TownStorage;
import com.hytowny.managers.ClaimManager;
import com.hytowny.util.ChunkUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;

/**
 * GUI for town management - creating, claiming, inviting, and managing town settings.
 */
public class TownGui extends InteractiveCustomUIPage<TownGui.TownData> {

    private final HyTowny plugin;
    private final World world;
    private final Ref<EntityStore> playerEntityRef;
    private final Store<EntityStore> entityStore;

    private String townNameInput = "";
    private String playerNameInput = "";
    private String amountInput = "";
    private String statusMessage = "";
    private boolean statusIsError = false;
    private String currentTab = "main"; // main, create, bank, settings, members
    private boolean confirmingLeave = false;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);

    public TownGui(@Nonnull PlayerRef playerRef, HyTowny plugin, World world,
                   Ref<EntityStore> playerEntityRef, Store<EntityStore> entityStore) {
        super(playerRef, CustomPageLifetime.CanDismiss, TownData.CODEC);
        this.plugin = plugin;
        this.world = world;
        this.playerEntityRef = playerEntityRef;
        this.entityStore = entityStore;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull TownData data) {
        super.handleDataEvent(ref, store, data);

        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            this.sendUpdate();
            return;
        }

        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();

        // Handle close button
        if (data.close != null) {
            this.close();
            return;
        }

        // Handle text input changes - just store the value, don't rebuild UI
        boolean textOnly = false;
        if (data.townName != null) {
            this.townNameInput = data.townName;
            textOnly = true;
        }
        if (data.playerName != null) {
            this.playerNameInput = data.playerName;
            textOnly = true;
        }
        if (data.amount != null) {
            this.amountInput = data.amount;
            textOnly = true;
        }

        // If only text changed, don't rebuild - just acknowledge
        if (textOnly && data.action == null && data.tab == null) {
            this.sendUpdate();
            return;
        }

        // Handle tab navigation
        if (data.tab != null) {
            this.currentTab = data.tab;
            this.statusMessage = "";
        }

        // Handle actions
        if (data.action != null) {
            handleAction(data.action, playerId, playerName, ref, store);
        }

        // Rebuild and send update
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        this.build(ref, commandBuilder, eventBuilder, store);
        this.sendUpdate(commandBuilder, eventBuilder, true);
    }

    private void handleAction(String action, UUID playerId, String playerName,
                              Ref<EntityStore> ref, Store<EntityStore> store) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        switch (action) {
            case "create_town" -> {
                if (townNameInput.isEmpty()) {
                    statusMessage = "Enter a town name!";
                    statusIsError = true;
                    return;
                }
                if (town != null) {
                    statusMessage = "You're already in a town!";
                    statusIsError = true;
                    return;
                }
                if (townStorage.townExists(townNameInput)) {
                    statusMessage = "Town name already taken!";
                    statusIsError = true;
                    return;
                }
                if (townNameInput.length() < 3 || townNameInput.length() > 24) {
                    statusMessage = "Name must be 3-24 characters!";
                    statusIsError = true;
                    return;
                }
                if (!townNameInput.matches("^[a-zA-Z0-9_-]+$")) {
                    statusMessage = "Invalid characters in name!";
                    statusIsError = true;
                    return;
                }
                // Check balance
                double cost = plugin.getPluginConfig().getTownCreationCost();
                if (cost > 0 && !HyConomy.has(playerName, cost)) {
                    statusMessage = "Need " + HyConomy.format(cost) + "!";
                    statusIsError = true;
                    return;
                }
                if (cost > 0 && !HyConomy.withdraw(playerName, cost)) {
                    statusMessage = "Payment failed!";
                    statusIsError = true;
                    return;
                }

                Town newTown = new Town(townNameInput, playerId, playerName);
                townStorage.saveTown(newTown);
                statusMessage = "Town created!";
                statusIsError = false;
                townNameInput = "";
                currentTab = "main";
            }

            case "claim" -> {
                if (town == null) {
                    statusMessage = "Join a town first!";
                    statusIsError = true;
                    return;
                }
                if (!town.isAssistant(playerId)) {
                    statusMessage = "No permission to claim!";
                    statusIsError = true;
                    return;
                }

                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                Vector3d pos = transform.getPosition();
                String worldName = world.getName();
                int chunkX = ChunkUtil.toChunkX(pos.getX());
                int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
                String claimKey = worldName + ":" + chunkX + "," + chunkZ;

                if (town.ownsClaim(claimKey)) {
                    statusMessage = "Already claimed!";
                    statusIsError = true;
                    return;
                }

                Town existingTown = townStorage.getTownByClaimKey(claimKey);
                if (existingTown != null) {
                    statusMessage = "Claimed by " + existingTown.getName();
                    statusIsError = true;
                    return;
                }

                // Check adjacency - claims must be face-adjacent (not diagonal)
                if (!town.isAdjacentToExistingClaim(worldName, chunkX, chunkZ)) {
                    statusMessage = "Must be adjacent (not diagonal)!";
                    statusIsError = true;
                    return;
                }

                // Check town bank balance (not player balance)
                double cost = plugin.getPluginConfig().getTownClaimCost();
                if (cost > 0 && town.getBalance() < cost) {
                    statusMessage = "Town needs " + HyConomy.format(cost) + "!";
                    statusIsError = true;
                    return;
                }

                ClaimManager.ClaimResult result = plugin.getClaimManager().claimChunk(
                        town.getMayorId(), worldName, pos.getX(), pos.getZ()
                );

                if (result == ClaimManager.ClaimResult.SUCCESS) {
                    if (cost > 0) town.withdraw(cost);  // Withdraw from town bank
                    town.addClaim(claimKey);
                    townStorage.saveTown(town);
                    plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);
                    statusMessage = "Claimed [" + chunkX + "," + chunkZ + "] for " + HyConomy.format(cost);
                    statusIsError = false;
                } else {
                    statusMessage = switch (result) {
                        case ALREADY_OWN -> "Already claimed!";
                        case CLAIMED_BY_OTHER -> "Claimed by other!";
                        case LIMIT_REACHED -> "Claim limit reached!";
                        case TOO_CLOSE_TO_OTHER_CLAIM -> "Too close to other claim!";
                        default -> "Claim failed!";
                    };
                    statusIsError = true;
                }
            }

            case "unclaim" -> {
                if (town == null) {
                    statusMessage = "Join a town first!";
                    statusIsError = true;
                    return;
                }
                if (!town.isAssistant(playerId)) {
                    statusMessage = "No permission!";
                    statusIsError = true;
                    return;
                }

                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                Vector3d pos = transform.getPosition();
                String worldName = world.getName();
                int chunkX = ChunkUtil.toChunkX(pos.getX());
                int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
                String claimKey = worldName + ":" + chunkX + "," + chunkZ;

                if (!town.ownsClaim(claimKey)) {
                    statusMessage = "Not your claim!";
                    statusIsError = true;
                    return;
                }

                plugin.getClaimStorage().removeClaim(town.getMayorId(), worldName, chunkX, chunkZ);
                town.removeClaim(claimKey);
                townStorage.saveTown(town);
                plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);
                statusMessage = "Unclaimed!";
                statusIsError = false;
            }

            case "deposit" -> {
                if (town == null) {
                    statusMessage = "Join a town first!";
                    statusIsError = true;
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(amountInput);
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid amount!";
                    statusIsError = true;
                    return;
                }

                if (amount <= 0) {
                    statusMessage = "Amount must be positive!";
                    statusIsError = true;
                    return;
                }

                if (!HyConomy.has(playerName, amount)) {
                    statusMessage = "Insufficient funds!";
                    statusIsError = true;
                    return;
                }

                if (!HyConomy.withdraw(playerName, amount)) {
                    statusMessage = "Withdrawal failed!";
                    statusIsError = true;
                    return;
                }

                town.deposit(amount);
                townStorage.saveTown(town);
                statusMessage = "Deposited " + HyConomy.format(amount);
                statusIsError = false;
                amountInput = "";
            }

            case "withdraw" -> {
                if (town == null) {
                    statusMessage = "Join a town first!";
                    statusIsError = true;
                    return;
                }
                if (!town.isAssistant(playerId)) {
                    statusMessage = "No permission!";
                    statusIsError = true;
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(amountInput);
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid amount!";
                    statusIsError = true;
                    return;
                }

                if (amount <= 0) {
                    statusMessage = "Amount must be positive!";
                    statusIsError = true;
                    return;
                }

                if (!town.withdraw(amount)) {
                    statusMessage = "Insufficient town funds!";
                    statusIsError = true;
                    return;
                }

                HyConomy.deposit(playerName, amount);
                townStorage.saveTown(town);
                statusMessage = "Withdrew " + HyConomy.format(amount);
                statusIsError = false;
                amountInput = "";
            }

            case "invite" -> {
                if (town == null) {
                    statusMessage = "Join a town first!";
                    statusIsError = true;
                    return;
                }
                if (!town.isAssistant(playerId)) {
                    statusMessage = "No permission!";
                    statusIsError = true;
                    return;
                }
                if (playerNameInput.isEmpty()) {
                    statusMessage = "Enter player name!";
                    statusIsError = true;
                    return;
                }

                // For now just show a success message - actual invite system would require more complex logic
                statusMessage = "Invited " + playerNameInput + "!";
                statusIsError = false;
                playerNameInput = "";
            }

            case "toggle_open" -> {
                if (town == null || !town.isMayor(playerId)) {
                    statusMessage = "Only mayor can toggle!";
                    statusIsError = true;
                    return;
                }
                Boolean newValue = town.getSettings().toggle("open");
                townStorage.saveTown(town);
                statusMessage = "Open: " + (newValue != null && newValue ? "ON" : "OFF");
                statusIsError = false;
            }

            case "toggle_pvp" -> {
                if (town == null || !town.isMayor(playerId)) {
                    statusMessage = "Only mayor can toggle!";
                    statusIsError = true;
                    return;
                }
                Boolean newValue = town.getSettings().toggle("pvp");
                townStorage.saveTown(town);
                statusMessage = "PvP: " + (newValue != null && newValue ? "ON" : "OFF");
                statusIsError = false;
            }

            case "toggle_explosion" -> {
                if (town == null || !town.isMayor(playerId)) {
                    statusMessage = "Only mayor can toggle!";
                    statusIsError = true;
                    return;
                }
                Boolean newValue = town.getSettings().toggle("explosion");
                townStorage.saveTown(town);
                statusMessage = "Explosions: " + (newValue != null && newValue ? "ON" : "OFF");
                statusIsError = false;
            }

            case "toggle_fire" -> {
                if (town == null || !town.isMayor(playerId)) {
                    statusMessage = "Only mayor can toggle!";
                    statusIsError = true;
                    return;
                }
                Boolean newValue = town.getSettings().toggle("fire");
                townStorage.saveTown(town);
                statusMessage = "Fire: " + (newValue != null && newValue ? "ON" : "OFF");
                statusIsError = false;
            }

            case "leave" -> {
                if (town == null) {
                    statusMessage = "Not in a town!";
                    statusIsError = true;
                    return;
                }
                // Require confirmation
                if (!confirmingLeave) {
                    confirmingLeave = true;
                    statusMessage = "Click again to confirm!";
                    statusIsError = true;
                    return;
                }
                // Confirmed - actually leave
                confirmingLeave = false;
                if (town.isMayor(playerId)) {
                    if (town.getResidentCount() > 1) {
                        statusMessage = "Transfer mayor first!";
                        statusIsError = true;
                        return;
                    }
                    townStorage.deleteTown(town.getName());
                    statusMessage = "Town disbanded!";
                    statusIsError = false;
                } else {
                    town.removeResident(playerId);
                    townStorage.saveTown(town);
                    townStorage.unindexPlayer(playerId);
                    statusMessage = "Left town!";
                    statusIsError = false;
                }
                this.close();
            }

            case "open_log" -> {
                if (town == null || !town.isAssistant(playerId)) {
                    statusMessage = "Only assistants can view log!";
                    statusIsError = true;
                    return;
                }
                this.close();
                Player player = entityStore.getComponent(playerEntityRef, Player.getComponentType());
                if (player != null) {
                    TownLogGui.openFor(plugin, player, playerEntityRef, entityStore, world);
                }
            }
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/hycrown_HyTowny_TownMenu.ui");

        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        // Status message
        cmd.set("#StatusMessage.Text", statusMessage);
        cmd.set("#StatusMessage.Style.TextColor", statusIsError ? "#ff5555" : "#55ff55");

        // Player balance and town bank - always show
        String balanceText = "Your Balance: " + HyConomy.format(HyConomy.getBalance(playerName));
        if (town != null) {
            var upkeepMgr = plugin.getUpkeepManager();
            String upkeepInfo = upkeepMgr != null ? upkeepMgr.formatUpkeepInfo(town) : "";
            balanceText += "  |  Town Bank: " + HyConomy.format(town.getBalance()) + "  |  " + upkeepInfo;
        }
        cmd.set("#PlayerBalance.Text", balanceText);

        // Town info section
        if (town != null) {
            cmd.set("#TownName.Text", town.getName());
            cmd.set("#TownInfo.Text",
                "Mayor: " + town.getMayorName() + "\n" +
                "Residents: " + town.getResidentCount() + "\n" +
                "Claims: " + town.getClaimCount() + "/" + plugin.getPluginConfig().getMaxTownClaims()
            );
            cmd.set("#NoTownMessage.Visible", false);
            cmd.set("#TownInfoPanel.Visible", true);
            cmd.set("#CreateTownPanel.Visible", false);

            // Show/hide buttons based on permissions
            boolean isAssistant = town.isAssistant(playerId);
            boolean isMayor = town.isMayor(playerId);
            cmd.set("#ClaimButton.Visible", isAssistant);
            cmd.set("#UnclaimButton.Visible", isAssistant);
            cmd.set("#InviteButton.Visible", isAssistant);
            cmd.set("#WithdrawButton.Visible", isAssistant);
            cmd.set("#SettingsButton.Visible", isMayor);
        } else {
            cmd.set("#TownInfoPanel.Visible", false);
            cmd.set("#NoTownMessage.Visible", true);
            cmd.set("#NoTownMessage.Text", "You are not in a town.\nCreate one or join an existing town!");
            cmd.set("#CreateTownPanel.Visible", true);

            // Town name input
            cmd.set("#TownNameField.Value", townNameInput);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TownNameField",
                    EventData.of("@TownName", "#TownNameField.Value"), false);

            // Create cost
            double createCost = plugin.getPluginConfig().getTownCreationCost();
            cmd.set("#CreateCost.Text", "Cost: " + HyConomy.format(createCost));

            // Create button
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#CreateTownButton",
                    EventData.of("Action", "create_town"), false);
        }

        // Claim/Unclaim buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimButton",
                EventData.of("Action", "claim"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnclaimButton",
                EventData.of("Action", "unclaim"), false);

        // Bank section
        cmd.set("#AmountField.Value", amountInput);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AmountField",
                EventData.of("@Amount", "#AmountField.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DepositButton",
                EventData.of("Action", "deposit"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawButton",
                EventData.of("Action", "withdraw"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#LogButton",
                EventData.of("Action", "open_log"), false);

        // Show log button for anyone in a town (GUI checks permission internally)
        cmd.set("#LogButton.Visible", town != null);

        // Invite section
        cmd.set("#PlayerNameField.Value", playerNameInput);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlayerNameField",
                EventData.of("@PlayerName", "#PlayerNameField.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#InviteButton",
                EventData.of("Action", "invite"), false);

        // Settings toggles - only for mayors
        if (town != null && town.isMayor(playerId)) {
            var settings = town.getSettings();
            cmd.set("#OpenToggle #CheckBox.Value", settings.isOpenTown());
            cmd.set("#PvPToggle #CheckBox.Value", settings.isPvpEnabled());
            cmd.set("#ExplosionToggle #CheckBox.Value", settings.isExplosionsEnabled());
            cmd.set("#FireToggle #CheckBox.Value", settings.isFireSpreadEnabled());

            // Bind checkbox ValueChanged events
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#OpenToggle #CheckBox",
                    EventData.of("Action", "toggle_open"), false);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PvPToggle #CheckBox",
                    EventData.of("Action", "toggle_pvp"), false);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ExplosionToggle #CheckBox",
                    EventData.of("Action", "toggle_explosion"), false);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FireToggle #CheckBox",
                    EventData.of("Action", "toggle_fire"), false);
        }

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Close", "true"), false);
    }

    /**
     * Open the town GUI for a player.
     */
    public static void openFor(HyTowny plugin, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        PlayerRef playerRef = player.getPlayerRef();
        TownGui gui = new TownGui(playerRef, plugin, world, ref, store);
        world.execute(() -> {
            player.getPageManager().openCustomPage(ref, store, gui);
        });
    }

    /**
     * Data class for handling GUI events.
     */
    public static class TownData {
        static final String KEY_TOWN_NAME = "@TownName";
        static final String KEY_PLAYER_NAME = "@PlayerName";
        static final String KEY_AMOUNT = "@Amount";
        static final String KEY_CLOSE = "Close";
        static final String KEY_TAB = "Tab";
        static final String KEY_ACTION = "Action";

        public static final BuilderCodec<TownData> CODEC = BuilderCodec.<TownData>builder(TownData.class, TownData::new)
                .addField(new KeyedCodec<>(KEY_TOWN_NAME, Codec.STRING),
                        (data, s) -> data.townName = s,
                        data -> data.townName)
                .addField(new KeyedCodec<>(KEY_PLAYER_NAME, Codec.STRING),
                        (data, s) -> data.playerName = s,
                        data -> data.playerName)
                .addField(new KeyedCodec<>(KEY_AMOUNT, Codec.STRING),
                        (data, s) -> data.amount = s,
                        data -> data.amount)
                .addField(new KeyedCodec<>(KEY_CLOSE, Codec.STRING),
                        (data, s) -> data.close = s,
                        data -> data.close)
                .addField(new KeyedCodec<>(KEY_TAB, Codec.STRING),
                        (data, s) -> data.tab = s,
                        data -> data.tab)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                        (data, s) -> data.action = s,
                        data -> data.action)
                .build();

        private String townName;
        private String playerName;
        private String amount;
        private String close;
        private String tab;
        private String action;
    }
}
