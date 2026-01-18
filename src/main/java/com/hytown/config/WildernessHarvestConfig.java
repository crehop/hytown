package com.hytown.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Configuration for wilderness harvest drops.
 * Loaded from wilderness_harvest.json.
 *
 * Example JSON:
 * {
 *   "harvestCooldownMs": 500,
 *   "messageChance": 0.2,
 *   "drops": [
 *     {
 *       "blockPattern": "Wood_Trunk",
 *       "items": [
 *         { "item": "Ingredient_Stick", "minAmount": 2, "maxAmount": 5, "chance": 1.0 }
 *       ]
 *     }
 *   ]
 * }
 */
public class WildernessHarvestConfig {

    private final Path configFile;
    private final HytaleLogger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Random random = new Random();

    // Parsed config data
    private long harvestCooldownMs = 500;
    private double messageChance = 0.2;
    private final Map<String, List<DropEntry>> dropsByPattern = new HashMap<>();

    /**
     * Represents a single item drop with chance and amount range.
     */
    public static class DropEntry {
        public String item;
        public int minAmount;
        public int maxAmount;
        public double chance; // 0.0 to 1.0

        public DropEntry() {}

        public DropEntry(String item, int minAmount, int maxAmount, double chance) {
            this.item = item;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chance = chance;
        }
    }

    /**
     * Represents drops for a specific block pattern.
     */
    public static class BlockDropConfig {
        public String blockPattern;
        public List<DropEntry> items = new ArrayList<>();
    }

    /**
     * Root JSON structure.
     */
    public static class ConfigData {
        public long harvestCooldownMs = 500;
        public double messageChance = 0.2;
        public List<BlockDropConfig> drops = new ArrayList<>();
    }

    public WildernessHarvestConfig(Path dataDirectory, HytaleLogger logger) {
        this.configFile = dataDirectory.resolve("wilderness_harvest.json");
        this.logger = logger;
    }

    /**
     * Load config from file, creating default if it doesn't exist.
     */
    public void load() {
        try {
            if (!Files.exists(configFile)) {
                saveDefaultConfig();
            }

            String json = Files.readString(configFile);
            ConfigData data = gson.fromJson(json, ConfigData.class);
            applyConfigData(data);
            logger.atInfo().log("[WildernessHarvest] Loaded %d block patterns from config", dropsByPattern.size());
        } catch (Exception e) {
            logger.atWarning().withCause(e).log("[WildernessHarvest] Failed to load config, using defaults");
            applyConfigData(buildDefaultConfigData());
        }
    }

    /**
     * Apply a ConfigData to the runtime fields.
     */
    private void applyConfigData(ConfigData data) {
        if (data == null) {
            data = buildDefaultConfigData();
        }
        this.harvestCooldownMs = data.harvestCooldownMs;
        this.messageChance = data.messageChance;
        dropsByPattern.clear();
        if (data.drops != null) {
            for (BlockDropConfig blockDrop : data.drops) {
                if (blockDrop.blockPattern != null && blockDrop.items != null) {
                    dropsByPattern.put(blockDrop.blockPattern, blockDrop.items);
                }
            }
        }
    }

