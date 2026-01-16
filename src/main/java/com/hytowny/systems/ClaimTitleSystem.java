package com.hytowny.systems;

import com.hytowny.config.PluginConfig;
import com.hytowny.data.ClaimStorage;
import com.hytowny.data.Town;
import com.hytowny.data.TownStorage;
import com.hytowny.util.ChunkUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticking system that shows a title banner when players enter or leave claimed zones.
 * Runs every tick for all players and displays a title when the claim status changes.
 */
public class ClaimTitleSystem extends EntityTickingSystem<EntityStore> {

    private static final Message WILDERNESS_MESSAGE = Message.raw("Wilderness").color(new Color(85, 255, 85));
    private static final String WILDERNESS_TEXT = "Wilderness";
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color CYAN = new Color(85, 255, 255);

    private final ClaimStorage claimStorage;
    private final TownStorage townStorage;
    private final PluginConfig config;
    private final Map<UUID, String> playerLastTitle;
    private final Map<UUID, Long> lastOverdueWarning;  // Track when we last warned about overdue

    public ClaimTitleSystem(ClaimStorage claimStorage, TownStorage townStorage, PluginConfig config) {
        this.claimStorage = claimStorage;
        this.townStorage = townStorage;
        this.config = config;
        this.playerLastTitle = new ConcurrentHashMap<>();
        this.lastOverdueWarning = new ConcurrentHashMap<>();
    }

    /**
     * Legacy constructor for backwards compatibility.
     */
    public ClaimTitleSystem(ClaimStorage claimStorage, TownStorage townStorage) {
        this(claimStorage, townStorage, null);
    }

    @Override
    public void tick(float deltaTime, int index, ArchetypeChunk<EntityStore> archetypeChunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) return;

        // Get player's current position and convert to chunk coordinates
        double posX = playerRef.getTransform().getPosition().getX();
        double posZ = playerRef.getTransform().getPosition().getZ();
        int chunkX = ChunkUtil.toChunkX(posX);
        int chunkZ = ChunkUtil.toChunkZ(posZ);
        String worldName = player.getWorld().getName();

        // Check if this chunk is claimed by a town
        Message titleMessage = WILDERNESS_MESSAGE;
        Message subtitleMessage = Message.raw("");
        String titleText = WILDERNESS_TEXT;

        String claimKey = worldName + ":" + chunkX + "," + chunkZ;
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town != null) {
            // Get town rank based on plots and citizens
            String rankName = "Outpost";
            if (config != null) {
                rankName = config.getTownRankName(town.getClaimCount(), town.getResidentCount());
            }

            // Town claim
            titleText = rankName + ": " + town.getName();
            boolean isOverdue = town.getMissedUpkeepDays() > 0;
            boolean pvpEnabled = town.getSettings().isPvpEnabled();

            // Build subtitle with PVP status
            String pvpStatus = pvpEnabled ? "PVP: ON" : "PVP: OFF";
            Color pvpColor = pvpEnabled ? RED : GREEN;

            if (town.isMember(playerRef.getUuid())) {
                if (isOverdue) {
                    // Show warning for overdue town
                    titleMessage = Message.raw(rankName + ": " + town.getName()).color(RED);
                    int daysLeft = 15 - town.getMissedUpkeepDays(); // GRACE_PERIOD_DAYS = 15
                    if (daysLeft <= 0) {
                        subtitleMessage = Message.raw("CRITICAL: Town will be deleted! | " + pvpStatus).color(RED);
                    } else {
                        subtitleMessage = Message.raw("OVERDUE! " + daysLeft + " days | " + pvpStatus).color(RED);
                    }

                    // Send chat warning every 30 seconds when entering own overdue town
                    long now = System.currentTimeMillis();
                    Long lastWarning = lastOverdueWarning.get(playerRef.getUuid());
                    if (lastWarning == null || now - lastWarning > 30000) {
                        lastOverdueWarning.put(playerRef.getUuid(), now);
                        playerRef.sendMessage(Message.raw("[Town] WARNING: Your town is in debt! Balance: $" +
                            String.format("%.2f", town.getBalance()) + ". Deposit funds to avoid deletion!").color(RED));
                    }
                } else {
                    titleMessage = Message.raw(rankName + ": " + town.getName()).color(CYAN);
                    subtitleMessage = Message.raw("Your " + rankName + " | " + pvpStatus).color(pvpColor);
                }
            } else {
                titleMessage = Message.raw(rankName + ": " + town.getName()).color(Color.WHITE);
                subtitleMessage = Message.raw(rankName + " | " + pvpStatus).color(pvpColor);
            }
        } else {
            // Check personal claims
            UUID claimOwner = claimStorage.getClaimOwner(worldName, chunkX, chunkZ);
            if (claimOwner != null) {
                String ownerName = claimStorage.getPlayerName(claimOwner);
                titleText = ownerName + "'s Claim";

                if (claimOwner.equals(playerRef.getUuid())) {
                    titleMessage = Message.raw("Your Claim").color(new Color(85, 255, 255));
                } else {
                    titleMessage = Message.raw(ownerName + "'s Claim").color(Color.WHITE);
                }
                subtitleMessage = Message.raw("Claim").color(new Color(170, 170, 170));
            }
        }

        // Only show title if the claim has changed
        String previousTitle = playerLastTitle.get(playerRef.getUuid());
        if (!titleText.equals(previousTitle)) {
            playerLastTitle.put(playerRef.getUuid(), titleText);
            EventTitleUtil.showEventTitleToPlayer(playerRef, titleMessage, subtitleMessage,
                    false, null, 2, 0.5f, 0.5f);
        }
    }

    /**
     * Remove player from tracking when they disconnect.
     */
    public void removePlayer(UUID playerId) {
        playerLastTitle.remove(playerId);
        lastOverdueWarning.remove(playerId);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
