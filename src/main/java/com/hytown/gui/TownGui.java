package com.hytown.gui;

import com.hycrown.hyconomy.HyConomy;
import com.hytown.HyTown;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.managers.ClaimManager;
import com.hytown.util.ChunkUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI for town management - creating, claiming, inviting, and managing town settings.
 */
public class TownGui extends InteractiveCustomUIPage<TownGui.TownData> {

    private final HyTown plugin;
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
    private boolean confirmingUnclaim = false;
    private String pendingUnclaimKey = null;
    private List<UUID> displayedMemberIds = new ArrayList<>();
    private static final int MAX_DISPLAYED_MEMBERS = 8;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);

    public TownGui(@Nonnull PlayerRef playerRef, HyTown plugin, World world,
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

        // Handle GUI navigation actions first
        if ("open_plot".equals(data.action)) {
            Town town = plugin.getTownStorage().getPlayerTown(playerId);
            if (town == null) {
                statusMessage = "Join a town first!";
                statusIsError = true;
            } else {
                Player player = entityStore.getComponent(playerEntityRef, Player.getComponentType());
                if (player != null) {
                    // Schedule the new page to open after this event handler completes
                    world.execute(() -> {
                        PlotGui.openForDirect(plugin, player, playerEntityRef, entityStore, world);
                    });
                }
                return;
            }
        }
        if ("open_log".equals(data.action)) {
            Town town = plugin.getTownStorage().getPlayerTown(playerId);
            if (town == null || !town.isAssistant(playerId)) {
                statusMessage = "Only assistants can view log!";
                statusIsError = true;
            } else {
                Player player = entityStore.getComponent(playerEntityRef, Player.getComponentType());
                if (player != null) {
                    // Schedule the new page to open after this event handler completes
                    world.execute(() -> {
                        TownLogGui.openForDirect(plugin, player, playerEntityRef, entityStore, world);
                    });
                }
                return;
            }
        }

        // Handle other actions
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
                    confirmingUnclaim = false;
                    pendingUnclaimKey = null;
                    return;
                }

                // Require confirmation
                if (!confirmingUnclaim || !claimKey.equals(pendingUnclaimKey)) {
                    confirmingUnclaim = true;
                    pendingUnclaimKey = claimKey;
                    statusMessage = "Click Unclaim again to confirm [" + chunkX + "," + chunkZ + "]";
                    statusIsError = true;
                    return;
                }

                // Confirmed - actually unclaim
                confirmingUnclaim = false;
                pendingUnclaimKey = null;

                plugin.getClaimStorage().removeClaim(town.getMayorId(), worldName, chunkX, chunkZ);
                town.removeClaim(claimKey);
                town.setPlotOwner(claimKey, null); // Clear plot owner
                townStorage.saveTown(town);
                plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);
                statusMessage = "Unclaimed [" + chunkX + "," + chunkZ + "]! Remaining: " + town.getClaimCount();
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

                // Find target player online
                UUID targetId = null;
                String targetName = playerNameInput;
                for (World w : HyTown.WORLDS.values()) {
                    for (Player p : w.getPlayers()) {
                        if (p.getDisplayName().equalsIgnoreCase(playerNameInput)) {
                            targetId = p.getUuid();
                            targetName = p.getDisplayName();
                            break;
                        }
                    }
                    if (targetId != null) break;
                }

                if (targetId == null) {
                    statusMessage = "Player not found online!";
                    statusIsError = true;
                    return;
                }

                // Check if already in a town
                Town existingTown = townStorage.getPlayerTown(targetId);
                if (existingTown != null) {
                    statusMessage = targetName + " is in another town!";
                    statusIsError = true;
                    return;
                }

                // Check if already invited
                if (townStorage.hasInvite(targetId, town.getName())) {
                    statusMessage = targetName + " already invited!";
                    statusIsError = true;
                    return;
                }

                // Store the invite
                townStorage.addInvite(targetId, town.getName());

                // Send message to target player
                for (World w : HyTown.WORLDS.values()) {
                    for (Player p : w.getPlayers()) {
                        if (p.getUuid().equals(targetId)) {
                            p.sendMessage(Message.raw("You have been invited to join " + town.getName() + "!").color(GREEN));
                            p.sendMessage(Message.raw("Invited by: " + playerName).color(new Color(255, 255, 255)));
                            p.sendMessage(Message.raw("Type /town join " + town.getName() + " to accept").color(new Color(255, 255, 85)));
                            break;
                        }
                    }
                }

                statusMessage = "Invited " + targetName + "!";
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

            // Member promote/demote actions
            default -> {
                if (action.startsWith("promote_") || action.startsWith("demote_")) {
                    if (town == null || !town.isMayor(playerId)) {
                        statusMessage = "Only mayor can manage ranks!";
                        statusIsError = true;
                        return;
                    }

                    try {
                        int memberIndex = Integer.parseInt(action.substring(action.lastIndexOf("_") + 1)) - 1;
                        if (memberIndex >= 0 && memberIndex < displayedMemberIds.size()) {
                            UUID targetId = displayedMemberIds.get(memberIndex);
                            String targetName = town.getResidentName(targetId);

                            if (town.isMayor(targetId)) {
                                statusMessage = "Can't change mayor's rank!";
                                statusIsError = true;
                                return;
                            }

                            if (action.startsWith("promote_")) {
                                // Resident -> Assistant
                                if (!town.getAssistants().contains(targetId)) {
                                    town.promoteToAssistant(targetId);
                                    town.logRankChange(playerId, playerName, targetName, "Assistant");
                                    townStorage.saveTown(town);
                                    statusMessage = targetName + " promoted to Assistant!";
                                    statusIsError = false;
                                } else {
                                    statusMessage = targetName + " is already Assistant!";
                                    statusIsError = true;
                                }
                            } else {
                                // Assistant -> Resident (demote) or Kick
                                if (town.getAssistants().contains(targetId)) {
                                    town.demoteFromAssistant(targetId);
                                    town.logRankChange(playerId, playerName, targetName, "Resident");
                                    townStorage.saveTown(town);
                                    statusMessage = targetName + " demoted to Resident!";
                                    statusIsError = false;
                                } else {
                                    // Already resident - kick them
                                    town.removeResident(targetId);
                                    town.logMemberKick(playerId, playerName, targetName);
                                    townStorage.saveTown(town);
                                    townStorage.unindexPlayer(targetId);
                                    statusMessage = targetName + " kicked from town!";
                                    statusIsError = false;
                                }
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/hycrown_HyTown_TownMenu.ui");

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

            // Build info text with first claim coords
            StringBuilder infoText = new StringBuilder();
            infoText.append("Mayor: ").append(town.getMayorName()).append("\n");
            infoText.append("Residents: ").append(town.getResidentCount()).append("\n");
            infoText.append("Claims: ").append(town.getClaimCount()).append("/").append(plugin.getPluginConfig().getMaxTownClaims());

            // Show first claim coordinates (converted to block coords)
            String firstClaim = town.getFirstClaimKey();
            if (firstClaim != null) {
                int[] chunkCoords = Town.parseClaimCoords(firstClaim);
                if (chunkCoords != null) {
                    // Convert chunk coords to block coords (center of chunk)
                    int blockX = chunkCoords[0] * 16 + 8;
                    int blockZ = chunkCoords[1] * 16 + 8;
                    infoText.append("\nOrigin: X=").append(blockX).append(", Z=").append(blockZ);
                }
            }

            cmd.set("#TownInfo.Text", infoText.toString());
            cmd.set("#NoTownMessage.Visible", false);
            cmd.set("#TownInfoPanel.Visible", true);
            cmd.set("#CreateTownPanel.Visible", false);

            // Show/hide buttons based on permissions
            boolean isAssistant = town.isAssistant(playerId);
            boolean isMayor = town.isMayor(playerId);
            cmd.set("#ClaimButton.Visible", isAssistant);
            cmd.set("#UnclaimButton.Visible", isAssistant);
            cmd.set("#PlotButton.Visible", true);
            cmd.set("#InviteButton.Visible", isAssistant);
            cmd.set("#WithdrawButton.Visible", isAssistant);
            cmd.set("#SettingsButton.Visible", isMayor);
        } else {
            cmd.set("#TownInfoPanel.Visible", false);
            cmd.set("#NoTownMessage.Visible", true);
            cmd.set("#NoTownLabel.Text", "You are not in a town.\nCreate one or join an existing town!");
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

        // Claim/Unclaim/Plot buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimButton",
                EventData.of("Action", "claim"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnclaimButton",
                EventData.of("Action", "unclaim"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PlotButton",
                EventData.of("Action", "open_plot"), false);

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

        // Members section
        cmd.set("#MembersSection.Visible", town != null);
        if (town != null) {
            boolean isMayor = town.isMayor(playerId);
            displayedMemberIds.clear();

            // Get all residents sorted: Mayor first, then Assistants, then Residents
            List<UUID> allMembers = new ArrayList<>();
            // Add mayor first
            allMembers.add(town.getMayorId());
            // Add assistants
            for (UUID id : town.getAssistants()) {
                if (!allMembers.contains(id)) allMembers.add(id);
            }
            // Add remaining residents
            for (UUID id : town.getResidents()) {
                if (!allMembers.contains(id)) allMembers.add(id);
            }

            // Populate member slots
            for (int i = 0; i < MAX_DISPLAYED_MEMBERS; i++) {
                int slot = i + 1;
                String memberGroup = "#Member" + slot;

                if (i < allMembers.size()) {
                    UUID memberId = allMembers.get(i);
                    displayedMemberIds.add(memberId);
                    String name = town.getResidentName(memberId);
                    String rank;
                    String rankColor;

                    if (town.isMayor(memberId)) {
                        rank = "Mayor";
                        rankColor = "#ffaa00";  // Gold
                    } else if (town.getAssistants().contains(memberId)) {
                        rank = "Assistant";
                        rankColor = "#55ff55";  // Green
                    } else {
                        rank = "Resident";
                        rankColor = "#aaaaaa";  // Gray
                    }

                    cmd.set(memberGroup + ".Visible", true);
                    cmd.set(memberGroup + "Name.Text", name);
                    cmd.set(memberGroup + "Rank.Text", rank);
                    cmd.set(memberGroup + "Rank.Style.TextColor", rankColor);

                    // Only show +/- buttons to mayor, and not for the mayor themselves
                    boolean canManage = isMayor && !town.isMayor(memberId);
                    cmd.set(memberGroup + "Promote.Visible", canManage);
                    cmd.set(memberGroup + "Demote.Visible", canManage);

                    if (canManage) {
                        evt.addEventBinding(CustomUIEventBindingType.Activating, memberGroup + "Promote",
                                EventData.of("Action", "promote_" + slot), false);
                        evt.addEventBinding(CustomUIEventBindingType.Activating, memberGroup + "Demote",
                                EventData.of("Action", "demote_" + slot), false);
                    }
                } else {
                    cmd.set(memberGroup + ".Visible", false);
                }
            }
        }

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
    public static void openFor(HyTown plugin, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        PlayerRef playerRef = player.getPlayerRef();
        TownGui gui = new TownGui(playerRef, plugin, world, ref, store);
        world.execute(() -> {
            player.getPageManager().openCustomPage(ref, store, gui);
        });
    }

    /**
     * Open the town GUI directly without world.execute wrapper.
     * Used when calling from another GUI that's already handling the scheduling.
     */
    public static void openForDirect(HyTown plugin, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        PlayerRef playerRef = player.getPlayerRef();
        TownGui gui = new TownGui(playerRef, plugin, world, ref, store);
        player.getPageManager().openCustomPage(ref, store, gui);
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
        static final String KEY_MEMBER_ID = "MemberId";

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
                .addField(new KeyedCodec<>(KEY_MEMBER_ID, Codec.STRING),
                        (data, s) -> data.memberId = s,
                        data -> data.memberId)
                .build();

        private String townName;
        private String playerName;
        private String amount;
        private String close;
        private String tab;
        private String action;
        private String memberId;
    }
}
