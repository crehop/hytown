package com.hytown.data;

import java.util.UUID;

/**
 * Represents a transaction in a town's history.
 * Used to track deposits, withdrawals, plot purchases, upkeep, and member changes.
 */
public class TownTransaction {

    public enum TransactionType {
        DEPOSIT("Deposit"),
        WITHDRAW("Withdraw"),
        PLOT_PURCHASE("Plot Purchase"),
        PLOT_UNCLAIM("Plot Unclaim"),
        UPKEEP("Upkeep"),
        MEMBER_JOIN("Member Join"),
        MEMBER_LEAVE("Member Leave"),
        MEMBER_KICK("Member Kick"),
        RANK_CHANGE("Rank Change"),
        TOWN_CREATE("Town Created"),
        SETTINGS_CHANGE("Settings Changed");

        private final String displayName;

        TransactionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private TransactionType type;
    private long timestamp;
    private UUID actorId;          // Who performed the action
    private String actorName;
    private double amount;          // For money transactions (0 for non-money transactions)
    private String details;         // Additional details (e.g., claim location, member name)

    // For JSON deserialization
    public TownTransaction() {}

    public TownTransaction(TransactionType type, UUID actorId, String actorName, double amount, String details) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.actorId = actorId;
        this.actorName = actorName;
        this.amount = amount;
        this.details = details;
    }

    // Convenience constructors
    public static TownTransaction deposit(UUID playerId, String playerName, double amount) {
        return new TownTransaction(TransactionType.DEPOSIT, playerId, playerName, amount, null);
    }

    public static TownTransaction withdraw(UUID playerId, String playerName, double amount) {
        return new TownTransaction(TransactionType.WITHDRAW, playerId, playerName, amount, null);
    }

    public static TownTransaction plotPurchase(UUID playerId, String playerName, double cost, String claimKey) {
        return new TownTransaction(TransactionType.PLOT_PURCHASE, playerId, playerName, cost, claimKey);
    }

    public static TownTransaction plotUnclaim(UUID playerId, String playerName, String claimKey) {
        return new TownTransaction(TransactionType.PLOT_UNCLAIM, playerId, playerName, 0, claimKey);
    }

    public static TownTransaction upkeep(double amount, int claimCount) {
        return new TownTransaction(TransactionType.UPKEEP, null, "System", amount,
            String.format("%d plots", claimCount));
    }

    public static TownTransaction memberJoin(UUID playerId, String playerName) {
        return new TownTransaction(TransactionType.MEMBER_JOIN, playerId, playerName, 0, null);
    }

    public static TownTransaction memberLeave(UUID playerId, String playerName) {
        return new TownTransaction(TransactionType.MEMBER_LEAVE, playerId, playerName, 0, null);
    }

    public static TownTransaction memberKick(UUID kickerId, String kickerName, String kickedName) {
        return new TownTransaction(TransactionType.MEMBER_KICK, kickerId, kickerName, 0, kickedName);
    }

    public static TownTransaction rankChange(UUID actorId, String actorName, String targetName, String newRank) {
        return new TownTransaction(TransactionType.RANK_CHANGE, actorId, actorName, 0,
            targetName + " -> " + newRank);
    }

    public static TownTransaction townCreate(UUID mayorId, String mayorName, double cost) {
        return new TownTransaction(TransactionType.TOWN_CREATE, mayorId, mayorName, cost, null);
    }

    // Getters
    public TransactionType getType() { return type; }
    public long getTimestamp() { return timestamp; }
    public UUID getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public double getAmount() { return amount; }
    public String getDetails() { return details; }

    // Setters for JSON
    public void setType(TransactionType type) { this.type = type; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setDetails(String details) { this.details = details; }

    /**
     * Get a formatted display string for this transaction.
     */
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDisplayName());

        if (amount != 0) {
            if (type == TransactionType.DEPOSIT) {
                sb.append(": +$").append(String.format("%.2f", amount));
            } else if (type == TransactionType.WITHDRAW || type == TransactionType.PLOT_PURCHASE ||
                       type == TransactionType.UPKEEP || type == TransactionType.TOWN_CREATE) {
                sb.append(": -$").append(String.format("%.2f", amount));
            }
        }

        if (actorName != null && !actorName.equals("System")) {
            sb.append(" by ").append(actorName);
        }

        if (details != null) {
            sb.append(" (").append(details).append(")");
        }

        return sb.toString();
    }
}
