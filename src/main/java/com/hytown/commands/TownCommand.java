package com.hytown.commands;

import com.hytown.HyTown;
import com.hytown.data.Town;
import com.hytown.data.TownStorage;
import com.hytown.data.TownTransaction;
import com.hytown.gui.TownGui;
import com.hytown.gui.TownHelpGui;
import com.hytown.managers.ClaimManager;
import com.hytown.util.ChunkUtil;
import com.hycrown.hyconomy.HyConomy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Main /town command with Towny-style subcommands.
 */
public class TownCommand extends AbstractPlayerCommand {

    private final HyTown plugin;

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color WHITE = new Color(255, 255, 255);

    public TownCommand(HyTown plugin) {
        super("town", "Town management commands");
        addAliases("t");
        setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    private String[] parseArgs(CommandContext ctx) {
        String input = ctx.getInputString().trim();
        if (input.isEmpty()) return new String[0];
        String[] allArgs = input.split("\\s+");
        if (allArgs.length > 0 && (allArgs[0].equalsIgnoreCase("town") || allArgs[0].equalsIgnoreCase("t"))) {
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

        // Check if player is admin
        Player player = store.getComponent(playerRef, Player.getComponentType());
        boolean isAdmin = player != null && player.hasPermission("towny.admin");

        if (args.length == 0) {
            showHelp(playerData, isAdmin);
            return;
        }

        UUID playerId = playerData.getUuid();
        String playerName = playerData.getUsername();
        String action = args[0];
        String arg1 = args.length > 1 ? args[1] : null;
        String arg2 = args.length > 2 ? args[2] : null;
        String arg3 = args.length > 3 ? args[3] : null;

        switch (action.toLowerCase()) {
            case "gui", "menu" -> handleGui(store, playerRef, playerData, world);
            case "help", "?" -> handleHelp(store, playerRef, playerData, world, isAdmin);
            case "new", "create" -> handleNew(playerData, playerId, playerName, arg1);
            case "delete" -> handleDelete(playerData, playerId);
            case "claim" -> handleClaim(store, playerRef, playerData, playerId, world);
            case "unclaim" -> handleUnclaim(store, playerRef, playerData, playerId, world);
            case "add", "invite" -> handleInvite(playerData, playerId, arg1);
            case "kick" -> handleKick(playerData, playerId, arg1);
            case "leave" -> handleLeave(playerData, playerId);
            case "join" -> handleJoin(playerData, playerId, playerName, arg1);
            case "info" -> handleInfo(playerData, playerId, arg1);
            case "list" -> handleList(playerData);
            case "spawn" -> handleSpawn(store, playerRef, playerData, playerId, world);
            case "deposit" -> handleDeposit(playerData, playerId, arg1);
            case "withdraw" -> handleWithdraw(playerData, playerId, arg1);
            case "balance", "bal" -> handleBalance(playerData, playerId);
            case "set" -> handleSet(store, playerRef, playerData, playerId, world, arg1, arg2);
            case "toggle" -> handleToggle(playerData, playerId, arg1);
            case "rank" -> handleRank(playerData, playerId, arg1, arg2, arg3);
            case "online" -> handleOnline(playerData, playerId);
            case "here" -> handleHere(store, playerRef, playerData, world);
            case "log" -> handleLog(playerData, playerId, arg1);
            case "board", "motd" -> handleBoard(ctx, playerData, playerId, args);
            default -> showHelp(playerData, isAdmin);
        }
    }

    private void handleGui(Store<EntityStore> store, Ref<EntityStore> playerRef,
                           PlayerRef playerData, World world) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            TownGui.openFor(plugin, player, playerRef, store, world);
        }
    }

