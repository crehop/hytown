package com.hytowny.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent storage of player playtime using JSON files.
 */
public class PlaytimeStorage {
    private final Path playtimeDirectory;
    private final Gson gson;
    private final Map<UUID, PlaytimeData> cache;

    public PlaytimeStorage(Path dataDirectory) {
        this.playtimeDirectory = dataDirectory.resolve("playtime");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = new ConcurrentHashMap<>();

        try {
            Files.createDirectories(playtimeDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PlaytimeData getPlaytime(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadPlaytime);
    }

    private PlaytimeData loadPlaytime(UUID playerId) {
        Path file = playtimeDirectory.resolve(playerId.toString() + ".json");

        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                PlaytimeJson data = gson.fromJson(json, PlaytimeJson.class);

                if (data != null) {
                    return new PlaytimeData(playerId, data.totalPlaytimeSeconds);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new PlaytimeData(playerId);
    }

    public void savePlaytime(UUID playerId) {
        PlaytimeData data = cache.get(playerId);
        if (data == null) return;

        Path file = playtimeDirectory.resolve(playerId.toString() + ".json");

        PlaytimeJson json = new PlaytimeJson();
        json.totalPlaytimeSeconds = data.getTotalPlaytimeSeconds();

        try {
            Files.writeString(file, gson.toJson(json));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAll() {
        for (UUID playerId : cache.keySet()) {
            savePlaytime(playerId);
        }
    }

    /**
     * Get all currently cached playtime data (for updating online players).
     */
    public Map<UUID, PlaytimeData> getCache() {
        return cache;
    }

    private static class PlaytimeJson {
        long totalPlaytimeSeconds;
    }
}
