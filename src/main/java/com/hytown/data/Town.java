package com.hytown.data;

import java.util.*;
import java.util.Collections;

/**
 * Represents a Town - a collection of claims owned by a group of players.
 * Based on TownyAdvanced's Town concept.
 */
public class Town {
    private String name;
    private UUID mayorId;
    private String mayorName;
    private Set<UUID> assistants = new HashSet<>();
    private Set<UUID> residents = new HashSet<>();
    private Map<UUID, String> residentNames = new HashMap<>();  // UUID -> name
    private Set<String> claimKeys = new HashSet<>();  // "world:chunkX,chunkZ"
    private TownSettings settings = new TownSettings();
    private double balance = 0.0;
    private long createdAt;

    // Spawn location
    private String spawnWorld;
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw, spawnPitch;
    private boolean hasSpawn = false;

    // Nation membership
    private String nationName = null;

    // Town board/message
    private String board = "";

    // Upkeep tracking
    private long lastUpkeepTime = 0;  // When upkeep was last collected
    private int missedUpkeepDays = 0; // How many days of upkeep have been missed

    // Transaction history (limited to last 100 entries)
    private static final int MAX_TRANSACTIONS = 100;
    private List<TownTransaction> transactionHistory = new ArrayList<>();

    // Plot ownership and settings
    // Key: claimKey ("world:x,z"), Value: owner UUID
    private Map<String, UUID> plotOwners = new HashMap<>();
    // Key: claimKey, Value: PlotSettings (overrides town defaults)
    private Map<String, PlotSettings> plotSettings = new HashMap<>();

    public Town(String name, UUID mayorId, String mayorName) {
        this.name = name;
        this.mayorId = mayorId;
        this.mayorName = mayorName;
        this.residents.add(mayorId);
        this.residentNames.put(mayorId, mayorName);
        this.createdAt = System.currentTimeMillis();
    }

    // For JSON deserialization
    public Town() {}

    // ==================== MEMBERSHIP ====================

    public boolean isMember(UUID playerId) {
        return residents.contains(playerId);
    }

    public boolean isMayor(UUID playerId) {
        return mayorId != null && mayorId.equals(playerId);
    }

    public boolean isAssistant(UUID playerId) {
        return isMayor(playerId) || assistants.contains(playerId);
    }

    public TownRank getRank(UUID playerId) {
        if (isMayor(playerId)) return TownRank.MAYOR;
        if (assistants.contains(playerId)) return TownRank.ASSISTANT;
        if (residents.contains(playerId)) return TownRank.RESIDENT;
        return null;
    }

    public void addResident(UUID playerId, String playerName) {
        residents.add(playerId);
        residentNames.put(playerId, playerName);
    }

    public void removeResident(UUID playerId) {
        residents.remove(playerId);
        residentNames.remove(playerId);
        assistants.remove(playerId);
    }

    public void promoteToAssistant(UUID playerId) {
        if (residents.contains(playerId)) {
            assistants.add(playerId);
        }
    }

    public void demoteFromAssistant(UUID playerId) {
        assistants.remove(playerId);
    }

    public void setMayor(UUID newMayorId, String newMayorName) {
        // Old mayor becomes assistant
        if (mayorId != null) {
            assistants.add(mayorId);
        }
        // New mayor
        this.mayorId = newMayorId;
        this.mayorName = newMayorName;
        assistants.remove(newMayorId);
        // Ensure new mayor is a resident
        if (!residents.contains(newMayorId)) {
            residents.add(newMayorId);
            residentNames.put(newMayorId, newMayorName);
        }
    }

    // ==================== CLAIMS ====================

    public void addClaim(String claimKey) {
        claimKeys.add(claimKey);
    }

    public void removeClaim(String claimKey) {
        claimKeys.remove(claimKey);
    }

    public boolean ownsClaim(String claimKey) {
        return claimKeys.contains(claimKey);
    }

    public int getClaimCount() {
        return claimKeys.size();
    }

    /**
     * Checks if a chunk is face-adjacent (N/S/E/W) to any existing town claim.
     * Diagonal adjacency is NOT allowed.
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return true if adjacent to existing claim (or if town has no claims yet)
     */
    public boolean isAdjacentToExistingClaim(String worldName, int chunkX, int chunkZ) {
        // First claim can be anywhere
        if (claimKeys.isEmpty()) {
            return true;
        }

        // Check face-adjacent chunks (N, S, E, W only - NOT diagonal)
        String[] adjacentKeys = {
            worldName + ":" + chunkX + "," + (chunkZ - 1),  // North
            worldName + ":" + chunkX + "," + (chunkZ + 1),  // South
            worldName + ":" + (chunkX - 1) + "," + chunkZ,  // West
            worldName + ":" + (chunkX + 1) + "," + chunkZ   // East
        };

        for (String key : adjacentKeys) {
            if (claimKeys.contains(key)) {
                return true;
            }
        }

        return false;
    }

