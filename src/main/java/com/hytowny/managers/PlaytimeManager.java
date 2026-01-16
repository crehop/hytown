package com.hytowny.managers;

import com.hytowny.config.PluginConfig;
import com.hytowny.data.PlaytimeData;
import com.hytowny.data.PlaytimeStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages playtime tracking for online players.
 */
public class PlaytimeManager {
    private final PlaytimeStorage storage;
    private final PluginConfig config;
    private final ScheduledExecutorService scheduler;

    public PlaytimeManager(PlaytimeStorage storage, PluginConfig config) {
        this.storage = storage;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Schedule periodic updates
        int interval = config.getPlaytimeUpdateIntervalSeconds();
        scheduler.scheduleAtFixedRate(this::updateAllSessions, interval, interval, TimeUnit.SECONDS);
    }

    /**
     * Called when a player joins the server.
     */
    public void onPlayerJoin(UUID playerId) {
        PlaytimeData data = storage.getPlaytime(playerId);
        data.startSession();
    }

    /**
     * Called when a player leaves the server.
     */
    public void onPlayerLeave(UUID playerId) {
        PlaytimeData data = storage.getPlaytime(playerId);
        data.endSession();
        storage.savePlaytime(playerId);
    }

    /**
     * Gets a player's playtime data.
     */
    public PlaytimeData getPlaytime(UUID playerId) {
        return storage.getPlaytime(playerId);
    }

    /**
     * Gets total playtime hours including current session.
     */
    public double getTotalHours(UUID playerId) {
        return storage.getPlaytime(playerId).getTotalHoursWithCurrentSession();
    }

    /**
     * Updates all active sessions and saves periodically.
     */
    private void updateAllSessions() {
        Map<UUID, PlaytimeData> cache = storage.getCache();
        for (Map.Entry<UUID, PlaytimeData> entry : cache.entrySet()) {
            PlaytimeData data = entry.getValue();
            if (data.isInSession()) {
                // Session is active, save periodically
                storage.savePlaytime(entry.getKey());
            }
        }
    }

    /**
     * Shuts down the playtime manager.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // End all sessions and save
        Map<UUID, PlaytimeData> cache = storage.getCache();
        for (Map.Entry<UUID, PlaytimeData> entry : cache.entrySet()) {
            PlaytimeData data = entry.getValue();
            if (data.isInSession()) {
                data.endSession();
            }
        }
        storage.saveAll();
    }
}
