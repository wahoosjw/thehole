package com.opvi.theholekeepsgettingbigger;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = TheHoleKeepsGettingBigger.MODID)
public class TheHoleEvents {

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        long time = level.getDayTime();

        if (time % 24000L == 0) {
            TheHole myHole = new TheHole();

            if (!myHole.HoleGenerated) {
                System.out.println("Generating The Hole at spawn...");
                myHole.generateHole(level, myHole.holeX, myHole.holeY, myHole.holeZ);
                myHole.HoleGenerated = true;
                //data.setDirty();
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
                    TheHole myHole = new TheHole();

                    // Use player's current location as center if unset
                    BlockPos pos = ctx.getSource().getPlayerOrException().blockPosition();
                    myHole.generateHole(level, pos.getX(), pos.getY(), pos.getZ());

                    myHole.HoleGenerated = true;
                    myHole.holeX = pos.getX();
                    myHole.holeY = pos.getY();
                    myHole.holeZ = pos.getZ();
                    //data.setDirty();

                    ctx.getSource().sendSuccess(() -> Component.literal("The Hole has opened."), false);
                    return 1;
                })
        );
    }
}
