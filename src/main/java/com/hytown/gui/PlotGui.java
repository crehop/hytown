package com.hytown.gui;

import com.hytown.HyTown;
import com.hytown.data.PlotSettings;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
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
import java.util.*;

/**
 * GUI for plot management - per-chunk settings within a town.
 */
public class PlotGui extends InteractiveCustomUIPage<PlotGui.PlotData> {

    private final HyTown plugin;
    private final World world;
    private final Ref<EntityStore> playerEntityRef;
    private final Store<EntityStore> entityStore;
    private final String claimKey;
    private final Town town;

    private String playerNameInput = "";
    private String ownerNameInput = "";
    private String statusMessage = "";
    private boolean statusIsError = false;
    private List<UUID> displayedAllowedIds = new ArrayList<>();
    private static final int MAX_DISPLAYED_ALLOWED = 8;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);

    public PlotGui(@Nonnull PlayerRef playerRef, HyTown plugin, World world,
                   Ref<EntityStore> playerEntityRef, Store<EntityStore> entityStore,
                   String claimKey, Town town) {
        super(playerRef, CustomPageLifetime.CanDismiss, PlotData.CODEC);
        this.plugin = plugin;
        this.world = world;
        this.playerEntityRef = playerEntityRef;
        this.entityStore = entityStore;
        this.claimKey = claimKey;
        this.town = town;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlotData data) {
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

        // Handle town button - go back to town menu
        if ("open_town".equals(data.action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                // Schedule the new page to open after this event handler completes
                world.execute(() -> {
                    TownGui.openForDirect(plugin, player, playerEntityRef, entityStore, world);
                });
            }
            return;
        }

        // Handle text input changes
        boolean textOnly = false;
        if (data.playerName != null) {
            this.playerNameInput = data.playerName;
            textOnly = true;
        }
        if (data.ownerName != null) {
            this.ownerNameInput = data.ownerName;
            textOnly = true;
        }

        if (textOnly && data.action == null) {
            this.sendUpdate();
            return;
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
        PlotSettings plotSettings = town.getOrCreatePlotSettings(claimKey);
        UUID plotOwner = town.getPlotOwner(claimKey);

        // Check permissions - must be plot owner, mayor, or assistant
        boolean canManage = town.isMayor(playerId) ||
                            town.getAssistants().contains(playerId) ||
                            (plotOwner != null && plotOwner.equals(playerId));

        if (!canManage) {
            statusMessage = "No permission to manage this plot!";
            statusIsError = true;
            return;
        }

        switch (action) {
            case "toggle_protection" -> {
                plotSettings.setOwnerProtection(!plotSettings.isOwnerProtection());
                townStorage.saveTown(town);
                statusMessage = "Owner Protection: " + (plotSettings.isOwnerProtection() ? "ON" : "OFF");
                statusIsError = false;
            }

            case "toggle_pvp" -> {
                Boolean newValue = plotSettings.toggle("pvp");
                townStorage.saveTown(town);
                statusMessage = "PvP: " + formatOverrideValue(newValue);
                statusIsError = false;
            }

            case "toggle_explosion" -> {
                Boolean newValue = plotSettings.toggle("explosion");
                townStorage.saveTown(town);
                statusMessage = "Explosions: " + formatOverrideValue(newValue);
                statusIsError = false;
            }

            case "toggle_fire" -> {
                Boolean newValue = plotSettings.toggle("fire");
                townStorage.saveTown(town);
                statusMessage = "Fire Spread: " + formatOverrideValue(newValue);
                statusIsError = false;
            }

            case "toggle_mobs" -> {
                Boolean newValue = plotSettings.toggle("mobs");
                townStorage.saveTown(town);
                statusMessage = "Mob Spawning: " + formatOverrideValue(newValue);
                statusIsError = false;
            }

            case "add_player" -> {
                if (playerNameInput.isEmpty()) {
                    statusMessage = "Enter a player name!";
                    statusIsError = true;
                    return;
                }

                // Find player UUID by name from town residents
                UUID targetId = null;
                String targetName = null;
                for (Map.Entry<UUID, String> entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(playerNameInput)) {
                        targetId = entry.getKey();
                        targetName = entry.getValue();
                        break;
                    }
                }

                if (targetId == null) {
                    statusMessage = "Player not found in town!";
                    statusIsError = true;
                    return;
                }

                if (plotSettings.isAllowedPlayer(targetId)) {
                    statusMessage = targetName + " already has access!";
                    statusIsError = true;
                    return;
                }

                plotSettings.addAllowedPlayer(targetId, targetName);
                townStorage.saveTown(town);
                statusMessage = "Added " + targetName + " to allowed list!";
                statusIsError = false;
                playerNameInput = "";
            }

            case "set_owner" -> {
                // Only mayor/assistant can set plot owner
                if (!town.isAssistant(playerId)) {
                    statusMessage = "Only assistants can set plot owner!";
                    statusIsError = true;
                    return;
                }

                if (ownerNameInput.isEmpty()) {
                    // Clear owner
                    town.setPlotOwner(claimKey, null);
                    townStorage.saveTown(town);
                    statusMessage = "Plot owner cleared!";
                    statusIsError = false;
                    return;
                }

                // Find player UUID by name from town residents
                UUID targetId = null;
                String targetName = null;
                for (Map.Entry<UUID, String> entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(ownerNameInput)) {
                        targetId = entry.getKey();
                        targetName = entry.getValue();
                        break;
                    }
                }

                if (targetId == null) {
                    statusMessage = "Player not found in town!";
                    statusIsError = true;
                    return;
                }

                town.setPlotOwner(claimKey, targetId);
                townStorage.saveTown(town);
                statusMessage = "Plot owner set to " + targetName + "!";
                statusIsError = false;
                ownerNameInput = "";
            }

            default -> {
                // Handle remove_X actions for allowed players
                if (action.startsWith("remove_")) {
                    try {
                        int index = Integer.parseInt(action.substring(7)) - 1;
                        if (index >= 0 && index < displayedAllowedIds.size()) {
                            UUID targetId = displayedAllowedIds.get(index);
                            String targetName = plotSettings.getAllowedPlayerName(targetId);
                            plotSettings.removeAllowedPlayer(targetId);
                            townStorage.saveTown(town);
                            statusMessage = "Removed " + targetName + " from allowed list!";
                            statusIsError = false;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    private String formatOverrideValue(Boolean value) {
        if (value == null) return "Use Town Default";
        return value ? "ON" : "OFF";
    }

    /**
     * Get color for status indicator.
     * Grey (#888888) = using town default
     * Green (#55ff55) = ON
     * Red (#ff5555) = OFF
     */
    private String getIndicatorColor(Boolean override, boolean effectiveValue) {
        if (override == null) {
            return "#888888"; // Grey for town default
        }
        return effectiveValue ? "#55ff55" : "#ff5555"; // Green for ON, Red for OFF
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/hycrown_HyTown_PlotMenu.ui");

        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        UUID playerId = playerRef.getUuid();
        PlotSettings plotSettings = town.getOrCreatePlotSettings(claimKey);
        UUID plotOwner = town.getPlotOwner(claimKey);

        // Parse claim key for display
        String[] parts = claimKey.split(":");
        String worldName = parts[0];
        String[] coords = parts[1].split(",");
        int chunkX = Integer.parseInt(coords[0]);
        int chunkZ = Integer.parseInt(coords[1]);

        // Status message
        cmd.set("#StatusMessage.Text", statusMessage);
        cmd.set("#StatusMessage.Style.TextColor", statusIsError ? "#ff5555" : "#55ff55");

        // Plot info
        cmd.set("#PlotTitle.Text", "Plot [" + chunkX + ", " + chunkZ + "]");
        cmd.set("#TownName.Text", "Town: " + town.getName());

        // Plot owner
        String ownerName = plotOwner != null ? town.getResidentName(plotOwner) : "None";
        cmd.set("#PlotOwner.Text", "Owner: " + ownerName);

        // Status indicators - show current effective state with colors
        var townSettings = town.getSettings();
        Boolean pvpOverride = plotSettings.getPvpEnabled();
        Boolean fireOverride = plotSettings.getFireSpreadEnabled();
        Boolean explosionOverride = plotSettings.getExplosionsEnabled();
        Boolean mobsOverride = plotSettings.getMobSpawningEnabled();

        // PvP indicator
        boolean pvpEffective = pvpOverride != null ? pvpOverride : townSettings.isPvpEnabled();
        cmd.set("#PvPIndicator.Style.TextColor", getIndicatorColor(pvpOverride, pvpEffective));

        // Fire indicator
        boolean fireEffective = fireOverride != null ? fireOverride : townSettings.isFireSpreadEnabled();
        cmd.set("#FireIndicator.Style.TextColor", getIndicatorColor(fireOverride, fireEffective));

        // Explosion indicator
        boolean explosionEffective = explosionOverride != null ? explosionOverride : townSettings.isExplosionsEnabled();
        cmd.set("#ExplosionIndicator.Style.TextColor", getIndicatorColor(explosionOverride, explosionEffective));

        // Mobs indicator
        boolean mobsEffective = mobsOverride != null ? mobsOverride : townSettings.isMobSpawningEnabled();
        cmd.set("#MobsIndicator.Style.TextColor", getIndicatorColor(mobsOverride, mobsEffective));

        // Check if player can manage this plot
        boolean canManage = town.isMayor(playerId) ||
                            town.getAssistants().contains(playerId) ||
                            (plotOwner != null && plotOwner.equals(playerId));

        // Set Owner section (only for assistants+)
        boolean canSetOwner = town.isAssistant(playerId);
        cmd.set("#SetOwnerSection.Visible", canSetOwner);
        if (canSetOwner) {
            cmd.set("#OwnerNameField.Value", ownerNameInput);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#OwnerNameField",
                    EventData.of("@OwnerName", "#OwnerNameField.Value"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetOwnerButton",
                    EventData.of("Action", "set_owner"), false);
        }

        // Settings section
        cmd.set("#SettingsSection.Visible", canManage);
        if (canManage) {
            // Owner Protection toggle
            cmd.set("#ProtectionToggle #CheckBox.Value", plotSettings.isOwnerProtection());
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ProtectionToggle #CheckBox",
                    EventData.of("Action", "toggle_protection"), false);

            // PvP toggle button
            cmd.set("#PvPToggle.Text", "PvP: " + formatOverrideValue(pvpOverride) +
                    (pvpOverride == null ? " (Town: " + (townSettings.isPvpEnabled() ? "ON" : "OFF") + ")" : ""));
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#PvPToggle",
                    EventData.of("Action", "toggle_pvp"), false);

            // Explosions toggle button
            cmd.set("#ExplosionToggle.Text", "Explosions: " + formatOverrideValue(explosionOverride) +
                    (explosionOverride == null ? " (Town: " + (townSettings.isExplosionsEnabled() ? "ON" : "OFF") + ")" : ""));
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ExplosionToggle",
                    EventData.of("Action", "toggle_explosion"), false);

            // Fire Spread toggle button
            cmd.set("#FireToggle.Text", "Fire Spread: " + formatOverrideValue(fireOverride) +
                    (fireOverride == null ? " (Town: " + (townSettings.isFireSpreadEnabled() ? "ON" : "OFF") + ")" : ""));
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#FireToggle",
                    EventData.of("Action", "toggle_fire"), false);

            // Mob Spawning toggle button
            cmd.set("#MobsToggle.Text", "Mob Spawning: " + formatOverrideValue(mobsOverride) +
                    (mobsOverride == null ? " (Town: " + (townSettings.isMobSpawningEnabled() ? "ON" : "OFF") + ")" : ""));
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#MobsToggle",
                    EventData.of("Action", "toggle_mobs"), false);
        }

        // Allowed Players section (only visible when Owner Protection is ON)
        boolean showAllowedSection = canManage && plotSettings.isOwnerProtection();
        cmd.set("#AllowedPlayersSection.Visible", showAllowedSection);
        if (showAllowedSection) {
            // Add player input
            cmd.set("#AddPlayerField.Value", playerNameInput);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AddPlayerField",
                    EventData.of("@PlayerName", "#AddPlayerField.Value"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#AddPlayerButton",
                    EventData.of("Action", "add_player"), false);

            // Allowed players list
            displayedAllowedIds.clear();
            Set<UUID> allowedPlayers = plotSettings.getAllowedPlayers();
            List<UUID> allowedList = new ArrayList<>(allowedPlayers);

            for (int i = 0; i < MAX_DISPLAYED_ALLOWED; i++) {
                int slot = i + 1;
                String playerGroup = "#Allowed" + slot;

                if (i < allowedList.size()) {
                    UUID allowedId = allowedList.get(i);
                    displayedAllowedIds.add(allowedId);
                    String name = plotSettings.getAllowedPlayerName(allowedId);

                    cmd.set(playerGroup + ".Visible", true);
                    cmd.set(playerGroup + "Name.Text", name);
                    evt.addEventBinding(CustomUIEventBindingType.Activating, playerGroup + "Remove",
                            EventData.of("Action", "remove_" + slot), false);
                } else {
                    cmd.set(playerGroup + ".Visible", false);
                }
            }
        }

        // Town button - go back to town menu
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TownButton",
                EventData.of("Action", "open_town"), false);

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Close", "true"), false);
    }

    /**
     * Open the plot GUI for a player at their current location.
     */
    public static void openFor(HyTown plugin, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        PlayerRef playerRef = player.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        // Get player's current chunk
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();
        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check if this chunk is owned by a town
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            playerRef.sendMessage(Message.raw("This chunk is not claimed by a town!").color(RED));
            return;
        }

        // Check if player can access this plot's settings
        UUID plotOwner = town.getPlotOwner(claimKey);
        boolean canAccess = town.isMayor(playerId) ||
                           town.getAssistants().contains(playerId) ||
                           (plotOwner != null && plotOwner.equals(playerId));

        if (!canAccess) {
            playerRef.sendMessage(Message.raw("You don't have permission to manage this plot!").color(RED));
            return;
        }

        PlotGui gui = new PlotGui(playerRef, plugin, world, ref, store, claimKey, town);
        world.execute(() -> {
            player.getPageManager().openCustomPage(ref, store, gui);
        });
    }

    /**
     * Open the plot GUI directly without world.execute wrapper.
     * Used when calling from another GUI that's already handling the scheduling.
     */
    public static void openForDirect(HyTown plugin, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        PlayerRef playerRef = player.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        // Get player's current chunk
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();
        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        // Check if this chunk is owned by a town
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            playerRef.sendMessage(Message.raw("This chunk is not claimed by a town!").color(RED));
            return;
        }

        // Check if player can access this plot's settings
        UUID plotOwner = town.getPlotOwner(claimKey);
        boolean canAccess = town.isMayor(playerId) ||
                           town.getAssistants().contains(playerId) ||
                           (plotOwner != null && plotOwner.equals(playerId));

        if (!canAccess) {
            playerRef.sendMessage(Message.raw("You don't have permission to manage this plot!").color(RED));
            return;
        }

        PlotGui gui = new PlotGui(playerRef, plugin, world, ref, store, claimKey, town);
        player.getPageManager().openCustomPage(ref, store, gui);
    }

    /**
     * Data class for handling GUI events.
     */
    public static class PlotData {
        static final String KEY_PLAYER_NAME = "@PlayerName";
        static final String KEY_OWNER_NAME = "@OwnerName";
        static final String KEY_CLOSE = "Close";
        static final String KEY_OPEN_TOWN = "OpenTown";
        static final String KEY_ACTION = "Action";

        public static final BuilderCodec<PlotData> CODEC = BuilderCodec.<PlotData>builder(PlotData.class, PlotData::new)
                .addField(new KeyedCodec<>(KEY_PLAYER_NAME, Codec.STRING),
                        (data, s) -> data.playerName = s,
                        data -> data.playerName)
                .addField(new KeyedCodec<>(KEY_OWNER_NAME, Codec.STRING),
                        (data, s) -> data.ownerName = s,
                        data -> data.ownerName)
                .addField(new KeyedCodec<>(KEY_CLOSE, Codec.STRING),
                        (data, s) -> data.close = s,
                        data -> data.close)
                .addField(new KeyedCodec<>(KEY_OPEN_TOWN, Codec.STRING),
                        (data, s) -> data.openTown = s,
                        data -> data.openTown)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                        (data, s) -> data.action = s,
                        data -> data.action)
                .build();

        private String playerName;
        private String ownerName;
        private String close;
        private String openTown;
        private String action;
    }

    private static final Color RED_COLOR = new Color(255, 85, 85);
}
