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

import java.util.LinkedList;
import java.util.Queue;


public class TheHole extends SavedData {
    public final Queue<BlockPos> blocksToRemove = new LinkedList<>();
    public boolean HoleGenerated = false;
    public int holeX = 0;
    public int holeY = 0;
    public int holeZ = 0;
    public int holeGrowthRate = 1;
    public int holeLevel = 0;
    public int holeDepth = 0;
    public int holeRadiusX = 0;
    public int holeRadiusZ = 0;
    public boolean needsShaftClear = false;
    public int rateX = 1;
    public int rateY = 1;
    public int rateZ = 1;

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

    /**
     * Levels up the hole by incrementing its level and running the handler for the new level if it exists.
     * If no handler exists for the new level, it reports and returns gracefully.
     */
    public boolean levelUpHole(ServerLevel level) {
        this.holeLevel++;
        BiConsumer<ServerLevel, BlockPos> handler = LEVEL_HANDLERS.get(this.holeLevel);
        if (handler != null) {
            handler.accept(level, new BlockPos(this.holeX, this.holeY, this.holeZ));
            System.out.println("Hole leveled up to " + this.holeLevel);
            return true;
        } else {
            System.out.println("Hole level " + this.holeLevel + " does not exist.");
            this.holeLevel--; // Revert level up
            return false;
        }
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
                blockStr.contains("bamboo") || blockStr.contains("sugar_cane") ||
                blockStr.contains("seagrass")) continue;
            System.out.println(block.toString());

