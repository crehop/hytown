package com.hytown.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages persistent storage of towns using JSON files.
 * Maintains indexes for fast lookup by name, claim, and player.
 *
 * Robustness features:
 * - Atomic writes using temp files + rename
 * - Corrupted file recovery with backup
 * - Periodic auto-save
 * - Thread-safe operations
 */
public class TownStorage {
    private final Path townsDirectory;
    private final Path indexFile;
    private final Path corruptedDirectory;
    private final Gson gson;

    // In-memory caches
    private final Map<String, Town> townsByName = new ConcurrentHashMap<>();           // townName (lowercase) -> Town
    private final Map<String, String> claimToTown = new ConcurrentHashMap<>();         // claimKey -> townName
    private final Map<UUID, String> playerToTown = new ConcurrentHashMap<>();          // playerId -> townName
    private final Map<UUID, Set<String>> pendingInvites = new ConcurrentHashMap<>();   // playerId -> Set<townNames>

    // Invite cooldown tracking: "playerId:townName" -> expiry timestamp (1 hour after deny)
    private final Map<String, Long> inviteCooldowns = new ConcurrentHashMap<>();
    private static final long INVITE_COOLDOWN_MS = 3600000; // 1 hour in milliseconds

    // Write lock for file operations
    private final Object writeLock = new Object();

    // Track if there are unsaved changes
    private volatile boolean dirty = false;

