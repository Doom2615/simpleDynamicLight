package com.github.K4RUNIO.simpleDynamicLight;


import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


//Chest fixes
public class ChestVisualFix {
    private final SimpleDynamicLight plugin;
    
    public ChestVisualFix(SimpleDynamicLight plugin) {
        this.plugin = plugin;
    }
    
    public void handleChestVisuals(Location lightLocation) {
        if (!isNearLargeChest(lightLocation)) return;
        
        // Delay the update to ensure chest renders properly
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateNearbyPlayers(lightLocation);
            updateChestBlocks(lightLocation);
        }, 3L);
    }
    
    private void updateNearbyPlayers(Location location) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(location.getWorld()) && 
                player.getLocation().distance(location) < 32) {
                player.updateInventory();
            }
        }
    }
    
    private void updateChestBlocks(Location location) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                if (block.getState() instanceof Chest) {
                    block.getState().update(true, false);
                }
            }
        }
    }
    
    private boolean isNearLargeChest(Location location) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                if (isChestBlock(block)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isChestBlock(Block block) {
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return false;
        }
        return ((Chest)block.getState()).getInventory() instanceof DoubleChestInventory;
    }
}

public class PlayerListener implements Listener {
    private final SimpleDynamicLight plugin;
    private final Map<UUID, Location> playerLightLocations = new HashMap<>();
    private final Map<UUID, Location> itemLightLocations = new HashMap<>();

    public PlayerListener(SimpleDynamicLight plugin) {
        this.plugin = plugin;
        startLightUpdateTask();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null || from == null || to.getBlock().equals(from.getBlock())) return;

        if (!plugin.isDynamicLightEnabled(player)) {
            removePlayerLight(player);
            return;
        }

        int lightLevel = getMaxLightLevel(player);
        if (lightLevel > 0) {
            updatePlayerLight(player, to, lightLevel);
        } else {
            removePlayerLight(player);
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isDynamicLightEnabled(player)) {
            removePlayerLight(player);
            return;
        }

        int lightLevel = getMaxLightLevel(player);
        if (lightLevel > 0) {
            updatePlayerLight(player, player.getLocation(), lightLevel);
        } else {
            removePlayerLight(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerLight(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isDynamicLightEnabled(player)) return;

        Item droppedItem = event.getItemDrop();
        int lightLevel = getLightLevel(droppedItem.getItemStack());

        if (lightLevel > 0) {
            removePlayerLight(player);
            updateItemLight(droppedItem, lightLevel);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        int lightLevel = getLightLevel(item.getItemStack());
        if (lightLevel > 0) updateItemLight(item, lightLevel);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        UUID itemId = item.getUniqueId();
        if (itemLightLocations.containsKey(itemId)) {
            removeItemLight(item);

            if (event.getEntity() instanceof Player player && plugin.isDynamicLightEnabled(player)) {
                int lightLevel = getLightLevel(item.getItemStack());
                if (lightLevel > 0) {
                    updatePlayerLight(player, player.getLocation(), lightLevel);
                }
            }
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        removeItemLight(event.getEntity());
    }

    private int getLightLevel(ItemStack item) {
        if (item == null) return 0;
        return plugin.getConfig().getInt("light-sources." + item.getType(), 0);
    }

    private int getMaxLightLevel(Player player) {
        PlayerInventory inv = player.getInventory();
        return Math.max(getLightLevel(inv.getItemInMainHand()), getLightLevel(inv.getItemInOffHand()));
    }

    // Updated container detection method
private boolean isContainerBlock(Material material) {
    return switch (material) {
        // All container types that can be opened/interacted with
        case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL, SHULKER_BOX,
             CRAFTING_TABLE, SMITHING_TABLE, FURNACE, BLAST_FURNACE, SMOKER,
             ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, GRINDSTONE,
             CARTOGRAPHY_TABLE, FLETCHING_TABLE, STONECUTTER,
             LOOM, COMPOSTER, BREWING_STAND -> true;
        default -> false;
    };
}

// Enhanced safety check with player context
private boolean isSafeToPlaceLight(Block block, Location playerLoc) {
    // Only place in air blocks
    if (!block.getType().isAir()) {
        return false;
    }

    // Check if this would block any nearby container
    if (wouldBlockContainer(block, playerLoc)) {
        return false;
    }
    // Special handling for large chests
    for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
            Block adjacent = block.getRelative(x, 0, z);
            if (isChestBlock(adjacent)) {
                // Don't place light blocks near large chests
                return false;
            }
        }
    }

    return true;
}

// Chest fixes
    private boolean isNearLargeChest(Location location) {
    int radius = 3;
    for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
            Block block = location.getBlock().getRelative(x, 0, z);
            if (isChestBlock(block)) {
                return true;
            }
        }
    }
    return false;
    }
    
    private boolean isChestBlock(Block block) {
    Material type = block.getType();
    if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
        return false;
    }
    
    // Additional check for double chests
    Chest chest = (Chest)block.getState();
    return chest.getInventory() instanceof DoubleChestInventory;
    }