            // Found the first valid terrain block
            return y;
    }

    // Fallback — place at minY if nothing valid found
    return minY;
}

    public void generateHole(ServerLevel level, int x, int y, int z) {
        if (HoleGenerated) {
            System.out.println("The hole has already been generated.");
            return;
        }
        // Randomize x and z within ±10
        this.holeX = x + (int)(Math.random() * 21) - 10;
        this.holeZ = z + (int)(Math.random() * 21) - 10;
        this.holeY = findTrueSurfaceY(level, this.holeX, this.holeZ);
        System.out.println("True surface Y: " + this.holeY);

        // Radius of the hole
        int radius = 5;
        System.out.println("Generating hole at: " + this.holeX + ", " + this.holeY + ", " + this.holeZ);
        // Clear downward (cylindrical pit)
        int depth = radius; // You can adjust depth as needed
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= radius) {
                for (int dy = 0; dy <= depth; dy++) {
                    BlockPos pos = new BlockPos(this.holeX + dx, this.holeY - dy, this.holeZ + dz);
                    
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            }
        this.needsShaftClear = true;
        this.expandHole(level);
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

    public void clearShaft(ServerLevel level) {
        int minY = Math.max(-56, this.holeY - this.holeDepth); // bottom of the hole
        int maxY = level.getMaxY() - 1;
        for (int dx = -this.holeRadiusX; dx <= this.holeRadiusX; dx++) {
            for (int dz = -this.holeRadiusZ; dz <= this.holeRadiusZ; dz++) {
                double norm = Math.sqrt(
                    (dx * dx) / (double)(this.holeRadiusX * this.holeRadiusX) +
                    (dz * dz) / (double)(this.holeRadiusZ * this.holeRadiusZ)
                );
                if (norm <= 1.0) {
                    int x = this.holeX + dx;
                    int z = this.holeZ + dz;
                    for (int y = minY; y <= maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }
    public boolean expandHole(ServerLevel level) {
        System.out.println("Expanding hole...");
        int prevRadiusX = this.holeRadiusX;
        int prevRadiusZ = this.holeRadiusZ;
        int prevDepth   = this.holeDepth;

        this.holeRadiusX += this.rateX;
        this.holeRadiusZ += this.rateZ;
        this.holeDepth   += this.rateY;

        boolean changed = false;

        // First, add the new bottom floor blocks (prioritize these)
        changed |= addBottomShell(level, prevDepth, this.holeDepth, prevRadiusX, prevRadiusZ);

        // Then, add the new side wall shell blocks
        changed |= addSideShell(level, prevRadiusX, prevRadiusZ);

        // Optionally, clear above as well (cylinder up, only new shell)
        changed |= addUpperShell(level, prevRadiusX, prevRadiusZ);
        this.needsShaftClear = true;
       return changed;
    }

    private boolean addBottomShell(ServerLevel level, int prevDepth, int newDepth, int prevRadiusX, int prevRadiusZ) {
        boolean changed = false;
        for (int dx = -this.holeRadiusX; dx <= this.holeRadiusX; dx++) {
            for (int dz = -this.holeRadiusZ; dz <= this.holeRadiusZ; dz++) {
                double norm = Math.sqrt(
                    (dx * dx) / (double)(this.holeRadiusX * this.holeRadiusX) +
                    (dz * dz) / (double)(this.holeRadiusZ * this.holeRadiusZ)
                );
                if (norm <= 1.0) {
                    for (int dy = prevDepth + 1; dy <= newDepth; dy++) {
                        int x = this.holeX + dx;
                        int y = this.holeY - dy;
                        int z = this.holeZ + dz;
                        if (y >= -56 && y < level.getMaxY()) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (!level.getBlockState(pos).isAir() && !this.blocksToRemove.contains(pos)) {
                                this.blocksToRemove.add(pos);
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean addSideShell(ServerLevel level, int prevRadiusX, int prevRadiusZ) {
        boolean changed = false;
        for (int dx = -this.holeRadiusX; dx <= this.holeRadiusX; dx++) {
            for (int dz = -this.holeRadiusZ; dz <= this.holeRadiusZ; dz++) {
                double normPrev = (prevRadiusX == 0 || prevRadiusZ == 0) ? 2.0 : Math.sqrt(
                    (dx * dx) / (double)(prevRadiusX * prevRadiusX) +
                    (dz * dz) / (double)(prevRadiusZ * prevRadiusZ)
                );
                double normNew = Math.sqrt(
                    (dx * dx) / (double)(this.holeRadiusX * this.holeRadiusX) +
                    (dz * dz) / (double)(this.holeRadiusZ * this.holeRadiusZ)
                );
                // Only process the new shell (was outside before, now inside)
                if (normNew <= 1.0 && normPrev > 1.0) {
                    for (int dy = 0; dy <= this.holeDepth; dy++) {
                        int x = this.holeX + dx;
                        int y = this.holeY - dy;
                        int z = this.holeZ + dz;
                        if (y >= -56 && y < level.getMaxY()) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (!level.getBlockState(pos).isAir() && !this.blocksToRemove.contains(pos)) {
                                this.blocksToRemove.add(pos);
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean addUpperShell(ServerLevel level, int prevRadiusX, int prevRadiusZ) {
        boolean changed = false;
        int cylinderHeight = 500;
        for (int dy = 1; dy <= cylinderHeight; dy++) {
            for (int dx = -this.holeRadiusX; dx <= this.holeRadiusX; dx++) {
                for (int dz = -this.holeRadiusZ; dz <= this.holeRadiusZ; dz++) {
                    double normPrev = (prevRadiusX == 0 || prevRadiusZ == 0) ? 2.0 : Math.sqrt(
                        (dx * dx) / (double)(prevRadiusX * prevRadiusX) +
                        (dz * dz) / (double)(prevRadiusZ * prevRadiusZ)
                    );
                    double normNew = Math.sqrt(
                        (dx * dx) / (double)(this.holeRadiusX * this.holeRadiusX) +
                        (dz * dz) / (double)(this.holeRadiusZ * this.holeRadiusZ)
                    );
                    if (normNew <= 1.0 && normPrev > 1.0) {
                        int x = this.holeX + dx;
                        int y = this.holeY + dy;
                        int z = this.holeZ + dz;
                        if (y > level.getMaxY() - 1 || y < -56) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).isAir() && !this.blocksToRemove.contains(pos)) {
                            this.blocksToRemove.add(pos);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    public static TheHole load(CompoundTag tag) {
        TheHole data = new TheHole();
        data.HoleGenerated = tag.getBoolean("HoleGenerated");
        data.holeX = tag.getInt("holeX");
        data.holeY = tag.getInt("holeY");
        data.holeZ = tag.getInt("holeZ");
        data.holeGrowthRate = tag.getInt("holeGrowthRate");
        data.holeLevel = tag.getInt("holeLevel");
        data.needsShaftClear = tag.getBoolean("needsShaftClear");

        // Load radius, depth, and rates
        data.holeRadiusX = tag.getInt("holeRadiusX");
        data.holeRadiusZ = tag.getInt("holeRadiusZ");
        data.holeDepth = tag.getInt("holeDepth");
        data.rateX = tag.getInt("rateX");
        data.rateY = tag.getInt("rateY");
        data.rateZ = tag.getInt("rateZ");

        // Load blocksToRemove queue
        data.blocksToRemove.clear();
        if (tag.contains("blocksToRemove", net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList("blocksToRemove", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                BlockPos pos = new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));
                data.blocksToRemove.add(pos);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("HoleGenerated", HoleGenerated);
        tag.putInt("holeX", holeX);
        tag.putInt("holeY", holeY);
        tag.putInt("holeZ", holeZ);
        tag.putInt("holeGrowthRate", holeGrowthRate);
        tag.putInt("holeLevel", holeLevel);
        tag.putBoolean("needsShaftClear", needsShaftClear);

        // Save radius, depth, and rates
        tag.putInt("holeRadiusX", holeRadiusX);
        tag.putInt("holeRadiusZ", holeRadiusZ);
        tag.putInt("holeDepth", holeDepth);
        tag.putInt("rateX", rateX);
        tag.putInt("rateY", rateY);
        tag.putInt("rateZ", rateZ);

        // Save blocksToRemove queue
        var list = new net.minecraft.nbt.ListTag();
        for (BlockPos pos : blocksToRemove) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            list.add(posTag);
        }
        tag.put("blocksToRemove", list);

        return tag;
    }

    public static final class Factory {
        public static TheHole load(CompoundTag tag, HolderLookup.Provider registries) {
            return TheHole.load(tag);
        }

        public static java.util.function.Supplier<TheHole> constructor() {
            return TheHole::new;
        }
    }

    public static TheHole get(ServerLevel level) {
        SavedData.Factory<TheHole> factory = new SavedData.Factory<>(
            TheHole::new,
            (tag, registries) -> TheHole.load(tag)
        );
        return level.getDataStorage().computeIfAbsent(factory, "thehole_data");
    }

    public void runLevelHandler(ServerLevel level, BlockPos center) {
        if (LEVEL_HANDLERS.containsKey(holeLevel)) {
            LEVEL_HANDLERS.get(holeLevel).accept(level, center);
        }
    }

}