    /**
     * Build the default ConfigData with all default drop patterns.
     * Single source of truth for defaults.
     */
    private ConfigData buildDefaultConfigData() {
        ConfigData data = new ConfigData();
        data.harvestCooldownMs = 500;
        data.messageChance = 0.2;
        data.drops = new ArrayList<>();

        // Trunk drops - Sticks (2-5) - matches any block containing "Trunk"
        BlockDropConfig trunk = new BlockDropConfig();
        trunk.blockPattern = "Trunk";
        trunk.items.add(new DropEntry("Ingredient_Stick", 2, 5, 1.0));
        data.drops.add(trunk);

        // Branch drops - Sticks (0-2), Tree Sap (1, 50% chance)
        BlockDropConfig branch = new BlockDropConfig();
        branch.blockPattern = "Branch";
        branch.items.add(new DropEntry("Ingredient_Stick", 0, 2, 1.0));
        branch.items.add(new DropEntry("Ingredient_Tree_Sap", 1, 1, 0.5));
        data.drops.add(branch);

        // Roots drops - Sticks (1-3), Tree Bark (1-2, 50% chance)
        BlockDropConfig roots = new BlockDropConfig();
        roots.blockPattern = "Roots";
        roots.items.add(new DropEntry("Ingredient_Stick", 1, 3, 1.0));
        roots.items.add(new DropEntry("Ingredient_Tree_Bark", 1, 2, 0.5));
        data.drops.add(roots);

        // Stone drops - Cobblestone, Flint (10% chance)
        BlockDropConfig stone = new BlockDropConfig();
        stone.blockPattern = "Stone";
        stone.items.add(new DropEntry("Block_Cobblestone", 1, 1, 1.0));
        stone.items.add(new DropEntry("Ingredient_Flint", 1, 1, 0.1));
        data.drops.add(stone);

        // Dirt drops - Dirt
        BlockDropConfig dirt = new BlockDropConfig();
        dirt.blockPattern = "Dirt";
        dirt.items.add(new DropEntry("Block_Dirt", 1, 1, 1.0));
        data.drops.add(dirt);

        // Sand drops - Sand
        BlockDropConfig sand = new BlockDropConfig();
        sand.blockPattern = "Sand";
        sand.items.add(new DropEntry("Block_Sand", 1, 1, 1.0));
        data.drops.add(sand);

        // Gravel drops - Gravel, Flint (25% chance)
        BlockDropConfig gravel = new BlockDropConfig();
        gravel.blockPattern = "Gravel";
        gravel.items.add(new DropEntry("Block_Gravel", 1, 1, 1.0));
        gravel.items.add(new DropEntry("Ingredient_Flint", 1, 1, 0.25));
        data.drops.add(gravel);

        // Leaves drops - Sticks (1-2, 30% chance)
        BlockDropConfig leaves = new BlockDropConfig();
        leaves.blockPattern = "Leaves";
        leaves.items.add(new DropEntry("Ingredient_Stick", 1, 2, 0.3));
        data.drops.add(leaves);

        return data;
    }

    /**
     * Save default config to file.
     */
    private void saveDefaultConfig() throws IOException {
        Files.createDirectories(configFile.getParent());
        String json = gson.toJson(buildDefaultConfigData());
        Files.writeString(configFile, json);
        logger.atInfo().log("[WildernessHarvest] Created default config at %s", configFile);
    }

    /**
     * Reload config from file.
     */
    public void reload() {
        load();
    }

    /**
     * Get the harvest cooldown in milliseconds.
     */
    public long getHarvestCooldownMs() {
        return harvestCooldownMs;
    }

    /**
     * Get the chance to show harvest message (0.0 to 1.0).
     */
    public double getMessageChance() {
        return messageChance;
    }

    /**
     * Check if a block matches any configured pattern.
     * @param blockName The block name (without namespace prefix)
     * @return The matching pattern key, or null if no match
     */
    public String getMatchingPattern(String blockName) {
        if (blockName == null) return null;

        // Check for exact match first
        if (dropsByPattern.containsKey(blockName)) {
            return blockName;
        }

        // Check if block name contains any pattern
        for (String pattern : dropsByPattern.keySet()) {
            if (blockName.contains(pattern)) {
                return pattern;
            }
        }

        return null;
    }

    /**
     * Get drops for a block pattern.
     * @param pattern The pattern key from getMatchingPattern()
     * @return List of drop entries, or empty list if none
     */
    public List<DropEntry> getDropsForPattern(String pattern) {
        return dropsByPattern.getOrDefault(pattern, new ArrayList<>());
    }

    /**
     * Calculate actual drops based on configured chances and amounts.
     * @param pattern The block pattern
     * @return Map of item ID to amount to drop
     */
    public Map<String, Integer> calculateDrops(String pattern) {
        Map<String, Integer> result = new HashMap<>();
        List<DropEntry> entries = getDropsForPattern(pattern);

        for (DropEntry entry : entries) {
            // Check if this item drops (based on chance)
            if (random.nextDouble() < entry.chance) {
                // Calculate amount
                int amount;
                if (entry.minAmount >= entry.maxAmount) {
                    amount = entry.minAmount;
                } else {
                    amount = entry.minAmount + random.nextInt(entry.maxAmount - entry.minAmount + 1);
                }

                if (amount > 0) {
                    // Add to result (accumulate if same item appears multiple times)
                    result.merge(entry.item, amount, Integer::sum);
                }
            }
        }

        return result;
    }

    /**
     * Check if any drops are configured.
     */
    public boolean hasDrops() {
        return !dropsByPattern.isEmpty();
    }

    /**
     * Get all configured patterns (for debug/admin).
     */
    public List<String> getConfiguredPatterns() {
        return new ArrayList<>(dropsByPattern.keySet());
    }
}