// Comprehensive container interaction protection
private boolean wouldBlockContainer(Block lightBlock, Location playerLoc) {
    // Check blocks below in 3x3 area
    for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
            Block below = lightBlock.getRelative(x, -1, z);
            if (isContainerBlock(below.getType())) {
                return true;
            }
        }
    }

    // Check player's line of sight to containers
    Block targetBlock = playerLoc.getBlock().getRelative(
        playerLoc.getDirection().getBlockX(),
        playerLoc.getDirection().getBlockY(),
        playerLoc.getDirection().getBlockZ()
    );

    if (isContainerBlock(targetBlock.getType())) {
        // Don't place light between player and container
        return lightBlock.getLocation().distanceSquared(targetBlock.getLocation()) < 9;
    }

    // Check for containers in interaction range
    for (int x = -2; x <= 2; x++) {
        for (int y = -1; y <= 2; y++) {
            for (int z = -2; z <= 2; z++) {
                Block nearby = playerLoc.getBlock().getRelative(x, y, z);
                if (isContainerBlock(nearby.getType())) {
                    // Don't place light directly above containers
                    if (lightBlock.getY() > nearby.getY() && 
                        lightBlock.getX() == nearby.getX() && 
                        lightBlock.getZ() == nearby.getZ()) {
                        return true;
                    }
                }
            }
        }
    }

    return false;
}

// Optimized light position finding
private Location findSafeLightLocation(Location origin) {
     // Try positions further away when near chests
    if (isNearLargeChest(origin)) {
        Location[] chestOffsets = {
            origin.clone().add(2, 2, 0),
            origin.clone().add(-2, 2, 0),
            origin.clone().add(0, 2, 2),
            origin.clone().add(0, 2, -2),
            origin.clone().add(0, 3, 0)
        };
        
        for (Location loc : chestOffsets) {
            Block block = loc.getBlock();
            if (isSafeToPlaceLight(block, origin)) {
                return loc;
            }
        }
    }
    // Try positions in order of preference
    Location[] offsets = {
        origin.clone().add(1, 2, 0),   // Right side (priority)
        origin.clone().add(-1, 2, 0),  // Left side
        origin.clone().add(0, 2, 1),   // Front
        origin.clone().add(0, 2, -1),  // Back
        origin.clone().add(1, 1, 0),   // Lower right
        origin.clone().add(-1, 1, 0),  // Lower left
        origin.clone().add(0, 1, 1),   // Lower front
        origin.clone().add(0, 1, -1),  // Lower back
        origin.clone().add(0, 3, 0)    // Higher position (last resort)
    };

    for (Location loc : offsets) {
        Block block = loc.getBlock();
        if (isSafeToPlaceLight(block, origin)) {
            return loc;
        }
    }
    return null;
}

    private void updatePlayerLight(Player player, Location location, int lightLevel) {
    UUID playerId = player.getUniqueId();
    Location oldLocation = playerLightLocations.get(playerId);

    if (oldLocation != null) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            oldLocation.getBlock().setType(Material.AIR);
        });
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        Location lightLoc = findSafeLightLocation(location);
        if (lightLoc == null) return;

        Block block = lightLoc.getBlock();
        block.setType(Material.LIGHT);

        if (block.getBlockData() instanceof Levelled lightData) {
            lightData.setLevel(lightLevel);
            block.setBlockData(lightData);
        }

        playerLightLocations.put(playerId, lightLoc);
    });
        if (isNearLargeChest(location)) {
        // Force chest rendering update
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().equals(location.getWorld()) && 
                    online.getLocation().distance(location) < 32) {
                    online.updateInventory();
                }
            }
        }, 2L);
        }
    }

    private void removePlayerLight(Player player) {
        UUID id = player.getUniqueId();
        Location loc = playerLightLocations.remove(id);
        if (loc != null) {
            Bukkit.getScheduler().runTask(plugin, () -> loc.getBlock().setType(Material.AIR));
        }
    }

    private void updateItemLight(Item item, int lightLevel) {
    UUID itemId = item.getUniqueId();
    Location oldLoc = itemLightLocations.get(itemId);

    if (oldLoc != null) {
        Bukkit.getScheduler().runTask(plugin, () -> oldLoc.getBlock().setType(Material.AIR));
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        Location lightLoc = item.getLocation().clone().add(0, 1, 0);
        Block block = lightLoc.getBlock();
        if (!isSafeToPlaceLight(block, item.getLocation())) return;  // Updated call

        block.setType(Material.LIGHT);
        if (block.getBlockData() instanceof Levelled lightData) {
            lightData.setLevel(lightLevel);
            block.setBlockData(lightData);
        }

        itemLightLocations.put(itemId, lightLoc);
    });
    }
    
    private void removeItemLight(Item item) {
        UUID itemId = item.getUniqueId();
        Location loc = itemLightLocations.remove(itemId);
        if (loc != null) {
            Bukkit.getScheduler().runTask(plugin, () -> loc.getBlock().setType(Material.AIR));
        }
    }

    private void startLightUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<UUID, Location> playerLights = new HashMap<>(playerLightLocations);
            for (UUID uuid : playerLights.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !plugin.isDynamicLightEnabled(player)) continue;

                Location current = player.getLocation();
                Location prev = playerLights.get(uuid);
                if (!current.equals(prev)) {
                    int lightLevel = getMaxLightLevel(player);
                    if (lightLevel > 0) updatePlayerLight(player, current, lightLevel);
                }
            }

            Map<UUID, Location> itemLights = new HashMap<>(itemLightLocations);
            for (UUID uuid : itemLights.keySet()) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity instanceof Item item) {
                    Location current = item.getLocation();
                    Location prev = itemLights.get(uuid);
                    if (!current.equals(prev)) {
                        updateItemLight(item, getLightLevel(item.getItemStack()));
                    }
                }
            }
        }, 0L, 5L);
    }
}
