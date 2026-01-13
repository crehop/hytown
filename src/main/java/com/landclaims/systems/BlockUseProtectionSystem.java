package com.landclaims.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.landclaims.listeners.ClaimProtectionListener;
import com.landclaims.listeners.ClaimProtectionListener.PlayerInteraction;
import com.landclaims.managers.ClaimManager;

import java.util.UUID;

/**
 * ECS System that intercepts block use events (chests, doors, etc.) to protect claimed areas.
 */
public class BlockUseProtectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private final ClaimManager claimManager;

    public BlockUseProtectionSystem(ClaimManager claimManager) {
        super(UseBlockEvent.Pre.class);
        this.claimManager = claimManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, UseBlockEvent.Pre event) {
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        String blockKey = ClaimProtectionListener.getBlockKey(targetBlock);
        PlayerInteraction interaction = ClaimProtectionListener.getInteraction(blockKey);

        if (interaction != null) {
            if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                    targetBlock.getX(), targetBlock.getZ())) {
                event.setCancelled(true);
            }
        } else {
            interaction = ClaimProtectionListener.findNearbyInteraction(targetBlock);
            if (interaction != null) {
                if (!claimManager.canInteract(interaction.playerId, interaction.worldName,
                        targetBlock.getX(), targetBlock.getZ())) {
                    event.setCancelled(true);
                }
            } else {
                // No player tracked - protect claimed chunks
                String worldName = "default";
                UUID owner = claimManager.getOwnerAt(worldName, targetBlock.getX(), targetBlock.getZ());
                if (owner != null) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
