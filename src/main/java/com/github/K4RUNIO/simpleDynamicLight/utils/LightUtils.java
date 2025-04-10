package com.github.K4RUNIO.simpleDynamicLight.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;

public class LightUtils {
    private static final Material LIGHT = Material.LIGHT;
    private static final Material AIR = Material.AIR;
    
    public static void placeLight(Location location, int level) {
        if (location == null) return;
        
        Block block = location.getBlock();
        if (block.getType() != AIR) return;
        
        block.setType(LIGHT);
        if (block.getBlockData() instanceof Levelled lightData) {
            lightData.setLevel(level);
            block.setBlockData(lightData);
        }
    }
    
    public static void removeLight(Location location) {
        if (location != null && location.getBlock().getType() == LIGHT) {
            location.getBlock().setType(AIR);
        }
    }
    
    public static boolean isSafeLocation(Block block, Location playerLoc) {
        return block.getType().isAir() && !wouldBlockContainer(block, playerLoc);
    }
    
    private static boolean wouldBlockContainer(Block block, Location playerLoc) {
        // Check blocks below
        Block below = block.getRelative(0, -1, 0);
        switch (below.getType()) {
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
            case SHULKER_BOX:
                return true;
            default:
                return false;
        }
    }
}
