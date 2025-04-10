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

public class PlayerListener implements Listener {
    private final SimpleDynamicLight plugin;
    private final Map<Player, Location> playerLightLocations = new HashMap<>();
    private final Map<Item, Location> itemLightLocations = new HashMap<>();

    public PlayerListener(SimpleDynamicLight plugin) {
        this.plugin = plugin;
        startLightUpdateTask();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null || (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ())) {
            return;
        }

        if (!plugin.isDynamicLightEnabled(player)) {
            removePlayerLight(player);
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack mainHandItem = inventory.getItemInMainHand();
        ItemStack offHandItem = inventory.getItemInOffHand();

        int lightLevel = Math.max(getLightLevel(mainHandItem), getLightLevel(offHandItem));

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

        PlayerInventory inventory = player.getInventory();
        ItemStack mainHandItem = inventory.getItemInMainHand();
        ItemStack offHandItem = inventory.getItemInOffHand();

        int lightLevel = Math.max(getLightLevel(mainHandItem), getLightLevel(offHandItem));

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

        if (!plugin.isDynamicLightEnabled(player)) {
            return;
        }

        Item droppedItem = event.getItemDrop();
        ItemStack itemStack = droppedItem.getItemStack();
        int lightLevel = getLightLevel(itemStack);

        if (lightLevel > 0) {
            removePlayerLight(player);
            updateItemLight(droppedItem, lightLevel);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();
        int lightLevel = getLightLevel(itemStack);

        if (lightLevel > 0) {
            updateItemLight(item, lightLevel);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (itemLightLocations.containsKey(item)) {
            removeItemLight(item);

            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();

                if (!plugin.isDynamicLightEnabled(player)) {
                    return;
                }

                ItemStack itemStack = item.getItemStack();
                int lightLevel = getLightLevel(itemStack);

                if (lightLevel > 0) {
                    updatePlayerLight(player, player.getLocation(), lightLevel);
                }
            }
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        if (itemLightLocations.containsKey(item)) {
            removeItemLight(item);
        }
    }

    private int getLightLevel(ItemStack item) {
        if (item == null) return 0;
        String itemType = item.getType().toString();
        return plugin.getConfig().getInt("light-sources." + itemType, 0);
    }

    private void updatePlayerLight(Player player, Location location, int lightLevel) {
    Location currentLightLocation = playerLightLocations.get(player);

    if (currentLightLocation != null) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            currentLightLocation.getBlock().setType(Material.AIR);
        });
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        // Place light block 2 blocks above the player's feet (at head level)
        Location lightLocation = location.clone().add(0, 2, 0);
        Block block = lightLocation.getBlock();
        block.setType(Material.LIGHT);

        Levelled lightData = (Levelled) block.getBlockData();
        lightData.setLevel(lightLevel);
        block.setBlockData(lightData);

        playerLightLocations.put(player, lightLocation);
    });
    }

    private void removePlayerLight(Player player) {
        Location lightLocation = playerLightLocations.remove(player);
        if (lightLocation != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                lightLocation.getBlock().setType(Material.AIR);
            });
        }
    }

    private void updateItemLight(Item item, int lightLevel) {
    Location itemLocation = item.getLocation();
    Location currentLightLocation = itemLightLocations.get(item);

    if (currentLightLocation != null) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            currentLightLocation.getBlock().setType(Material.AIR);
        });
    }

    Bukkit.getScheduler().runTask(plugin, () -> {
        // Place light block 1 block above the item
        Location lightLocation = itemLocation.clone().add(0, 1, 0);
        Block block = lightLocation.getBlock();
        block.setType(Material.LIGHT);

        Levelled lightData = (Levelled) block.getBlockData();
        lightData.setLevel(lightLevel);
        block.setBlockData(lightData);

        itemLightLocations.put(item, lightLocation);
    });
    }
    
    

    private void removeItemLight(Item item) {
        Location lightLocation = itemLightLocations.remove(item);
        if (lightLocation != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                lightLocation.getBlock().setType(Material.AIR);
            });
        }
    }

    private void startLightUpdateTask() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Map.Entry<Player, Location> entry : playerLightLocations.entrySet()) {
                Player player = entry.getKey();
                Location currentLightLocation = entry.getValue();
                Location playerLocation = player.getLocation();

                if (!playerLocation.equals(currentLightLocation)) {
                    PlayerInventory inventory = player.getInventory();
                    ItemStack mainHandItem = inventory.getItemInMainHand();
                    ItemStack offHandItem = inventory.getItemInOffHand();

                    int lightLevel = Math.max(getLightLevel(mainHandItem), getLightLevel(offHandItem));

                    if (lightLevel > 0 && plugin.isDynamicLightEnabled(player)) {
                        updatePlayerLight(player, playerLocation, lightLevel);
                    }
                }
            }

            for (Map.Entry<Item, Location> entry : itemLightLocations.entrySet()) {
                Item item = entry.getKey();
                Location currentLightLocation = entry.getValue();
                Location itemLocation = item.getLocation();

                if (!itemLocation.equals(currentLightLocation)) {
                    updateItemLight(item, getLightLevel(item.getItemStack()));
                }
            }
        }, 0L, 5L);
    }
}
