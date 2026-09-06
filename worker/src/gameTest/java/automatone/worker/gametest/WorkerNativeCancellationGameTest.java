package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerEntityController;
import automatone.worker.WorkerMod;
import baritone.api.IBaritone;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.input.Input;
import baritone.utils.InputOverrideHandler;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder("automatone_worker_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerNativeCancellationGameTest {
    private static final int POST_CANCEL_TICKS = 20;

    @GameTest(template = "worker_movement", batch = "worker_m3_native_cancel", timeoutTicks = 80)
    public static void mineCancelSynchronouslyClearsNativeBreakIntentAndInputs(GameTestHelper helper) {
        NativeBreakFixture fixture;
        try {
            fixture = NativeBreakFixture.create(helper);
        } catch (Throwable failure) {
            helper.fail("Native cancellation fixture setup failed: " + failure);
            return;
        }
        NativeBreakFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> beginNativeBreak(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_native_helper_stop", timeoutTicks = 40)
    public static void explicitHelperStopClearsBreakStateWithoutPriorHelperHit(GameTestHelper helper) {
        NativeBreakFixture fixture;
        try {
            fixture = NativeBreakFixture.create(helper);
        } catch (Throwable failure) {
            helper.fail("Native helper-stop fixture setup failed: " + failure);
            return;
        }
        NativeBreakFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> stopUnmarkedBreak(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_native_release", timeoutTicks = 50)
    public static void releasedNativeClickClearsUnfinishedBreakState(GameTestHelper helper) {
        NativeBreakFixture fixture;
        try {
            fixture = NativeBreakFixture.create(helper);
        } catch (Throwable failure) {
            helper.fail("Native release fixture setup failed: " + failure);
            return;
        }
        NativeBreakFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> beginBreakForRelease(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_native_lost_ray", timeoutTicks = 60)
    public static void lostRayClearsUnfinishedBreakAndAllowsFreshNativeIntent(GameTestHelper helper) {
        NativeBreakFixture fixture;
        try {
            fixture = NativeBreakFixture.create(helper);
        } catch (Throwable failure) {
            helper.fail("Native lost-ray fixture setup failed: " + failure);
            return;
        }
        NativeBreakFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> beginBreakForLostRay(helper, startedFixture));
    }

    private static void beginNativeBreak(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            fixture.lookAtTarget();
            fixture.input.setInputForceState(Input.CLICK_LEFT, true);
            helper.runAfterDelay(1, () -> observeNativeSwingBeforeCancel(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation break setup failed", failure);
        }
    }

    private static void observeNativeSwingBeforeCancel(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertNativeBreakStarted(helper);
            helper.runAfterDelay(1, () -> cancelNativeBreak(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native swing observation failed", failure);
        }
    }

    private static void cancelNativeBreak(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertNativeBreakStarted(helper);
            fixture.assertVanillaSwingProgressed(helper);
            fixture.runtime.getMineProcess().mine(new BlockOptionalMetaLookup(Blocks.STONE));
            helper.assertTrue(fixture.runtime.getMineProcess().isActive(),
                    "The explicit native mining process must be active before cancellation");
            for (Input input : Input.values()) {
                fixture.input.setInputForceState(input, true);
            }

            fixture.runtime.getMineProcess().cancel();

            helper.assertFalse(fixture.runtime.getMineProcess().isActive(),
                    "MineProcess.cancel() must deactivate the native process synchronously");
            fixture.assertIdle(helper, "MineProcess.cancel() must synchronously clear the worker break state");
            fixture.assertAllInputsReleased(helper,
                    "MineProcess.cancel() must synchronously release every native input override");
            fixture.assertTargetIntact(helper,
                    "MineProcess.cancel() must leave an unfinished native target intact");
            helper.runAfterDelay(POST_CANCEL_TICKS, () -> assertCancelledBreakStaysIdle(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation observation failed", failure);
        }
    }

    private static void assertCancelledBreakStaysIdle(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertIdle(helper, "A cancelled native break must remain idle after the bounded tick window");
            fixture.assertAllInputsReleased(helper,
                    "A cancelled native break must not restore any input override during the bounded tick window");
            fixture.assertTargetIntact(helper,
                    "A cancelled native break must not finish the stale target during the bounded tick window");
            fixture.runtime.getMineProcess().mine(new BlockOptionalMetaLookup(Blocks.STONE));
            helper.assertTrue(fixture.runtime.getMineProcess().isActive(),
                    "A cancelled native mining process must accept a fresh restart");
            fixture.lookAtTarget();
            fixture.input.setInputForceState(Input.CLICK_LEFT, true);
            helper.runAfterDelay(1, () -> assertFreshBreakAfterCancel(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation bounded observation failed", failure);
        }
    }

    private static void assertFreshBreakAfterCancel(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertNativeBreakStarted(helper);
            fixture.runtime.getMineProcess().cancel();
            fixture.runtime.getMineProcess().cancel();
            fixture.assertIdle(helper, "Repeated native cancellation must remain safe after a fresh break");
            fixture.assertAllInputsReleased(helper,
                    "Repeated native cancellation must leave every native input override released");
            fixture.assertTargetIntact(helper,
                    "A fresh break cancelled before completion must keep the target intact");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation restart observation failed", failure);
        }
    }

    private static void stopUnmarkedBreak(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            fixture.beginControllerBreak();
            fixture.assertBreakInProgress(helper,
                    "A direct worker break starts while BlockBreakHelper has no retained hitting flag");
            fixture.input.getBlockBreakHelper().stopBreakingBlock();
            fixture.assertIdle(helper,
                    "Explicit native helper stop must clear controller state even without a prior helper hit");
            fixture.assertTargetIntact(helper,
                    "Explicit native helper stop must leave the unfinished target intact");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native helper-stop observation failed", failure);
        }
    }

    private static void beginBreakForRelease(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            fixture.lookAtTarget();
            fixture.input.setInputForceState(Input.CLICK_LEFT, true);
            helper.runAfterDelay(1, () -> releaseNativeBreak(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native release break setup failed", failure);
        }
    }

    private static void releaseNativeBreak(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertNativeBreakStarted(helper);
            fixture.input.setInputForceState(Input.CLICK_LEFT, false);
            helper.runAfterDelay(1, () -> assertReleasedBreakIsIdle(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native release observation failed", failure);
        }
    }

    private static void assertReleasedBreakIsIdle(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertIdle(helper, "Releasing native CLICK_LEFT must clear unfinished worker break state");
            fixture.assertTargetIntact(helper, "Releasing native CLICK_LEFT must leave the unfinished target intact");
            helper.assertFalse(fixture.input.isInputForcedDown(Input.CLICK_LEFT),
                    "The released native CLICK_LEFT input must stay released");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native release reset observation failed", failure);
        }
    }

    private static void beginBreakForLostRay(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            fixture.lookAtTarget();
            fixture.input.setInputForceState(Input.CLICK_LEFT, true);
            helper.runAfterDelay(1, () -> loseNativeRay(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native lost-ray break setup failed", failure);
        }
    }

    private static void loseNativeRay(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertNativeBreakStarted(helper);
            fixture.lookStraightUp();
            helper.runAfterDelay(1, () -> restartAfterLostRay(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native lost-ray setup failed", failure);
        }
    }

    private static void restartAfterLostRay(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertIdle(helper, "A lost native ray must clear unfinished worker break state");
            fixture.assertTargetIntact(helper, "A lost native ray must leave the unfinished target intact");
            fixture.lookAtTarget();
            fixture.input.setInputForceState(Input.CLICK_LEFT, true);
            helper.runAfterDelay(1, () -> assertFreshBreakAfterLostRay(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native lost-ray reset observation failed", failure);
        }
    }

    private static void assertFreshBreakAfterLostRay(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.assertNativeBreakStarted(helper);
            fixture.input.clearAllKeys();
            fixture.input.getBlockBreakHelper().stopBreakingBlock();
            fixture.assertIdle(helper, "A native break restarted after a lost ray must still be cancellable");
            fixture.assertTargetIntact(helper,
                    "Cancelling a native break restarted after a lost ray must keep the target intact");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native lost-ray restart observation failed", failure);
        }
    }

    private static void succeedAndClose(GameTestHelper helper, NativeBreakFixture fixture) {
        try {
            fixture.close();
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("Native cancellation cleanup failed: " + failure);
        }
    }

    private static void failAndClose(GameTestHelper helper, NativeBreakFixture fixture, String message, Throwable failure) {
        try {
            fixture.close();
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        helper.fail(message + ": " + failure);
    }

    private static final class NativeBreakFixture {
        private static final BlockPos WORKER_POSITION = new BlockPos(4, 1, 2);
        private static final BlockPos TARGET_POSITION = new BlockPos(4, 1, 5);

        private final GameTestHelper helper;
        private final SwingCaptureWorker worker;
        private final IBaritone runtime;
        private final WorkerEntityController controller;
        private final InputOverrideHandler input;
        private final BlockPos target;
        private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();

        private NativeBreakFixture(
                GameTestHelper helper,
                SwingCaptureWorker worker,
                IBaritone runtime,
                WorkerEntityController controller,
                InputOverrideHandler input,
                BlockPos target
        ) {
            this.helper = helper;
            this.worker = worker;
            this.runtime = runtime;
            this.controller = controller;
            this.input = input;
            this.target = target;
        }

        private static NativeBreakFixture create(GameTestHelper helper) {
            SwingCaptureWorker worker = null;
            NativeBreakFixture fixture = null;
            try {
                worker = spawnWorker(helper);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new AssertionError("Native cancellation fixture requires a live worker runtime");
                }
                if (!(runtime.getInputOverrideHandler() instanceof InputOverrideHandler input)) {
                    throw new AssertionError("Native cancellation fixture requires the concrete input override handler");
                }
                WorkerEntityController controller = (WorkerEntityController) runtime.getPlayerContext().playerController();
                BlockPos target = helper.absolutePos(TARGET_POSITION);
                fixture = new NativeBreakFixture(helper, worker, runtime, controller, input, target);
                fixture.buildChamber();
                fixture.placeWorker();
                worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                worker.setSelectedSlot(0);
                return fixture;
            } catch (Throwable failure) {
                if (fixture != null) {
                    fixture.close();
                } else {
                    WorkerGameTestSupport.discardWorker(worker);
                }
                throw failure;
            }
        }

        private static SwingCaptureWorker spawnWorker(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            SwingCaptureWorker worker = new SwingCaptureWorker(WorkerMod.WORKER.get(), level);
            BlockPos position = helper.absolutePos(BlockPos.ZERO);
            worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            worker.setNoGravity(true);
            if (!level.addFreshEntity(worker)) {
                throw new AssertionError("Native cancellation fixture worker was rejected by the ServerLevel");
            }
            return worker;
        }

        private void buildChamber() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            for (int x = 2; x <= 6; x++) {
                for (int z = 1; z <= 7; z++) {
                    replace(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 7; z++) {
                    replace(origin.offset(2, y, z), Blocks.STONE.defaultBlockState());
                    replace(origin.offset(6, y, z), Blocks.STONE.defaultBlockState());
                }
                for (int x = 3; x <= 5; x++) {
                    replace(origin.offset(x, y, 1), Blocks.STONE.defaultBlockState());
                    replace(origin.offset(x, y, 7), Blocks.STONE.defaultBlockState());
                }
            }
            replace(target, Blocks.STONE.defaultBlockState());
        }

        private void placeWorker() {
            BlockPos position = helper.absolutePos(WORKER_POSITION);
            worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            worker.setNoGravity(false);
        }

        private void assertGrounded(GameTestHelper helper) {
            helper.assertTrue(worker.onGround(), "Native cancellation must start with a grounded worker");
        }

        private void lookAtTarget() {
            worker.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        }

        private void lookStraightUp() {
            worker.setXRot(-90.0F);
        }

        private void beginControllerBreak() {
            lookAtTarget();
            if (!(runtime.getPlayerContext().objectMouseOver() instanceof net.minecraft.world.phys.BlockHitResult hit)
                    || !target.equals(hit.getBlockPos())) {
                throw new AssertionError("Direct helper-stop fixture must raycast its native target");
            }
            if (!controller.onPlayerDamageBlock(target, hit.getDirection())) {
                throw new AssertionError("Direct helper-stop fixture must establish worker break progress");
            }
        }

        private void assertNativeBreakStarted(GameTestHelper helper) {
            assertBreakInProgress(helper, "A held native CLICK_LEFT input must reach the worker break controller target");
            helper.assertTrue(worker.mainHandSwingCalls > 0,
                    "A held native CLICK_LEFT input must make the real worker swing");
        }

        private void assertVanillaSwingProgressed(GameTestHelper helper) {
            helper.assertTrue(worker.swingTime > 0 && worker.attackAnim > 0.0F,
                    "A native worker swing must advance vanilla swing state on a later entity tick");
        }

        private void assertBreakInProgress(GameTestHelper helper, String message) {
            helper.assertTrue(target.equals(controller.breakingBlock()),
                    message);
            helper.assertTrue(controller.breakProgress() > 0.0F && controller.breakProgress() < 1.0F,
                    "An active worker break must create incomplete progress; got "
                            + controller.breakProgress());
            helper.assertTrue(controller.breakStage() >= 0,
                    "An active worker break must publish a non-idle crack stage");
            assertTargetIntact(helper,
                    "The fixture must observe worker progress before the target is removed");
        }

        private void assertIdle(GameTestHelper helper, String message) {
            helper.assertTrue(controller.breakingBlock() == null, message);
            helper.assertTrue(controller.breakProgress() == 0.0F && controller.breakStage() == -1,
                    "An idle native break state must have no progress or crack stage");
            helper.assertFalse(controller.hasBrokenBlock(), "An idle native break state must not report completion");
        }

        private void assertAllInputsReleased(GameTestHelper helper, String message) {
            for (Input input : Input.values()) {
                helper.assertFalse(this.input.isInputForcedDown(input), message + ": " + input);
            }
        }

        private void assertTargetIntact(GameTestHelper helper, String message) {
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.STONE), message);
        }

        private void replace(BlockPos position, BlockState state) {
            originalBlocks.putIfAbsent(position, helper.getLevel().getBlockState(position));
            helper.getLevel().setBlock(position, state, 3);
        }

        private void close() {
            input.clearAllKeys();
            controller.resetBlockRemoving();
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                helper.getLevel().setBlock(entry.getKey(), entry.getValue(), 3);
            }
            WorkerGameTestSupport.discardWorker(worker);
        }
    }

    private static final class SwingCaptureWorker extends WorkerEntity {
        private int mainHandSwingCalls;

        private SwingCaptureWorker(EntityType<? extends WorkerEntity> type, Level level) {
            super(type, level);
        }

        @Override
        public void swing(InteractionHand hand) {
            if (hand == InteractionHand.MAIN_HAND) {
                mainHandSwingCalls++;
            }
            super.swing(hand);
        }
    }
}
