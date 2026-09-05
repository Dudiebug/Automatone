package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import baritone.api.IBaritone;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder("automatone_worker_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerMovementGameTest {
    private static final int SETTLE_TICKS = 2;
    private static final int MOVEMENT_TICKS = 8;
    private static final int INPUT_RELEASE_TICKS = 3;
    private static final int IDLE_OBSERVATION_TICKS = 20;
    private static final int PLATFORM_RADIUS = 6;
    private static final double MINIMUM_DISPLACEMENT = 0.10D;
    private static final double MAXIMUM_IDLE_DISTANCE_SQUARED = 0.0001D;
    private static final float MAXIMUM_LOOK_ERROR = 20.0F;

    @GameTest(template = "worker_movement", batch = "worker_movement_direct", timeoutTicks = 120)
    public static void nativeInputMovesSidewaysJumpsAndLooks(GameTestHelper helper) {
        MovementFixture fixture = null;
        try {
            fixture = MovementFixture.create(helper);
            MovementFixture testFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> beginForwardMovement(helper, testFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native direct-movement fixture failed", failure);
        }
    }

    @GameTest(template = "worker_movement", batch = "worker_movement_conflicts", timeoutTicks = 100)
    public static void vanillaControlsCannotOverrideNativeInput(GameTestHelper helper) {
        MovementFixture fixture = null;
        try {
            fixture = MovementFixture.create(helper);
            MovementFixture testFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> queueVanillaConflicts(helper, testFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Vanilla-control conflict fixture failed", failure);
        }
    }

    @GameTest(template = "worker_movement", batch = "worker_movement_idle_cancel", timeoutTicks = 120)
    public static void idleAndNativeCancellationClearStaleMovement(GameTestHelper helper) {
        MovementFixture fixture = null;
        try {
            fixture = MovementFixture.create(helper);
            MovementFixture testFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> observeInitialIdle(helper, testFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Idle/cancellation fixture failed", failure);
        }
    }

    private static void beginForwardMovement(GameTestHelper helper, MovementFixture fixture) {
        try {
            assertGrounded(helper, fixture.worker, "Native forward movement");
            Vec3 startingPosition = fixture.worker.position();
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
            helper.runAfterDelay(MOVEMENT_TICKS, () -> verifyForwardMovement(helper, fixture, startingPosition));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native forward movement failed", failure);
        }
    }

    private static void verifyForwardMovement(
            GameTestHelper helper,
            MovementFixture fixture,
            Vec3 startingPosition
    ) {
        try {
            helper.assertTrue(fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD),
                    "Native forward input must remain held during the bounded movement window");
            helper.assertTrue(fixture.worker.getZ() > startingPosition.z + MINIMUM_DISPLACEMENT,
                    "Native forward input must move the worker forward on the server");
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
            helper.runAfterDelay(INPUT_RELEASE_TICKS, () -> beginSidewaysMovement(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native forward movement observation failed", failure);
        }
    }

    private static void beginSidewaysMovement(GameTestHelper helper, MovementFixture fixture) {
        try {
            helper.assertFalse(fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD),
                    "The direct fixture must release forward input before the sideways measurement");
            Vec3 startingPosition = fixture.worker.position();
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.MOVE_LEFT, true);
            helper.runAfterDelay(MOVEMENT_TICKS, () -> verifySidewaysMovement(helper, fixture, startingPosition));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native sideways movement failed", failure);
        }
    }

    private static void verifySidewaysMovement(
            GameTestHelper helper,
            MovementFixture fixture,
            Vec3 startingPosition
    ) {
        try {
            helper.assertTrue(fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_LEFT),
                    "Native sideways input must remain held during the bounded movement window");
            helper.assertTrue(Math.abs(fixture.worker.getX() - startingPosition.x) > MINIMUM_DISPLACEMENT,
                    "Native sideways input must move the worker sideways on the server");
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.MOVE_LEFT, false);
            helper.runAfterDelay(INPUT_RELEASE_TICKS, () -> beginJump(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native sideways movement observation failed", failure);
        }
    }

    private static void beginJump(GameTestHelper helper, MovementFixture fixture) {
        try {
            assertGrounded(helper, fixture.worker, "Native jump");
            double startingY = fixture.worker.getY();
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            helper.runAfterDelay(2, () -> verifyJump(helper, fixture, startingY));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native jump failed", failure);
        }
    }

    private static void verifyJump(GameTestHelper helper, MovementFixture fixture, double startingY) {
        try {
            helper.assertTrue(fixture.worker.getY() > startingY + MINIMUM_DISPLACEMENT,
                    "Native jump input must lift the grounded worker");
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
            fixture.runtime.getLookBehavior().updateTarget(new Rotation(90.0F, 25.0F), false);
            helper.runAfterDelay(2, () -> verifyLook(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native jump observation failed", failure);
        }
    }

    private static void verifyLook(GameTestHelper helper, MovementFixture fixture) {
        try {
            helper.assertTrue(angleDistance(fixture.worker.getYRot(), 90.0F) < MAXIMUM_LOOK_ERROR,
                    "Native look input must apply the requested server yaw");
            helper.assertTrue(Math.abs(fixture.worker.getXRot() - 25.0F) < MAXIMUM_LOOK_ERROR,
                    "Native look input must apply the requested server pitch");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native look observation failed", failure);
        }
    }

    private static void queueVanillaConflicts(GameTestHelper helper, MovementFixture fixture) {
        try {
            assertGrounded(helper, fixture.worker, "Vanilla-control conflict");
            Vec3 startingPosition = fixture.worker.position();
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
            fixture.runtime.getLookBehavior().updateTarget(new Rotation(90.0F, 25.0F), false);
            fixture.worker.getMoveControl().setWantedPosition(
                    fixture.worker.getX() + 4.0D,
                    fixture.worker.getY(),
                    fixture.worker.getZ(),
                    1.0D
            );
            fixture.worker.getLookControl().setLookAt(
                    fixture.worker.getX(),
                    fixture.worker.getY(),
                    fixture.worker.getZ() - 8.0D
            );
            fixture.worker.getJumpControl().jump();
            fixture.worker.getNavigation().moveTo(
                    fixture.worker.getX() + 4.0D,
                    fixture.worker.getY(),
                    fixture.worker.getZ(),
                    1.0D
            );
            helper.runAfterDelay(MOVEMENT_TICKS,
                    () -> verifyNativeControlSurvivesConflicts(helper, fixture, startingPosition));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Vanilla-control conflict setup failed", failure);
        }
    }

    private static void verifyNativeControlSurvivesConflicts(
            GameTestHelper helper,
            MovementFixture fixture,
            Vec3 startingPosition
    ) {
        try {
            helper.assertTrue(fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD),
                    "Queued vanilla movement must not clear native forward input");
            helper.assertTrue(fixture.worker.zza > 0.0F,
                    "Queued vanilla controls must not zero the worker's native forward field");
            helper.assertTrue(fixture.worker.getX() < startingPosition.x - MINIMUM_DISPLACEMENT,
                    "Queued vanilla navigation must not move the worker away from its native forward direction");
            helper.assertTrue(Math.abs(fixture.worker.getY() - startingPosition.y) < MINIMUM_DISPLACEMENT,
                    "Queued vanilla jump control must not lift a worker without native jump input");
            helper.assertTrue(angleDistance(fixture.worker.getYRot(), 90.0F) < MAXIMUM_LOOK_ERROR,
                    "Queued vanilla look control must not overwrite the native server look target");
            float yawAfterConflict = fixture.worker.getYRot();
            Vec3 positionAfterConflict = fixture.worker.position();
            helper.runAfterDelay(MOVEMENT_TICKS,
                    () -> verifyConflictWindowRemainsStable(helper, fixture, yawAfterConflict, positionAfterConflict));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Vanilla-control conflict observation failed", failure);
        }
    }

    private static void verifyConflictWindowRemainsStable(
            GameTestHelper helper,
            MovementFixture fixture,
            float yawAfterConflict,
            Vec3 positionAfterConflict
    ) {
        try {
            helper.assertTrue(fixture.worker.getX() < positionAfterConflict.x - MINIMUM_DISPLACEMENT,
                    "Native forward motion must remain continuous instead of jittering under queued vanilla controls");
            helper.assertTrue(angleDistance(fixture.worker.getYRot(), yawAfterConflict) < MAXIMUM_LOOK_ERROR,
                    "Queued vanilla look control must not reclaim yaw during the bounded observation window");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Vanilla-control stability observation failed", failure);
        }
    }

    private static void observeInitialIdle(GameTestHelper helper, MovementFixture fixture) {
        try {
            assertGrounded(helper, fixture.worker, "Initial idle");
            Vec3 idlePosition = fixture.worker.position();
            helper.runAfterDelay(IDLE_OBSERVATION_TICKS,
                    () -> beginCancellationObservation(helper, fixture, idlePosition));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Initial idle setup failed", failure);
        }
    }

    private static void beginCancellationObservation(
            GameTestHelper helper,
            MovementFixture fixture,
            Vec3 idlePosition
    ) {
        try {
            assertGrounded(helper, fixture.worker, "Initial idle window");
            assertHorizontallyStable(helper, idlePosition, fixture.worker.position(),
                    "A worker without native input must stand still for 20 ticks");
            assertNoHorizontalVelocity(helper, fixture.worker, "An idle worker must not retain horizontal velocity");
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
            Vec3 movingStart = fixture.worker.position();
            helper.runAfterDelay(MOVEMENT_TICKS,
                    () -> cancelNativeMovement(helper, fixture, movingStart));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Initial idle observation failed", failure);
        }
    }

    private static void cancelNativeMovement(GameTestHelper helper, MovementFixture fixture, Vec3 movingStart) {
        try {
            assertGrounded(helper, fixture.worker, "Native cancellation");
            helper.assertTrue(fixture.worker.getZ() > movingStart.z + MINIMUM_DISPLACEMENT,
                    "Cancellation fixture must observe real native forward movement before cancellation");
            fixture.runtime.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            helper.assertTrue(fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD)
                            && fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.JUMP),
                    "Cancellation fixture must seed native directional and jump input before native cancellation");
            helper.assertTrue(fixture.runtime.getPathingBehavior().cancelEverything(),
                    "Native cancellation must accept a safe grounded worker");
            helper.runAfterDelay(1, () -> verifyCancellationAtNextEntityTick(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation setup failed", failure);
        }
    }

    private static void verifyCancellationAtNextEntityTick(GameTestHelper helper, MovementFixture fixture) {
        try {
            assertGrounded(helper, fixture.worker, "Native cancellation next entity tick");
            helper.assertFalse(fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD),
                    "Native cancellation must clear stale directional input by the next entity tick");
            helper.assertFalse(fixture.runtime.getInputOverrideHandler().isInputForcedDown(Input.JUMP),
                    "Native cancellation must clear stale jump input by the next entity tick");
            assertNoHorizontalVelocity(helper, fixture.worker,
                    "Native cancellation must clear grounded horizontal velocity by the next entity tick");
            Vec3 stoppedPosition = fixture.worker.position();
            helper.runAfterDelay(IDLE_OBSERVATION_TICKS,
                    () -> verifyCancellationRemainsIdle(helper, fixture, stoppedPosition));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation next-tick observation failed", failure);
        }
    }

    private static void verifyCancellationRemainsIdle(
            GameTestHelper helper,
            MovementFixture fixture,
            Vec3 stoppedPosition
    ) {
        try {
            assertGrounded(helper, fixture.worker, "Native cancellation idle window");
            assertHorizontallyStable(helper, stoppedPosition, fixture.worker.position(),
                    "Native cancellation must leave the worker stationary for 20 ticks");
            assertNoHorizontalVelocity(helper, fixture.worker,
                    "Cancelled worker must retain no horizontal velocity after 20 ticks");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation idle-window observation failed", failure);
        }
    }

    private static void assertGrounded(GameTestHelper helper, WorkerEntity worker, String phase) {
        helper.assertTrue(worker.onGround(), phase + " requires the worker to be grounded");
    }

    private static void assertNoHorizontalVelocity(GameTestHelper helper, WorkerEntity worker, String message) {
        Vec3 velocity = worker.getDeltaMovement();
        helper.assertTrue(horizontalDistanceSquared(Vec3.ZERO, velocity) <= MAXIMUM_IDLE_DISTANCE_SQUARED, message);
    }

    private static void assertHorizontallyStable(GameTestHelper helper, Vec3 expected, Vec3 actual, String message) {
        helper.assertTrue(horizontalDistanceSquared(expected, actual) <= MAXIMUM_IDLE_DISTANCE_SQUARED, message);
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static float angleDistance(float first, float second) {
        return Math.abs(Mth.wrapDegrees(first - second));
    }

    private static void succeedAndClose(GameTestHelper helper, MovementFixture fixture) {
        fixture.close();
        helper.succeed();
    }

    private static void failAndClose(
            GameTestHelper helper,
            MovementFixture fixture,
            String message,
            Throwable failure
    ) {
        if (fixture != null) {
            fixture.close();
        }
        helper.fail(message + ": " + failure);
    }

    private static final class MovementFixture {
        private final WorkerEntity worker;
        private final IBaritone runtime;
        private final Map<BlockPos, BlockState> replacedGround;
        private boolean closed;

        private MovementFixture(WorkerEntity worker, IBaritone runtime, Map<BlockPos, BlockState> replacedGround) {
            this.worker = worker;
            this.runtime = runtime;
            this.replacedGround = replacedGround;
        }

        private static MovementFixture create(GameTestHelper helper) {
            Map<BlockPos, BlockState> replacedGround = new LinkedHashMap<>();
            BlockPos platformOrigin = helper.absolutePos(BlockPos.ZERO);
            for (int x = 0; x <= PLATFORM_RADIUS * 2; x++) {
                for (int z = 0; z <= PLATFORM_RADIUS * 2; z++) {
                    BlockPos ground = platformOrigin.offset(x, 0, z);
                    replacedGround.put(ground, helper.getLevel().getBlockState(ground));
                    helper.getLevel().setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
                }
            }

            WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
            BlockPos spawn = helper.absolutePos(new BlockPos(PLATFORM_RADIUS, 1, PLATFORM_RADIUS));
            worker.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
            worker.setNoGravity(false);
            worker.setYRot(0.0F);
            worker.setXRot(0.0F);
            IBaritone runtime = worker.runtime();
            if (runtime == null) {
                WorkerGameTestSupport.discardWorker(worker);
                restoreGround(helper, replacedGround);
                throw new AssertionError("Movement fixture requires a live worker runtime");
            }
            return new MovementFixture(worker, runtime, replacedGround);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            WorkerGameTestSupport.discardWorker(worker);
            restoreGround(worker.level(), replacedGround);
        }

        private static void restoreGround(GameTestHelper helper, Map<BlockPos, BlockState> replacedGround) {
            restoreGround(helper.getLevel(), replacedGround);
        }

        private static void restoreGround(net.minecraft.world.level.Level level, Map<BlockPos, BlockState> replacedGround) {
            for (Map.Entry<BlockPos, BlockState> entry : replacedGround.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
        }
    }
}