    public TownStorage(Path dataDirectory) {
        this.townsDirectory = dataDirectory.resolve("towns");
        this.indexFile = townsDirectory.resolve("_index.json");
        this.corruptedDirectory = townsDirectory.resolve("corrupted");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();

        try {
            Files.createDirectories(townsDirectory);
            Files.createDirectories(corruptedDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }

        loadAll();

        // Log loaded towns
        System.out.println("[TownStorage] Loaded " + townsByName.size() + " towns");
    }

    // ==================== LOADING ====================

    /**
     * Load all towns from disk.
     * Also tries to recover from .bak files if main files are corrupted.
     */
    public void loadAll() {
        townsByName.clear();
        claimToTown.clear();
        playerToTown.clear();

        // Clean up any leftover temp files from crashed saves
        cleanupTempFiles();

        try (var stream = Files.list(townsDirectory)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .filter(p -> !p.getFileName().toString().endsWith(".bak"))
                    .forEach(this::loadTownFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Load pending invites from index
        loadIndex();

        // Try to recover any towns from backup files that weren't loaded
        recoverFromBackups();
    }

    /**
     * Clean up leftover temp files from crashed saves.
     */
    private void cleanupTempFiles() {
        try (var stream = Files.list(townsDirectory)) {
            stream.filter(p -> p.toString().endsWith(".tmp"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            System.out.println("[TownStorage] Cleaned up temp file: " + p.getFileName());
                        } catch (IOException e) {
                            System.err.println("[TownStorage] Could not clean up temp file: " + p);
                        }
                    });
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Try to recover towns from .bak files if main files failed to load.
     */
    private void recoverFromBackups() {
        try (var stream = Files.list(townsDirectory)) {
            stream.filter(p -> p.toString().endsWith(".bak"))
                    .forEach(backupFile -> {
                        String townName = backupFile.getFileName().toString().replace(".json.bak", "");
                        // Only try to recover if we don't already have this town loaded
                        if (!townsByName.containsKey(townName.toLowerCase())) {
                            System.out.println("[TownStorage] Attempting to recover " + townName + " from backup...");
                            try {
                                String json = Files.readString(backupFile);
                                Town town = gson.fromJson(json, Town.class);
                                if (town != null && town.getName() != null) {
                                    town.validateAfterLoad();
                                    cacheTown(town);
                                    // Restore the backup as the main file
                                    Path mainFile = townsDirectory.resolve(sanitize(town.getName()) + ".json");
                                    Files.copy(backupFile, mainFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    System.out.println("[TownStorage] RECOVERED town from backup: " + town.getName());
                                }
                            } catch (Exception e) {
                                System.err.println("[TownStorage] Failed to recover " + townName + " from backup: " + e.getMessage());
                            }
                        }
                    });
        } catch (IOException e) {
            // Ignore
        }
    }

    private void loadTownFile(Path file) {
        try {
            String json = Files.readString(file);

            // Validate JSON is not empty or truncated
            if (json == null || json.trim().isEmpty()) {
                System.err.println("[TownStorage] Empty file detected: " + file);
                moveToCorrupted(file, "empty");
                return;
            }

            Town town = gson.fromJson(json, Town.class);
            if (town != null && town.getName() != null) {
                // Validate and fix any data inconsistencies
                town.validateAfterLoad();
                cacheTown(town);
                System.out.println("[TownStorage] Loaded town: " + town.getName() +
                        " (residents=" + town.getResidentCount() + ", claims=" + town.getClaimCount() + ")");
            } else {
                System.err.println("[TownStorage] Invalid town data in file: " + file);
                moveToCorrupted(file, "invalid_data");
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("[TownStorage] Corrupted JSON in " + file + ": " + e.getMessage());
            moveToCorrupted(file, "json_syntax_error");
        } catch (Exception e) {
            System.err.println("[TownStorage] Failed to load town from " + file + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Move a corrupted file to the corrupted directory for manual recovery.
     */
    private void moveToCorrupted(Path file, String reason) {
        try {
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newName = file.getFileName().toString().replace(".json", "") +
                    "_" + reason + "_" + timestamp + ".json";
            Path dest = corruptedDirectory.resolve(newName);
            Files.move(file, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[TownStorage] Moved corrupted file to: " + dest);
        } catch (IOException e) {
            System.err.println("[TownStorage] Failed to move corrupted file: " + e.getMessage());
        }
    }

    private void loadIndex() {
        if (Files.exists(indexFile)) {
            try {
                String json = Files.readString(indexFile);
                Type type = new TypeToken<Map<String, Set<String>>>() {}.getType();
                Map<String, Set<String>> invites = gson.fromJson(json, type);
                if (invites != null) {
                    for (Map.Entry<String, Set<String>> entry : invites.entrySet()) {
                        try {
                            UUID playerId = UUID.fromString(entry.getKey());
                            pendingInvites.put(playerId, new HashSet<>(entry.getValue()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void cacheTown(Town town) {
        String nameLower = town.getName().toLowerCase();
        townsByName.put(nameLower, town);

        // Index all claims
        for (String claimKey : town.getClaimKeys()) {
            claimToTown.put(claimKey, town.getName());
        }

        // Index all residents
        for (UUID residentId : town.getResidents()) {
            playerToTown.put(residentId, town.getName());
        }
    }

    // ==================== SAVING ====================

    /**
     * Save a single town to disk using atomic write.
     * Uses temp file + rename to prevent corruption on crash.
     */
    public void saveTown(Town town) {
        synchronized (writeLock) {
            Path file = townsDirectory.resolve(sanitize(town.getName()) + ".json");
            Path tempFile = townsDirectory.resolve(sanitize(town.getName()) + ".json.tmp");

            try {
                // Serialize to JSON
                String json = gson.toJson(town);

                // Validate serialization worked (sanity check)
                if (json == null || json.trim().isEmpty()) {
                    System.err.println("[TownStorage] ERROR: Empty JSON generated for town: " + town.getName());
                    return;
                }

                // Write to temp file first
                Files.writeString(tempFile, json);

                // Verify temp file was written correctly
                String verification = Files.readString(tempFile);
                if (!json.equals(verification)) {
                    System.err.println("[TownStorage] ERROR: Verification failed for town: " + town.getName());
                    Files.deleteIfExists(tempFile);
                    return;
                }

                // Create backup of existing file before overwriting
                if (Files.exists(file)) {
                    Path backupFile = townsDirectory.resolve(sanitize(town.getName()) + ".json.bak");
                    try {
                        Files.copy(file, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        // Backup failure is not critical, continue with save
                        System.err.println("[TownStorage] Warning: Could not create backup for " + town.getName());
                    }
                }

                // Atomic rename (this is atomic on most filesystems)
                Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);

                dirty = false;

            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Fallback for filesystems that don't support atomic move
                try {
                    String json = gson.toJson(town);
                    Files.writeString(file, json);
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    System.err.println("[TownStorage] ERROR saving town " + town.getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                }
            } catch (IOException e) {
                System.err.println("[TownStorage] ERROR saving town " + town.getName() + ": " + e.getMessage());
                e.printStackTrace();
                // Try to clean up temp file
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
        }

        // Re-cache to update indexes (outside the lock to avoid deadlock)
        uncacheTown(town.getName());
        cacheTown(town);
    }

    /**
     * Save the index file (invites, etc.) using atomic write.
     */
    public void saveIndex() {
        synchronized (writeLock) {
            Path tempFile = townsDirectory.resolve("_index.json.tmp");
            Map<String, Set<String>> toSave = new HashMap<>();
            for (Map.Entry<UUID, Set<String>> entry : pendingInvites.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }
            try {
                String json = gson.toJson(toSave);
                Files.writeString(tempFile, json);
                Files.move(tempFile, indexFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("[TownStorage] ERROR saving index: " + e.getMessage());
                e.printStackTrace();
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Save all towns to disk.
     */
    public void saveAll() {
        System.out.println("[TownStorage] Saving all " + townsByName.size() + " towns...");
        int saved = 0;
        int errors = 0;
        for (Town town : townsByName.values()) {
            try {
                saveTown(town);
                saved++;
            } catch (Exception e) {
                errors++;
                System.err.println("[TownStorage] ERROR saving " + town.getName() + ": " + e.getMessage());
            }
        }
        saveIndex();
        if (errors > 0) {
            System.err.println("[TownStorage] Saved " + saved + " towns with " + errors + " errors!");
        } else {
            System.out.println("[TownStorage] Saved " + saved + " towns and index file successfully");
        }
    }

    /**
     * Reload all towns from disk. Useful for admin commands.
     * Warning: This will discard any unsaved in-memory changes!
     */
    public void reload() {
        System.out.println("[TownStorage] Reloading all towns from disk...");
        loadAll();
        System.out.println("[TownStorage] Reload complete. Loaded " + townsByName.size() + " towns.");
    }

    /**
     * Mark that there are unsaved changes (for auto-save optimization).
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Check if there are unsaved changes.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Get storage statistics for debugging/admin commands.
     */
    public String getStats() {
        return String.format("Towns: %d, Claims indexed: %d, Players indexed: %d, Pending invites: %d",
                townsByName.size(), claimToTown.size(), playerToTown.size(), pendingInvites.size());
    }

    // ==================== DELETION ====================

    /**
     * Delete a town.
     */
    public void deleteTown(String townName) {
        Town town = getTown(townName);
        if (town == null) return;

        // Remove from caches
        uncacheTown(townName);

        // Delete file
        Path file = townsDirectory.resolve(sanitize(townName) + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uncacheTown(String townName) {
        String nameLower = townName.toLowerCase();
        Town town = townsByName.remove(nameLower);
        if (town != null) {
            // Remove claim indexes
            for (String claimKey : town.getClaimKeys()) {
                claimToTown.remove(claimKey);
            }
            // Remove resident indexes
            for (UUID residentId : town.getResidents()) {
                playerToTown.remove(residentId);
            }
        }
    }

    // ==================== QUERIES ====================

    /**
     * Get a town by name (case-insensitive).
     */
    public Town getTown(String name) {
        if (name == null) return null;
        return townsByName.get(name.toLowerCase());
    }

    /**
     * Get the town that owns a specific claim.
     */
    public Town getTownByClaimKey(String claimKey) {
        String townName = claimToTown.get(claimKey);
        return townName != null ? getTown(townName) : null;
    }

    /**
     * Get the town a player belongs to.
     */
    public Town getPlayerTown(UUID playerId) {
        String townName = playerToTown.get(playerId);
        return townName != null ? getTown(townName) : null;
    }

    /**
     * Check if a town exists.
     */
    public boolean townExists(String name) {
        return townsByName.containsKey(name.toLowerCase());
    }

    /**
     * Get all towns.
     */
    public Collection<Town> getAllTowns() {
        return new ArrayList<>(townsByName.values());
    }

    /**
     * Get the number of towns.
     */
    public int getTownCount() {
        return townsByName.size();
    }

    // ==================== INVITES ====================

    /**
     * Add an invite for a player to a town.
     */
    public void addInvite(UUID playerId, String townName) {
        pendingInvites.computeIfAbsent(playerId, k -> new HashSet<>()).add(townName);
        saveIndex();
    }

    /**
     * Remove an invite.
     */
    public void removeInvite(UUID playerId, String townName) {
        Set<String> invites = pendingInvites.get(playerId);
        if (invites != null) {
            invites.remove(townName);
            if (invites.isEmpty()) {
                pendingInvites.remove(playerId);
            }
        }
        saveIndex();
    }

    /**
     * Check if a player has an invite to a town.
     */
    public boolean hasInvite(UUID playerId, String townName) {
        Set<String> invites = pendingInvites.get(playerId);
        return invites != null && invites.contains(townName);
    }

    /**
     * Get all pending invites for a player.
     */
    public Set<String> getInvites(UUID playerId) {
        Set<String> invites = pendingInvites.get(playerId);
        return invites != null ? new HashSet<>(invites) : new HashSet<>();
    }

    /**
     * Clear all invites for a player.
     */
    public void clearInvites(UUID playerId) {
        pendingInvites.remove(playerId);
        saveIndex();
    }

    // ==================== INVITE COOLDOWNS ====================

    /**
     * Deny an invite and set a cooldown preventing re-invite for 1 hour.
     */
    public void denyInvite(UUID playerId, String townName) {
        // Remove the pending invite
        removeInvite(playerId, townName);

        // Set cooldown - 1 hour from now
        String cooldownKey = playerId.toString() + ":" + townName.toLowerCase();
        inviteCooldowns.put(cooldownKey, System.currentTimeMillis() + INVITE_COOLDOWN_MS);
    }

    /**
     * Check if a player is on cooldown for invites from a specific town.
     * @return true if on cooldown, false if can be invited
     */
    public boolean isOnInviteCooldown(UUID playerId, String townName) {
        String cooldownKey = playerId.toString() + ":" + townName.toLowerCase();
        Long expiryTime = inviteCooldowns.get(cooldownKey);
        if (expiryTime == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiryTime) {
            // Cooldown expired, remove it
            inviteCooldowns.remove(cooldownKey);
            return false;
        }
        return true;
    }

    /**
     * Get remaining cooldown time in minutes for a player/town combo.
     * @return remaining minutes, or 0 if no cooldown
     */
    public int getRemainingCooldownMinutes(UUID playerId, String townName) {
        String cooldownKey = playerId.toString() + ":" + townName.toLowerCase();
        Long expiryTime = inviteCooldowns.get(cooldownKey);
        if (expiryTime == null) {
            return 0;
        }
        long remainingMs = expiryTime - System.currentTimeMillis();
        if (remainingMs <= 0) {
            inviteCooldowns.remove(cooldownKey);
            return 0;
        }
        return (int) Math.ceil(remainingMs / 60000.0);
    }

    /**
     * Clean up expired cooldowns (called periodically).
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        inviteCooldowns.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    // ==================== UTILITY ====================

    /**
     * Update indexes when a claim is added to a town.
     */
    public void indexClaim(String claimKey, String townName) {
        claimToTown.put(claimKey, townName);
    }

    /**
     * Update indexes when a claim is removed from a town.
     */
    public void unindexClaim(String claimKey) {
        claimToTown.remove(claimKey);
    }

    /**
     * Update indexes when a player joins a town.
     */
    public void indexPlayer(UUID playerId, String townName) {
        playerToTown.put(playerId, townName);
    }

    /**
     * Update indexes when a player leaves a town.
     */
    public void unindexPlayer(UUID playerId) {
        playerToTown.remove(playerId);
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // ==================== BACKUPS ====================

    private static final int MAX_BACKUPS = 10;
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Create a backup of all town data.
     * Keeps the last 10 daily backups (rolling).
     */
    public void createBackup() {
        Path backupDir = townsDirectory.resolve("backups");
        try {
            Files.createDirectories(backupDir);

            // Create today's backup folder
            String today = LocalDate.now().format(BACKUP_DATE_FORMAT);
            Path todayBackup = backupDir.resolve(today);
            Files.createDirectories(todayBackup);

            // Copy all town files to backup
            for (Town town : townsByName.values()) {
                Path source = townsDirectory.resolve(sanitize(town.getName()) + ".json");
                Path dest = todayBackup.resolve(sanitize(town.getName()) + ".json");
                if (Files.exists(source)) {
                    Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Also backup the index file
            if (Files.exists(indexFile)) {
                Files.copy(indexFile, todayBackup.resolve("_index.json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("[TownStorage] Created backup: " + today);

            // Clean up old backups (keep last MAX_BACKUPS)
            cleanOldBackups(backupDir);

        } catch (IOException e) {
            System.err.println("[TownStorage] Failed to create backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove backups older than MAX_BACKUPS days.
     */
    private void cleanOldBackups(Path backupDir) {
        try (var stream = Files.list(backupDir)) {
            List<Path> backups = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), Comparator.reverseOrder()))
                    .collect(Collectors.toList());

            // Delete backups beyond MAX_BACKUPS
            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Path oldBackup = backups.get(i);
                deleteDirectory(oldBackup);
                System.out.println("[TownStorage] Deleted old backup: " + oldBackup.getFileName());
            }
        } catch (IOException e) {
            System.err.println("[TownStorage] Failed to clean old backups: " + e.getMessage());
        }
    }

    /**
     * Recursively delete a directory.
     */
    private void deleteDirectory(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete: " + path);
                        }
                    });
        }
    }

    /**
     * List available backups.
     */
    public List<String> listBackups() {
        Path backupDir = townsDirectory.resolve("backups");
        if (!Files.exists(backupDir)) {
            return new ArrayList<>();
        }

        try (var stream = Files.list(backupDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Restore from a backup (by date string like "2026-01-16").
     * Returns true if successful.
     */
    public boolean restoreBackup(String dateStr) {
        Path backupDir = townsDirectory.resolve("backups").resolve(dateStr);
        if (!Files.exists(backupDir)) {
            System.err.println("[TownStorage] Backup not found: " + dateStr);
            return false;
        }

        try {
            // Copy backup files back to main directory
            try (var stream = Files.list(backupDir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(source -> {
                            try {
                                Path dest = townsDirectory.resolve(source.getFileName());
                                Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                System.err.println("Failed to restore: " + source.getFileName());
                            }
                        });
            }

            // Reload all data
            loadAll();
            System.out.println("[TownStorage] Restored from backup: " + dateStr);
            return true;

        } catch (IOException e) {
            System.err.println("[TownStorage] Failed to restore backup: " + e.getMessage());
            return false;
        }
    }
}
