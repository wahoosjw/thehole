package com.opvi.theholekeepsgettingbigger;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import net.minecraft.resources.ResourceLocation;


import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = TheHoleKeepsGettingBigger.MODID)
public class TheHoleEvents {
    public static int HOLE_EXPAND_INTERVAL_TICKS = 200; // Default: every 200 ticks (10 seconds)
    private static int tickCounter = 0;
    private static int tickCounterRemove = 0;
    
    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        TheHole myHole = TheHole.get(level);

        // Remove blocks from queue as usual
        int ticksLeft = HOLE_EXPAND_INTERVAL_TICKS - tickCounter;
        int blocksInQueue = myHole.blocksToRemove.size();
        int blocksPerTick = 1;
        if (ticksLeft > 0) {
            blocksPerTick = Math.max(1, blocksInQueue / ticksLeft);
        }
        for (int i = 0; i < blocksPerTick && !myHole.blocksToRemove.isEmpty(); i++) {
            BlockPos pos = myHole.blocksToRemove.poll();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }

        // If the queue is empty and we need to clear the shaft, do so and set the flag to false
        if (myHole.blocksToRemove.isEmpty() && myHole.HoleGenerated && myHole.needsShaftClear) {
            System.out.println("Clearing shaft...");
            myHole.clearShaft(level);
            myHole.needsShaftClear = false; // Don't clear again until next expansion
            myHole.setDirty();
            tickCounter = 0; // Optionally reset tick counter here
        }

        // Only expand if the queue is empty and shaft has been cleared
        if (myHole.blocksToRemove.isEmpty() && myHole.HoleGenerated && !myHole.needsShaftClear) {
            tickCounter++;
            if (tickCounter >= HOLE_EXPAND_INTERVAL_TICKS) {
                myHole.expandHole(level);
                myHole.setDirty();
                myHole.needsShaftClear = true; // Set flag to clear shaft after expansion
                tickCounter = 0;
            }
        }
        long time = level.getDayTime();

        if (time % 24000L == 0) {
            //TheHole myHole = TheHole.get(level);

            if (!myHole.HoleGenerated) {
                System.out.println("Generating The Hole at spawn...");
                // Get the first player in the world (if any)
                if (!level.players().isEmpty()) {
                    BlockPos playerPos = level.players().get(0).blockPosition();
                    int playerX = playerPos.getX();
                    int playerZ = playerPos.getZ();
                    int randomX = playerX + level.random.nextInt(21) - 10;
                    int randomZ = playerZ + level.random.nextInt(21) - 10;
                    int y = level.getHeight() - 1;
                    myHole.holeX = randomX;
                    myHole.holeY = y;
                    myHole.holeZ = randomZ;
                } else {
                    // fallback to spawn if no players are present
                    BlockPos spawn = level.getSharedSpawnPos();
                    int playerX = spawn.getX();
                    int playerZ = spawn.getZ();
                    int randomX = playerX + level.random.nextInt(21) - 10;
                    int randomZ = playerZ + level.random.nextInt(21) - 10;
                    int y = level.getHeight() - 1;
                    myHole.holeX = randomX;
                    myHole.holeY = y;
                    myHole.holeZ = randomZ;
                }
                myHole.generateHole(level, myHole.holeX, myHole.holeY, myHole.holeZ);
                myHole.HoleGenerated = true;
                myHole.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        System.out.println("[TheHole] RegisterCommandsEvent fired!");

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            literal("makehole")
                .requires(cs -> cs.hasPermission(2))  // OP level 2
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    
                    TheHole myHole = TheHole.get(level);
                    if (myHole.HoleGenerated) {
                        ctx.getSource().sendFailure(Component.literal("The Hole already exists in this world!"));
                        return 0;
                    }
                    // Use player's current location as center if unset
                    BlockPos pos = ctx.getSource().getPlayerOrException().blockPosition();
                    myHole.generateHole(level, pos.getX(), pos.getY(), pos.getZ());

                    myHole.HoleGenerated = true;
                    myHole.setDirty();

                    ctx.getSource().sendSuccess(() -> Component.literal("The Hole has opened."), false);
                    return 1;
                })
        );

        dispatcher.register(
            literal("leveluphole")
                .requires(cs -> cs.hasPermission(2))
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    TheHole myHole = TheHole.get(level);
                    boolean success = myHole.levelUpHole(level);
                    if (success) {
                        myHole.setDirty();
                        ctx.getSource().sendSuccess(() -> Component.literal("The Hole leveled up to " + myHole.holeLevel + "!"), false);
                        return 1;
                    } else {
                        ctx.getSource().sendFailure(Component.literal("No handler exists for level " + (myHole.holeLevel + 1) + ". The Hole did not level up."));
                        return 0;
                    }
                })
        );

        dispatcher.register(
            literal("placeholetemplate")
                .requires(cs -> cs.hasPermission(2))
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    BlockPos playerPos = ctx.getSource().getPlayerOrException().blockPosition();
                    // Get the direction the player is facing
                    var look = ctx.getSource().getPlayerOrException().getLookAngle();
                    int dx = (int) Math.round(look.x * 25);
                    int dz = (int) Math.round(look.z * 25);
                    BlockPos targetPos = playerPos;

                    // Load and place the structure
                var structureManager = level.getStructureManager();
                var id = ResourceLocation.fromNamespaceAndPath("thehole", "structure");
                System.out.println("Looking for structure: " + id);
                structureManager.listTemplates().forEach(id2 -> System.out.println("Found structure: " + id2));
                var template = structureManager.getOrCreate(id);
                if (template == null) {
                    ctx.getSource().sendFailure(Component.literal("Could not find structure.nbt!"));
                    return 0;
                }
                template.placeInWorld(
                    level,
                    targetPos,
                    targetPos,
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                    level.random,
                    2
                );
                System.out.println("Template size: " + template.getSize());

                    ctx.getSource().sendSuccess(() -> Component.literal("Placed structure.nbt at " + targetPos), false);
                    return 1;
                })
        );
    }
    
}