    private void handleHelp(Store<EntityStore> store, Ref<EntityStore> playerRef,
                            PlayerRef playerData, World world, boolean isAdmin) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            TownHelpGui.openFor(plugin, player, playerRef, store, world, isAdmin);
        }
    }

    private void handleNew(PlayerRef playerData, UUID playerId, String playerName, String townName) {
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town new <name>").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town existingTown = townStorage.getPlayerTown(playerId);
        if (existingTown != null) {
            playerData.sendMessage(Message.raw("You are already in town: " + existingTown.getName()).color(RED));
            return;
        }

        if (townStorage.townExists(townName)) {
            playerData.sendMessage(Message.raw("A town with that name already exists!").color(RED));
            return;
        }

        if (townName.length() < 3 || townName.length() > 24) {
            playerData.sendMessage(Message.raw("Town name must be 3-24 characters!").color(RED));
            return;
        }
        if (!townName.matches("^[a-zA-Z0-9_-]+$")) {
            playerData.sendMessage(Message.raw("Town name can only contain letters, numbers, _ and -").color(RED));
            return;
        }

        // Check if player has enough money (applies to everyone, including ops)
        double cost = plugin.getPluginConfig().getTownCreationCost();
        if (cost > 0) {
            if (!HyConomy.has(playerName, cost)) {
                playerData.sendMessage(Message.raw("You need " + HyConomy.format(cost) + " to create a town!").color(RED));
                playerData.sendMessage(Message.raw("Your balance: " + HyConomy.format(HyConomy.getBalance(playerName))).color(GRAY));
                return;
            }
            if (!HyConomy.withdraw(playerName, cost)) {
                playerData.sendMessage(Message.raw("Failed to withdraw funds!").color(RED));
                return;
            }
        }

        Town town = new Town(townName, playerId, playerName);
        townStorage.saveTown(town);

        playerData.sendMessage(Message.raw("Town '" + townName + "' created!").color(GREEN));
        if (cost > 0) {
            playerData.sendMessage(Message.raw("Cost: " + HyConomy.format(cost)).color(GRAY));
        }
        playerData.sendMessage(Message.raw("Use /town claim to claim land for your town.").color(GRAY));
    }

    private void handleDelete(PlayerRef playerData, UUID playerId) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You are not in a town!").color(RED));
            return;
        }

        if (!town.isMayor(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor can delete the town!").color(RED));
            return;
        }

        String townName = town.getName();

        for (String claimKey : town.getClaimKeys()) {
            String[] parts = claimKey.split(":");
            if (parts.length == 2) {
                String claimWorld = parts[0];
                String[] coords = parts[1].split(",");
                if (coords.length == 2) {
                    try {
                        int chunkX = Integer.parseInt(coords[0]);
                        int chunkZ = Integer.parseInt(coords[1]);
                        plugin.getClaimStorage().removeClaim(town.getMayorId(), claimWorld, chunkX, chunkZ);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        townStorage.deleteTown(townName);
        playerData.sendMessage(Message.raw("Town '" + townName + "' has been deleted.").color(YELLOW));
    }

    private void handleClaim(Store<EntityStore> store, Ref<EntityStore> playerRef,
                             PlayerRef playerData, UUID playerId, World world) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);
        String playerName = playerData.getUsername();

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town to claim land!").color(RED));
            return;
        }

        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor or assistants can claim land!").color(RED));
            return;
        }

        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();

        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        if (town.ownsClaim(claimKey)) {
            playerData.sendMessage(Message.raw("This chunk is already claimed by your town!").color(RED));
            return;
        }

        Town existingTown = townStorage.getTownByClaimKey(claimKey);
        if (existingTown != null) {
            playerData.sendMessage(Message.raw("This chunk is claimed by: " + existingTown.getName()).color(RED));
            return;
        }

        // Check adjacency - claims must be face-adjacent (not diagonal)
        if (!town.isAdjacentToExistingClaim(worldName, chunkX, chunkZ)) {
            playerData.sendMessage(Message.raw("Claims must be adjacent to existing town land!").color(RED));
            playerData.sendMessage(Message.raw("Diagonal claims are not allowed - claims must share a full side.").color(GRAY));
            return;
        }

        // Check town claim limit
        int maxTownClaims = plugin.getPluginConfig().getMaxTownClaims();
        if (town.getClaimCount() >= maxTownClaims) {
            playerData.sendMessage(Message.raw("Town has reached maximum claims (" + maxTownClaims + ")!").color(RED));
            return;
        }

        // Check if player has enough money for claim (applies to everyone, including ops)
        double cost = plugin.getPluginConfig().getTownClaimCost();
        if (cost > 0) {
            if (!HyConomy.has(playerName, cost)) {
                playerData.sendMessage(Message.raw("You need " + HyConomy.format(cost) + " to claim land!").color(RED));
                playerData.sendMessage(Message.raw("Your balance: " + HyConomy.format(HyConomy.getBalance(playerName))).color(GRAY));
                return;
            }
            if (!HyConomy.withdraw(playerName, cost)) {
                playerData.sendMessage(Message.raw("Failed to withdraw funds!").color(RED));
                return;
            }
        }

        ClaimManager.ClaimResult result = plugin.getClaimManager().claimChunk(
                town.getMayorId(), worldName, pos.getX(), pos.getZ()
        );

        if (result == ClaimManager.ClaimResult.SUCCESS) {
            town.addClaim(claimKey);
            townStorage.saveTown(town);
            String costMsg = cost > 0 ? " (Cost: " + HyConomy.format(cost) + ")" : "";
            playerData.sendMessage(Message.raw("Claimed chunk [" + chunkX + ", " + chunkZ + "] for " + town.getName() + costMsg).color(GREEN));
            plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);
        } else {
            // Refund if claim failed
            if (cost > 0) {
                HyConomy.deposit(playerName, cost);
            }
            switch (result) {
                case ALREADY_OWN -> playerData.sendMessage(Message.raw("Already claimed!").color(RED));
                case CLAIMED_BY_OTHER -> playerData.sendMessage(Message.raw("Claimed by someone else!").color(RED));
                case LIMIT_REACHED -> playerData.sendMessage(Message.raw("Claim limit reached!").color(RED));
                case TOO_CLOSE_TO_OTHER_CLAIM -> playerData.sendMessage(Message.raw("Too close to another claim!").color(RED));
            }
        }
    }

    private void handleUnclaim(Store<EntityStore> store, Ref<EntityStore> playerRef,
                               PlayerRef playerData, UUID playerId, World world) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor or assistants can unclaim land!").color(RED));
            return;
        }

        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();

        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        if (!town.ownsClaim(claimKey)) {
            playerData.sendMessage(Message.raw("This chunk is not claimed by your town!").color(RED));
            return;
        }

        plugin.getClaimStorage().removeClaim(town.getMayorId(), worldName, chunkX, chunkZ);
        town.removeClaim(claimKey);
        townStorage.saveTown(town);

        playerData.sendMessage(Message.raw("Unclaimed chunk [" + chunkX + ", " + chunkZ + "]").color(GREEN));
        plugin.refreshWorldMapChunk(worldName, chunkX, chunkZ);
    }

    private void handleInvite(PlayerRef playerData, UUID playerId, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town add <player>").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor or assistants can invite players!").color(RED));
            return;
        }

        playerData.sendMessage(Message.raw("Invited " + targetName + " to " + town.getName()).color(GREEN));
        playerData.sendMessage(Message.raw("They can join with /town join " + town.getName()).color(GRAY));
    }

    private void handleKick(PlayerRef playerData, UUID playerId, String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town kick <player>").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor or assistants can kick players!").color(RED));
            return;
        }

        UUID targetId = null;
        for (var entry : town.getResidentNames().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                targetId = entry.getKey();
                break;
            }
        }

        if (targetId == null) {
            playerData.sendMessage(Message.raw(targetName + " is not in your town!").color(RED));
            return;
        }

        if (town.isMayor(targetId)) {
            playerData.sendMessage(Message.raw("You cannot kick the mayor!").color(RED));
            return;
        }

        String kickedName = town.getResidentName(targetId);
        town.removeResident(targetId);
        town.logMemberKick(playerId, playerData.getUsername(), kickedName);
        townStorage.saveTown(town);
        townStorage.unindexPlayer(targetId);

        playerData.sendMessage(Message.raw("Kicked " + targetName + " from " + town.getName()).color(GREEN));
    }

    private void handleLeave(PlayerRef playerData, UUID playerId) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You are not in a town!").color(RED));
            return;
        }

        if (town.isMayor(playerId)) {
            if (town.getResidentCount() > 1) {
                playerData.sendMessage(Message.raw("You must transfer mayor first! Use /town set mayor <player>").color(RED));
                return;
            }
            townStorage.deleteTown(town.getName());
            playerData.sendMessage(Message.raw("You left and " + town.getName() + " has been disbanded.").color(YELLOW));
            return;
        }

        String leavingName = playerData.getUsername();
        town.logMemberLeave(playerId, leavingName);
        town.removeResident(playerId);
        townStorage.saveTown(town);
        townStorage.unindexPlayer(playerId);

        playerData.sendMessage(Message.raw("You left " + town.getName()).color(GREEN));
    }

    private void handleJoin(PlayerRef playerData, UUID playerId, String playerName, String townName) {
        if (townName == null || townName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town join <town>").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town currentTown = townStorage.getPlayerTown(playerId);
        if (currentTown != null) {
            playerData.sendMessage(Message.raw("You are already in " + currentTown.getName() + "!").color(RED));
            return;
        }

        Town town = townStorage.getTown(townName);
        if (town == null) {
            playerData.sendMessage(Message.raw("Town '" + townName + "' not found!").color(RED));
            return;
        }

        if (!town.getSettings().isOpenTown()) {
            playerData.sendMessage(Message.raw("This town is not open!").color(RED));
            return;
        }

        town.addResident(playerId, playerName);
        town.logMemberJoin(playerId, playerName);
        townStorage.saveTown(town);
        townStorage.indexPlayer(playerId, town.getName());

        playerData.sendMessage(Message.raw("You joined " + town.getName() + "!").color(GREEN));
    }

    private void handleInfo(PlayerRef playerData, UUID playerId, String townName) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town;

        if (townName == null || townName.isEmpty()) {
            town = townStorage.getPlayerTown(playerId);
            if (town == null) {
                playerData.sendMessage(Message.raw("You are not in a town! Use /town info <name>").color(RED));
                return;
            }
        } else {
            town = townStorage.getTown(townName);
            if (town == null) {
                playerData.sendMessage(Message.raw("Town '" + townName + "' not found!").color(RED));
                return;
            }
        }

        playerData.sendMessage(Message.raw("========== " + town.getName() + " ==========").color(GOLD));
        playerData.sendMessage(Message.raw("Mayor: " + town.getMayorName()).color(WHITE));
        playerData.sendMessage(Message.raw("Residents: " + town.getResidentCount()).color(WHITE));
        playerData.sendMessage(Message.raw("Claims: " + town.getClaimCount() + "/" + plugin.getPluginConfig().getMaxTownClaims()).color(WHITE));
        playerData.sendMessage(Message.raw("Balance: " + HyConomy.format(town.getBalance())).color(WHITE));
        playerData.sendMessage(Message.raw("Board: " + town.getBoard()).color(GRAY));
    }

    private void handleList(PlayerRef playerData) {
        TownStorage townStorage = plugin.getTownStorage();
        Collection<Town> towns = townStorage.getAllTowns();

        if (towns.isEmpty()) {
            playerData.sendMessage(Message.raw("No towns exist yet!").color(YELLOW));
            return;
        }

        playerData.sendMessage(Message.raw("========== Towns (" + towns.size() + ") ==========").color(GOLD));
        for (Town town : towns) {
            playerData.sendMessage(Message.raw(town.getName() + " - Mayor: " + town.getMayorName() +
                    ", Residents: " + town.getResidentCount()).color(WHITE));
        }
    }

    private void handleSpawn(Store<EntityStore> store, Ref<EntityStore> playerRef,
                             PlayerRef playerData, UUID playerId, World world) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        if (!town.hasSpawn()) {
            playerData.sendMessage(Message.raw("Town spawn not set!").color(RED));
            return;
        }

        world.execute(() -> {
            Vector3d pos = new Vector3d(town.getSpawnX(), town.getSpawnY(), town.getSpawnZ());
            Vector3f rot = new Vector3f(town.getSpawnYaw(), town.getSpawnPitch(), 0);
            Teleport teleport = new Teleport(world, pos, rot);
            store.addComponent(playerRef, Teleport.getComponentType(), teleport);
        });

        playerData.sendMessage(Message.raw("Teleported to " + town.getName() + " spawn!").color(GREEN));
    }

    private void handleDeposit(PlayerRef playerData, UUID playerId, String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town deposit <amount>").color(RED));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
            return;
        }

        if (amount <= 0) {
            playerData.sendMessage(Message.raw("Amount must be positive!").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);
        String playerName = playerData.getUsername();

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        // Check and withdraw from player's HyConomy balance
        if (!HyConomy.has(playerName, amount)) {
            playerData.sendMessage(Message.raw("Insufficient funds! You have: " + HyConomy.format(HyConomy.getBalance(playerName))).color(RED));
            return;
        }

        if (!HyConomy.withdraw(playerName, amount)) {
            playerData.sendMessage(Message.raw("Failed to withdraw from your balance!").color(RED));
            return;
        }

        town.deposit(amount, playerId, playerName);
        townStorage.saveTown(town);
        playerData.sendMessage(Message.raw("Deposited " + HyConomy.format(amount) + " to " + town.getName()).color(GREEN));
        playerData.sendMessage(Message.raw("Town balance: " + HyConomy.format(town.getBalance())).color(GRAY));
    }

    private void handleWithdraw(PlayerRef playerData, UUID playerId, String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town withdraw <amount>").color(RED));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            playerData.sendMessage(Message.raw("Invalid amount!").color(RED));
            return;
        }

        if (amount <= 0) {
            playerData.sendMessage(Message.raw("Amount must be positive!").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);
        String playerName = playerData.getUsername();

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor or assistants can withdraw!").color(RED));
            return;
        }

        if (!town.withdraw(amount, playerId, playerName)) {
            playerData.sendMessage(Message.raw("Insufficient town funds! Town balance: " + HyConomy.format(town.getBalance())).color(RED));
            return;
        }

        // Deposit to player's HyConomy balance
        HyConomy.deposit(playerName, amount);
        townStorage.saveTown(town);
        playerData.sendMessage(Message.raw("Withdrew " + HyConomy.format(amount) + " from " + town.getName()).color(GREEN));
        playerData.sendMessage(Message.raw("Town balance: " + HyConomy.format(town.getBalance())).color(GRAY));
    }

    private void handleBalance(PlayerRef playerData, UUID playerId) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);
        String playerName = playerData.getUsername();

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        playerData.sendMessage(Message.raw("========== Balance ==========").color(GOLD));
        playerData.sendMessage(Message.raw("Your balance: " + HyConomy.format(HyConomy.getBalance(playerName))).color(WHITE));
        playerData.sendMessage(Message.raw(town.getName() + " balance: " + HyConomy.format(town.getBalance())).color(GREEN));
    }

    private void handleSet(Store<EntityStore> store, Ref<EntityStore> playerRef,
                           PlayerRef playerData, UUID playerId, World world, String setting, String value) {
        if (setting == null || setting.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town set <spawn|board|mayor> [value]").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        switch (setting.toLowerCase()) {
            case "spawn" -> {
                if (!town.isMayor(playerId)) {
                    playerData.sendMessage(Message.raw("Only the mayor can set spawn!").color(RED));
                    return;
                }
                TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
                Vector3d pos = transform.getPosition();
                Vector3f rot = transform.getRotation();
                town.setSpawn(world.getName(), pos.getX(), pos.getY(), pos.getZ(), rot.getX(), rot.getY());
                townStorage.saveTown(town);
                playerData.sendMessage(Message.raw("Town spawn set!").color(GREEN));
            }
            case "board" -> {
                if (value == null) {
                    playerData.sendMessage(Message.raw("Usage: /town set board <message>").color(RED));
                    return;
                }
                town.setBoard(value);
                townStorage.saveTown(town);
                playerData.sendMessage(Message.raw("Town board updated!").color(GREEN));
            }
            case "mayor" -> {
                if (!town.isMayor(playerId)) {
                    playerData.sendMessage(Message.raw("Only the mayor can transfer leadership!").color(RED));
                    return;
                }
                if (value == null) {
                    playerData.sendMessage(Message.raw("Usage: /town set mayor <player>").color(RED));
                    return;
                }
                UUID newMayorId = null;
                String newMayorName = value;
                for (var entry : town.getResidentNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(value)) {
                        newMayorId = entry.getKey();
                        newMayorName = entry.getValue();
                        break;
                    }
                }
                if (newMayorId == null) {
                    playerData.sendMessage(Message.raw(value + " is not in your town!").color(RED));
                    return;
                }
                town.setMayor(newMayorId, newMayorName);
                townStorage.saveTown(town);
                playerData.sendMessage(Message.raw(newMayorName + " is now the mayor!").color(GREEN));
            }
            default -> playerData.sendMessage(Message.raw("Unknown setting: " + setting).color(RED));
        }
    }

    private void handleToggle(PlayerRef playerData, UUID playerId, String setting) {
        if (setting == null || setting.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /town toggle <pvp|explosion|fire|mobs|open>").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        if (!town.isMayor(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor can toggle settings!").color(RED));
            return;
        }

        Boolean newValue = town.getSettings().toggle(setting);
        if (newValue == null) {
            playerData.sendMessage(Message.raw("Unknown toggle: " + setting).color(RED));
            return;
        }

        townStorage.saveTown(town);
        playerData.sendMessage(Message.raw(setting.toUpperCase() + " is now " + (newValue ? "ON" : "OFF")).color(GREEN));
    }

    private void handleRank(PlayerRef playerData, UUID playerId, String action, String targetName, String rank) {
        if (action == null || targetName == null || rank == null) {
            playerData.sendMessage(Message.raw("Usage: /town rank add/remove <player> assistant").color(RED));
            return;
        }

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null || !town.isMayor(playerId)) {
            playerData.sendMessage(Message.raw("Only the mayor can manage ranks!").color(RED));
            return;
        }

        UUID targetId = null;
        for (var entry : town.getResidentNames().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                targetId = entry.getKey();
                break;
            }
        }

        if (targetId == null) {
            playerData.sendMessage(Message.raw(targetName + " is not in your town!").color(RED));
            return;
        }

        if (action.equalsIgnoreCase("add")) {
            town.promoteToAssistant(targetId);
            playerData.sendMessage(Message.raw(targetName + " is now an assistant!").color(GREEN));
        } else {
            town.demoteFromAssistant(targetId);
            playerData.sendMessage(Message.raw(targetName + " is no longer an assistant!").color(GREEN));
        }
        townStorage.saveTown(town);
    }

    private void handleOnline(PlayerRef playerData, UUID playerId) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        playerData.sendMessage(Message.raw("Online members of " + town.getName() + ":").color(GOLD));
        playerData.sendMessage(Message.raw("(Feature coming soon)").color(GRAY));
    }

    private void handleHere(Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerData, World world) {
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition();
        String worldName = world.getName();

        int chunkX = ChunkUtil.toChunkX(pos.getX());
        int chunkZ = ChunkUtil.toChunkZ(pos.getZ());
        String claimKey = worldName + ":" + chunkX + "," + chunkZ;

        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getTownByClaimKey(claimKey);

        if (town != null) {
            playerData.sendMessage(Message.raw("This chunk belongs to: " + town.getName()).color(GREEN));
        } else {
            UUID owner = plugin.getClaimManager().getOwnerAt(worldName, pos.getX(), pos.getZ());
            if (owner != null) {
                String ownerName = plugin.getClaimStorage().getPlayerName(owner);
                playerData.sendMessage(Message.raw("Claimed by: " + ownerName).color(YELLOW));
            } else {
                playerData.sendMessage(Message.raw("Wilderness (unclaimed)").color(GRAY));
            }
        }
        playerData.sendMessage(Message.raw("Chunk: [" + chunkX + ", " + chunkZ + "]").color(GRAY));
    }

    private void handleLog(PlayerRef playerData, UUID playerId, String pageStr) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        // Only assistants and mayor can view the log
        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only mayor and assistants can view the transaction log!").color(RED));
            return;
        }

        List<TownTransaction> transactions = town.getTransactionHistory();

        if (transactions.isEmpty()) {
            playerData.sendMessage(Message.raw("No transactions recorded yet.").color(YELLOW));
            return;
        }

        // Pagination
        int page = 1;
        if (pageStr != null) {
            try {
                page = Integer.parseInt(pageStr);
            } catch (NumberFormatException ignored) {}
        }

        int perPage = 10;
        int totalPages = (int) Math.ceil(transactions.size() / (double) perPage);
        page = Math.max(1, Math.min(page, totalPages));

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, transactions.size());

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm");

        playerData.sendMessage(Message.raw("========== " + town.getName() + " Transaction Log ==========").color(GOLD));
        playerData.sendMessage(Message.raw("Page " + page + "/" + totalPages + " (" + transactions.size() + " total)").color(GRAY));

        for (int i = start; i < end; i++) {
            TownTransaction tx = transactions.get(i);
            String time = dateFormat.format(new Date(tx.getTimestamp()));
            String displayStr = tx.getDisplayString();

            // Color based on transaction type
            Color color = switch (tx.getType()) {
                case DEPOSIT -> GREEN;
                case WITHDRAW, UPKEEP, PLOT_PURCHASE, TOWN_CREATE -> RED;
                case MEMBER_JOIN -> GREEN;
                case MEMBER_LEAVE, MEMBER_KICK -> YELLOW;
                default -> WHITE;
            };

            playerData.sendMessage(Message.raw("[" + time + "] " + displayStr).color(color));
        }

        if (totalPages > 1) {
            playerData.sendMessage(Message.raw("Use /town log <page> to view more").color(GRAY));
        }
    }

    private void handleBoard(CommandContext ctx, PlayerRef playerData, UUID playerId, String[] args) {
        TownStorage townStorage = plugin.getTownStorage();
        Town town = townStorage.getPlayerTown(playerId);

        if (town == null) {
            playerData.sendMessage(Message.raw("You must be in a town!").color(RED));
            return;
        }

        // No message provided - show current board
        if (args.length < 2) {
            String currentBoard = town.getBoard();
            if (currentBoard == null || currentBoard.isEmpty()) {
                playerData.sendMessage(Message.raw("No town message set.").color(YELLOW));
            } else {
                playerData.sendMessage(Message.raw("[" + town.getName() + "] " + currentBoard).color(GOLD));
            }
            playerData.sendMessage(Message.raw("Usage: /town board <message> (100 chars max)").color(GRAY));
            return;
        }

        // Only mayor/assistant can set the board
        if (!town.isAssistant(playerId)) {
            playerData.sendMessage(Message.raw("Only mayor and assistants can set the town message!").color(RED));
            return;
        }

        // Get the full message (everything after "board ")
        String input = ctx.getInputString().trim();
        int boardIndex = input.toLowerCase().indexOf("board ");
        if (boardIndex == -1) {
            boardIndex = input.toLowerCase().indexOf("motd ");
        }

        String message = "";
        if (boardIndex != -1) {
            message = input.substring(boardIndex + 6).trim();
        }

        // Check for clear command
        if (message.equalsIgnoreCase("clear") || message.equalsIgnoreCase("none") || message.isEmpty()) {
            town.setBoard("");
            townStorage.saveTown(town);
            playerData.sendMessage(Message.raw("Town message cleared!").color(GREEN));
            return;
        }

        // Enforce 100 character limit
        if (message.length() > 100) {
            playerData.sendMessage(Message.raw("Message too long! Max 100 characters (yours: " + message.length() + ")").color(RED));
            return;
        }

        town.setBoard(message);
        townStorage.saveTown(town);
        playerData.sendMessage(Message.raw("Town message set!").color(GREEN));
        playerData.sendMessage(Message.raw("[" + town.getName() + "] " + message).color(GOLD));
    }

    private void showHelp(PlayerRef playerData, boolean isAdmin) {
        playerData.sendMessage(Message.raw("========== Town Commands ==========").color(GOLD));
        playerData.sendMessage(Message.raw("/town help - Open help GUI (recommended)").color(WHITE));
        playerData.sendMessage(Message.raw("/town gui - Open town management UI").color(WHITE));
        playerData.sendMessage(Message.raw("/town new <name> - Create a town").color(WHITE));
        playerData.sendMessage(Message.raw("/town delete - Delete your town (mayor only)").color(WHITE));
        playerData.sendMessage(Message.raw("/town claim - Claim current chunk").color(WHITE));
        playerData.sendMessage(Message.raw("/town unclaim - Unclaim current chunk").color(WHITE));
        playerData.sendMessage(Message.raw("/town add/invite <player> - Invite player").color(WHITE));
        playerData.sendMessage(Message.raw("/town kick <player> - Kick a resident").color(WHITE));
        playerData.sendMessage(Message.raw("/town join <town> - Join an open town").color(WHITE));
        playerData.sendMessage(Message.raw("/town leave - Leave your town").color(WHITE));
        playerData.sendMessage(Message.raw("/town info [town] - View town info").color(WHITE));
        playerData.sendMessage(Message.raw("/town list - List all towns").color(WHITE));
        playerData.sendMessage(Message.raw("/town spawn - Teleport to town spawn").color(WHITE));
        playerData.sendMessage(Message.raw("/town set spawn - Set town spawn (in your town)").color(WHITE));
        playerData.sendMessage(Message.raw("/town set mayor <player> - Transfer mayor").color(WHITE));
        playerData.sendMessage(Message.raw("/town board <msg> - Set town MOTD (100 chars)").color(WHITE));
        playerData.sendMessage(Message.raw("/town deposit/withdraw <$> - Bank").color(WHITE));
        playerData.sendMessage(Message.raw("/town balance - Check balances").color(WHITE));
        playerData.sendMessage(Message.raw("/town toggle <pvp|open|fire|explosion>").color(WHITE));
        playerData.sendMessage(Message.raw("/town rank add/remove <player> assistant").color(WHITE));
        playerData.sendMessage(Message.raw("/town here - Chunk info").color(WHITE));
        playerData.sendMessage(Message.raw("/town log [page] - Transaction history").color(WHITE));

        if (isAdmin) {
            playerData.sendMessage(Message.raw("========== Admin Commands ==========").color(GOLD));
            playerData.sendMessage(Message.raw("/townyadmin gui - Admin control panel").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin reload - Reload config").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin save - Force save all data").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin debug - Debug info").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin town <name> - View/manage town").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin town <name> delete").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin town <name> setbalance <$>").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin wild toggle|sety|info").color(YELLOW));
            playerData.sendMessage(Message.raw("/townyadmin set <setting> <value>").color(YELLOW));
        }
    }
}
