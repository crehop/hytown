package com.hytown.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration manager for HyTown plugin.
 * Supports in-game configuration via /claim admin command.
 */
public class PluginConfig {
    private final Path configFile;
    private final Gson gson;
    private ConfigData config;

    public PluginConfig(Path dataDirectory) {
        this.configFile = dataDirectory.resolve("config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.config = new ConfigData();
        load();
    }

    /**
     * Load configuration from file, with migration from old field names.
     */
    public void load() {
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                // Migrate old field names to new ones
                boolean needsMigration = false;

                if (obj.has("startingChunks") && !obj.has("startingClaims")) {
                    obj.addProperty("startingClaims", obj.get("startingChunks").getAsInt());
                    obj.remove("startingChunks");
                    needsMigration = true;
                }
                if (obj.has("chunksPerHour") && !obj.has("claimsPerHour")) {
                    obj.addProperty("claimsPerHour", obj.get("chunksPerHour").getAsInt());
                    obj.remove("chunksPerHour");
                    needsMigration = true;
                }
                if (obj.has("maxClaimsPerPlayer") && !obj.has("maxClaims")) {
                    obj.addProperty("maxClaims", obj.get("maxClaimsPerPlayer").getAsInt());
                    obj.remove("maxClaimsPerPlayer");
                    needsMigration = true;
                }
                if (obj.has("playtimeUpdateIntervalSeconds") && !obj.has("playtimeSaveInterval")) {
                    obj.addProperty("playtimeSaveInterval", obj.get("playtimeUpdateIntervalSeconds").getAsInt());
                    obj.remove("playtimeUpdateIntervalSeconds");
                    needsMigration = true;
                }

                // Parse the (possibly migrated) config
                ConfigData loaded = gson.fromJson(obj, ConfigData.class);
                if (loaded != null) {
                    config = loaded;
                }

                // Save migrated config with new field names
                if (needsMigration) {
                    save();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    /**
     * Reload configuration from file.
     */
    public void reload() {
        load();
    }

    /**
     * Save current configuration to file.
     */
    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, gson.toJson(config));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===== GETTERS =====

    public int getClaimsPerHour() {
        return config.claimsPerHour;
    }

    public int getStartingClaims() {
        return config.startingClaims;
    }

    public int getMaxClaims() {
        return config.maxClaims;
    }

    public int getPlaytimeSaveInterval() {
        return config.playtimeSaveInterval;
    }

    public int getClaimBufferSize() {
        return config.claimBufferSize;
    }

    // ===== TOWN GETTERS =====

    public double getTownCreationCost() {
        return config.townCreationCost;
    }

    public double getTownClaimCost() {
        return config.townClaimCost;
    }

    public int getMaxTownClaims() {
        return config.maxTownClaims;
    }

    public double getTownUpkeepBase() {
        return config.townUpkeepBase;
    }

    public double getTownUpkeepPerClaim() {
        return config.townUpkeepPerClaim;
    }

    public int getTownUpkeepHour() {
        return config.townUpkeepHour;
    }

    // ===== WILD PROTECTION GETTERS =====

    public boolean isWildProtectionEnabled() {
        return config.wildProtectionEnabled;
    }

    public int getWildProtectionMinY() {
        return config.wildProtectionMinY;
    }

    public boolean isWildDestroyBelowAllowed() {
        return config.wildDestroyBelowAllowed;
    }

    public boolean isWildBuildBelowAllowed() {
        return config.wildBuildBelowAllowed;
    }

    public List<String> getWildBlockDenyList() {
        return config.wildBlockDenyList;
    }

    /**
     * Check if an item ID matches any griefing pattern.
     */
    public boolean isGriefingBlock(String itemId) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase();
        for (String pattern : config.wildBlockDenyList) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // ===== SETTERS (auto-save) =====

    public void setClaimsPerHour(int value) {
        config.claimsPerHour = Math.max(0, value);
        save();
    }

    public void setStartingClaims(int value) {
        config.startingClaims = Math.max(0, value);
        save();
    }

    public void setMaxClaims(int value) {
        config.maxClaims = Math.max(1, value);
        save();
    }

    public void setPlaytimeSaveInterval(int value) {
        config.playtimeSaveInterval = Math.max(10, value);
        save();
    }

    public void setClaimBufferSize(int value) {
        config.claimBufferSize = Math.max(0, value);  // 0 = disabled
        save();
    }

    // ===== TOWN SETTERS =====

    public void setTownCreationCost(double value) {
        config.townCreationCost = Math.max(0, value);
        save();
    }

    public void setTownClaimCost(double value) {
        config.townClaimCost = Math.max(0, value);
        save();
    }

    public void setMaxTownClaims(int value) {
        config.maxTownClaims = Math.max(1, value);
        save();
    }

    public void setTownUpkeepBase(double value) {
        config.townUpkeepBase = Math.max(0, value);
        save();
    }

    public void setTownUpkeepPerClaim(double value) {
        config.townUpkeepPerClaim = Math.max(0, value);
        save();
    }

    public void setTownUpkeepHour(int value) {
        config.townUpkeepHour = Math.max(0, Math.min(23, value));
        save();
    }

    // ===== WILD PROTECTION SETTERS =====

    public void setWildProtectionEnabled(boolean enabled) {
        config.wildProtectionEnabled = enabled;
        save();
    }

    public void setWildProtectionMinY(int y) {
        config.wildProtectionMinY = y;
        save();
    }

    public void setWildDestroyBelowAllowed(boolean allowed) {
        config.wildDestroyBelowAllowed = allowed;
        save();
    }

    public void setWildBuildBelowAllowed(boolean allowed) {
        config.wildBuildBelowAllowed = allowed;
        save();
    }

    // ===== LEGACY GETTERS (for compatibility) =====

    /** @deprecated Use getClaimsPerHour() */
    public int getChunksPerHour() {
        return getClaimsPerHour();
    }

    /** @deprecated Use getStartingClaims() */
    public int getStartingChunks() {
        return getStartingClaims();
    }

    /** @deprecated Use getMaxClaims() */
    public int getMaxClaimsPerPlayer() {
        return getMaxClaims();
    }

    /** @deprecated Use getPlaytimeSaveInterval() */
    public int getPlaytimeUpdateIntervalSeconds() {
        return getPlaytimeSaveInterval();
    }

    // ===== CALCULATION METHODS =====

    /**
     * Calculate how many chunks a player can claim based on their playtime.
     */
    public int calculateMaxClaims(double playtimeHours) {
        int fromPlaytime = (int) (playtimeHours * config.claimsPerHour);
        int total = config.startingClaims + fromPlaytime;
        return Math.min(total, config.maxClaims);
    }

    /**
     * Calculate hours needed to unlock the next claim.
     */
    public double hoursUntilNextClaim(double currentHours, int currentClaims) {
        if (currentClaims >= config.maxClaims) {
            return -1; // Already at max
        }

        int fromPlaytime = currentClaims - config.startingClaims;
        if (fromPlaytime < 0) {
            return 0; // Still have starting claims available
        }

        double hoursNeeded = (double) (fromPlaytime + 1) / config.claimsPerHour;
        return Math.max(0, hoursNeeded - currentHours);
    }

    /**
     * Configuration data structure for JSON serialization.
     * Uses clear, user-friendly field names.
     */
    private static class ConfigData {
        // Personal claims settings
        int startingClaims = 4;
        int claimsPerHour = 2;
        int maxClaims = 50;
        int playtimeSaveInterval = 60;
        int claimBufferSize = 2;  // Buffer zone in chunks around claims where others can't claim

        // Town settings
        double townCreationCost = 1000.0;
        double townClaimCost = 50.0;
        int maxTownClaims = 100;

        // Town upkeep settings
        double townUpkeepBase = 100.0;       // Base daily upkeep for having a town
        double townUpkeepPerClaim = 50.0;    // Upkeep per claimed chunk (50/day/plot)
        int townUpkeepHour = 12;             // Hour of day (0-23) when upkeep is collected

        // Wild protection settings
        boolean wildProtectionEnabled = true;
        int wildProtectionMinY = 0;  // Y-level below which blocks are unprotected
        boolean wildDestroyBelowAllowed = true;
        boolean wildBuildBelowAllowed = false;

        // Block deny list - items with IDs containing any of these strings are blocked in wilderness
        List<String> wildBlockDenyList = createDefaultBlockDenyList();

        private static List<String> createDefaultBlockDenyList() {
            List<String> patterns = new ArrayList<>();
            patterns.add("fluid");
            patterns.add("fire");
            patterns.add("tnt");
            patterns.add("bomb");
            patterns.add("explosive");
            patterns.add("dynamite");
            return patterns;
        }

        // Town rank thresholds (expandable array)
        List<TownRankDefinition> townRanks = createDefaultRanks();

        private static List<TownRankDefinition> createDefaultRanks() {
            List<TownRankDefinition> ranks = new ArrayList<>();
            ranks.add(new TownRankDefinition("Outpost", 0, 0));       // Default - any town
            ranks.add(new TownRankDefinition("Settlement", 5, 3));    // 5+ plots, 3+ citizens
            ranks.add(new TownRankDefinition("Village", 10, 5));      // 10+ plots, 5+ citizens
            ranks.add(new TownRankDefinition("Town", 25, 7));         // 25+ plots, 7+ citizens
            ranks.add(new TownRankDefinition("City", 50, 12));        // 50+ plots, 12+ citizens
            ranks.add(new TownRankDefinition("Kingdom", 100, 25));    // 100+ plots, 25+ citizens
            ranks.add(new TownRankDefinition("Empire", 250, 50));     // 250+ plots, 50+ citizens
            return ranks;
        }
    }

    /**
     * Definition for a town rank with requirements.
     */
    public static class TownRankDefinition {
        public String name;
        public int minPlots;
        public int minCitizens;

        public TownRankDefinition() {}

        public TownRankDefinition(String name, int minPlots, int minCitizens) {
            this.name = name;
            this.minPlots = minPlots;
            this.minCitizens = minCitizens;
        }
    }

    // ===== TOWN RANK METHODS =====

    /**
     * Get the rank name for a town based on plots and citizens.
     * Returns the highest rank the town qualifies for.
     */
    public String getTownRankName(int plots, int citizens) {
        String rank = "Outpost"; // Default
        for (TownRankDefinition def : config.townRanks) {
            if (plots >= def.minPlots && citizens >= def.minCitizens) {
                rank = def.name;
            }
        }
        return rank;
    }

    /**
     * Get all town rank definitions.
     */
    public List<TownRankDefinition> getTownRanks() {
        return new ArrayList<>(config.townRanks);
    }
}
