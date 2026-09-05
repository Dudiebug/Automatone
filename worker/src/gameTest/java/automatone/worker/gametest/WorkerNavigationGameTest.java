package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
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
public final class WorkerNavigationGameTest {
    private static final int SETTLE_TICKS = 2;
    private static final int MAXIMUM_ARRIVAL_TICKS = 400;
    private static final int IDLE_OBSERVATION_TICKS = 20;
    private static final int TEST_TIMEOUT_TICKS = 450;
    private static final int CORRIDOR_WIDTH = 3;
    private static final int CORRIDOR_HEIGHT = 5;
    private static final int CORRIDOR_LENGTH = 15;
    private static final BlockPos SPAWN_POSITION = new BlockPos(1, 1, 2);
    private static final BlockPos FLAT_GOAL_POSITION = new BlockPos(1, 1, 8);
    private static final BlockPos STEP_POSITION = new BlockPos(1, 1, 8);
    private static final BlockPos STEP_GOAL_POSITION = new BlockPos(1, 2, 8);
    private static final BlockPos LONG_GOAL_POSITION = new BlockPos(1, 1, 12);
    private static final double MINIMUM_MOVEMENT_DISTANCE_SQUARED = 0.01D;
    private static final double MAXIMUM_IDLE_DRIFT_SQUARED = 0.0001D;

    private WorkerNavigationGameTest() {
    }

