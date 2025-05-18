package com.opvi.theholekeepsgettingbigger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;


public class TheHole {

    public boolean HoleGenerated = false;
    public int holeX = 0;
    public int holeY = 0;
    public int holeZ = 0;
    public int holeGrowthRate = 1;
    public int holeLevel = 0;

    // Hole growth handlers by level
    private static final Map<Integer, BiConsumer<ServerLevel, BlockPos>> LEVEL_HANDLERS = new HashMap<>();
    static {
        LEVEL_HANDLERS.put(1, (level, pos) -> {
            // Example: deeper pit
            for (int y = 0; y < 3; y++) {
                level.setBlock(pos.below(y), Blocks.AIR.defaultBlockState(), 2);
            }
        });

        LEVEL_HANDLERS.put(2, (level, pos) -> {
            // Example: place obsidian floor
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlock(pos.offset(x, -5, z), Blocks.OBSIDIAN.defaultBlockState(), 2);
                }
            }
        });
    }

    public int findTrueSurfaceY(ServerLevel level, int x, int z) {
        int minY = level.getMinY();
        System.out.println("Min Y: " + minY);
        int maxY = minY + level.getHeight();
        System.out.println("Max Y: " + maxY);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = maxY - 1; y >= minY; y--) {
            pos.set(x, y, z);
            Block block = level.getBlockState(pos).getBlock();

            // Skip air, water, logs, leaves, coral, mushrooms, bamboo, sugar cane
            if (block == Blocks.AIR || block == Blocks.WATER) continue;
            String blockStr = block.toString().toLowerCase();
            if (blockStr.contains("leaves") || blockStr.contains("log") ||
                blockStr.contains("kelp") || blockStr.contains("grass") ||
                blockStr.contains("tall_grass") || blockStr.contains("mob") ||
                blockStr.contains("coral") || blockStr.contains("mushroom") ||
                blockStr.contains("bamboo") || blockStr.contains("sugar_cane")) continue;
            System.out.println(block.toString());

            // Found the first valid terrain block
            return y;
    }

    // Fallback — place at minY if nothing valid found
    return minY;
}

    public void generateHole(ServerLevel level, int x, int y, int z) {
        // Randomize x and z within ±10
        this.holeX = x + (int)(Math.random() * 21) - 10;
        this.holeZ = z + (int)(Math.random() * 21) - 10;
        this.holeY = findTrueSurfaceY(level, this.holeX, this.holeZ);
        System.out.println("True surface Y: " + this.holeY);

        // Radius of the hole
        int radius = 5;
        System.out.println("Generating hole at: " + this.holeX + ", " + this.holeY + ", " + this.holeZ);
        // Clear downward (circular pit)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= radius) {
                    int depth = (int) (radius - distance);
                    for (int dy = 0; dy <= depth; dy++) {
                        BlockPos pos = new BlockPos(this.holeX + dx, this.holeY - dy, this.holeZ + dz);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

    // Clear cylinder above hole
    int cylinderHeight = 500;
    for (int dy = 1; dy <= cylinderHeight; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) <= radius) {
                    BlockPos pos = new BlockPos(this.holeX + dx, this.holeY + dy, this.holeZ + dz);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
            }
        }   
    }

    runLevelHandler(level, new BlockPos(x, y, z));
    }

    // public void generateHole(ServerLevel level, int x, int y, int z) {
    //     // Adjust y to put the hole on the surface,
    //     // ignoring any leaf, log, or water blocks.
    //     Block[] leafBlocks = {
    //         Blocks.OAK_LEAVES,
    //         Blocks.BIRCH_LEAVES,
    //         Blocks.SPRUCE_LEAVES,
    //         Blocks.JUNGLE_LEAVES,
    //         Blocks.ACACIA_LEAVES,
    //         Blocks.DARK_OAK_LEAVES
    //     };

    //     Block[] logBlocks = {
    //         Blocks.OAK_LOG,
    //         Blocks.BIRCH_LOG,
    //         Blocks.SPRUCE_LOG,
    //         Blocks.JUNGLE_LOG,
    //         Blocks.ACACIA_LOG,
    //         Blocks.DARK_OAK_LOG
    //     };

    //     int worldBottom = level.getMinY();
    //     // Raise the y coordinate until the block above is something solid (not water, log or leaf)
    //     while (y < level.getHeight() - 1) {
    //         BlockPos posAbove = new BlockPos(x, y + 1, z);
    //         Block blockAbove = level.getBlockState(posAbove).getBlock();
            
    //         boolean isLeaf = false;
    //         for (Block leaf : leafBlocks) {
    //             if (blockAbove == leaf) {
    //                 isLeaf = true;
    //                 break;
    //             }
    //         }
            
    //         boolean isLog = false;
    //         for (Block log : logBlocks) {
    //             if (blockAbove == log) {
    //                 isLog = true;
    //                 break;
    //             }
    //         }
            
    //         // Consider water as allowed as well.
    //         boolean isWater = (blockAbove == Blocks.WATER);
            
    //         if (isLeaf || isLog || isWater) {
    //             y++;
    //         } else {
    //             break;
    //         }
    //     }
        
    //     this.holeX = x;
    //     this.holeY = y;
    //     this.holeZ = z;

    //     int radius = 5;
    //     // Clear the hole (a circular pit carved downward)
    //     for (int dx = -radius; dx <= radius; dx++) {
    //         for (int dz = -radius; dz <= radius; dz++) {
    //             double distance = Math.sqrt(dx * dx + dz * dz);
    //             if (distance <= radius) {
    //                 int depth = (int) (radius - distance);
    //                 for (int dy = 0; dy <= depth; dy++) {
    //                     BlockPos pos = new BlockPos(x + dx, y - dy, z + dz);
    //                     level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    //                 }
    //             }
    //         }
    //     }

    //     // Clear a cylinder above the hole's edge.
    //     int cylinderHeight = 5; // Height of the cylinder
    //     for (int dy = 1; dy <= cylinderHeight; dy++) {
    //         for (int dx = -radius; dx <= radius; dx++) {
    //             for (int dz = -radius; dz <= radius; dz++) {
    //                 if (Math.sqrt(dx * dx + dz * dz) <= radius) {
    //                     BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
    //                     level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    //                 }
    //             }
    //         }
    //     }

    //     runLevelHandler(level, new BlockPos(x, y, z));
    // }

    public void runLevelHandler(ServerLevel level, BlockPos center) {
        if (LEVEL_HANDLERS.containsKey(holeLevel)) {
            LEVEL_HANDLERS.get(holeLevel).accept(level, center);
        }
    }

}
