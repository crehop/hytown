package com.hytowny.data;

import java.util.UUID;

/**
 * Tracks a player's total playtime on the server.
 */
public class PlaytimeData {
    private final UUID playerId;
    private long totalPlaytimeSeconds;
    private long sessionStartTime;

    public PlaytimeData(UUID playerId) {
        this.playerId = playerId;
        this.totalPlaytimeSeconds = 0;
        this.sessionStartTime = 0;
    }

    public PlaytimeData(UUID playerId, long totalPlaytimeSeconds) {
        this.playerId = playerId;
        this.totalPlaytimeSeconds = totalPlaytimeSeconds;
        this.sessionStartTime = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getTotalPlaytimeSeconds() {
        return totalPlaytimeSeconds;
    }

    public double getTotalPlaytimeHours() {
        return totalPlaytimeSeconds / 3600.0;
    }

    public void addPlaytime(long seconds) {
        this.totalPlaytimeSeconds += seconds;
    }

    public void startSession() {
        this.sessionStartTime = System.currentTimeMillis();
    }

    public void endSession() {
        if (sessionStartTime > 0) {
            long sessionSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000;
            totalPlaytimeSeconds += sessionSeconds;
            sessionStartTime = 0;
        }
    }

    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public boolean isInSession() {
        return sessionStartTime > 0;
    }

    /**
     * Gets total playtime including current session.
     */
    public long getTotalWithCurrentSession() {
        if (sessionStartTime > 0) {
            long currentSessionSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000;
            return totalPlaytimeSeconds + currentSessionSeconds;
        }
        return totalPlaytimeSeconds;
    }

    public double getTotalHoursWithCurrentSession() {
        return getTotalWithCurrentSession() / 3600.0;
    }
}
