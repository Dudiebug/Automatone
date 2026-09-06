package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerEntityController;
import automatone.worker.WorkerMod;
import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.utils.ToolSet;
import java.lang.reflect.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exercises one native MineProcess request without supplying a target position to the worker. */
@GameTestHolder("automatone_worker_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerNativeMineProcessGameTest {
    private static final int SETTLE_TICKS = 2;
    private static final int MAXIMUM_PROOF_TICKS = 700;
    private static final int TEST_TIMEOUT_TICKS = 760;
    private static final int MAXIMUM_DEMO_SLOW_COMPLETION_TICKS = 900;
    private static final int DEMO_SLOW_COMPLETION_TIMEOUT_TICKS = 960;
    private static final long NATIVE_ASYNC_YIELD_MILLIS = 50L;
    private static final double MINIMUM_PATH_DISPLACEMENT_SQUARED = 4.0D;
    private static final float MAXIMUM_HEAD_YAW_ERROR = 30.0F;
    private static final double SLOW_TEST_BLOCK_BREAK_SPEED = 0.025D;

    private WorkerNativeMineProcessGameTest() {
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m3_native_mining", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void nativeMineProcessDiscoversPathsFacesAndMinesIsolatedIronOre(GameTestHelper helper) {
        NativeMiningFixture fixture = null;
        try {
            fixture = NativeMiningFixture.create(helper);
            NativeMiningFixture startedFixture = fixture;
            helper.runAfterDelay(SETTLE_TICKS, () -> beginNativeMining(helper, startedFixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native MineProcess fixture setup failed", failure);
        }
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m3_demo_cost", timeoutTicks = 100)
    public static void demoMiningCostEstimateTracksConfiguredBlockBreakSpeed(GameTestHelper helper) {
        DemoSession demo = null;
        boolean previousPotionSetting = Baritone.settings().considerPotionEffects.value;
        try {
            demo = startDemo(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
            AttributeInstance breakSpeed = demo.worker.getAttribute(Attributes.BLOCK_BREAK_SPEED);
            helper.assertTrue(breakSpeed != null,
                    "The demo worker must expose the real block-break-speed attribute");

            for (boolean considerPotions : List.of(false, true)) {
                Baritone.settings().considerPotionEffects.value = considerPotions;
                breakSpeed.setBaseValue(1.0D);
                double normalSpeed = new ToolSet(demo.runtime.getPlayerContext())
                        .getStrVsBlock(Blocks.IRON_ORE.defaultBlockState());
                breakSpeed.setBaseValue(SLOW_TEST_BLOCK_BREAK_SPEED);
                double demoSpeed = new ToolSet(demo.runtime.getPlayerContext())
                        .getStrVsBlock(Blocks.IRON_ORE.defaultBlockState());
                helper.assertTrue(normalSpeed > 0.0D
                                && Math.abs(demoSpeed / normalSpeed - SLOW_TEST_BLOCK_BREAK_SPEED) < 0.000001D,
                        "ToolSet must scale iron-ore cost by the demo block-break-speed attribute with considerPotionEffects="
                                + considerPotions + "; normal=" + normalSpeed + ", demo=" + demoSpeed);
            }

            ArmorStand nonWorker = new ArmorStand(helper.getLevel(), 0.0D, 0.0D, 0.0D);
            helper.assertTrue(nonWorker.getAttribute(Attributes.BLOCK_BREAK_SPEED) == null,
                    "The fallback assertion requires a normal non-worker host without block-break-speed");
            SimpleContainer inventory = new SimpleContainer(9);
            inventory.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            double fallbackSpeed = new ToolSet(toolSetContext(nonWorker, inventory))
                    .getStrVsBlock(Blocks.IRON_ORE.defaultBlockState());
            double vanillaSpeed = ToolSet.calculateSpeedVsBlock(new ItemStack(Items.IRON_PICKAXE),
                    Blocks.IRON_ORE.defaultBlockState());
            helper.assertTrue(Math.abs(fallbackSpeed - vanillaSpeed) < 0.000001D,
                    "A host without block-break-speed must retain the vanilla multiplier of one");
        } finally {
            Baritone.settings().considerPotionEffects.value = previousPotionSetting;
            if (demo != null) {
                demo.close();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m3_demo", timeoutTicks = DEMO_SLOW_COMPLETION_TIMEOUT_TICKS)
    public static void demoSessionCompletesSlowMiningWithOutsideIronOre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DemoSession demo = null;
        Map<BlockPos, BlockState> outsideTerrain = new LinkedHashMap<>();
        try {
            demo = startDemo(level, helper.absolutePos(BlockPos.ZERO));
            AttributeInstance breakSpeed = demo.worker.getAttribute(Attributes.BLOCK_BREAK_SPEED);
            helper.assertTrue(breakSpeed != null,
                    "The slow-demo completion regression requires the worker block-break-speed attribute");
            breakSpeed.setBaseValue(SLOW_TEST_BLOCK_BREAK_SPEED);
            BlockPos outsideOre = demo.target().offset(-15, 0, 0);
            seedOutsideIronTerrain(level, outsideOre, outsideTerrain);
            helper.assertTrue(level.getBlockState(outsideOre).is(Blocks.IRON_ORE),
                    "The demo regression must include another iron ore outside the chamber");
            helper.assertTrue(!outsideOre.equals(demo.target()),
                    "The outside terrain ore must not replace the demo chamber target");
            demo.start();
            DemoSession startedDemo = demo;
            helper.runAfterDelay(1, () -> observeSlowDemoMining(helper, startedDemo, outsideOre, outsideTerrain,
                    new DemoMiningObservation(), 1));
        } catch (Throwable failure) {
            failAndCloseDemo(helper, demo, level, outsideTerrain, "Slow demo mining setup failed", failure);
        }
    }

    private static void beginNativeMining(GameTestHelper helper, NativeMiningFixture fixture) {
        try {
            fixture.assertReady(helper);
            // This is the sole product action: no target coordinate, queue, scanner, or path input is supplied by the worker.
            fixture.runtime.getMineProcess().mine(Blocks.IRON_ORE);
            fixture.markNativeStart();
            helper.assertTrue(fixture.runtime.getMineProcess().isActive(),
                    "The direct native MineProcess request must become active");
            helper.runAfterDelay(1, () -> observeNativeMining(helper, fixture, 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native MineProcess start failed", failure);
        }
    }

    private static void observeNativeMining(GameTestHelper helper, NativeMiningFixture fixture, int elapsedTicks) {
        try {
            waitForNativeAsyncWork();
            fixture.sampleProgress();
            if (fixture.targetIsAir()) {
                fixture.assertCompletedProof(helper);
                succeedAndClose(helper, fixture);
                return;
            }
            helper.assertTrue(elapsedTicks < MAXIMUM_PROOF_TICKS,
                    "The direct native MineProcess request did not destroy the isolated ore within "
                            + MAXIMUM_PROOF_TICKS + " ticks; observed=" + fixture.describeObservation());
            helper.runAfterDelay(1, () -> observeNativeMining(helper, fixture, elapsedTicks + 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Native MineProcess observation failed", failure);
        }
    }

    static DemoSession startDemo(ServerLevel level, BlockPos origin) {
        return DemoSession.create(level, origin);
    }

    private static IPlayerContext toolSetContext(ArmorStand player, SimpleContainer inventory) {
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "player" -> player;
                    case "inventory" -> inventory;
                    case "selectedSlot" -> 0;
                    default -> throw new UnsupportedOperationException(method.toString());
                }
        );
    }

    private static void observeSlowDemoMining(
            GameTestHelper helper,
            DemoSession demo,
            BlockPos outsideOre,
            Map<BlockPos, BlockState> outsideTerrain,
            DemoMiningObservation observation,
            int elapsedTicks
    ) {
        try {
            waitForNativeAsyncWork();
            observation.sample(demo);
            if (demo.chamber.targetStateIs(Blocks.AIR)) {
                helper.assertTrue(demo.chamber.level.getBlockState(outsideOre).is(Blocks.IRON_ORE),
                        "The unreachable outside ore must remain terrain while the demo completes its chamber target");
                helper.assertTrue(observation.targetProgressSamples > 1,
                        "The configured slow demo must expose progressive target damage before completion; observed="
                                + observation.describe(demo));
                closeAndRestoreDemo(demo, demo.chamber.level, outsideTerrain);
                helper.succeed();
                return;
            }
            helper.assertTrue(elapsedTicks < MAXIMUM_DEMO_SLOW_COMPLETION_TICKS,
                    "The configured slow demo did not complete its chamber target within "
                            + MAXIMUM_DEMO_SLOW_COMPLETION_TICKS + " ticks; observed=" + observation.describe(demo));
            helper.runAfterDelay(1, () -> observeSlowDemoMining(helper, demo, outsideOre, outsideTerrain, observation,
                    elapsedTicks + 1));
        } catch (Throwable failure) {
            failAndCloseDemo(helper, demo, demo.chamber.level, outsideTerrain,
                    "Slow demo mining observation failed", failure);
        }
    }

    private static void seedOutsideIronTerrain(
            ServerLevel level,
            BlockPos outsideOre,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos position = outsideOre.offset(x, y, z);
                    originalBlocks.putIfAbsent(position, level.getBlockState(position));
                    level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(outsideOre, Blocks.IRON_ORE.defaultBlockState(), 3);
    }

    private static void closeAndRestoreDemo(DemoSession demo, Level level, Map<BlockPos, BlockState> outsideTerrain) {
        try {
            demo.close();
        } finally {
            outsideTerrain.forEach((position, state) -> level.setBlock(position, state, 3));
        }
    }

    private static void failAndCloseDemo(
            GameTestHelper helper,
            DemoSession demo,
            Level level,
            Map<BlockPos, BlockState> outsideTerrain,
            String message,
            Throwable failure
    ) {
        try {
            if (demo != null) {
                closeAndRestoreDemo(demo, level, outsideTerrain);
            } else {
                outsideTerrain.forEach((position, state) -> level.setBlock(position, state, 3));
            }
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        helper.fail(message + ": " + failure);
    }

    private static final class DemoMiningObservation {
        private int targetProgressSamples;
        private int targetProgressResets;
        private float previousTargetProgress;
        private boolean targetWasBreaking;

        private void sample(DemoSession demo) {
            WorkerEntityController controller = (WorkerEntityController) demo.runtime.getPlayerContext().playerController();
            boolean targetBreaking = demo.target().equals(controller.breakingBlock());
            float progress = controller.breakProgress();
            if (targetWasBreaking && (!targetBreaking || progress + 0.000001F < previousTargetProgress)) {
                targetProgressResets++;
            }
            if (targetBreaking && progress > 0.0F && progress < 1.0F) {
                targetProgressSamples++;
                previousTargetProgress = progress;
            }
            targetWasBreaking = targetBreaking;
        }

        private String describe(DemoSession demo) {
            return "targetProgressSamples=" + targetProgressSamples
                    + ", targetProgressResets=" + targetProgressResets
                    + ", mineActive=" + demo.runtime.getMineProcess().isActive()
                    + ", pathing=" + demo.runtime.getPathingBehavior().isPathing()
                    + ", inProgress=" + demo.runtime.getPathingBehavior().getInProgress().isPresent()
                    + ", worker=" + demo.worker.position()
                    + ", target=" + demo.target();
        }
    }

    private static void succeedAndClose(GameTestHelper helper, NativeMiningFixture fixture) {
        try {
            fixture.close();
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("Native MineProcess fixture cleanup failed: " + failure);
        }
    }

    private static void failAndClose(
            GameTestHelper helper,
            NativeMiningFixture fixture,
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

    private static float angleDistance(float first, float second) {
        return Math.abs(Mth.wrapDegrees(first - second));
    }

    private static void waitForNativeAsyncWork() {
        // Native scan and path calculation use wall-clock budgets while GameTest ticks advance faster than real time.
        // Give that work one normal server tick per observation without changing product settings or tick limits.
        try {
            Thread.sleep(NATIVE_ASYNC_YIELD_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GameTest native-work pacing was interrupted", interrupted);
        }
    }

    private static String describeRuntimeValue(Object value) {
        return value == null ? "<null>" : value.getClass().getName() + "[" + value + "]";
    }

    static final class DemoSession {
        private static final double DEMO_BLOCK_BREAK_SPEED = 1.0D;

        private final MiningChamber chamber;
        private final WorkerEntity worker;
        private final IBaritone runtime;
        private boolean started;
        private boolean closed;

        private DemoSession(MiningChamber chamber, WorkerEntity worker, IBaritone runtime) {
            this.chamber = chamber;
            this.worker = worker;
            this.runtime = runtime;
        }

        private static DemoSession create(ServerLevel level, BlockPos origin) {
            MiningChamber chamber = new MiningChamber(level, origin);
            WorkerEntity worker = null;
            try {
                chamber.build();
                worker = spawnWorker(level, chamber.workerPosition());
                worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                worker.setSelectedSlot(0);
                AttributeInstance breakSpeed = worker.getAttribute(Attributes.BLOCK_BREAK_SPEED);
                if (breakSpeed == null) {
                    throw new IllegalStateException("Demo worker is missing the block-break-speed attribute");
                }
                breakSpeed.setBaseValue(DEMO_BLOCK_BREAK_SPEED);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new IllegalStateException("Demo worker did not acquire a native runtime");
                }
                return new DemoSession(chamber, worker, runtime);
            } catch (Throwable failure) {
                if (worker != null) {
                    worker.remove(Entity.RemovalReason.DISCARDED);
                }
                chamber.restore();
                throw failure;
            }
        }

        BlockPos target() {
            return chamber.target();
        }

        BlockPos viewPosition() {
            return chamber.origin().offset(8, 3, 1);
        }

        void start() {
            if (closed) {
                throw new IllegalStateException("Cannot start a closed M3 demo");
            }
            if (started) {
                throw new IllegalStateException("M3 demo mining has already started");
            }
            runtime.getMineProcess().mine(Blocks.IRON_ORE);
            started = true;
        }

        void cancel() {
            if (!closed) {
                runtime.getMineProcess().cancel();
            }
        }

        void close() {
            if (closed) {
                return;
            }
            try {
                cancel();
                worker.remove(Entity.RemovalReason.DISCARDED);
            } finally {
                closed = true;
                chamber.restore();
            }
        }
    }

    private static final class NativeMiningFixture {
        private final MiningChamber chamber;
        private final WorkerEntity worker;
        private final IBaritone runtime;
        private final WorkerEntityController controller;
        private final Vec3 startingPosition;
        private final int initialToolDamage;
        private boolean sawMineActive;
        private boolean sawNativeGoal;
        private boolean sawNativePath;
        private boolean sawMovement;
        private boolean sawIntermediateProgress;
        private boolean sawTargetRay;
        private boolean sawHeadYawConvergence;
        private int progressiveBreakSamples;
        private long nativeStartNanos = -1L;
        private boolean closed;

        private NativeMiningFixture(
                MiningChamber chamber,
                WorkerEntity worker,
                IBaritone runtime,
                WorkerEntityController controller,
                Vec3 startingPosition,
                int initialToolDamage
        ) {
            this.chamber = chamber;
            this.worker = worker;
            this.runtime = runtime;
            this.controller = controller;
            this.startingPosition = startingPosition;
            this.initialToolDamage = initialToolDamage;
        }

        private static NativeMiningFixture create(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            MiningChamber chamber = new MiningChamber(level, helper.absolutePos(BlockPos.ZERO));
            WorkerEntity worker = null;
            try {
                chamber.build();
                worker = spawnWorker(level, chamber.workerPosition());
                worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                worker.setSelectedSlot(0);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new AssertionError("Native mining fixture requires a live worker runtime");
                }
                WorkerEntityController controller = (WorkerEntityController) runtime.getPlayerContext().playerController();
                return new NativeMiningFixture(chamber, worker, runtime, controller, worker.position(),
                        worker.getMainHandItem().getDamageValue());
            } catch (Throwable failure) {
                if (worker != null) {
                    worker.remove(Entity.RemovalReason.DISCARDED);
                }
                chamber.restore();
                throw failure;
            }
        }

        private void assertReady(GameTestHelper helper) {
            helper.assertTrue(worker.onGround(), "Native mining proof must start with a grounded worker");
            helper.assertTrue(chamber.target().distToCenterSqr(worker.position()) > 4.5D * 4.5D,
                    "The isolated ore must begin outside worker reach");
            helper.assertTrue(worker.getMainHandItem().is(Items.IRON_PICKAXE),
                    "The proof worker must begin with the selected iron pickaxe");
            helper.assertTrue(chamber.targetStateIs(Blocks.IRON_ORE),
                    "The chamber must contain exactly the isolated iron ore target");
            helper.assertFalse(runtime.getMineProcess().isActive(),
                    "The proof worker must begin without a native mining request");
        }

        private void sampleProgress() {
            sawMineActive |= runtime.getMineProcess().isActive();
            sawNativeGoal |= runtime.getPathingBehavior().getGoal() != null;
            sawNativePath |= runtime.getPathingBehavior().isPathing();
            sawMovement |= horizontalDistanceSquared(startingPosition, worker.position())
                    >= MINIMUM_PATH_DISPLACEMENT_SQUARED;

            if (!chamber.targetStateIs(Blocks.IRON_ORE)
                    || !chamber.target().equals(controller.breakingBlock())) {
                return;
            }
            float progress = controller.breakProgress();
            if (progress <= 0.0F || progress >= 1.0F) {
                return;
            }
            sawIntermediateProgress = true;
            progressiveBreakSamples++;
            HitResult trace = RayTraceUtils.rayTraceTowards(worker,
                    new Rotation(worker.getYRot(), worker.getXRot()), controller.getBlockReachDistance());
            if (trace.getType() == HitResult.Type.BLOCK && trace instanceof BlockHitResult hit
                    && chamber.target().equals(hit.getBlockPos())
                    && worker.getEyePosition(1.0F).distanceTo(hit.getLocation()) <= controller.getBlockReachDistance()) {
                sawTargetRay = true;
            }
            // Native look is applied at server tick end; only require the observable head/body sync after one entity tick.
            if (progressiveBreakSamples > 1
                    && angleDistance(worker.getYHeadRot(), worker.getYRot()) <= MAXIMUM_HEAD_YAW_ERROR) {
                sawHeadYawConvergence = true;
            }
        }

        private void markNativeStart() {
            nativeStartNanos = System.nanoTime();
        }

        private void assertCompletedProof(GameTestHelper helper) {
            helper.assertTrue(sawMineActive,
                    "The direct MineProcess request must remain active long enough for native work to start");
            helper.assertTrue(sawNativeGoal && sawNativePath,
                    "Native discovery must publish and execute a path to the isolated ore; observed=" + describeObservation());
            helper.assertTrue(sawMovement,
                    "The worker must travel toward the initially unreachable ore; observed=" + describeObservation());
            helper.assertTrue(sawIntermediateProgress,
                    "The worker controller must expose incomplete progressive ore damage before destruction");
            helper.assertTrue(sawTargetRay,
                    "During native break progress, the worker eye ray must hit the isolated ore within 4.5-block reach");
            helper.assertTrue(sawHeadYawConvergence,
                    "During native progressive interaction, the worker head yaw must converge with native facing after one tick");
            helper.assertTrue(chamber.hasNearbyDrop(Items.RAW_IRON),
                    "Normal iron-ore destruction must produce a nearby raw-iron item entity");
            ItemStack tool = worker.getMainHandItem();
            helper.assertTrue(tool.is(Items.IRON_PICKAXE) && tool.getDamageValue() > initialToolDamage,
                    "The selected iron pickaxe must take normal mining wear; initial=" + initialToolDamage
                            + ", actual=" + tool.getDamageValue());
        }

        private boolean targetIsAir() {
            return chamber.targetStateIs(Blocks.AIR);
        }

        private String describeObservation() {
            return "mineActive=" + sawMineActive
                    + ", goal=" + sawNativeGoal
                    + ", path=" + sawNativePath
                    + ", movement=" + sawMovement
                    + ", intermediate=" + sawIntermediateProgress
                    + ", ray=" + sawTargetRay
                    + ", head=" + sawHeadYawConvergence
                    + ", currentGoal=" + describeRuntimeValue(runtime.getPathingBehavior().getGoal())
                    + ", mostRecentCommand=" + runtime.getPathingControlManager().mostRecentCommand()
                            .map(command -> "type=" + command.commandType + ", goal="
                                    + describeRuntimeValue(command.goal) + ", text=" + command)
                            .orElse("<none>")
                    + ", inProgress=" + runtime.getPathingBehavior().getInProgress()
                            .map(WorkerNativeMineProcessGameTest::describeRuntimeValue)
                            .orElse("<none>")
                    + ", nativeWallMillis=" + nativeWallMillis()
                    + ", worker=" + worker.position()
                    + ", target=" + chamber.target();
        }

        private long nativeWallMillis() {
            return nativeStartNanos < 0L ? -1L : (System.nanoTime() - nativeStartNanos) / 1_000_000L;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                runtime.getMineProcess().cancel();
                worker.remove(Entity.RemovalReason.DISCARDED);
            } finally {
                chamber.clearDrops();
                chamber.restore();
            }
        }
    }

    static final class MiningChamber {
        private static final int WIDTH = 17;
        private static final int HEIGHT = 5;
        private static final int DEPTH = 9;
        private static final BlockPos WORKER_OFFSET = new BlockPos(2, 1, 4);
        private static final BlockPos TARGET_OFFSET = new BlockPos(13, 1, 4);

        private final Level level;
        private final BlockPos origin;
        private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();

        MiningChamber(Level level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
        }

        private BlockPos origin() {
            return origin;
        }

        BlockPos workerPosition() {
            return origin.offset(WORKER_OFFSET);
        }

        BlockPos target() {
            return origin.offset(TARGET_OFFSET);
        }

        private boolean targetStateIs(net.minecraft.world.level.block.Block block) {
            return level.getBlockState(target()).is(block);
        }

        void build() {
            for (int x = 0; x < WIDTH; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    replace(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            for (int y = 1; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    replace(origin.offset(x, y, 0), Blocks.STONE.defaultBlockState());
                    replace(origin.offset(x, y, DEPTH - 1), Blocks.STONE.defaultBlockState());
                }
                for (int z = 1; z < DEPTH - 1; z++) {
                    replace(origin.offset(0, y, z), Blocks.STONE.defaultBlockState());
                    replace(origin.offset(WIDTH - 1, y, z), Blocks.STONE.defaultBlockState());
                }
            }
            replace(target(), Blocks.IRON_ORE.defaultBlockState());
        }

        private boolean hasNearbyDrop(net.minecraft.world.item.Item item) {
            return !level.getEntities(EntityTypeTest.forClass(ItemEntity.class), new AABB(target()).inflate(3.0D),
                    entity -> entity.getItem().is(item)).isEmpty();
        }

        void clearDrops() {
            for (ItemEntity item : level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                    new AABB(origin.getX(), origin.getY(), origin.getZ(),
                            origin.getX() + WIDTH, origin.getY() + HEIGHT, origin.getZ() + DEPTH).inflate(1.0D), entity -> true)) {
                item.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        void replace(BlockPos position, BlockState state) {
            originalBlocks.putIfAbsent(position, level.getBlockState(position));
            level.setBlock(position, state, 3);
        }

        void restore() {
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            originalBlocks.clear();
        }
    }

    static WorkerEntity spawnWorker(ServerLevel level, BlockPos position) {
        WorkerEntity worker = WorkerMod.WORKER.get().create(level);
        if (worker == null) {
            throw new IllegalStateException("Registered worker entity type did not create an entity");
        }
        worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        worker.setNoGravity(false);
        if (!level.addFreshEntity(worker)) {
            throw new IllegalStateException("Server level rejected the native mining proof worker");
        }
        return worker;
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }
}
