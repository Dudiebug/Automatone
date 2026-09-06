package automatone.worker.gametest;

import com.mojang.brigadier.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(WorkerGameTestMod.MOD_ID)
public final class WorkerGameTestMod {
    public static final String MOD_ID = "automatone_worker_gametest";
    private static WorkerNativeMineProcessGameTest.DemoSession demo;

    public WorkerGameTestMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(WorkerGameTestMod::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("worker_m3_demo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start").executes(context -> startDemo(context.getSource())))
                .then(Commands.literal("cancel").executes(context -> cancelDemo(context.getSource()))));
    }

    private static int startDemo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer viewer = source.getPlayerOrException();
        ServerLevel level = viewer.serverLevel();
        if (demo != null) {
            demo.close();
        }
        int floorY = Math.min(level.getMaxBuildHeight() - 6,
                Math.max(level.getMinBuildHeight(), viewer.blockPosition().getY() + 24));
        BlockPos origin = new BlockPos(viewer.getBlockX() - 2, floorY, viewer.getBlockZ() + 8);
        demo = WorkerNativeMineProcessGameTest.startDemo(level, origin);
        BlockPos view = demo.viewPosition();
        viewer.teleportTo(level, view.getX() + 0.5D, view.getY(), view.getZ() + 0.5D, -60.0F, 27.0F);
        demo.start();
        source.sendSuccess(() -> Component.literal("M3 demo started at " + view + "; target=" + demo.target()
                + ". The worker mines at 2.5% speed for visible progressive cracks. Use /worker_m3_demo cancel to stop it."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelDemo(CommandSourceStack source) {
        if (demo == null) {
            source.sendFailure(Component.literal("No M3 demo is active. Use /worker_m3_demo start first."));
            return 0;
        }
        demo.cancel();
        source.sendSuccess(() -> Component.literal("M3 demo cancelled; the bounded chamber remains for observing cleanup."), false);
        return Command.SINGLE_SUCCESS;
    }
}
