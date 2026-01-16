package com.hytown.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytown.config.PluginConfig;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.data.TrustLevel;
import com.hytown.managers.ClaimManager;
import com.hytown.util.ChunkUtil;
import com.hytown.util.Messages;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS System that intercepts block place events to protect claimed areas.
 * Also handles wild protection for wilderness blocks above Y threshold.
 */
public class BlockPlaceProtectionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private final ClaimManager claimManager;
    private final PluginConfig config;
    private final TownStorage townStorage;
    private final HytaleLogger logger;

    // Rate limit messages - don't spam players
    private static final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 2000; // 2 seconds

    private static final Color RED = new Color(255, 85, 85);

    public BlockPlaceProtectionSystem(ClaimManager claimManager, PluginConfig config,
                                      TownStorage townStorage, HytaleLogger logger) {
        super(PlaceBlockEvent.class);
        this.claimManager = claimManager;
        this.config = config;
        this.townStorage = townStorage;
        this.logger = logger;
    }

    private boolean canSendMessage(UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerId);
        if (lastTime == null || now - lastTime > MESSAGE_COOLDOWN_MS) {
            lastMessageTime.put(playerId, now);
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }

    @Override
    public void handle(int entityIndex, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        // Get the entity that triggered this event
        Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);
        if (entityRef == null) return;

        // Get player components
        Player player = store.getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        UUID playerId = playerRef.getUuid();
        String worldName = player.getWorld().getName();

        // Check if this chunk is claimed
        UUID claimOwner = claimManager.getOwnerAt(worldName, targetBlock.getX(), targetBlock.getZ());

        if (claimOwner != null) {
            // CLAIMED LAND - check permission
            int chunkX = ChunkUtil.toChunkX(targetBlock.getX());
            int chunkZ = ChunkUtil.toChunkZ(targetBlock.getZ());
            String claimKey = worldName + ":" + chunkX + "," + chunkZ;

            // Check if it's a town claim
            Town town = townStorage != null ? townStorage.getTownByClaimKey(claimKey) : null;

            if (town != null) {
                // Town claim - check if player is a member
                if (town.isMember(playerId)) {
                    return; // Town members can build
                }
                // Check town settings for outsiders
                if (town.getSettings().canOutsiderBuild()) {
                    return;
                }
                // Not allowed
                event.setCancelled(true);
                if (canSendMessage(playerId)) {
                    player.sendMessage(Message.raw("You cannot build in " + town.getName()).color(RED));
                }
                return;
            }

            // Personal claim - check trust level
            if (!claimManager.hasPermissionAt(playerId, worldName, targetBlock.getX(), targetBlock.getZ(), TrustLevel.BUILD)) {
                event.setCancelled(true);
                if (canSendMessage(playerId)) {
                    player.sendMessage(Messages.cannotBuildHere());
                }
            }
        } else {
            // WILDERNESS - check wild protection
            if (config.isWildProtectionEnabled()) {
                // Check bypass permission
                if (player.hasPermission("towny.wild.bypass")) {
                    return;
                }

                int blockY = targetBlock.getY();
                int minY = config.getWildProtectionMinY();

                if (blockY > minY) {
                    // Above threshold - check if building is allowed
                    if (!config.isWildBuildBelowAllowed()) {
                        // Wild building is disabled above threshold
                        event.setCancelled(true);
                        if (canSendMessage(playerId)) {
                            player.sendMessage(Message.raw("Wild protection: Cannot place blocks above Y=" + minY).color(RED));
                        }
                    }
                } else {
                    // Below threshold - check if building below is allowed
                    if (!config.isWildBuildBelowAllowed()) {
                        event.setCancelled(true);
                        if (canSendMessage(playerId)) {
                            player.sendMessage(Message.raw("Wilderness building is disabled.").color(RED));
                        }
                    }
                }
            }
        }
    }
}
