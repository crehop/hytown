package com.hytown.data;

import java.util.*;

/**
 * Per-plot settings that can override town defaults.
 * Null values mean "use town default".
 */
public class PlotSettings {

    // Plot owner protection - when true, only owner + allowed players + mayor/assistants can open containers
    private boolean ownerProtection = false;

    // Players allowed to access containers on this plot (besides owner and staff)
    private Set<UUID> allowedPlayers = new HashSet<>();
    private Map<UUID, String> allowedPlayerNames = new HashMap<>();  // UUID -> name for display

    // Override settings - null means use town default
    private Boolean pvpEnabled = null;
    private Boolean explosionsEnabled = null;
    private Boolean fireSpreadEnabled = null;
    private Boolean mobSpawningEnabled = null;

    // ==================== OWNER PROTECTION ====================

    public boolean isOwnerProtection() { return ownerProtection; }
    public void setOwnerProtection(boolean ownerProtection) { this.ownerProtection = ownerProtection; }

    // ==================== ALLOWED PLAYERS ====================

    public Set<UUID> getAllowedPlayers() { return new HashSet<>(allowedPlayers); }

    public Map<UUID, String> getAllowedPlayerNames() { return new HashMap<>(allowedPlayerNames); }

    public boolean isAllowedPlayer(UUID playerId) {
        return allowedPlayers.contains(playerId);
    }

    public void addAllowedPlayer(UUID playerId, String playerName) {
        allowedPlayers.add(playerId);
        allowedPlayerNames.put(playerId, playerName);
    }

    public void removeAllowedPlayer(UUID playerId) {
        allowedPlayers.remove(playerId);
        allowedPlayerNames.remove(playerId);
    }

    public String getAllowedPlayerName(UUID playerId) {
        return allowedPlayerNames.getOrDefault(playerId, playerId.toString().substring(0, 8));
    }

    // ==================== OVERRIDE GETTERS ====================

    public Boolean getPvpEnabled() { return pvpEnabled; }
    public Boolean getExplosionsEnabled() { return explosionsEnabled; }
    public Boolean getFireSpreadEnabled() { return fireSpreadEnabled; }
    public Boolean getMobSpawningEnabled() { return mobSpawningEnabled; }

    // ==================== OVERRIDE SETTERS ====================

    public void setPvpEnabled(Boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public void setExplosionsEnabled(Boolean explosionsEnabled) { this.explosionsEnabled = explosionsEnabled; }
    public void setFireSpreadEnabled(Boolean fireSpreadEnabled) { this.fireSpreadEnabled = fireSpreadEnabled; }
    public void setMobSpawningEnabled(Boolean mobSpawningEnabled) { this.mobSpawningEnabled = mobSpawningEnabled; }

    // ==================== UTILITY ====================

    /**
     * Toggle a setting by name.
     * Cycles: null (use town) -> true -> false -> null
     * @return the new value (null means use town default)
     */
    public Boolean toggle(String settingName) {
        switch (settingName.toLowerCase()) {
            case "pvp" -> {
                pvpEnabled = cycleBoolean(pvpEnabled);
                return pvpEnabled;
            }
            case "explosion", "explosions" -> {
                explosionsEnabled = cycleBoolean(explosionsEnabled);
                return explosionsEnabled;
            }
            case "fire" -> {
                fireSpreadEnabled = cycleBoolean(fireSpreadEnabled);
                return fireSpreadEnabled;
            }
            case "mobs" -> {
                mobSpawningEnabled = cycleBoolean(mobSpawningEnabled);
                return mobSpawningEnabled;
            }
            case "protection", "ownerprotection" -> {
                ownerProtection = !ownerProtection;
                return ownerProtection;
            }
            default -> { return null; }
        }
    }

    /**
     * Cycle a Boolean: null -> true -> false -> null
     */
    private Boolean cycleBoolean(Boolean current) {
        if (current == null) return true;
        if (current) return false;
        return null;
    }

    /**
     * Get effective value with town default fallback.
     */
    public boolean getEffectivePvp(TownSettings townSettings) {
        return pvpEnabled != null ? pvpEnabled : townSettings.isPvpEnabled();
    }

    public boolean getEffectiveExplosions(TownSettings townSettings) {
        return explosionsEnabled != null ? explosionsEnabled : townSettings.isExplosionsEnabled();
    }

    public boolean getEffectiveFire(TownSettings townSettings) {
        return fireSpreadEnabled != null ? fireSpreadEnabled : townSettings.isFireSpreadEnabled();
    }

    public boolean getEffectiveMobs(TownSettings townSettings) {
        return mobSpawningEnabled != null ? mobSpawningEnabled : townSettings.isMobSpawningEnabled();
    }

    /**
     * Check if any overrides are set.
     */
    public boolean hasOverrides() {
        return ownerProtection || pvpEnabled != null || explosionsEnabled != null ||
               fireSpreadEnabled != null || mobSpawningEnabled != null;
    }
}
