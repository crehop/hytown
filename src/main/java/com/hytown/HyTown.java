package com.hytown;

import com.hytown.commands.ClaimCommand;
import com.hytown.commands.TownCommand;
import com.hytown.commands.ResidentCommand;
import com.hytown.commands.PlotCommand;
import com.hytown.commands.TownyAdminCommand;
import com.hytown.commands.TownyHelpCommand;
import com.hytown.config.BlockGroups;
import com.hytown.config.PluginConfig;
import com.hytown.data.ClaimStorage;
import com.hytown.data.PlaytimeStorage;
import com.hytown.data.TownStorage;
import com.hytown.listeners.ClaimProtectionListener;
import com.hytown.managers.ClaimManager;
import com.hytown.managers.PlaytimeManager;
import com.hytown.map.ClaimMapOverlayProvider;
import com.hytown.map.HyTownWorldMapProvider;
import com.hytown.systems.BlockBreakProtectionSystem;
import com.hytown.systems.BlockDamageProtectionSystem;
import com.hytown.systems.BlockPlaceProtectionSystem;
import com.hytown.systems.BlockUseProtectionSystem;
import com.hytown.systems.ClaimTitleSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.IWorldMapProvider;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.math.util.ChunkUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.HashMap;
import java.util.Map;

/**
 * HyTown - A chunk-based land claiming plugin with playtime-based limits.
 *
 * Features:
 * - Claim chunks to protect your builds
 * - More playtime = more claim chunks available
 * - Trust other players in your claims
 * - Full protection for claimed areas
 */
public class HyTown extends JavaPlugin {

    private PluginConfig config;
    private BlockGroups blockGroups;
    private ClaimStorage claimStorage;
    private PlaytimeStorage playtimeStorage;
    private TownStorage townStorage;
    private ClaimManager claimManager;
    private PlaytimeManager playtimeManager;
    private ClaimProtectionListener protectionListener;
    private ClaimMapOverlayProvider mapOverlayProvider;
    private ClaimTitleSystem claimTitleSystem;
    private com.hytown.managers.UpkeepManager upkeepManager;

    // Track registered worlds for map provider
    public static final Map<String, World> WORLDS = new HashMap<>();

    public HyTown(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        getLogger().atSevere().log("========== HYTOWN PLUGIN STARTING ==========");

        // Initialize configuration
        config = new PluginConfig(getDataDirectory());
        blockGroups = new BlockGroups(getDataDirectory());

        // Initialize storage
        claimStorage = new ClaimStorage(getDataDirectory());
        playtimeStorage = new PlaytimeStorage(getDataDirectory());
        townStorage = new TownStorage(getDataDirectory());

        // Initialize static accessor for map system
        HyTownAccess.init(claimStorage);

        // Initialize managers
        claimManager = new ClaimManager(claimStorage, playtimeStorage, config, blockGroups);
        playtimeManager = new PlaytimeManager(playtimeStorage, config);
        upkeepManager = new com.hytown.managers.UpkeepManager(config, townStorage, getLogger());

        // Register the personal claim command (/claim)
        getCommandRegistry().registerCommand(new ClaimCommand(this));

        // Register Towny-style commands
        getCommandRegistry().registerCommand(new TownCommand(this));
        getCommandRegistry().registerCommand(new ResidentCommand(this));
        getCommandRegistry().registerCommand(new PlotCommand(this));
        getCommandRegistry().registerCommand(new TownyAdminCommand(this));
        getCommandRegistry().registerCommand(new TownyHelpCommand(this));
        getLogger().atInfo().log("Registered Towny commands: /town, /resident, /plot, /townyadmin, /townyhelp");

        // Register protection event listeners (for PlayerInteractEvent)
        protectionListener = new ClaimProtectionListener(this);
        protectionListener.register(getEventRegistry());

        // Register player connect/disconnect events for name tracking
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Register world map provider codec
        try {
            IWorldMapProvider.CODEC.register(HyTownWorldMapProvider.ID,
                    HyTownWorldMapProvider.class, HyTownWorldMapProvider.CODEC);
            getLogger().atInfo().log("Registered HyTownWorldMapProvider codec");
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Failed to register world map provider codec");
        }

        // Register world events for map provider setup
        getEventRegistry().registerGlobal(AddWorldEvent.class, this::onWorldAdd);
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemove);

