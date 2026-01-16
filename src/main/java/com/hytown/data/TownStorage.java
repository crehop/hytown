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
 */
public class TownStorage {
    private final Path townsDirectory;
    private final Path indexFile;
    private final Gson gson;

    // In-memory caches
    private final Map<String, Town> townsByName = new ConcurrentHashMap<>();           // townName (lowercase) -> Town
    private final Map<String, String> claimToTown = new ConcurrentHashMap<>();         // claimKey -> townName
    private final Map<UUID, String> playerToTown = new ConcurrentHashMap<>();          // playerId -> townName
    private final Map<UUID, Set<String>> pendingInvites = new ConcurrentHashMap<>();   // playerId -> Set<townNames>

    public TownStorage(Path dataDirectory) {
        this.townsDirectory = dataDirectory.resolve("towns");
        this.indexFile = townsDirectory.resolve("_index.json");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();

        try {
            Files.createDirectories(townsDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }

        loadAll();
    }

    // ==================== LOADING ====================

    /**
     * Load all towns from disk.
     */
    public void loadAll() {
        townsByName.clear();
        claimToTown.clear();
        playerToTown.clear();

        try (var stream = Files.list(townsDirectory)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .forEach(this::loadTownFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Load pending invites from index
        loadIndex();
    }

    private void loadTownFile(Path file) {
        try {
            String json = Files.readString(file);
            Town town = gson.fromJson(json, Town.class);
            if (town != null && town.getName() != null) {
                cacheTown(town);
            }
        } catch (Exception e) {
            System.err.println("Failed to load town from " + file + ": " + e.getMessage());
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
     * Save a single town to disk.
     */
    public void saveTown(Town town) {
        Path file = townsDirectory.resolve(sanitize(town.getName()) + ".json");
        try {
            Files.writeString(file, gson.toJson(town));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Re-cache to update indexes
        uncacheTown(town.getName());
        cacheTown(town);
    }

    /**
     * Save the index file (invites, etc.)
     */
    public void saveIndex() {
        Map<String, Set<String>> toSave = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : pendingInvites.entrySet()) {
            toSave.put(entry.getKey().toString(), entry.getValue());
        }
        try {
            Files.writeString(indexFile, gson.toJson(toSave));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save all towns to disk.
     */
    public void saveAll() {
        for (Town town : townsByName.values()) {
            saveTown(town);
        }
        saveIndex();
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