    // ==================== PLOTS ====================

    /**
     * Set the owner of a plot.
     */
    public void setPlotOwner(String claimKey, UUID ownerId) {
        if (ownerId == null) {
            plotOwners.remove(claimKey);
        } else {
            plotOwners.put(claimKey, ownerId);
        }
    }

    /**
     * Get the owner of a plot, or null if unassigned.
     */
    public UUID getPlotOwner(String claimKey) {
        return plotOwners.get(claimKey);
    }

    /**
     * Check if a player owns a specific plot.
     */
    public boolean isPlotOwner(String claimKey, UUID playerId) {
        UUID owner = plotOwners.get(claimKey);
        return owner != null && owner.equals(playerId);
    }

    /**
     * Get or create PlotSettings for a plot.
     */
    public PlotSettings getOrCreatePlotSettings(String claimKey) {
        return plotSettings.computeIfAbsent(claimKey, k -> new PlotSettings());
    }

    /**
     * Get PlotSettings for a plot, or null if none set.
     */
    public PlotSettings getPlotSettings(String claimKey) {
        return plotSettings.get(claimKey);
    }

    /**
     * Check if plot owner protection is enabled for a plot.
     */
    public boolean isPlotProtected(String claimKey) {
        PlotSettings ps = plotSettings.get(claimKey);
        return ps != null && ps.isOwnerProtection();
    }

    /**
     * Check if a player can open containers on a plot.
     * Non-members can NEVER access containers.
     * Returns true if:
     * - Player is a town member AND plot protection is disabled (town default)
     * - Player is the plot owner
     * - Player is on the plot's allowed players list
     * - Player is mayor or assistant
     */
    public boolean canAccessContainers(String claimKey, UUID playerId) {
        // Non-members can never access containers
        if (!isMember(playerId)) {
            return false;
        }

        // If plot protection is not enabled, all town members can access
        if (!isPlotProtected(claimKey)) {
            return true;
        }

        // Plot protection is enabled - check if player has access
        // Mayor and assistants always have access
        if (isAssistant(playerId)) {
            return true;
        }

        // Plot owner has access
        if (isPlotOwner(claimKey, playerId)) {
            return true;
        }

        // Check if player is on the allowed list
        PlotSettings ps = plotSettings.get(claimKey);
        if (ps != null && ps.isAllowedPlayer(playerId)) {
            return true;
        }

        return false;
    }

    /**
     * Get effective PvP setting for a plot (plot override or town default).
     */
    public boolean isPvpEnabledAt(String claimKey) {
        PlotSettings ps = plotSettings.get(claimKey);
        if (ps != null && ps.getPvpEnabled() != null) {
            return ps.getPvpEnabled();
        }
        return settings.isPvpEnabled();
    }

    /**
     * Get effective explosions setting for a plot.
     */
    public boolean isExplosionsEnabledAt(String claimKey) {
        PlotSettings ps = plotSettings.get(claimKey);
        if (ps != null && ps.getExplosionsEnabled() != null) {
            return ps.getExplosionsEnabled();
        }
        return settings.isExplosionsEnabled();
    }

    /**
     * Get effective fire spread setting for a plot.
     */
    public boolean isFireSpreadEnabledAt(String claimKey) {
        PlotSettings ps = plotSettings.get(claimKey);
        if (ps != null && ps.getFireSpreadEnabled() != null) {
            return ps.getFireSpreadEnabled();
        }
        return settings.isFireSpreadEnabled();
    }

    /**
     * Get effective mob spawning setting for a plot.
     */
    public boolean isMobSpawningEnabledAt(String claimKey) {
        PlotSettings ps = plotSettings.get(claimKey);
        if (ps != null && ps.getMobSpawningEnabled() != null) {
            return ps.getMobSpawningEnabled();
        }
        return settings.isMobSpawningEnabled();
    }

