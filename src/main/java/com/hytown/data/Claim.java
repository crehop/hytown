package com.hytown.data;

/**
 * Represents a single claimed chunk.
 */
public class Claim {
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final long claimedAt;

    public Claim(String world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.claimedAt = System.currentTimeMillis();
    }

    public Claim(String world, int chunkX, int chunkZ, long claimedAt) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.claimedAt = claimedAt;
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public long getClaimedAt() {
        return claimedAt;
    }

    /**
     * Returns a unique key for this claim (world:chunkX,chunkZ)
     */
    public String getKey() {
        return world + ":" + chunkX + "," + chunkZ;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Claim claim = (Claim) obj;
        return chunkX == claim.chunkX && chunkZ == claim.chunkZ && world.equals(claim.world);
    }

    @Override
    public int hashCode() {
        int result = world.hashCode();
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        return result;
    }
}
