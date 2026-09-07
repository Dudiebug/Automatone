package automatone.worker.gametest;

import automatone.worker.MiningSession;
import baritone.api.Settings;
import baritone.api.process.IMineProcess.TerminationReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Failures come from real native mining; no consumer target positions or mocked outcomes. */
@GameTestHolder("automatone_worker_m6_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerM6FailureGameTest {
    private WorkerM6FailureGameTest() { }

    @GameTest(template = "worker_native_mining", batch = "m6_no_targets", timeoutTicks = 600)
    public static void absentTargetsReportNoTargets(GameTestHelper helper) {
        WorkerMiningSessionGameTest.Fixture fixture = new WorkerMiningSessionGameTest.Fixture(helper, Blocks.WET_SPONGE, false);
        fixture.targets.forEach(pos -> fixture.chamber.replace(pos, Blocks.AIR.defaultBlockState()));
        start(helper, fixture, "NO_TARGETS", false);
    }

    @GameTest(template = "worker_native_mining", batch = "m6_path_failure", timeoutTicks = 600)
    public static void unreachableTargetsReportPathFailure(GameTestHelper helper) {
        WorkerMiningSessionGameTest.Fixture fixture = new WorkerMiningSessionGameTest.Fixture(helper);
        BlockPos feet = fixture.chamber.workerPosition();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    if (x != 0 || z != 0 || y == 2) {
                        fixture.chamber.replace(feet.offset(x, y, z), Blocks.BEDROCK.defaultBlockState());
                    }
                }
            }
        }
        start(helper, fixture, "PATH_FAILED", false);
    }

    @GameTest(template = "worker_native_mining", batch = "m6_break_disabled", timeoutTicks = 600)
    public static void disabledBreakingReportsUsefulFailure(GameTestHelper helper) {
        start(helper, new WorkerMiningSessionGameTest.Fixture(helper), "BREAK_DISABLED", false);
    }

    @GameTest(template = "worker_native_mining", batch = "m6_break_exception", timeoutTicks = 600)
    public static void allowedBlockExceptionStillCompletesExactSourceQuantity(GameTestHelper helper) {
        start(helper, new WorkerMiningSessionGameTest.Fixture(helper), "", true);
    }

    @GameTest(template = "worker_native_mining", batch = "m6_native_termination", timeoutTicks = 100)
    public static void nativeCompletionAndAsyncFailureRemainDistinctAcrossCleanup(GameTestHelper helper) throws Exception {
        try (WorkerMiningSessionGameTest.Fixture fixture = new WorkerMiningSessionGameTest.Fixture(helper)) {
            baritone.process.MineProcess process = (baritone.process.MineProcess) fixture.worker.runtime().getMineProcess();
            fixture.worker.setItem(1, new net.minecraft.world.item.ItemStack(Blocks.IRON_ORE));
            process.mine(1, Blocks.IRON_ORE);
            process.onTick(false, true);
            helper.assertTrue(process.terminationReason().orElseThrow() == TerminationReason.COMPLETED,
                    "native item completion must report COMPLETED");
            process.cancel();
            helper.assertTrue(process.terminationReason().orElseThrow() == TerminationReason.COMPLETED,
                    "cleanup replaced native completion");
            process.mine(Blocks.IRON_ORE);
            helper.assertTrue(process.terminationReason().isEmpty(), "new invocation retained old completion");
            java.lang.reflect.Field generation = baritone.process.MineProcess.class.getDeclaredField("scanGeneration");
            generation.setAccessible(true);
            long activeGeneration = generation.getLong(process);
            java.lang.reflect.Method publish = baritone.process.MineProcess.class.getDeclaredMethod(
                    "publishScanResult", long.class, List.class, RuntimeException.class);
            publish.setAccessible(true);
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { publish.invoke(process, activeGeneration, List.of(), new IllegalStateException("M6 scan fixture")); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }, baritone.Baritone.getExecutor()).get(5, java.util.concurrent.TimeUnit.SECONDS);
            process.onTick(false, true);
            helper.assertTrue(!process.isActive() && process.terminationReason().orElseThrow() == TerminationReason.INTERNAL_FAILURE,
                    "scan exception was not classified at the native mailbox boundary");
            process.cancel(); process.onLostControl();
            helper.assertTrue(process.terminationReason().orElseThrow() == TerminationReason.INTERNAL_FAILURE,
                    "repeated cleanup erased native failure");
        }
        helper.succeed();
    }

    private static void start(GameTestHelper helper, WorkerMiningSessionGameTest.Fixture fixture, String error, boolean exception) {
        try {
            Settings settings = fixture.worker.effectiveSettings().copy();
            settings.exploreForBlocks.value = false;
            settings.legitMine.value = false;
            settings.mineScanDroppedItems.value = false;
            settings.blacklistClosestOnFailure.value = true;
            settings.allowBreak.value = !error.equals("BREAK_DISABLED") && !exception;
            settings.allowBreakAnyway.value = exception ? List.of(fixture.target) : List.of();
            fixture.worker.applySettings(settings);
            fixture.worker.startMining(BuiltInRegistries.BLOCK.getKey(fixture.target), 1);
            observe(helper, fixture, error, 0);
        } catch (Throwable failure) { fail(helper, fixture, failure); }
    }

    private static void observe(GameTestHelper helper, WorkerMiningSessionGameTest.Fixture fixture, String error, int ticks) {
        try {
            MiningSession.Snapshot status = fixture.worker.miningStatus();
            if (status.state() == MiningSession.State.RUNNING && ticks < 540) {
                helper.runAfterDelay(1, () -> observe(helper, fixture, error, ticks + 1));
                return;
            }
            helper.assertTrue(status.state() == (error.isEmpty() ? MiningSession.State.COMPLETED : MiningSession.State.FAILED)
                    && status.error().equals(error), "unexpected native/product termination: " + status);
            helper.assertTrue(status.completed() == (error.isEmpty() ? 1 : 0), "failure/completion changed source count");
            helper.assertTrue(fixture.worker.runtime().getMineProcess().terminationReason().orElseThrow()
                    == (error.isEmpty() ? TerminationReason.CANCELLED : TerminationReason.valueOf(error)), "native reason disagrees with product");
            fixture.worker.runtime().getMineProcess().cancel();
            helper.runAfterDelay(10, () -> {
                try {
                    helper.assertTrue(status.equals(fixture.worker.miningStatus()), "cleanup overwrote terminal product state");
                    helper.assertTrue(!fixture.worker.runtime().getMineProcess().isActive(), "terminal mining resumed");
                    if (error.isEmpty()) { helper.assertTrue(fixture.destroyed() == 1, "finite job destroyed extra source blocks"); }
                    else if (!error.equals("NO_TARGETS")) { helper.assertTrue(fixture.destroyed() == 0, "failed job destroyed a source"); }
                    fixture.close();
                    helper.succeed();
                } catch (Throwable failure) { fail(helper, fixture, failure); }
            });
        } catch (Throwable failure) { fail(helper, fixture, failure); }
    }

    private static void fail(GameTestHelper helper, WorkerMiningSessionGameTest.Fixture fixture, Throwable failure) {
        fixture.close();
        helper.fail("M6 native failure contract: " + failure);
    }
}