    /**
     * Get all plots owned by a player.
     */
    public Set<String> getPlayerPlots(UUID playerId) {
        Set<String> plots = new HashSet<>();
        for (Map.Entry<String, UUID> entry : plotOwners.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                plots.add(entry.getKey());
            }
        }
        return plots;
    }

    // ==================== ECONOMY ====================

    public void deposit(double amount) {
        this.balance += amount;
    }

    /**
     * Deposit with transaction logging.
     */
    public void deposit(double amount, UUID playerId, String playerName) {
        this.balance += amount;
        addTransaction(TownTransaction.deposit(playerId, playerName, amount));
    }

    public boolean withdraw(double amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }

    /**
     * Withdraw with transaction logging.
     */
    public boolean withdraw(double amount, UUID playerId, String playerName) {
        if (balance >= amount) {
            balance -= amount;
            addTransaction(TownTransaction.withdraw(playerId, playerName, amount));
            return true;
        }
        return false;
    }

    // ==================== TRANSACTIONS ====================

    /**
     * Add a transaction to the history, keeping only the last MAX_TRANSACTIONS.
     */
    public void addTransaction(TownTransaction transaction) {
        transactionHistory.add(transaction);
        // Trim to max size
        while (transactionHistory.size() > MAX_TRANSACTIONS) {
            transactionHistory.remove(0);
        }
    }

    /**
     * Get the transaction history (newest first).
     */
    public List<TownTransaction> getTransactionHistory() {
        List<TownTransaction> reversed = new ArrayList<>(transactionHistory);
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Get the transaction history (oldest first).
     */
    public List<TownTransaction> getTransactionHistoryChronological() {
        return new ArrayList<>(transactionHistory);
    }

    /**
     * Log upkeep collection.
     */
    public void logUpkeep(double amount, int claimCount) {
        addTransaction(TownTransaction.upkeep(amount, claimCount));
    }

    /**
     * Log plot purchase.
     */
    public void logPlotPurchase(UUID playerId, String playerName, double cost, String claimKey) {
        addTransaction(TownTransaction.plotPurchase(playerId, playerName, cost, claimKey));
    }

    /**
     * Log plot unclaim.
     */
    public void logPlotUnclaim(UUID playerId, String playerName, String claimKey) {
        addTransaction(TownTransaction.plotUnclaim(playerId, playerName, claimKey));
    }

    /**
     * Log member join.
     */
    public void logMemberJoin(UUID playerId, String playerName) {
        addTransaction(TownTransaction.memberJoin(playerId, playerName));
    }

    /**
     * Log member leave.
     */
    public void logMemberLeave(UUID playerId, String playerName) {
        addTransaction(TownTransaction.memberLeave(playerId, playerName));
    }

    /**
     * Log member kick.
     */
    public void logMemberKick(UUID kickerId, String kickerName, String kickedName) {
        addTransaction(TownTransaction.memberKick(kickerId, kickerName, kickedName));
    }

    /**
     * Log rank change.
     */
    public void logRankChange(UUID actorId, String actorName, String targetName, String newRank) {
        addTransaction(TownTransaction.rankChange(actorId, actorName, targetName, newRank));
    }

    // ==================== SPAWN ====================

    public void setSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        this.spawnWorld = world;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnYaw = yaw;
        this.spawnPitch = pitch;
        this.hasSpawn = true;
    }

    public void clearSpawn() {
        this.hasSpawn = false;
    }

    // ==================== GETTERS ====================

    public String getName() { return name; }
    public UUID getMayorId() { return mayorId; }
    public String getMayorName() { return mayorName; }
    public Set<UUID> getAssistants() { return new HashSet<>(assistants); }
    public Set<UUID> getResidents() { return new HashSet<>(residents); }
    public Map<UUID, String> getResidentNames() { return new HashMap<>(residentNames); }
    public Set<String> getClaimKeys() { return new HashSet<>(claimKeys); }
    public TownSettings getSettings() { return settings; }
    public double getBalance() { return balance; }
    public long getCreatedAt() { return createdAt; }
    public String getNationName() { return nationName; }
    public String getBoard() { return board != null ? board : ""; }
    public boolean hasSpawn() { return hasSpawn; }
    public String getSpawnWorld() { return spawnWorld; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public float getSpawnYaw() { return spawnYaw; }
    public float getSpawnPitch() { return spawnPitch; }

    public String getResidentName(UUID playerId) {
        return residentNames.getOrDefault(playerId, playerId.toString().substring(0, 8));
    }

    public int getResidentCount() {
        return residents.size();
    }

    public long getLastUpkeepTime() { return lastUpkeepTime; }
    public int getMissedUpkeepDays() { return missedUpkeepDays; }

    // ==================== SETTERS ====================

    public void setName(String name) { this.name = name; }
    public void setNationName(String nationName) { this.nationName = nationName; }
    public void setBoard(String board) { this.board = board != null ? board : ""; }
    public void setBalance(double balance) { this.balance = balance; }
    public void setSettings(TownSettings settings) { this.settings = settings; }

    // For JSON deserialization
    public void setMayorId(UUID mayorId) { this.mayorId = mayorId; }
    public void setMayorName(String mayorName) { this.mayorName = mayorName; }
    public void setAssistants(Set<UUID> assistants) { this.assistants = assistants; }
    public void setResidents(Set<UUID> residents) { this.residents = residents; }
    public void setResidentNames(Map<UUID, String> residentNames) { this.residentNames = residentNames; }
    public void setClaimKeys(Set<String> claimKeys) { this.claimKeys = claimKeys; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setLastUpkeepTime(long lastUpkeepTime) { this.lastUpkeepTime = lastUpkeepTime; }
    public void setMissedUpkeepDays(int missedUpkeepDays) { this.missedUpkeepDays = missedUpkeepDays; }

    // ==================== ENUM ====================

    public enum TownRank {
        MAYOR("Mayor"),
        ASSISTANT("Assistant"),
        RESIDENT("Resident");

        private final String displayName;

        TownRank(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
