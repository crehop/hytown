package com.hytown.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytown.commands.WildernessCommand;
import com.hytown.config.PluginConfig;
import com.hytown.config.WildernessHarvestConfig;
import com.hytown.managers.ClaimManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS System that gives players configurable item drops when they attempt to break
 * blocks in the wilderness (even though the block is protected).
 *
 * Drops are configured via wilderness_harvest.json with block patterns and item drop chances.
 */
public class WildernessHarvestSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final ClaimManager claimManager;
    private final PluginConfig config;
    private final WildernessHarvestConfig harvestConfig;
    private final HytaleLogger logger;
    private final Random random = new Random();

    // Rate limit harvesting - prevent spam clicking
    private static final Map<UUID, Long> lastHarvestTime = new ConcurrentHashMap<>();

    // Message colors
    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color AQUA = new Color(85, 255, 255);
    private static final Color RED = new Color(255, 85, 85);


    public WildernessHarvestSystem(ClaimManager claimManager, PluginConfig config,
                                    WildernessHarvestConfig harvestConfig, HytaleLogger logger) {
        super(BreakBlockEvent.class);
        this.claimManager = claimManager;
        this.config = config;
        this.harvestConfig = harvestConfig;
        this.logger = logger;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // Run FIRST to intercept before other systems
        return Collections.singleton(RootDependency.first());
    }

    @Override
    public void handle(int entityIndex, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
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

        // Only apply in wilderness (unclaimed areas)
        UUID claimOwner = claimManager.getOwnerAt(worldName, targetBlock.getX(), targetBlock.getZ());
        if (claimOwner != null) {
            // This is claimed land, not wilderness - don't give harvest loot
            return;
        }

        // Check if wilderness protection is actually enabled
        if (!config.isWildProtectionEnabled()) {
            return;
        }

        // Check bypass permission - if they can bypass, they break blocks normally
        if (player.hasPermission("hytown.wild.bypass")) {
            return;
        }

        // Check if block is above wilderness protection threshold
        int blockY = targetBlock.getY();
        int minY = config.getWildProtectionMinY();
        if (blockY <= minY) {
            // Below wilderness protection threshold - blocks break normally, no harvest needed
            return;
        }

        // At this point, we know:
        // 1. Player is in wilderness (no claim)
        // 2. Wild protection is enabled
        // 3. Block is above Y threshold (will be protected)
        // 4. Player doesn't have bypass permission
        // So the block won't break, but we give harvest items

        // Get block type and check if it matches any configured pattern
        BlockType blockType = event.getBlockType();
        if (blockType == null) return;

        String blockId = blockType.getId();
        if (blockId == null) return;

        // Extract the block name (remove namespace if present)
        String blockName = blockId;
        if (blockId.contains(":")) {
            blockName = blockId.substring(blockId.indexOf(":") + 1);
        }

        // Show debug message if player has debug mode enabled
        if (WildernessCommand.isDebugEnabled(playerId)) {
            player.sendMessage(Message.raw("[DEBUG] Block: " + blockName).color(AQUA));
        }

        // Check if this block matches any configured pattern
        String matchingPattern = harvestConfig.getMatchingPattern(blockName);
        if (matchingPattern == null) {
            // No drops configured for this block
            return;
        }

        // Check cooldown to prevent spam
        long remainingCooldown = getRemainingCooldown(playerId);
        if (remainingCooldown > 0) {
            // Still cancel the event even if on cooldown
            event.setCancelled(true);
            // Show cooldown message
            double seconds = remainingCooldown / 1000.0;
            player.sendMessage(Message.raw(String.format("Wait %.1fs to harvest more", seconds)).color(GRAY));
            return;
        }
        // Mark harvest time
        lastHarvestTime.put(playerId, System.currentTimeMillis());

        // Calculate and spawn the harvest items, cancel event, and show message
        spawnHarvestItems(commandBuffer, store, entityRef, player, playerId, targetBlock, blockName, matchingPattern, event);
    }

    /**
     * Get remaining cooldown in milliseconds, or 0 if can harvest.
     */
    private long getRemainingCooldown(UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastTime = lastHarvestTime.get(playerId);
        long cooldown = harvestConfig.getHarvestCooldownMs();

        if (lastTime == null) {
            return 0;
        }
        long elapsed = now - lastTime;
        if (elapsed >= cooldown) {
            return 0;
        }
        return cooldown - elapsed;
    }

    /**
     * Spawn harvest items at the player's position using world.execute() for proper entity spawning.
     * Uses the simpler generateItemDrops() method that handles item physics correctly.
     */
    private void spawnHarvestItems(CommandBuffer<EntityStore> commandBuffer, Store<EntityStore> store,
                                    Ref<EntityStore> entityRef, Player player, UUID playerId, Vector3i blockPos,
                                    String blockName, String pattern, BreakBlockEvent event) {
        try {
            // Cancel the block break since this is wilderness protection
            event.setCancelled(true);

            // Calculate drops from config
            Map<String, Integer> drops = harvestConfig.calculateDrops(pattern);

            if (drops.isEmpty()) {
                // No drops rolled (bad luck) - just silently cancel, no message
                return;
            }

            // Get player's position for drop location
            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            Vector3d playerPos = transform != null ? transform.getPosition() : null;

            // Calculate drop position - spawn at the block position (center, slightly above)
            final Vector3d dropPosition;
            if (playerPos != null) {
                // Spawn at block position but use player's Y + 1 to ensure it's visible
                dropPosition = new Vector3d(
                    blockPos.getX() + 0.5,
                    playerPos.getY() + 1.0,
                    blockPos.getZ() + 0.5
                );
            } else {
                dropPosition = new Vector3d(
                    blockPos.getX() + 0.5,
                    blockPos.getY() + 1.0,
                    blockPos.getZ() + 0.5
                );
            }

            // Build list of all items to drop
            List<ItemStack> allItems = new ArrayList<>();
            List<String> dropMessages = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : drops.entrySet()) {
                String itemId = entry.getKey();
                int amount = entry.getValue();

                if (amount <= 0) continue;

                allItems.add(new ItemStack(itemId, amount));
                dropMessages.add(amount + "x " + getItemDisplayName(itemId));
            }

            // If block is a trunk, also drop 5 of the trunk block itself
            if (blockName.toLowerCase().contains("trunk")) {
                int trunkAmount = 5;
                allItems.add(new ItemStack(blockName, trunkAmount));
                dropMessages.add(trunkAmount + "x " + getItemDisplayName(blockName));
            }

            if (allItems.isEmpty()) {
                return;
            }

            // Spawn items using world.execute() to ensure proper world thread execution
            final List<ItemStack> itemsToSpawn = allItems;
            player.getWorld().execute(() -> {
                try {
                    Store<EntityStore> worldStore = player.getWorld().getEntityStore().getStore();

                    // Use generateItemDrops with zero rotation - let items fall naturally
                    Vector3f rotation = new Vector3f(0, 0, 0);

                    Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                        worldStore,
                        itemsToSpawn,
                        dropPosition,
                        rotation
                    );

                    // Add all item entities to the world
                    for (Holder<EntityStore> holder : holders) {
                        if (holder != null) {
                            worldStore.addEntity(holder, AddReason.SPAWN);
                        }
                    }
                } catch (Exception e) {
                    logger.atWarning().withCause(e).log("[WildernessHarvest] Error in world.execute() spawning items");
                }
            });

            // Send feedback message showing what was harvested
            String itemList = String.join(", ", dropMessages);
            player.sendMessage(Message.raw("Wilderness Harvest: " + itemList).color(GREEN));

            // For non-tree blocks (like stone), also show the Y-level protection message
            if (!pattern.equalsIgnoreCase("Trunk")) {
                int minY = config.getWildProtectionMinY();
                int blockY = blockPos.getY();
                player.sendMessage(Message.raw("Wilderness Protection: Go below Y=" + minY + " to break (Current Y: " + blockY + ")").color(RED));
            }

            logger.atFine().log("[WildernessHarvest] Player %s harvested from %s (pattern: %s): %s",
                    playerId, blockName, pattern, itemList);
        } catch (Exception e) {
            logger.atWarning().withCause(e).log("[WildernessHarvest] Error spawning items for player %s", playerId);
        }
    }

    /**
     * Get a display-friendly name for an item ID.
     */
    private String getItemDisplayName(String itemId) {
        if (itemId == null) return "unknown";

        // Remove common prefixes
        String name = itemId;
        if (name.startsWith("Ingredient_")) {
            name = name.substring("Ingredient_".length());
        } else if (name.startsWith("Block_")) {
            name = name.substring("Block_".length());
        } else if (name.startsWith("Item_")) {
            name = name.substring("Item_".length());
        }

        // Convert underscores to spaces and lowercase
        return name.replace("_", " ").toLowerCase();
    }
}