        // Initialize map overlay provider (for markers, kept for compatibility)
        mapOverlayProvider = new ClaimMapOverlayProvider(claimStorage, getLogger());

        // Register ECS block protection systems
        getLogger().atSevere().log("[DEBUG] Registering ECS block protection systems...");
        try {
            getLogger().atSevere().log("[DEBUG] Registering BlockDamageProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockDamageProtectionSystem(claimManager, getLogger()));
            getLogger().atSevere().log("[DEBUG] Registering BlockBreakProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockBreakProtectionSystem(claimManager, config, townStorage, getLogger()));
            getLogger().atSevere().log("[DEBUG] Registering BlockPlaceProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockPlaceProtectionSystem(claimManager, config, townStorage, getLogger()));
            getLogger().atSevere().log("[DEBUG] Registering BlockUseProtectionSystem...");
            getEntityStoreRegistry().registerSystem(new BlockUseProtectionSystem(claimManager, townStorage, getLogger()));

            // Register claim title system (shows banner when entering/leaving claims)
            getLogger().atSevere().log("[DEBUG] Creating ClaimTitleSystem...");
            claimTitleSystem = new ClaimTitleSystem(claimStorage, townStorage, config);
            getLogger().atSevere().log("[DEBUG] Registering ClaimTitleSystem...");
            getEntityStoreRegistry().registerSystem(claimTitleSystem);

            getLogger().atSevere().log("[DEBUG] All ECS systems registered successfully!");
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("[DEBUG] ERROR registering ECS systems");
        }
    }

    /**
     * Called when a world is added - set up our custom map provider.
     */
    private void onWorldAdd(AddWorldEvent event) {
        World world = event.getWorld();
        WORLDS.put(world.getName(), world);
        getLogger().atWarning().log("[Map] World added: %s (deleteOnRemove=%s)", world.getName(), world.getWorldConfig().isDeleteOnRemove());

        // Set our custom world map provider for persistent worlds
        try {
            if (!world.getWorldConfig().isDeleteOnRemove()) {
                world.getWorldConfig().setWorldMapProvider(new HyTownWorldMapProvider());
                getLogger().atWarning().log("[Map] Set HyTownWorldMapProvider for world: %s", world.getName());
            }
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("[Map] Failed to set map provider for world: %s", world.getName());
        }
    }

    /**
     * Called when a world is removed.
     */
    private void onWorldRemove(RemoveWorldEvent event) {
        WORLDS.remove(event.getWorld().getName());
    }

    @Override
    public void start() {
        getLogger().atSevere().log("========== HYTOWN PLUGIN STARTED ==========");
        getLogger().atWarning().log("[Map] Known worlds: %s", WORLDS.keySet());

        // Check upkeep on startup (will only collect if it's the right time of day)
        if (upkeepManager != null) {
            upkeepManager.checkUpkeep();
            getLogger().atInfo().log("[Upkeep] Upkeep check on startup complete");
        }

        // Start background upkeep checker thread
        startUpkeepChecker();
    }

    /**
     * Start a background thread to check upkeep periodically.
     */
    private void startUpkeepChecker() {
        Thread upkeepThread = new Thread(() -> {
            while (true) {
                try {
                    // Check every 5 minutes
                    Thread.sleep(300000);
                    if (upkeepManager != null) {
                        upkeepManager.checkUpkeep();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    getLogger().atWarning().withCause(e).log("[Upkeep] Error in upkeep check");
                }
            }
        });
        upkeepThread.setDaemon(true);
        upkeepThread.setName("HyTown-UpkeepChecker");
        upkeepThread.start();
        getLogger().atInfo().log("[Upkeep] Started background upkeep checker");
    }

    /**
     * Refreshes the entire world map to show updated claims.
     * Clears both server and client caches to force regeneration.
     * Called after claiming/unclaiming chunks.
     */
    public void refreshWorldMap(String worldName) {
        World world = WORLDS.get(worldName);
        if (world == null) {
            getLogger().atWarning().log("[Map] Cannot refresh map - world not found: %s", worldName);
            return;
        }

        try {
            // 1. Set the generator (in case it changed)
            var worldMap = world.getWorldConfig().getWorldMapProvider().getGenerator(world);
            world.getWorldMapManager().setGenerator(worldMap);

            // 2. Clear server-side cached map images
            world.getWorldMapManager().clearImages();

            // 3. Clear each player's client-side cache to force re-request
            for (Player player : world.getPlayers()) {
                try {
                    player.getWorldMapTracker().clear();
                } catch (Exception e) {
                    getLogger().atFine().withCause(e).log("[Map] Error clearing map for player");
                }
            }

            getLogger().atInfo().log("[Map] Refreshed map for world: %s (%d players notified)",
                    worldName, world.getPlayers().size());
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("[Map] Error refreshing map for world: %s", worldName);
        }
    }

    /**
     * Refreshes specific chunks on the world map.
     * More efficient than refreshing the entire map when only a few chunks changed.
     *
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    public void refreshWorldMapChunk(String worldName, int chunkX, int chunkZ) {
        World world = WORLDS.get(worldName);
        if (world == null) {
            return;
        }

        try {
            // Create a set with this chunk and its neighbors (for border updates)
            LongSet chunksToRefresh = new LongOpenHashSet();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    chunksToRefresh.add(ChunkUtil.indexChunk(chunkX + dx, chunkZ + dz));
                }
            }

            // Clear server-side cached images for these chunks
            world.getWorldMapManager().clearImagesInChunks(chunksToRefresh);

            // Clear each player's client-side cache for these chunks
            for (Player player : world.getPlayers()) {
                try {
                    player.getWorldMapTracker().clearChunks(chunksToRefresh);
                } catch (Exception e) {
                    getLogger().atFine().withCause(e).log("[Map] Error clearing chunks for player");
                }
            }

            getLogger().atFine().log("[Map] Refreshed chunk %d,%d in world %s", chunkX, chunkZ, worldName);
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("[Map] Error refreshing chunk %d,%d", chunkX, chunkZ);
        }
    }

    @Override
    public void shutdown() {
        // Shutdown playtime manager (saves all sessions)
        if (playtimeManager != null) {
            playtimeManager.shutdown();
        }

        // Save all claim data
        if (claimStorage != null) {
            claimStorage.saveAll();
        }

        // Save all town data
        if (townStorage != null) {
            townStorage.saveAll();
        }

        getLogger().atInfo().log("HyTown shutdown complete!");
    }

    public PluginConfig getPluginConfig() {
        return config;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    /**
     * Called when a player joins - start tracking their playtime.
     * Note: This should be hooked into the server's player join event.
     */
    public void onPlayerJoin(java.util.UUID playerId) {
        playtimeManager.onPlayerJoin(playerId);
    }

    /**
     * Called when a player leaves - save their playtime.
     * Note: This should be hooked into the server's player leave event.
     */
    public void onPlayerLeave(java.util.UUID playerId) {
        playtimeManager.onPlayerLeave(playerId);
    }

    private static final java.awt.Color GOLD = new java.awt.Color(255, 170, 0);
    private static final java.awt.Color WHITE = new java.awt.Color(255, 255, 255);

    /**
     * Handles player connect event - register username and start playtime.
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        getLogger().atSevere().log("[DEBUG] onPlayerConnect START");
        try {
            var playerRef = event.getPlayerRef();
            getLogger().atSevere().log("[DEBUG] Got playerRef: %s", playerRef != null);
            if (playerRef != null) {
                java.util.UUID playerId = playerRef.getUuid();
                String username = playerRef.getUsername();
                getLogger().atSevere().log("[DEBUG] Player: %s (%s)", username, playerId);

                // Store player name for map display
                getLogger().atSevere().log("[DEBUG] Setting player name...");
                claimStorage.setPlayerName(playerId, username);

                // Start playtime tracking
                getLogger().atSevere().log("[DEBUG] Starting playtime tracking...");
                playtimeManager.onPlayerJoin(playerId);

                // Warn if player's town is overdue on upkeep
                getLogger().atSevere().log("[DEBUG] Checking upkeep warning...");
                if (upkeepManager != null) {
                    upkeepManager.warnIfOverdue(playerId);
                }

                getLogger().atSevere().log("[DEBUG] onPlayerConnect completed for: %s", username);
            }
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log("[DEBUG] ERROR in onPlayerConnect");
        }
    }

    /**
     * Handles player disconnect event - save playtime and clear map cache.
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        try {
            var playerRef = event.getPlayerRef();
            if (playerRef != null) {
                java.util.UUID playerId = playerRef.getUuid();

                // Save playtime
                playtimeManager.onPlayerLeave(playerId);

                // Clear map overlay cache for this player
                if (mapOverlayProvider != null) {
                    mapOverlayProvider.clearPlayerCache(playerId);
                }

                // Clear title tracking for this player
                if (claimTitleSystem != null) {
                    claimTitleSystem.removePlayer(playerId);
                }

                getLogger().atFine().log("Player disconnected: %s", playerId);
            }
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Error handling player disconnect");
        }
    }

    /**
     * Refreshes all claim chunks for a specific player.
     * Called when trust is added/removed to update the trusted player names on the map.
     *
     * @param playerId The UUID of the claim owner
     */
    public void refreshPlayerClaimChunks(java.util.UUID playerId) {
        var playerClaims = claimStorage.getPlayerClaims(playerId);
        if (playerClaims == null) {
            return;
        }

        // Group claims by world for efficient refresh
        Map<String, java.util.List<int[]>> claimsByWorld = new HashMap<>();
        for (var claim : playerClaims.getClaims()) {
            claimsByWorld.computeIfAbsent(claim.getWorld(), k -> new java.util.ArrayList<>())
                    .add(new int[]{claim.getChunkX(), claim.getChunkZ()});
        }

        // Refresh each world's chunks
        for (var entry : claimsByWorld.entrySet()) {
            String worldName = entry.getKey();
            World world = WORLDS.get(worldName);
            if (world == null) {
                continue;
            }

            try {
                LongSet chunksToRefresh = new LongOpenHashSet();
                for (int[] coords : entry.getValue()) {
                    // Add the claim chunk and its neighbors for border updates
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            chunksToRefresh.add(ChunkUtil.indexChunk(coords[0] + dx, coords[1] + dz));
                        }
                    }
                }

                // Clear server-side cached images
                world.getWorldMapManager().clearImagesInChunks(chunksToRefresh);

                // Clear each player's client-side cache
                for (Player player : world.getPlayers()) {
                    try {
                        player.getWorldMapTracker().clearChunks(chunksToRefresh);
                    } catch (Exception e) {
                        getLogger().atFine().withCause(e).log("[Map] Error clearing chunks for player");
                    }
                }

                getLogger().atFine().log("[Map] Refreshed %d claim chunks for player %s in world %s",
                        entry.getValue().size(), playerId, worldName);
            } catch (Exception e) {
                getLogger().atWarning().withCause(e).log("[Map] Error refreshing claims for player %s", playerId);
            }
        }
    }

    /**
     * Gets the map overlay provider for external access.
     */
    public ClaimMapOverlayProvider getMapOverlayProvider() {
        return mapOverlayProvider;
    }

    /**
     * Gets the claim storage for direct access (e.g., for name updates).
     */
    public ClaimStorage getClaimStorage() {
        return claimStorage;
    }

    /**
     * Gets the town storage for direct access.
     */
    public TownStorage getTownStorage() {
        return townStorage;
    }

    /**
     * Gets the upkeep manager for calculating upkeep costs.
     */
    public com.hytown.managers.UpkeepManager getUpkeepManager() {
        return upkeepManager;
    }
}
