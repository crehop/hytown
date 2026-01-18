package com.hytown.commands;

import com.hytown.HyTown;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.gui.PlotGui;
import com.hytown.util.ChunkUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
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

    private final HyTown plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color GRAY = new Color(170, 170, 170);

    public PlotCommand(HyTown plugin) {
        super("plot", "Manage town plots - claim/unclaim plots, set permissions, put for sale. Use /plot help for subcommands");
        addAliases("p");
        setAllowsExtraArguments(true);
        requirePermission("hytown.use");
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

        // No args or "gui" - open the plot management GUI
        if (args.length == 0) {
            openPlotGui(store, playerRef, playerData, world);
            return;
        }

        UUID playerId = playerData.getUuid();
        String action = args[0];
        String arg1 = args.length > 1 ? args[1] : null;
        String arg2 = args.length > 2 ? args[2] : null;

        switch (action.toLowerCase()) {
            case "gui" -> openPlotGui(store, playerRef, playerData, world);
            case "claim" -> handleClaim(store, playerRef, playerData, playerId, world);
            case "unclaim" -> handleUnclaim(store, playerRef, playerData, playerId, world);
            case "reset" -> handleReset(store, playerRef, playerData, playerId, world);
            case "forsale" -> handleForSale(store, playerRef, playerData, playerId, world, arg1);
            case "notforsale", "nfs" -> handleNotForSale(playerData);
            case "info" -> handleInfo(store, playerRef, playerData, world);
            case "set" -> handleSet(store, playerRef, playerData, playerId, world, arg1, arg2);
            case "help", "?" -> showHelp(playerData);
            default -> showHelp(playerData);
        }
    }

    private void openPlotGui(Store<EntityStore> store, Ref<EntityStore> playerRef,
                             PlayerRef playerData, World world) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            playerData.sendMessage(Message.raw("Unable to open GUI!").color(RED));
            return;
        }

        PlotGui.openFor(plugin, player, playerRef, store, world);
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

        // Check if plot already has an owner
        UUID currentOwner = town.getPlotOwner(claimKey);
        if (currentOwner != null) {
            String ownerName = town.getResidentName(currentOwner);
            if (currentOwner.equals(playerId)) {
                playerData.sendMessage(Message.raw("You already own this plot!").color(YELLOW));
            } else {
                playerData.sendMessage(Message.raw("This plot is owned by " + ownerName + "!").color(RED));
            }
            return;
        }

        // Only assistants+ can claim plots for themselves, or mayor can assign
        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only assistants and mayors can claim plot ownership!").color(RED));
            return;
        }

        // Claim the plot for this player
        town.setPlotOwner(claimKey, playerId);
        townStorage.saveTown(town);

        playerData.sendMessage(Message.raw("You now own plot [" + chunkX + ", " + chunkZ + "]!").color(GREEN));
        playerData.sendMessage(Message.raw("Use /plot set protection on to enable container protection.").color(GRAY));
    }

    private void handleUnclaim(Store<EntityStore> store, Ref<EntityStore> playerRef,
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

        UUID currentOwner = town.getPlotOwner(claimKey);
        if (currentOwner == null) {
            playerData.sendMessage(Message.raw("This plot has no personal owner (town-owned).").color(YELLOW));
            return;
        }

        // Only owner or mayor can unclaim
        if (!currentOwner.equals(playerId) && !town.isMayor(playerId)) {
            playerData.sendMessage(Message.raw("Only the plot owner or mayor can unclaim!").color(RED));
            return;
        }

        // Release the plot back to town
        town.setPlotOwner(claimKey, null);
        townStorage.saveTown(town);

        playerData.sendMessage(Message.raw("Plot [" + chunkX + ", " + chunkZ + "] released back to town.").color(GREEN));
    }

    private void handleReset(Store<EntityStore> store, Ref<EntityStore> playerRef,
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

        // Only mayor can reset plots
        if (!town.isMayor(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor can reset plots!").color(RED));
            return;
        }

        // Clear plot owner and settings
        town.setPlotOwner(claimKey, null);
        town.clearPlotSettings(claimKey);
        townStorage.saveTown(town);

        playerData.sendMessage(Message.raw("Plot [" + chunkX + ", " + chunkZ + "] reset to town defaults.").color(GREEN));
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

    private void handleSet(Store<EntityStore> store, Ref<EntityStore> playerRef,
                           PlayerRef playerData, UUID playerId, World world, String perm, String value) {
        if (perm == null) {
            showSetHelp(playerData);
            return;
        }

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

        // Check permission - plot owner, mayor, or assistant can modify
        UUID plotOwner = town.getPlotOwner(claimKey);
        boolean isOwner = plotOwner != null && plotOwner.equals(playerId);
        if (!isOwner && !town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only plot owner or assistants can modify settings!").color(RED));
            return;
        }

        // Handle toggle vs explicit set
        com.hytown.data.PlotSettings settings = town.getOrCreatePlotSettings(claimKey);

        if (value == null) {
            // Toggle mode
            Boolean newValue = settings.toggle(perm);
            if (newValue == null && !perm.equalsIgnoreCase("protection") && !perm.equalsIgnoreCase("ownerprotection")) {
                // For override settings, null means "use town default"
                townStorage.saveTown(town);
                playerData.sendMessage(Message.raw("Plot " + perm + " set to: Town Default").color(GREEN));
                return;
            }
            if (newValue == null) {
                playerData.sendMessage(Message.raw("Unknown setting: " + perm).color(RED));
                showSetHelp(playerData);
                return;
            }
            townStorage.saveTown(town);
            playerData.sendMessage(Message.raw("Plot " + perm + " set to: " + (newValue ? "ON" : "OFF")).color(GREEN));
        } else {
            // Explicit set mode
            boolean enabled = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
            boolean disabled = value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no");
            boolean useDefault = value.equalsIgnoreCase("default") || value.equalsIgnoreCase("town");

            switch (perm.toLowerCase()) {
                case "protection", "ownerprotection" -> {
                    settings.setOwnerProtection(enabled);
                    playerData.sendMessage(Message.raw("Plot owner protection: " + (enabled ? "ON" : "OFF")).color(GREEN));
                    if (enabled) {
                        playerData.sendMessage(Message.raw("Only you and allowed players can access containers.").color(GRAY));
                    }
                }
                case "pvp" -> {
                    settings.setPvpEnabled(useDefault ? null : enabled);
                    playerData.sendMessage(Message.raw("Plot PvP: " + (useDefault ? "Town Default" : (enabled ? "ON" : "OFF"))).color(GREEN));
                }
                case "explosion", "explosions" -> {
                    settings.setExplosionsEnabled(useDefault ? null : enabled);
                    playerData.sendMessage(Message.raw("Plot explosions: " + (useDefault ? "Town Default" : (enabled ? "ON" : "OFF"))).color(GREEN));
                }
                case "fire" -> {
                    settings.setFireSpreadEnabled(useDefault ? null : enabled);
                    playerData.sendMessage(Message.raw("Plot fire spread: " + (useDefault ? "Town Default" : (enabled ? "ON" : "OFF"))).color(GREEN));
                }
                case "mobs" -> {
                    settings.setMobSpawningEnabled(useDefault ? null : enabled);
                    playerData.sendMessage(Message.raw("Plot mob spawning: " + (useDefault ? "Town Default" : (enabled ? "ON" : "OFF"))).color(GREEN));
                }
                default -> {
                    playerData.sendMessage(Message.raw("Unknown setting: " + perm).color(RED));
                    showSetHelp(playerData);
                    return;
                }
            }
            townStorage.saveTown(town);
        }
    }

    private void showSetHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("========== Plot Settings ==========").color(GOLD));
        playerData.sendMessage(Message.raw("/plot set protection <on|off>").color(WHITE));
        playerData.sendMessage(Message.raw("  Enable container protection for this plot").color(GRAY));
        playerData.sendMessage(Message.raw("/plot set pvp <on|off|default>").color(WHITE));
        playerData.sendMessage(Message.raw("/plot set explosion <on|off|default>").color(WHITE));
        playerData.sendMessage(Message.raw("/plot set fire <on|off|default>").color(WHITE));
        playerData.sendMessage(Message.raw("/plot set mobs <on|off|default>").color(WHITE));
    }

    private void showHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("========== Plot Commands ==========").color(GOLD));
        playerData.sendMessage(Message.raw("/plot - Open plot management GUI").color(WHITE));
        playerData.sendMessage(Message.raw("/plot info - Show plot information").color(WHITE));
        playerData.sendMessage(Message.raw("/plot claim - Claim personal plot ownership").color(WHITE));
        playerData.sendMessage(Message.raw("/plot unclaim - Release plot back to town").color(WHITE));
        playerData.sendMessage(Message.raw("/plot reset - Reset plot to town defaults (mayor only)").color(WHITE));
        playerData.sendMessage(Message.raw("/plot set <perm> <on|off|default> - Set permissions").color(WHITE));
        playerData.sendMessage(Message.raw("/plot forsale <price> - Set for sale").color(WHITE));
    }
}
