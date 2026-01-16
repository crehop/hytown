package com.hytowny.commands;

import com.hytowny.HyTowny;
import com.hytowny.data.Town;
import com.hytowny.data.TownStorage;
import com.hytowny.util.ChunkUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;

/**
 * /plot command - Manage individual plots within towns.
 */
public class PlotCommand extends AbstractPlayerCommand {

    private final HyTowny plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GRAY = new Color(170, 170, 170);

    public PlotCommand(HyTowny plugin) {
        super("plot", "Manage individual plots within towns");
        addAliases("p");
        setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    private String[] parseArgs(CommandContext ctx) {
        String input = ctx.getInputString().trim();
        if (input.isEmpty()) return new String[0];
        String[] allArgs = input.split("\\s+");
        if (allArgs.length > 0 && (allArgs[0].equalsIgnoreCase("plot") || allArgs[0].equalsIgnoreCase("p"))) {
            String[] args = new String[allArgs.length - 1];
            System.arraycopy(allArgs, 1, args, 0, allArgs.length - 1);
            return args;
        }
        return allArgs;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> playerRef, @Nonnull PlayerRef playerData, @Nonnull World world) {

        String[] args = parseArgs(ctx);
        if (args.length == 0) {
            showHelp(playerData);
            return;
        }

        UUID playerId = playerData.getUuid();
        String action = args[0];
        String arg1 = args.length > 1 ? args[1] : null;
        String arg2 = args.length > 2 ? args[2] : null;

        switch (action.toLowerCase()) {
            case "claim" -> handleClaim(store, playerRef, playerData, playerId, world);
            case "unclaim" -> handleUnclaim(playerData);
            case "forsale" -> handleForSale(store, playerRef, playerData, playerId, world, arg1);
            case "notforsale", "nfs" -> handleNotForSale(playerData);
            case "info" -> handleInfo(store, playerRef, playerData, world);
            case "set" -> handleSet(playerData, arg1, arg2);
            default -> showHelp(playerData);
        }
    }

    private void handleClaim(Store<EntityStore> store, Ref<EntityStore> playerRef,
                             PlayerRef playerData, UUID playerId, World world) {
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();

        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town == null) {
            playerData.sendMessage(Message.raw("This plot is not part of a town!").color(RED));
            return;
        }

        if (!town.isMember(playerId)) {
            playerData.sendMessage(Message.raw("You must be a member of " + town.getName() + "!").color(RED));
            return;
        }

        playerData.sendMessage(Message.raw("Plot claiming within towns coming soon!").color(YELLOW));
    }

    private void handleUnclaim(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("Plot unclaiming within towns coming soon!").color(YELLOW));
    }

    private void handleForSale(Store<EntityStore> store, Ref<EntityStore> playerRef,
                               PlayerRef playerData, UUID playerId, World world, String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /plot forsale <price>").color(RED));
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            playerData.sendMessage(Message.raw("Invalid price!").color(RED));
            return;
        }

        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());

        playerData.sendMessage(Message.raw("Plot [" + chunkX + ", " + chunkZ + "] set for sale at $" +
                String.format("%.2f", price)).color(GREEN));
        playerData.sendMessage(Message.raw("(Full plot sale system coming soon)").color(GRAY));
    }

    private void handleNotForSale(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("Plot removed from sale.").color(GREEN));
    }

    private void handleInfo(Store<EntityStore> store, Ref<EntityStore> playerRef,
                            PlayerRef playerData, World world) {
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();

        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        playerData.sendMessage(Message.raw("========== Plot Info ==========").color(GOLD));
        playerData.sendMessage(Message.raw("Chunk: [" + chunkX + ", " + chunkZ + "]").color(WHITE));
        playerData.sendMessage(Message.raw("World: " + worldName).color(WHITE));

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town != null) {
            playerData.sendMessage(Message.raw("Town: " + town.getName()).color(GREEN));
            playerData.sendMessage(Message.raw("Mayor: " + town.getMayorName()).color(WHITE));
        } else {
            UUID owner = plugin.getClaimManager().getOwnerAt(worldName, pos.getX(), pos.getZ());
            if (owner != null) {
                String ownerName = plugin.getClaimStorage().getPlayerName(owner);
                playerData.sendMessage(Message.raw("Owner: " + ownerName + " (personal)").color(YELLOW));
            } else {
                playerData.sendMessage(Message.raw("Status: Wilderness").color(GRAY));
            }
        }
    }

    private void handleSet(PlayerRef playerData, String perm, String value) {
        if (perm == null || value == null) {
            playerData.sendMessage(Message.raw("Usage: /plot set <perm> <on|off>").color(RED));
            return;
        }

        boolean enabled = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
        playerData.sendMessage(Message.raw("Plot " + perm + " set to " + (enabled ? "ON" : "OFF")).color(GREEN));
        playerData.sendMessage(Message.raw("(Per-plot permissions coming soon)").color(GRAY));
    }

    private void showHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("========== Plot Commands ==========").color(GOLD));
        playerData.sendMessage(Message.raw("/plot claim - Claim plot in your town").color(WHITE));
        playerData.sendMessage(Message.raw("/plot forsale <price> - Set for sale").color(WHITE));
        playerData.sendMessage(Message.raw("/plot info - Show plot information").color(WHITE));
        playerData.sendMessage(Message.raw("/plot set <perm> <on|off> - Set perms").color(WHITE));
    }
}
