package com.hytown.data;

/**
 * Per-town configurable settings.
 * Controls PvP, explosions, mob spawning, and permissions for outsiders.
 */
public class TownSettings {

    // Toggle settings
    private boolean pvpEnabled = false;
    private boolean explosionsEnabled = false;
    private boolean fireSpreadEnabled = false;
    private boolean mobSpawningEnabled = true;

    // Access settings for outsiders (non-members)
    private boolean publicSpawn = false;     // Can outsiders use /town spawn?
    private boolean openTown = false;        // Can anyone join without invite?

    // Permission flags for outsiders
    private boolean outsiderBuild = false;
    private boolean outsiderDestroy = false;
    private boolean outsiderSwitch = false;  // Doors, buttons, levers
    private boolean outsiderItemUse = false;

    // Permission flags for residents (non-mayor/assistant)
    private boolean residentBuild = true;
    private boolean residentDestroy = true;
    private boolean residentSwitch = true;
    private boolean residentItemUse = true;

    // Tax settings
    private double dailyTax = 0.0;
    private double plotTax = 0.0;

    // ==================== TOGGLE GETTERS ====================

    public boolean isPvpEnabled() { return pvpEnabled; }
    public boolean isExplosionsEnabled() { return explosionsEnabled; }
    public boolean isFireSpreadEnabled() { return fireSpreadEnabled; }
    public boolean isMobSpawningEnabled() { return mobSpawningEnabled; }
    public boolean isPublicSpawn() { return publicSpawn; }
    public boolean isOpenTown() { return openTown; }

    // ==================== PERMISSION GETTERS ====================

    public boolean canOutsiderBuild() { return outsiderBuild; }
    public boolean canOutsiderDestroy() { return outsiderDestroy; }
    public boolean canOutsiderSwitch() { return outsiderSwitch; }
    public boolean canOutsiderItemUse() { return outsiderItemUse; }

    public boolean canResidentBuild() { return residentBuild; }
    public boolean canResidentDestroy() { return residentDestroy; }
    public boolean canResidentSwitch() { return residentSwitch; }
    public boolean canResidentItemUse() { return residentItemUse; }

    // ==================== TAX GETTERS ====================

    public double getDailyTax() { return dailyTax; }
    public double getPlotTax() { return plotTax; }

    // ==================== TOGGLE SETTERS ====================

    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public void setExplosionsEnabled(boolean explosionsEnabled) { this.explosionsEnabled = explosionsEnabled; }
    public void setFireSpreadEnabled(boolean fireSpreadEnabled) { this.fireSpreadEnabled = fireSpreadEnabled; }
    public void setMobSpawningEnabled(boolean mobSpawningEnabled) { this.mobSpawningEnabled = mobSpawningEnabled; }
    public void setPublicSpawn(boolean publicSpawn) { this.publicSpawn = publicSpawn; }
    public void setOpenTown(boolean openTown) { this.openTown = openTown; }

    // ==================== PERMISSION SETTERS ====================

    public void setOutsiderBuild(boolean outsiderBuild) { this.outsiderBuild = outsiderBuild; }
    public void setOutsiderDestroy(boolean outsiderDestroy) { this.outsiderDestroy = outsiderDestroy; }
    public void setOutsiderSwitch(boolean outsiderSwitch) { this.outsiderSwitch = outsiderSwitch; }
    public void setOutsiderItemUse(boolean outsiderItemUse) { this.outsiderItemUse = outsiderItemUse; }

    public void setResidentBuild(boolean residentBuild) { this.residentBuild = residentBuild; }
    public void setResidentDestroy(boolean residentDestroy) { this.residentDestroy = residentDestroy; }
    public void setResidentSwitch(boolean residentSwitch) { this.residentSwitch = residentSwitch; }
    public void setResidentItemUse(boolean residentItemUse) { this.residentItemUse = residentItemUse; }

    // ==================== TAX SETTERS ====================

    public void setDailyTax(double dailyTax) { this.dailyTax = Math.max(0, dailyTax); }
    public void setPlotTax(double plotTax) { this.plotTax = Math.max(0, plotTax); }

    // ==================== UTILITY ====================

    /**
     * Toggle a setting by name.
     * @return the new value, or null if setting name not found
     */
    public Boolean toggle(String settingName) {
        switch (settingName.toLowerCase()) {
            case "pvp" -> { pvpEnabled = !pvpEnabled; return pvpEnabled; }
            case "explosion", "explosions" -> { explosionsEnabled = !explosionsEnabled; return explosionsEnabled; }
            case "fire" -> { fireSpreadEnabled = !fireSpreadEnabled; return fireSpreadEnabled; }
            case "mobs" -> { mobSpawningEnabled = !mobSpawningEnabled; return mobSpawningEnabled; }
            case "public" -> { publicSpawn = !publicSpawn; return publicSpawn; }
            case "open" -> { openTown = !openTown; return openTown; }
            default -> { return null; }
        }
    }

    /**
     * Get a setting value by name.
     */
    public String getSetting(String settingName) {
        return switch (settingName.toLowerCase()) {
            case "pvp" -> String.valueOf(pvpEnabled);
            case "explosion", "explosions" -> String.valueOf(explosionsEnabled);
            case "fire" -> String.valueOf(fireSpreadEnabled);
            case "mobs" -> String.valueOf(mobSpawningEnabled);
            case "public" -> String.valueOf(publicSpawn);
            case "open" -> String.valueOf(openTown);
            case "tax" -> String.valueOf(dailyTax);
            case "plottax" -> String.valueOf(plotTax);
            default -> null;
        };
    }
}
