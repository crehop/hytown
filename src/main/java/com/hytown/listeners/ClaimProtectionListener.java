package com.hytown.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.InteractionType;
import com.hytown.HyTown;
import com.hytown.managers.ClaimManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for player interaction events for protection checks.
 */
public class ClaimProtectionListener {
    private final HyTown plugin;
    private final ClaimManager claimManager;
    private final HytaleLogger logger;

    // Track player interactions for ECS event correlation
    private static final Map<String, PlayerInteraction> pendingInteractions = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerInteraction> playerLastInteraction = new ConcurrentHashMap<>();

    private static final long INTERACTION_TIMEOUT_MS = 5000;

    public ClaimProtectionListener(HyTown plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
        this.logger = plugin.getLogger();
    }

    /**
     * Register player event handlers.
     */
    public void register(EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);
        eventRegistry.registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    /**
     * Handle player interactions - check claim protection.
     * Primary interactions (attacks) are allowed in wilderness but blocked in towns with PVP off.
     */
    private void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        UUID playerId = player.getUuid();
        InteractionType actionType = event.getActionType();
        String worldName = player.getWorld().getName();

        // For Primary interactions (attacks), only block if in a town/plot with PVP disabled
        if (actionType == InteractionType.Primary) {
            // Check if this is in a claimed town
            int chunkX = targetBlock.getX() >> 4;
            int chunkZ = targetBlock.getZ() >> 4;
            String claimKey = worldName + ":" + chunkX + "," + chunkZ;
            var town = plugin.getTownStorage().getTownByClaimKey(claimKey);

            if (town != null) {
                // Check plot settings first, then fall back to town settings
                var plotSettings = town.getPlotSettings(claimKey);
                boolean pvpEnabled;
                if (plotSettings != null) {
                    pvpEnabled = plotSettings.getEffectivePvp(town.getSettings());
                } else {
                    pvpEnabled = town.getSettings().isPvpEnabled();
                }

                if (!pvpEnabled) {
                    // PVP is disabled - block the attack
                    event.setCancelled(true);
                }
            }
            // In wilderness or town/plot with PVP on - allow the attack
            return;
        }

        // Track interaction for ECS event correlation
        String blockKey = getBlockKey(targetBlock);
        PlayerInteraction interaction = new PlayerInteraction(playerId, worldName, targetBlock, System.currentTimeMillis());
        pendingInteractions.put(blockKey, interaction);
        playerLastInteraction.put(playerId, interaction);

        cleanupOldInteractions();

        // Check if this location is protected (for non-attack interactions)
        boolean canInteract = claimManager.canInteract(playerId, worldName, targetBlock.getX(), targetBlock.getZ());

        if (!canInteract) {
            logger.atFine().log("Blocked interaction: player=%s block=%s action=%s", playerId, targetBlock, actionType);
            event.setCancelled(true);
        }
    }

    // Static accessors for BlockProtectionSystems
    public static PlayerInteraction getInteraction(String blockKey) {
        PlayerInteraction interaction = pendingInteractions.get(blockKey);
        if (interaction != null && !interaction.isExpired()) {
            return interaction;
        }
        return null;
    }

    public static PlayerInteraction findNearbyInteraction(Vector3i targetBlock) {
        String blockKey = getBlockKey(targetBlock);
        PlayerInteraction exact = pendingInteractions.get(blockKey);
        if (exact != null && !exact.isExpired()) {
            return exact;
        }

        for (PlayerInteraction interaction : playerLastInteraction.values()) {
            if (interaction.isExpired()) continue;
            if (interaction.blockPos == null) continue;

            int dx = Math.abs(targetBlock.getX() - interaction.blockPos.getX());
            int dy = Math.abs(targetBlock.getY() - interaction.blockPos.getY());
            int dz = Math.abs(targetBlock.getZ() - interaction.blockPos.getZ());

            if (dx <= 1 && dy <= 1 && dz <= 1) {
                return interaction;
            }
        }
        return null;
    }

    public static void removeInteraction(String blockKey) {
        pendingInteractions.remove(blockKey);
    }

    public static String getBlockKey(Vector3i pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void cleanupOldInteractions() {
        long now = System.currentTimeMillis();
        pendingInteractions.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > INTERACTION_TIMEOUT_MS);
        playerLastInteraction.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > INTERACTION_TIMEOUT_MS);
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef != null) {
            plugin.onPlayerJoin(playerRef.getUuid());
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef != null) {
            UUID playerId = playerRef.getUuid();
            plugin.onPlayerLeave(playerId);
        }
    }

    /**
     * Tracks a player's interaction with a block.
     */
    public static class PlayerInteraction {
        public final UUID playerId;
        public final String worldName;
        public final Vector3i blockPos;
        public final long timestamp;

        public PlayerInteraction(UUID playerId, String worldName, Vector3i blockPos, long timestamp) {
            this.playerId = playerId;
            this.worldName = worldName;
            this.blockPos = blockPos;
            this.timestamp = timestamp;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > INTERACTION_TIMEOUT_MS;
        }
    }
}