    @GameTest(template = "worker_navigation", batch = "worker_navigation_flat", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void nativeGoalReachesFlatCorridorTarget(GameTestHelper helper) {
        NavigationFixture fixture = null;
        try {
            fixture = NavigationFixture.create(helper);
            NavigationFixture testFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> beginFlatNavigation(helper, testFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Flat native-navigation fixture failed", failure);
        }
    }

    @GameTest(template = "worker_navigation", batch = "worker_navigation_step", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void nativeGoalTraversesOneBlockRiseWithoutTerrainMutation(GameTestHelper helper) {
        NavigationFixture fixture = null;
        try {
            fixture = NavigationFixture.create(helper);
            fixture.placeStep();
            NavigationFixture testFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> beginStepNavigation(helper, testFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "One-block-rise native-navigation fixture failed", failure);
        }
    }

    @GameTest(template = "worker_navigation", batch = "worker_navigation_cancel", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void nativeCancellationStopsMovementAfterMeasuredPathProgress(GameTestHelper helper) {
        NavigationFixture fixture = null;
        try {
            fixture = NavigationFixture.create(helper);
            NavigationFixture testFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> beginCancellationNavigation(helper, testFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native-cancellation fixture failed", failure);
        }
    }

    @GameTest(template = "worker_navigation", batch = "worker_navigation_removal", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void removalAfterNativePathProgressDisposesTheRuntime(GameTestHelper helper) {
        NavigationFixture fixture = null;
        try {
            fixture = NavigationFixture.create(helper);
            NavigationFixture testFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> beginRemovalNavigation(helper, testFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native-removal fixture failed", failure);
        }
    }

    private static void beginFlatNavigation(GameTestHelper helper, NavigationFixture fixture) {
        try {
            fixture.assertReadyForNavigation(helper);
            GoalBlock goal = new GoalBlock(fixture.absolute(FLAT_GOAL_POSITION));
            fixture.observation.begin();
            fixture.runtime.getCustomGoalProcess().setGoalAndPath(goal);
            helper.runAfterDelay(1, () -> awaitArrival(helper, fixture, goal, false, 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Flat native-navigation start failed", failure);
        }
    }

    private static void beginStepNavigation(GameTestHelper helper, NavigationFixture fixture) {
        try {
            fixture.assertReadyForNavigation(helper);
            fixture.assertStepPresent(helper);
            GoalBlock goal = new GoalBlock(fixture.absolute(STEP_GOAL_POSITION));
            fixture.observation.begin();
            fixture.runtime.getCustomGoalProcess().setGoalAndPath(goal);
            helper.runAfterDelay(1, () -> awaitArrival(helper, fixture, goal, true, 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "One-block-rise native-navigation start failed", failure);
        }
    }

    private static void awaitArrival(
            GameTestHelper helper,
            NavigationFixture fixture,
            GoalBlock goal,
            boolean stepScenario,
            int elapsedTicks
    ) {
        try {
            fixture.observation.sample(fixture.runtime);
            if (stepScenario) {
                fixture.observation.sampleWorker(fixture.worker, fixture.absolute(STEP_GOAL_POSITION).getY());
            }
            fixture.assertInsideCorridor(helper);
            if (isGoalCompleted(fixture, goal)) {
                assertNativeGoalCompletion(helper, fixture, goal);
                if (stepScenario) {
                    fixture.assertStepPresent(helper);
                    helper.assertTrue(fixture.observation.sawOneBlockAscent,
                            "The native path must raise the worker onto the one-block obstacle");
                }
                fixture.assertTerrainUnchanged(helper);
                succeedAndClose(helper, fixture);
                return;
            }
            helper.assertTrue(elapsedTicks < MAXIMUM_ARRIVAL_TICKS,
                    "Native goal did not complete within " + MAXIMUM_ARRIVAL_TICKS + " ticks");
            helper.runAfterDelay(1, () -> awaitArrival(helper, fixture, goal, stepScenario, elapsedTicks + 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native goal arrival failed", failure);
        }
    }

    private static void beginCancellationNavigation(GameTestHelper helper, NavigationFixture fixture) {
        try {
            fixture.assertReadyForNavigation(helper);
            GoalBlock goal = new GoalBlock(fixture.absolute(LONG_GOAL_POSITION));
            Vec3 startingPosition = fixture.worker.position();
            fixture.observation.begin();
            fixture.runtime.getCustomGoalProcess().setGoalAndPath(goal);
            helper.runAfterDelay(1, () -> awaitMovementThenCancel(helper, fixture, goal, startingPosition, 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation start failed", failure);
        }
    }

    private static void awaitMovementThenCancel(
            GameTestHelper helper,
            NavigationFixture fixture,
            GoalBlock goal,
            Vec3 startingPosition,
            int elapsedTicks
    ) {
        try {
            fixture.observation.sample(fixture.runtime);
            fixture.assertInsideCorridor(helper);
            if (fixture.observation.sawNativePathExecution()
                    && fixture.worker.onGround()
                    && !isAtGoal(fixture.worker, goal)
                    && horizontalDistanceSquared(startingPosition, fixture.worker.position())
                    > MINIMUM_MOVEMENT_DISTANCE_SQUARED) {
                helper.assertTrue(fixture.runtime.getCustomGoalProcess().isActive(),
                        "Cancellation must occur while the native goal process still owns the route");
                helper.assertTrue(fixture.runtime.getPathingBehavior().cancelEverything(),
                        "Native cancellation must accept grounded movement after measured path progress");
                helper.runAfterDelay(1, () -> verifyNextTickCancellation(helper, fixture));
                return;
            }
            helper.assertTrue(elapsedTicks < MAXIMUM_ARRIVAL_TICKS,
                    "Native path did not produce cancellable grounded movement within " + MAXIMUM_ARRIVAL_TICKS + " ticks");
            helper.runAfterDelay(1,
                    () -> awaitMovementThenCancel(helper, fixture, goal, startingPosition, elapsedTicks + 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation progress observation failed", failure);
        }
    }

    private static void verifyNextTickCancellation(GameTestHelper helper, NavigationFixture fixture) {
        try {
            fixture.assertInsideCorridor(helper);
            helper.assertTrue(fixture.worker.onGround(),
                    "Cancellation must be observed on a grounded worker at the next entity tick");
            assertAllNativeInputsReleased(helper, fixture.runtime);
            helper.assertFalse(fixture.runtime.getCustomGoalProcess().isActive(),
                    "Native cancellation must release the custom goal process by the next entity tick");
            helper.assertFalse(fixture.runtime.getPathingBehavior().isPathing(),
                    "Native cancellation must stop current path execution by the next entity tick");
            Vec3 stoppedPosition = fixture.worker.position();
            helper.runAfterDelay(IDLE_OBSERVATION_TICKS,
                    () -> verifyCancellationRemainsIdle(helper, fixture, stoppedPosition));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation next-tick observation failed", failure);
        }
    }

    private static void verifyCancellationRemainsIdle(
            GameTestHelper helper,
            NavigationFixture fixture,
            Vec3 stoppedPosition
    ) {
        try {
            fixture.assertInsideCorridor(helper);
            helper.assertTrue(fixture.worker.onGround(),
                    "Cancelled worker must remain grounded throughout the idle observation window");
            helper.assertTrue(horizontalDistanceSquared(stoppedPosition, fixture.worker.position())
                            <= MAXIMUM_IDLE_DRIFT_SQUARED,
                    "Native cancellation must limit grounded horizontal drift to 0.01 blocks over 20 ticks");
            fixture.assertTerrainUnchanged(helper);
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native cancellation idle-window observation failed", failure);
        }
    }

    private static void beginRemovalNavigation(GameTestHelper helper, NavigationFixture fixture) {
        try {
            fixture.assertReadyForNavigation(helper);
            GoalBlock goal = new GoalBlock(fixture.absolute(LONG_GOAL_POSITION));
            Vec3 startingPosition = fixture.worker.position();
            fixture.observation.begin();
            fixture.runtime.getCustomGoalProcess().setGoalAndPath(goal);
            helper.runAfterDelay(1, () -> awaitMovementThenRemove(helper, fixture, goal, startingPosition, 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native removal start failed", failure);
        }
    }

    private static void awaitMovementThenRemove(
            GameTestHelper helper,
            NavigationFixture fixture,
            GoalBlock goal,
            Vec3 startingPosition,
            int elapsedTicks
    ) {
        try {
            fixture.observation.sample(fixture.runtime);
            fixture.assertInsideCorridor(helper);
            if (fixture.observation.sawNativePathExecution()
                    && fixture.worker.onGround()
                    && !isAtGoal(fixture.worker, goal)
                    && horizontalDistanceSquared(startingPosition, fixture.worker.position())
                    > MINIMUM_MOVEMENT_DISTANCE_SQUARED) {
                helper.assertTrue(fixture.observation.preTicks > 0 && fixture.observation.postTicks > 0,
                        "Removal must follow observed live native runtime events");
                int preTicksBeforeRemoval = fixture.observation.preTicks;
                int postTicksBeforeRemoval = fixture.observation.postTicks;
                fixture.worker.remove(Entity.RemovalReason.DISCARDED);
                helper.assertTrue(fixture.worker.isRemoved(), "Removal must mark the worker removed");
                helper.assertTrue(fixture.worker.runtime() == null,
                        "Removal must clear the worker runtime reference");
                helper.assertTrue(fixture.runtime.isDisposed(), "Removal must dispose the owned runtime");
                helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == fixture.baselineRuntimeCount
                                && !BaritoneAPI.getProvider().getAllBaritones().contains(fixture.runtime),
                        "Removal must unregister only the fixture runtime from the provider");
                helper.runAfterDelay(2,
                        () -> verifyNoEventsAfterRemoval(helper, fixture, preTicksBeforeRemoval, postTicksBeforeRemoval));
                return;
            }
            helper.assertTrue(elapsedTicks < MAXIMUM_ARRIVAL_TICKS,
                    "Native path did not produce removable grounded movement within " + MAXIMUM_ARRIVAL_TICKS + " ticks");
            helper.runAfterDelay(1,
                    () -> awaitMovementThenRemove(helper, fixture, goal, startingPosition, elapsedTicks + 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native removal progress observation failed", failure);
        }
    }

    private static void verifyNoEventsAfterRemoval(
            GameTestHelper helper,
            NavigationFixture fixture,
            int preTicksBeforeRemoval,
            int postTicksBeforeRemoval
    ) {
        try {
            helper.assertTrue(fixture.observation.preTicks == preTicksBeforeRemoval
                            && fixture.observation.postTicks == postTicksBeforeRemoval,
                    "Disposed runtime must receive no later PRE or POST events across real server ticks");
            fixture.assertTerrainUnchanged(helper);
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native removal post-disposal observation failed", failure);
        }
    }

    private static boolean isGoalCompleted(NavigationFixture fixture, GoalBlock goal) {
        return isAtGoal(fixture.worker, goal)
                && !fixture.runtime.getCustomGoalProcess().isActive()
                && fixture.runtime.getCustomGoalProcess().getGoal() == null;
    }

    private static boolean isAtGoal(WorkerEntity worker, GoalBlock goal) {
        return worker.blockPosition().equals(goal.getGoalPos());
    }

    private static void assertNativeGoalCompletion(GameTestHelper helper, NavigationFixture fixture, GoalBlock goal) {
        helper.assertTrue(fixture.observation.sawGoalActivity,
                "The native custom-goal process must become active before completion");
        helper.assertTrue(fixture.observation.sawNativePathExecution(),
                "The native pathing behavior must execute a path before completion");
        helper.assertTrue(goal.equals(fixture.runtime.getCustomGoalProcess().mostRecentGoal()),
                "Completion must retain the exact native GoalBlock as the most recent goal");
        helper.assertFalse(fixture.runtime.getPathingBehavior().isPathing(),
                "A completed native goal must not retain an executing path");
    }

    private static void assertAllNativeInputsReleased(GameTestHelper helper, IBaritone runtime) {
        for (Input input : Input.values()) {
            helper.assertFalse(runtime.getInputOverrideHandler().isInputForcedDown(input),
                    "Native cancellation must release " + input + " by the next entity tick");
        }
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static void succeedAndClose(GameTestHelper helper, NavigationFixture fixture) {
        try {
            fixture.close();
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("Navigation fixture cleanup failed: " + failure);
        }
    }

    private static void failAndClose(
            GameTestHelper helper,
            NavigationFixture fixture,
            String message,
            Throwable failure
    ) {
        if (fixture != null) {
            try {
                fixture.close();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        helper.fail(message + ": " + failure);
    }

    private static final class NavigationFixture {
        private final WorkerEntity worker;
        private final IBaritone runtime;
        private final Level level;
        private final BlockPos origin;
        private final int baselineRuntimeCount;
        private final boolean originalAllowBreak;
        private final boolean originalAllowPlace;
        private final Map<BlockPos, BlockState> originalTerrain;
        private final Map<BlockPos, BlockState> expectedTerrain;
        private final NativePathObservation observation = new NativePathObservation();
        private boolean closed;

        private NavigationFixture(
                WorkerEntity worker,
                IBaritone runtime,
                Level level,
                BlockPos origin,
                int baselineRuntimeCount,
                boolean originalAllowBreak,
                boolean originalAllowPlace,
                Map<BlockPos, BlockState> originalTerrain
        ) {
            this.worker = worker;
            this.runtime = runtime;
            this.level = level;
            this.origin = origin;
            this.baselineRuntimeCount = baselineRuntimeCount;
            this.originalAllowBreak = originalAllowBreak;
            this.originalAllowPlace = originalAllowPlace;
            this.originalTerrain = originalTerrain;
            this.expectedTerrain = new LinkedHashMap<>(originalTerrain);
        }

        private static NavigationFixture create(GameTestHelper helper) {
            Level level = helper.getLevel();
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            Map<BlockPos, BlockState> originalTerrain = snapshotTerrain(level, origin);
            boolean originalAllowBreak = Baritone.settings().allowBreak.value;
            boolean originalAllowPlace = Baritone.settings().allowPlace.value;
            int baselineRuntimeCount = BaritoneAPI.getProvider().getAllBaritones().size();
            WorkerEntity worker = null;
            try {
                Baritone.settings().allowBreak.value = false;
                Baritone.settings().allowPlace.value = false;
                worker = WorkerGameTestSupport.spawnWorker(helper);
                BlockPos spawn = absolute(origin, SPAWN_POSITION);
                worker.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
                worker.setNoGravity(false);
                worker.setYRot(0.0F);
                worker.setXRot(0.0F);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new AssertionError("Navigation fixture requires a live worker runtime");
                }
                if (BaritoneAPI.getProvider().getAllBaritones().size() != baselineRuntimeCount + 1
                        || !BaritoneAPI.getProvider().getAllBaritones().contains(runtime)) {
                    throw new AssertionError("Navigation fixture must add exactly one worker runtime");
                }
                NavigationFixture fixture = new NavigationFixture(worker, runtime, level, origin, baselineRuntimeCount,
                        originalAllowBreak, originalAllowPlace, originalTerrain);
                fixture.buildCorridor();
                runtime.getGameEventHandler().registerEventListener(fixture.observation);
                return fixture;
            } catch (Throwable failure) {
                WorkerGameTestSupport.discardWorker(worker);
                restoreTerrain(level, originalTerrain);
                Baritone.settings().allowBreak.value = originalAllowBreak;
                Baritone.settings().allowPlace.value = originalAllowPlace;
                throw failure;
            }
        }

        private BlockPos absolute(BlockPos relative) {
            return absolute(origin, relative);
        }

        private void assertReadyForNavigation(GameTestHelper helper) {
            BlockPos spawnFloor = absolute(SPAWN_POSITION).below();
            helper.assertTrue(worker.onGround(),
                    "Navigation must begin grounded; position=" + worker.position()
                            + ", velocity=" + worker.getDeltaMovement()
                            + ", onGround=" + worker.onGround()
                            + ", blockBelowSpawn=" + level.getBlockState(spawnFloor));
            helper.assertFalse(Baritone.settings().allowBreak.value,
                    "Navigation fixture must disable native block breaking");
            helper.assertFalse(Baritone.settings().allowPlace.value,
                    "Navigation fixture must disable native block placement");
            helper.assertFalse(runtime.getCustomGoalProcess().isActive(),
                    "Navigation fixture must start from an idle custom-goal process");
            assertInsideCorridor(helper);
        }

        private void placeStep() {
            BlockPos step = absolute(STEP_POSITION);
            setExpectedStone(step);
        }

        private void buildCorridor() {
            for (int x = 0; x < CORRIDOR_WIDTH; x++) {
                for (int z = 0; z < CORRIDOR_LENGTH; z++) {
                    setExpectedStone(origin.offset(x, 0, z));
                }
            }
            for (int y = 1; y <= 3; y++) {
                for (int z = 0; z < CORRIDOR_LENGTH; z++) {
                    setExpectedStone(origin.offset(0, y, z));
                    setExpectedStone(origin.offset(CORRIDOR_WIDTH - 1, y, z));
                }
                setExpectedStone(origin.offset(1, y, 0));
                setExpectedStone(origin.offset(1, y, CORRIDOR_LENGTH - 1));
            }
        }

        private void setExpectedStone(BlockPos position) {
            BlockState state = Blocks.STONE.defaultBlockState();
            level.setBlock(position, state, 3);
            expectedTerrain.put(position, state);
        }

        private void assertStepPresent(GameTestHelper helper) {
            helper.assertTrue(level.getBlockState(absolute(STEP_POSITION)).is(Blocks.STONE),
                    "The one-block obstacle must remain present in the no-bypass corridor");
        }

        private void assertInsideCorridor(GameTestHelper helper) {
            BlockPos feet = worker.blockPosition();
            int centerX = origin.getX() + 1;
            helper.assertTrue(feet.getX() == centerX
                            && feet.getZ() > origin.getZ()
                            && feet.getZ() < origin.getZ() + CORRIDOR_LENGTH - 1,
                    "Worker must remain inside the one-cell corridor and cannot bypass the obstacle");
        }

        private void assertTerrainUnchanged(GameTestHelper helper) {
            for (Map.Entry<BlockPos, BlockState> entry : expectedTerrain.entrySet()) {
                helper.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                        "Native navigation must not mutate corridor terrain at " + entry.getKey());
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                WorkerGameTestSupport.discardWorker(worker);
            } finally {
                try {
                    restoreTerrain(level, originalTerrain);
                } finally {
                    Baritone.settings().allowBreak.value = originalAllowBreak;
                    Baritone.settings().allowPlace.value = originalAllowPlace;
                }
            }
        }

        private static Map<BlockPos, BlockState> snapshotTerrain(Level level, BlockPos origin) {
            Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
            for (int x = 0; x < CORRIDOR_WIDTH; x++) {
                for (int y = 0; y < CORRIDOR_HEIGHT; y++) {
                    for (int z = 0; z < CORRIDOR_LENGTH; z++) {
                        BlockPos position = origin.offset(x, y, z);
                        terrain.put(position, level.getBlockState(position));
                    }
                }
            }
            return terrain;
        }

        private static void restoreTerrain(Level level, Map<BlockPos, BlockState> terrain) {
            for (Map.Entry<BlockPos, BlockState> entry : terrain.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
        }

        private static BlockPos absolute(BlockPos origin, BlockPos relative) {
            return origin.offset(relative.getX(), relative.getY(), relative.getZ());
        }
    }

    private static final class NativePathObservation implements AbstractGameEventListener {
        private boolean collecting;
        private boolean sawGoalActivity;
        private boolean sawPathExecution;
        private boolean sawOneBlockAscent;
        private int preTicks;
        private int postTicks;

        private void begin() {
            collecting = true;
        }

        private void sample(IBaritone runtime) {
            if (!collecting) {
                return;
            }
            sawGoalActivity |= runtime.getCustomGoalProcess().isActive();
            sawPathExecution |= runtime.getPathingBehavior().isPathing();
        }

        private void sampleWorker(WorkerEntity worker, int raisedY) {
            if (collecting) {
                sawOneBlockAscent |= worker.blockPosition().getY() >= raisedY;
            }
        }

        private boolean sawNativePathExecution() {
            return sawGoalActivity && sawPathExecution;
        }

        @Override
        public void onTick(TickEvent event) {
            if (collecting && event.getState() == EventState.PRE && event.getType() == TickEvent.Type.IN) {
                preTicks++;
            }
        }

        @Override
        public void onPostTick(TickEvent event) {
            if (collecting && event.getState() == EventState.POST && event.getType() == TickEvent.Type.IN) {
                postTicks++;
            }
        }
    }
}
