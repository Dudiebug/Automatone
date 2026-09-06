package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerEntityController;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@GameTestHolder("automatone_worker_m5_placement_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerPlacementGameTest {
    private static final int MAX_NATIVE_TICKS = 480;
    private static final int NATIVE_TIMEOUT_TICKS = 520;
    private static final int CHAMBER_WIDTH = 3;
    private static final int CHAMBER_HEIGHT = 5;
    private static final int CHAMBER_LENGTH = 15;
    private static final BlockPos BRIDGE_WORKER = new BlockPos(1, 1, 2);
    private static final BlockPos BRIDGE_GOAL = new BlockPos(1, 1, 12);
    private static final BlockPos BRIDGE_GAP = new BlockPos(1, 0, 7);
    private static final BlockPos PILLAR_WORKER = new BlockPos(1, 1, 7);
    private static final BlockPos PILLAR_GOAL = new BlockPos(1, 3, 7);
    private static final BlockPos DIRECT_WORKER = new BlockPos(6, 1, 2);
    private static final BlockPos DIRECT_SUPPORT = new BlockPos(6, 1, 5);
    private static final BlockPos DIRECT_TARGET = new BlockPos(6, 1, 4);
    private static final BlockPos FAR_SUPPORT = new BlockPos(6, 1, 10);
    private static final BlockPos FAR_TARGET = new BlockPos(6, 1, 9);

    private WorkerPlacementGameTest() {
    }

    @GameTest(template = "worker_navigation", batch = "worker_m5_native_bridge", timeoutTicks = NATIVE_TIMEOUT_TICKS)
    public static void nativeGoalBridgesForcedGapWithExactCobblestoneConsumption(GameTestHelper helper) {
        PlacementFixture fixture = null;
        try {
            fixture = PlacementFixture.nativeChamber(helper, false);
            PlacementFixture testFixture = fixture;
            helper.runAfterDelay(2, () -> beginNativeGoal(helper, testFixture, new GoalBlock(testFixture.absolute(BRIDGE_GOAL))));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native bridge placement fixture failed", failure);
        }
    }

    @GameTest(template = "worker_navigation", batch = "worker_m5_native_pillar", timeoutTicks = NATIVE_TIMEOUT_TICKS)
    public static void nativeGoalPillarsVerticallyWithExactCobblestoneConsumption(GameTestHelper helper) {
        PlacementFixture fixture = null;
        try {
            fixture = PlacementFixture.nativeChamber(helper, true);
            PlacementFixture testFixture = fixture;
            helper.runAfterDelay(2, () -> beginNativeGoal(helper, testFixture, new GoalBlock(testFixture.absolute(PILLAR_GOAL))));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native pillar placement fixture failed", failure);
        }
    }

    @GameTest(template = "worker_movement", batch = "worker_m5_controller_placement", timeoutTicks = 160)
    public static void controllerEnforcesPlacementGuardsAndRollback(GameTestHelper helper) {
        PlacementFixture fixture = null;
        WorkerEntity otherWorker = null;
        Consumer<EntityMobGriefingEvent> griefListener = null;
        Consumer<EntityPlaceEvent> cancellationListener = null;
        try {
            fixture = PlacementFixture.direct(helper);
            fixture.prepareDirectTarget(false);

            fixture.hold(new ItemStack(Items.COBBLESTONE, 2));
            int initialSuccessSupply = fixture.countItem(Items.COBBLESTONE);
            InteractionResult success = fixture.controller.processRightClickBlock(
                    fixture.worker, fixture.level, InteractionHand.MAIN_HAND, fixture.aimAtSupport(DIRECT_SUPPORT));
            helper.assertTrue(success == InteractionResult.SUCCESS,
                    "A visible in-reach block item must return SUCCESS from the real worker controller");
            helper.assertTrue(fixture.level.getBlockState(fixture.absolute(DIRECT_TARGET)).is(Blocks.COBBLESTONE),
                    "A successful worker placement must mutate the adjacent target block");
            helper.assertTrue(fixture.countItem(Items.COBBLESTONE) == initialSuccessSupply - 1,
                    "A successful worker placement must consume exactly one block item");

            fixture.prepareDirectTarget(false);
            fixture.hold(ItemStack.EMPTY);
            assertRejected(helper, fixture, fixture.aimAtSupport(DIRECT_SUPPORT),
                    fixture.worker, fixture.level, Blocks.AIR.defaultBlockState(), 0,
                    "An empty worker hand must reject placement without mutation");

            fixture.prepareDirectTarget(false);
            fixture.hold(new ItemStack(Items.STICK));
            assertRejected(helper, fixture, fixture.aimAtSupport(DIRECT_SUPPORT),
                    fixture.worker, fixture.level, Blocks.AIR.defaultBlockState(), 0,
                    "A non-block held item must reject placement without mutation");

            fixture.prepareDirectTarget(false);
            fixture.hold(new ItemStack(Items.COBBLESTONE));
            BlockHitResult blockedHit = fixture.aimAtSupport(DIRECT_SUPPORT);
            fixture.set(DIRECT_TARGET, Blocks.STONE.defaultBlockState());
            assertRejected(helper, fixture, blockedHit,
                    fixture.worker, fixture.level, Blocks.STONE.defaultBlockState(), 1,
                    "An occupied target must reject placement and preserve the held block");

            fixture.prepareDirectTarget(false);
            fixture.hold(new ItemStack(Items.COBBLESTONE));
            fixture.setAllowPlace(false);
            assertRejected(helper, fixture, fixture.aimAtSupport(DIRECT_SUPPORT),
                    fixture.worker, fixture.level, Blocks.AIR.defaultBlockState(), 1,
                    "allowPlace=false must reject placement without mutation");
            fixture.restoreSettings();

            fixture.prepareDirectTarget(false);
            fixture.hold(new ItemStack(Items.COBBLESTONE));
            otherWorker = WorkerGameTestSupport.spawnWorker(helper);
            otherWorker.setNoGravity(true);
            assertRejected(helper, fixture, fixture.aimAtSupport(DIRECT_SUPPORT),
                    otherWorker, fixture.level, Blocks.AIR.defaultBlockState(), 1,
                    "A non-owner entity context must reject placement without mutation");

            fixture.prepareDirectTarget(false);
            fixture.hold(new ItemStack(Items.COBBLESTONE));
            ServerLevel otherLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(otherLevel != null, "The dedicated GameTest server must expose the Nether level");
            assertRejected(helper, fixture, fixture.aimAtSupport(DIRECT_SUPPORT),
                    fixture.worker, otherLevel, Blocks.AIR.defaultBlockState(), 1,
                    "A foreign world context must reject placement without mutation");

            fixture.prepareFarTarget();
            fixture.hold(new ItemStack(Items.COBBLESTONE));
            assertRejected(helper, fixture, fixture.syntheticHit(FAR_SUPPORT, Direction.NORTH),
                    fixture.worker, fixture.level, Blocks.AIR.defaultBlockState(), 1,
                    "A supplied hit outside eye reach must reject placement without mutation");

            fixture.prepareDirectTarget(false);
            fixture.hold(new ItemStack(Items.COBBLESTONE));
            WorkerEntity subject = fixture.worker;
            AtomicBoolean griefDenied = new AtomicBoolean();
            griefListener = event -> {
                if (subject.equals(event.getEntity())) {
                    griefDenied.set(true);
                    event.setCanGrief(false);
                }
            };
            NeoForge.EVENT_BUS.addListener(griefListener);
            try {
                assertRejected(helper, fixture, fixture.aimAtSupport(DIRECT_SUPPORT),
                        fixture.worker, fixture.level, Blocks.AIR.defaultBlockState(), 1,
                        "A denied entity-griefing hook must reject placement without mutation");
            } finally {
                NeoForge.EVENT_BUS.unregister(griefListener);
                griefListener = null;
            }
            helper.assertTrue(griefDenied.get(), "The denied-grief placement must reach the NeoForge grief hook");

            fixture.prepareDirectTarget(false);
            fixture.hold(new ItemStack(Items.COBBLESTONE));
            BlockPos cancellationTarget = fixture.absolute(DIRECT_TARGET);
            AtomicBoolean cancelled = new AtomicBoolean();
            cancellationListener = event -> {
                if (subject.equals(event.getEntity()) && event.getPos().equals(cancellationTarget)) {
                    cancelled.set(true);
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(cancellationListener);
            try {
                assertRejected(helper, fixture, fixture.aimAtSupport(DIRECT_SUPPORT),
                        fixture.worker, fixture.level, Blocks.AIR.defaultBlockState(), 1,
                        "A cancelled EntityPlaceEvent must roll back the block and held item");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancellationListener);
                cancellationListener = null;
            }
            helper.assertTrue(cancelled.get(), "The cancelled placement must reach EntityPlaceEvent");
        } catch (Throwable failure) {
            helper.fail("Worker placement-controller contract failed: " + failure);
        } finally {
            if (griefListener != null) {
                NeoForge.EVENT_BUS.unregister(griefListener);
            }
            if (cancellationListener != null) {
                NeoForge.EVENT_BUS.unregister(cancellationListener);
            }
            WorkerGameTestSupport.discardWorker(otherWorker);
            if (fixture != null) {
                fixture.close();
            }
        }
        helper.succeed();
    }

    private static void beginNativeGoal(GameTestHelper helper, PlacementFixture fixture, GoalBlock goal) {
        try {
            fixture.assertNativeReady(helper);
            fixture.observation.begin();
            fixture.runtime.getCustomGoalProcess().setGoalAndPath(goal);
            helper.runAfterDelay(1, () -> awaitNativeGoal(helper, fixture, goal, 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native placement goal start failed", failure);
        }
    }

    private static void awaitNativeGoal(GameTestHelper helper, PlacementFixture fixture, GoalBlock goal, int elapsedTicks) {
        try {
            fixture.observation.sample(fixture.runtime);
            if (fixture.isAtGoal(goal)
                    && !fixture.runtime.getCustomGoalProcess().isActive()
                    && fixture.runtime.getCustomGoalProcess().getGoal() == null) {
                fixture.assertNativeComplete(helper, goal);
                succeedAndClose(helper, fixture);
                return;
            }
            helper.assertTrue(elapsedTicks < MAX_NATIVE_TICKS,
                    "Native placement goal did not complete within " + MAX_NATIVE_TICKS
                            + " ticks; worker=" + fixture.worker.position()
                            + ", placed=" + fixture.countPlacedCobblestone());
            helper.runAfterDelay(1, () -> awaitNativeGoal(helper, fixture, goal, elapsedTicks + 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native placement goal failed", failure);
        }
    }

    private static void assertRejected(
            GameTestHelper helper,
            PlacementFixture fixture,
            BlockHitResult hit,
            LivingEntity player,
            Level world,
            BlockState expectedTarget,
            int expectedCobblestone,
            String message
    ) {
        InteractionResult result = fixture.controller.processRightClickBlock(player, world, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result != InteractionResult.SUCCESS, message + "; result=" + result);
        helper.assertTrue(fixture.level.getBlockState(fixture.absolute(DIRECT_TARGET)).equals(expectedTarget),
                message + "; target changed to " + fixture.level.getBlockState(fixture.absolute(DIRECT_TARGET)));
        helper.assertTrue(fixture.countItem(Items.COBBLESTONE) == expectedCobblestone,
                message + "; cobblestone count changed to " + fixture.countItem(Items.COBBLESTONE));
    }

    private static void succeedAndClose(GameTestHelper helper, PlacementFixture fixture) {
        try {
            fixture.close();
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("Worker placement fixture cleanup failed: " + failure);
        }
    }

    private static void failAndClose(
            GameTestHelper helper,
            PlacementFixture fixture,
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

    private static final class PlacementFixture {
        private final Level level;
        private final BlockPos origin;
        private final WorkerEntity worker;
        private final IBaritone runtime;
        private final WorkerEntityController controller;
        private final Map<BlockPos, BlockState> originalTerrain;
        private final Settings initialSettings;
        private final NativePlacementObservation observation;
        private boolean closed;
        private int initialNativeCobblestone;

        private PlacementFixture(
                Level level,
                BlockPos origin,
                WorkerEntity worker,
                IBaritone runtime,
                Map<BlockPos, BlockState> originalTerrain
        ) {
            this.level = level;
            this.origin = origin;
            this.worker = worker;
            this.runtime = runtime;
            this.controller = (WorkerEntityController) runtime.getPlayerContext().playerController();
            this.originalTerrain = originalTerrain;
            this.initialSettings = runtime.getSettings().copy();
            this.observation = new NativePlacementObservation();
        }

        private static PlacementFixture nativeChamber(GameTestHelper helper, boolean pillar) {
            Level level = helper.getLevel();
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            Map<BlockPos, BlockState> originalTerrain = snapshot(level, origin,
                    CHAMBER_WIDTH, CHAMBER_HEIGHT, CHAMBER_LENGTH);
            WorkerEntity worker = null;
            try {
                worker = WorkerGameTestSupport.spawnWorker(helper);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new AssertionError("Native placement fixture requires a live worker runtime");
                }
                PlacementFixture fixture = new PlacementFixture(level, origin, worker, runtime, originalTerrain);
                fixture.buildChamber(pillar);
                BlockPos workerPosition = fixture.absolute(pillar ? PILLAR_WORKER : BRIDGE_WORKER);
                worker.moveTo(workerPosition.getX() + 0.5D, workerPosition.getY(),
                        workerPosition.getZ() + 0.5D, 0.0F, 0.0F);
                worker.setNoGravity(false);
                fixture.configureNativeSettings();
                fixture.initialNativeCobblestone = pillar ? 6 : 8;
                fixture.hold(new ItemStack(Items.COBBLESTONE, fixture.initialNativeCobblestone));
                runtime.getGameEventHandler().registerEventListener(fixture.observation);
                return fixture;
            } catch (Throwable failure) {
                WorkerGameTestSupport.discardWorker(worker);
                restore(level, originalTerrain);
                throw failure;
            }
        }

        private static PlacementFixture direct(GameTestHelper helper) {
            Level level = helper.getLevel();
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            Map<BlockPos, BlockState> originalTerrain = snapshot(level, origin, 13, 4, 13);
            WorkerEntity worker = null;
            try {
                worker = WorkerGameTestSupport.spawnWorker(helper);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new AssertionError("Direct placement fixture requires a live worker runtime");
                }
                PlacementFixture fixture = new PlacementFixture(level, origin, worker, runtime, originalTerrain);
                BlockPos workerPosition = fixture.absolute(DIRECT_WORKER);
                worker.moveTo(workerPosition.getX() + 0.5D, workerPosition.getY(),
                        workerPosition.getZ() + 0.5D, 0.0F, 0.0F);
                worker.setNoGravity(true);
                fixture.hold(ItemStack.EMPTY);
                return fixture;
            } catch (Throwable failure) {
                WorkerGameTestSupport.discardWorker(worker);
                restore(level, originalTerrain);
                throw failure;
            }
        }

        private void configureNativeSettings() {
            Settings settings = runtime.getSettings().copy();
            settings.allowBreak.value = false;
            settings.allowPlace.value = true;
            settings.allowInventory.value = false;
            settings.allowParkour.value = false;
            settings.allowParkourPlace.value = false;
            settings.allowParkourAscend.value = false;
            settings.allowDiagonalAscend.value = false;
            settings.allowDiagonalDescend.value = false;
            settings.maxFallHeightNoWater.value = 0;
            settings.overshootTraverse.value = false;
            runtime.applySettings(settings);
        }

        private void assertNativeReady(GameTestHelper helper) {
            helper.assertTrue(worker.onGround(), "Native placement must begin with a grounded worker");
            helper.assertFalse(runtime.getSettings().allowBreak.value,
                    "Native placement fixture must disable block breaking");
            helper.assertTrue(runtime.getSettings().allowPlace.value,
                    "Native placement fixture must enable block placement");
            helper.assertFalse(runtime.getCustomGoalProcess().isActive(),
                    "Native placement fixture must begin with an idle custom-goal process");
        }

        private void assertNativeComplete(GameTestHelper helper, GoalBlock goal) {
            helper.assertTrue(observation.sawGoalActivity,
                    "The native custom-goal process must become active before placement completion");
            helper.assertTrue(observation.sawPathExecution,
                    "Native pathing must execute before placement completion");
            helper.assertTrue(goal.equals(runtime.getCustomGoalProcess().mostRecentGoal()),
                    "Native placement must retain the exact GoalBlock after completion");
            helper.assertFalse(runtime.getPathingBehavior().isPathing(),
                    "A completed native placement goal must not retain an executing path");
            int placed = countPlacedCobblestone();
            int consumed = initialNativeCobblestone - countItem(Items.COBBLESTONE);
            helper.assertTrue(placed > 0,
                    "The native goal must place at least one cobblestone block");
            helper.assertTrue(consumed == placed,
                    "World placements must equal consumed cobblestone items; placed=" + placed
                            + ", consumed=" + consumed);
        }

        private boolean isAtGoal(GoalBlock goal) {
            return worker.blockPosition().equals(goal.getGoalPos());
        }

        private void buildChamber(boolean pillar) {
            for (int x = 0; x < CHAMBER_WIDTH; x++) {
                for (int z = 0; z < CHAMBER_LENGTH; z++) {
                    set(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            for (int y = 1; y < CHAMBER_HEIGHT; y++) {
                for (int z = 0; z < CHAMBER_LENGTH; z++) {
                    set(new BlockPos(0, y, z), Blocks.STONE.defaultBlockState());
                    set(new BlockPos(CHAMBER_WIDTH - 1, y, z), Blocks.STONE.defaultBlockState());
                }
                set(new BlockPos(1, y, 0), Blocks.STONE.defaultBlockState());
                set(new BlockPos(1, y, CHAMBER_LENGTH - 1), Blocks.STONE.defaultBlockState());
            }
            if (!pillar) {
                for (int z = BRIDGE_GAP.getZ() - 1; z <= BRIDGE_GAP.getZ() + 1; z++) {
                    for (int y = -2; y <= 0; y++) {
                        BlockPos local = new BlockPos(1, y, z);
                        originalTerrain.putIfAbsent(absolute(local), level.getBlockState(absolute(local)));
                        set(local, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        private void prepareDirectTarget(boolean occupied) {
            set(DIRECT_SUPPORT, Blocks.STONE.defaultBlockState());
            set(DIRECT_TARGET, occupied ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }

        private void prepareFarTarget() {
            set(FAR_SUPPORT, Blocks.STONE.defaultBlockState());
            set(FAR_TARGET, Blocks.AIR.defaultBlockState());
            set(DIRECT_SUPPORT, Blocks.AIR.defaultBlockState());
            set(DIRECT_TARGET, Blocks.AIR.defaultBlockState());
        }

        private BlockHitResult aimAtSupport(BlockPos support) {
            worker.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(absolute(support)));
            HitResult trace = runtime.getPlayerContext().objectMouseOver();
            if (!(trace instanceof BlockHitResult hit) || !hit.getBlockPos().equals(absolute(support))) {
                throw new AssertionError("Expected the worker ray to hit support " + absolute(support)
                        + " but got " + trace + " from " + worker.getEyePosition(1.0F));
            }
            return hit;
        }

        private BlockHitResult syntheticHit(BlockPos support, Direction face) {
            BlockPos absoluteSupport = absolute(support);
            return new BlockHitResult(Vec3.atCenterOf(absoluteSupport), face, absoluteSupport, false);
        }

        private void hold(ItemStack stack) {
            worker.setItem(0, stack);
            worker.setSelectedSlot(0);
        }

        private void setAllowPlace(boolean enabled) {
            Settings settings = runtime.getSettings().copy();
            settings.allowPlace.value = enabled;
            runtime.applySettings(settings);
        }

        private void restoreSettings() {
            runtime.applySettings(initialSettings.copy());
        }

        private int countItem(Item item) {
            int count = 0;
            for (int slot = 0; slot < worker.getContainerSize(); slot++) {
                ItemStack stack = worker.getItem(slot);
                if (stack.is(item)) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private int countPlacedCobblestone() {
            int count = 0;
            for (Map.Entry<BlockPos, BlockState> entry : originalTerrain.entrySet()) {
                if (!entry.getValue().is(Blocks.COBBLESTONE)
                        && level.getBlockState(entry.getKey()).is(Blocks.COBBLESTONE)) {
                    count++;
                }
            }
            return count;
        }

        private BlockPos absolute(BlockPos local) {
            return origin.offset(local.getX(), local.getY(), local.getZ());
        }

        private void set(BlockPos local, BlockState state) {
            BlockPos absolute = absolute(local);
            level.setBlock(absolute, state, 3);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                WorkerGameTestSupport.discardWorker(worker);
            } finally {
                restore(level, originalTerrain);
            }
        }

        private static Map<BlockPos, BlockState> snapshot(Level level, BlockPos origin,
                                                            int width, int height, int length) {
            Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < length; z++) {
                        BlockPos position = origin.offset(x, y, z);
                        terrain.put(position, level.getBlockState(position));
                    }
                }
            }
            return terrain;
        }

        private static void restore(Level level, Map<BlockPos, BlockState> terrain) {
            for (Map.Entry<BlockPos, BlockState> entry : terrain.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
        }
    }

    private static final class NativePlacementObservation
            implements baritone.api.event.listener.AbstractGameEventListener {
        private boolean collecting;
        private boolean sawGoalActivity;
        private boolean sawPathExecution;

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

        @Override
        public void onTick(baritone.api.event.events.TickEvent event) {
            // Native state is sampled from the server-thread GameTest callback.
        }

        @Override
        public void onPostTick(baritone.api.event.events.TickEvent event) {
            // Native state is sampled from the server-thread GameTest callback.
        }
    }
}
