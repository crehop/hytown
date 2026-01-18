package com.hytown.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.NewSpawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytown.managers.ClaimManager;

import javax.annotation.Nonnull;

/**
 * ECS System that despawns creatures (NPCs, mobs) that spawn in claimed town territory.
 * Projectiles (arrows, daggers, spears, axes, bombs, etc.), items, and players are allowed.
 *
 * Uses RefChangeSystem to detect when entities get the NewSpawnComponent (just spawned).
 */
public class TownCreatureDespawnSystem extends RefChangeSystem<EntityStore, NewSpawnComponent> {

    private final ClaimManager claimManager;
    private final HytaleLogger logger;

    public TownCreatureDespawnSystem(ClaimManager claimManager, HytaleLogger logger) {
        this.claimManager = claimManager;
        this.logger = logger;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NewSpawnComponent.getComponentType();
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, NewSpawnComponent> componentType() {
        return NewSpawnComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> entityRef, @Nonnull NewSpawnComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Entity just spawned - check if it should be despawned
        checkAndDespawn(entityRef, store, commandBuffer);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> entityRef, @Nonnull NewSpawnComponent oldComponent,
                                @Nonnull NewSpawnComponent newComponent, @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Component was updated - not typically used for NewSpawnComponent
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> entityRef, @Nonnull NewSpawnComponent component,
                                    @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Component was removed - nothing to do
    }

    private void checkAndDespawn(Ref<EntityStore> entityRef, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Skip if it's a player
            PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
            if (playerRef != null) return;

            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player != null) return;

            // Skip if it's a projectile (arrows, daggers, spears, axes, bombs, etc.)
            Projectile projectile = store.getComponent(entityRef, Projectile.getComponentType());
            if (projectile != null) return;

            // Skip if it's an item drop
            ItemComponent itemComponent = store.getComponent(entityRef, ItemComponent.getComponentType());
            if (itemComponent != null) return;

            // Skip if already marked for despawn
            DespawnComponent despawnCheck = store.getComponent(entityRef, DespawnComponent.getComponentType());
            if (despawnCheck != null) return;

            // Get entity position
            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d pos = transform.getPosition();
            if (pos == null) return;

            int blockX = (int) Math.floor(pos.getX());
            int blockZ = (int) Math.floor(pos.getZ());

            // Get world name from store
            String worldName;
            try {
                worldName = store.getExternalData().getWorld().getName();
            } catch (Exception e) {
                return; // Can't determine world, skip
            }

            // Check if this position is in a claimed chunk
            if (claimManager.getOwnerAt(worldName, blockX, blockZ) != null) {
                // Entity spawned in claimed territory - despawn it immediately
                TimeResource timeResource = store.getResource(TimeResource.getResourceType());
                if (timeResource != null) {
                    DespawnComponent despawn = DespawnComponent.despawnInSeconds(timeResource, 0);
                    commandBuffer.addComponent(entityRef, DespawnComponent.getComponentType(), despawn);
                }
            }
        } catch (Exception e) {
            // Silently ignore errors to prevent spam
        }
    }
}
