package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerEntityController;
import baritone.api.IBaritone;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder("automatone_worker_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerBreakProgressGameTest {
    private static final int COMPARISON_ADVANCES = 3;
    private static final int COMPLETION_ATTEMPTS = 20;

    @GameTest(template = "worker_movement", batch = "worker_m3_progression", timeoutTicks = 80)
    public static void groundedStoneAccruesIntermediateProgressBeforeCompletion(GameTestHelper helper) {
        MiningFixture fixture;
        try {
            fixture = MiningFixture.create(helper, Blocks.STONE.defaultBlockState());
        } catch (Throwable failure) {
            helper.fail("Worker mining progression setup failed: " + failure);
            return;
        }
        MiningFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> startBreak(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_tool_speed", timeoutTicks = 80)
    public static void selectedIronPickaxeProgressesStoneFasterThanEmptyHand(GameTestHelper helper) {
        MiningFixture fixture;
        try {
            fixture = MiningFixture.create(helper, Blocks.STONE.defaultBlockState());
            fixture.worker.setItem(0, ItemStack.EMPTY);
            fixture.worker.setItem(1, new ItemStack(Items.IRON_PICKAXE));
            fixture.worker.setSelectedSlot(0);
        } catch (Throwable failure) {
            helper.fail("Worker tool-speed comparison setup failed: " + failure);
            return;
        }
        MiningFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> measureEmptyHandProgress(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_bedrock", timeoutTicks = 30)
    public static void bedrockCannotBeginOrCompleteWorkerDestruction(GameTestHelper helper) {
        MiningFixture fixture;
        try {
            fixture = MiningFixture.create(helper, Blocks.BEDROCK.defaultBlockState());
        } catch (Throwable failure) {
            helper.fail("Worker bedrock fixture setup failed: " + failure);
            return;
        }
        MiningFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> assertBedrockRejected(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_drops", timeoutTicks = 60)
    public static void ironOreUsesSelectedToolForWearAndNormalDropEvent(GameTestHelper helper) {
        MiningFixture fixture;
        try {
            fixture = MiningFixture.create(helper, Blocks.IRON_ORE.defaultBlockState());
            fixture.register(new BlockDropCapture(fixture.worker, fixture.target));
        } catch (Throwable failure) {
            helper.fail("Worker iron-ore drop fixture setup failed: " + failure);
            return;
        }
        MiningFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> mineOreForDropObservation(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_tool_breakage", timeoutTicks = 60)
    public static void nearBrokenSelectedPickaxeBreaksAfterOreAndRetainsDropTool(GameTestHelper helper) {
        MiningFixture fixture;
        try {
            fixture = MiningFixture.create(helper, Blocks.IRON_ORE.defaultBlockState());
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
            fixture.worker.setItem(0, pickaxe);
            fixture.register(new BlockDropCapture(fixture.worker, fixture.target));
        } catch (Throwable failure) {
            helper.fail("Worker near-broken pickaxe fixture setup failed: " + failure);
            return;
        }
        MiningFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> mineOreWithNearBrokenPickaxe(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_modifiers", timeoutTicks = 80)
    public static void hasteIncreasesSelectedToolProgress(GameTestHelper helper) {
        MiningFixture fixture;
        try {
            fixture = MiningFixture.create(helper, Blocks.STONE.defaultBlockState());
        } catch (Throwable failure) {
            helper.fail("Worker haste fixture setup failed: " + failure);
            return;
        }
        MiningFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> measureBaselineProgress(helper, startedFixture));
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_destroy_hook", timeoutTicks = 60)
    public static void cancelledLivingDestroyBlockEventKeepsTargetIntact(GameTestHelper helper) {
        MiningFixture fixture;
        try {
            fixture = MiningFixture.create(helper, Blocks.STONE.defaultBlockState());
            fixture.register(new LivingDestroyBlockCapture(fixture.worker, fixture.target));
        } catch (Throwable failure) {
            helper.fail("Worker destruction-hook fixture setup failed: " + failure);
            return;
        }
        MiningFixture startedFixture = fixture;
        helper.runAfterDelay(2, () -> mineUntilHookDenial(helper, startedFixture, COMPLETION_ATTEMPTS));
    }

    private static void startBreak(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            fixture.damageOnce(helper);
            helper.runAfterDelay(1, () -> assertIntermediateProgress(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker mining progression start failed", failure);
        }
    }

    private static void assertIntermediateProgress(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.damageOnce(helper);
            helper.assertTrue(fixture.controller.breakProgress() > 0.0F
                            && fixture.controller.breakProgress() < 1.0F,
                    "Grounded stone with the selected iron pickaxe must accrue incomplete break progress; got "
                            + fixture.controller.breakProgress());
            helper.assertTrue(fixture.helper.getLevel().getBlockState(fixture.target).is(Blocks.STONE),
                    "The intermediate progression observation must occur before stone is removed");
            helper.assertFalse(fixture.controller.hasBrokenBlock(),
                    "Intermediate progression must not report completion");
            helper.assertTrue(fixture.controller.breakStage() >= 0,
                    "Intermediate progression must publish a non-idle crack stage");
            fixture.controller.resetBlockRemoving();
            helper.assertTrue(fixture.controller.breakStage() == -1,
                    "An explicit break reset must clear the crack stage");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker mining progression observation failed", failure);
        }
    }

    private static void assertBedrockRejected(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            helper.assertFalse(fixture.attemptDamage(),
                    "Negative-hardness bedrock must reject worker break intent");
            helper.assertTrue(fixture.targetStateIs(Blocks.BEDROCK),
                    "A rejected bedrock attempt must leave the target unchanged");
            helper.assertFalse(fixture.controller.hasBrokenBlock(),
                    "A rejected bedrock attempt must not report completion");
            helper.assertTrue(fixture.controller.breakProgress() == 0.0F,
                    "A rejected bedrock attempt must not accrue break progress");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker bedrock observation failed", failure);
        }
    }

    private static void mineOreForDropObservation(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            mineUntilRemoved(helper, fixture, COMPLETION_ATTEMPTS,
                    () -> assertOreDropAndWear(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker iron-ore drop progression failed", failure);
        }
    }

    private static void assertOreDropAndWear(GameTestHelper helper, MiningFixture fixture) {
        try {
            BlockDropCapture capture = fixture.capture(BlockDropCapture.class);
            helper.assertTrue(fixture.targetIsAir(), "Completed iron-ore mining must remove the target block");
            helper.assertTrue(capture.calls == 1,
                    "Normal iron-ore destruction must publish exactly one matching BlockDropsEvent; got " + capture.calls);
            helper.assertTrue(capture.sawDrop(Items.RAW_IRON),
                    "The normal BlockDropsEvent must contain raw iron");
            helper.assertTrue(capture.tool.is(Items.IRON_PICKAXE),
                    "The normal BlockDropsEvent must receive the selected iron pickaxe");
            helper.assertTrue(fixture.worker.getMainHandItem().is(Items.IRON_PICKAXE)
                            && fixture.worker.getMainHandItem().getDamageValue() == 1,
                    "Successful iron-ore mining must apply one use to the selected iron pickaxe");
            helper.assertTrue(fixture.controller.breakStage() == -1,
                    "Completed destruction must clear the worker crack stage");
            helper.assertFalse(fixture.inventoryContains(Items.RAW_IRON),
                    "Normal drops must not be injected into the worker inventory");
            helper.runAfterDelay(1, () -> assertRawIronSpawnedInWorld(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker iron-ore drop observation failed", failure);
        }
    }

    private static void assertRawIronSpawnedInWorld(GameTestHelper helper, MiningFixture fixture) {
        try {
            helper.assertTrue(fixture.hasNearbyDrop(Items.RAW_IRON),
                    "Normal iron-ore destruction must spawn its raw-iron drop in the world");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker raw-iron world-drop observation failed", failure);
        }
    }

    private static void mineOreWithNearBrokenPickaxe(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            mineUntilRemoved(helper, fixture, COMPLETION_ATTEMPTS,
                    () -> assertNearBrokenPickaxeResult(helper, fixture));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker near-broken pickaxe progression failed", failure);
        }
    }

    private static void assertNearBrokenPickaxeResult(GameTestHelper helper, MiningFixture fixture) {
        try {
            BlockDropCapture capture = fixture.capture(BlockDropCapture.class);
            helper.assertTrue(fixture.targetIsAir(), "The near-broken pickaxe must still remove iron ore");
            helper.assertTrue(fixture.worker.getMainHandItem().isEmpty(),
                    "The selected pickaxe at one remaining use must break after mining ore");
            helper.assertTrue(capture.calls == 1 && capture.tool.is(Items.IRON_PICKAXE)
                            && capture.sawDrop(Items.RAW_IRON),
                    "The normal drop event must retain the pre-damage selected pickaxe and raw-iron drop");
            helper.assertFalse(fixture.inventoryContains(Items.RAW_IRON),
                    "A broken selected tool must not cause predicted raw-iron inventory insertion");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker near-broken pickaxe observation failed", failure);
        }
    }

    private static void measureBaselineProgress(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            advanceAndObserve(helper, fixture, COMPARISON_ADVANCES,
                    baseline -> beginHasteMeasurement(helper, fixture, baseline));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker baseline mining-modifier measurement failed", failure);
        }
    }

    private static void beginHasteMeasurement(GameTestHelper helper, MiningFixture fixture, float baseline) {
        try {
            helper.assertTrue(baseline > 0.0F && baseline < 1.0F,
                    "The unmodified selected-tool baseline must be incomplete and positive; got " + baseline);
            fixture.controller.resetBlockRemoving();
            fixture.worker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1));
            helper.runAfterDelay(1, () -> measureHasteProgress(helper, fixture, baseline));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker haste mining-modifier setup failed", failure);
        }
    }

    private static void measureHasteProgress(GameTestHelper helper, MiningFixture fixture, float baseline) {
        try {
            advanceAndObserve(helper, fixture, COMPARISON_ADVANCES,
                    hasted -> assertHasteProgress(helper, fixture, baseline, hasted));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker haste mining-modifier measurement failed", failure);
        }
    }

    private static void assertHasteProgress(GameTestHelper helper, MiningFixture fixture, float baseline, float hasted) {
        try {
            helper.assertTrue(hasted > baseline,
                    "Haste must increase selected-tool mining progress; baseline=" + baseline + ", haste=" + hasted);
            helper.assertTrue(hasted < 1.0F,
                    "The bounded haste comparison must observe progress before completion; got " + hasted);
            helper.assertTrue(fixture.targetStateIs(Blocks.STONE),
                    "The bounded haste comparison must retain the target block");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker haste mining-modifier observation failed", failure);
        }
    }

    private static void mineUntilHookDenial(GameTestHelper helper, MiningFixture fixture, int remainingAttempts) {
        try {
            boolean accepted = fixture.attemptDamage();
            if (!accepted) {
                assertHookDenial(helper, fixture);
                return;
            }
            if (remainingAttempts == 1) {
                throw new AssertionError("The destruction hook was never reached within " + COMPLETION_ATTEMPTS + " attempts");
            }
            helper.runAfterDelay(1, () -> mineUntilHookDenial(helper, fixture, remainingAttempts - 1));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker destruction-hook progression failed", failure);
        }
    }

    private static void assertHookDenial(GameTestHelper helper, MiningFixture fixture) {
        try {
            LivingDestroyBlockCapture capture = fixture.capture(LivingDestroyBlockCapture.class);
            helper.assertTrue(capture.calls == 1,
                    "The non-player LivingDestroyBlockEvent must be raised exactly once before denial; got " + capture.calls);
            helper.assertTrue(fixture.targetStateIs(Blocks.STONE),
                    "A cancelled LivingDestroyBlockEvent must keep the worker target intact");
            helper.assertTrue(fixture.controller.breakProgress() == 0.0F && fixture.controller.breakStage() == -1,
                    "A cancelled destruction hook must reset progress and crack stage");
            helper.assertFalse(fixture.controller.hasBrokenBlock(),
                    "A cancelled destruction hook must not report completed destruction");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker destruction-hook denial observation failed", failure);
        }
    }

    private static void measureEmptyHandProgress(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.assertGrounded(helper);
            advanceAndObserve(helper, fixture, COMPARISON_ADVANCES,
                    handProgress -> beginSelectedToolMeasurement(helper, fixture, handProgress));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker empty-hand speed measurement failed", failure);
        }
    }

    private static void beginSelectedToolMeasurement(GameTestHelper helper, MiningFixture fixture, float handProgress) {
        try {
            helper.assertTrue(fixture.worker.getMainHandItem().isEmpty(),
                    "The hand baseline must use the empty selected slot");
            helper.assertTrue(handProgress > 0.0F && handProgress < 1.0F,
                    "The empty-hand baseline must retain incomplete positive progress; got " + handProgress);
            helper.assertTrue(fixture.helper.getLevel().getBlockState(fixture.target).is(Blocks.STONE),
                    "The empty-hand baseline must not remove the comparison block");
            fixture.controller.resetBlockRemoving();
            fixture.worker.setSelectedSlot(1);
            helper.runAfterDelay(1, () -> measureSelectedToolProgress(helper, fixture, handProgress));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker empty-hand speed observation failed", failure);
        }
    }

    private static void measureSelectedToolProgress(GameTestHelper helper, MiningFixture fixture, float handProgress) {
        try {
            helper.assertTrue(fixture.worker.getMainHandItem().is(Items.IRON_PICKAXE),
                    "The comparison must select the worker's iron pickaxe slot");
            advanceAndObserve(helper, fixture, COMPARISON_ADVANCES,
                    toolProgress -> assertSelectedToolProgress(helper, fixture, handProgress, toolProgress));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker selected-tool speed measurement failed", failure);
        }
    }

    private static void assertSelectedToolProgress(
            GameTestHelper helper,
            MiningFixture fixture,
            float handProgress,
            float toolProgress
    ) {
        try {
            helper.assertTrue(toolProgress > handProgress,
                    "The actually selected iron pickaxe must progress stone faster than the empty selected slot; hand="
                            + handProgress + ", pickaxe=" + toolProgress);
            helper.assertTrue(toolProgress < 1.0F,
                    "The bounded selected-tool comparison must observe progress before completion; got " + toolProgress);
            helper.assertTrue(fixture.helper.getLevel().getBlockState(fixture.target).is(Blocks.STONE),
                    "The selected-tool comparison must not remove stone before its bounded observation completes");
            succeedAndClose(helper, fixture);
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker selected-tool speed observation failed", failure);
        }
    }

    private static void advanceAndObserve(
            GameTestHelper helper,
            MiningFixture fixture,
            int remainingAdvances,
            ProgressObserver observer
    ) {
        fixture.damageOnce(helper);
        if (remainingAdvances == 1) {
            observer.observe(fixture.controller.breakProgress());
            return;
        }
        helper.runAfterDelay(1, () -> advanceAndObserve(helper, fixture, remainingAdvances - 1, observer));
    }

    private static void mineUntilRemoved(
            GameTestHelper helper,
            MiningFixture fixture,
            int remainingAttempts,
            CompletionObserver observer
    ) {
        try {
            boolean accepted = fixture.attemptDamage();
            if (fixture.targetIsAir()) {
                helper.assertTrue(accepted, "Successful block removal must keep the final worker break call accepted");
                observer.observe();
                return;
            }
            helper.assertTrue(accepted, "An intact visible mining target must keep accepting break progress");
            if (remainingAttempts == 1) {
                throw new AssertionError("Worker mining did not remove the target within " + COMPLETION_ATTEMPTS + " attempts");
            }
            helper.runAfterDelay(1, () -> mineUntilRemoved(helper, fixture, remainingAttempts - 1, observer));
        } catch (Throwable failure) {
            failAndClose(helper, fixture, "Worker completion progression failed", failure);
        }
    }

    private static void succeedAndClose(GameTestHelper helper, MiningFixture fixture) {
        try {
            fixture.close();
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("Worker mining progression cleanup failed: " + failure);
        }
    }

    private static void failAndClose(GameTestHelper helper, MiningFixture fixture, String message, Throwable failure) {
        try {
            fixture.close();
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        helper.fail(message + ": " + failure);
    }

    @FunctionalInterface
    private interface ProgressObserver {
        void observe(float progress);
    }

    @FunctionalInterface
    private interface CompletionObserver {
        void observe();
    }

    /** Captures one target-specific drop hook without changing normal drop behavior. */
    public static final class BlockDropCapture {
        private final WorkerEntity worker;
        private final BlockPos target;
        private int calls;
        private ItemStack tool = ItemStack.EMPTY;
        private final List<ItemStack> drops = new ArrayList<>();

        private BlockDropCapture(WorkerEntity worker, BlockPos target) {
            this.worker = worker;
            this.target = target;
        }

        @SubscribeEvent
        public void onBlockDrops(BlockDropsEvent event) {
            if (!(event.getBreaker() instanceof WorkerEntity breaker) || breaker.getId() != worker.getId()
                    || !target.equals(event.getPos())) {
                return;
            }
            calls++;
            tool = event.getTool().copy();
            for (ItemEntity drop : event.getDrops()) {
                drops.add(drop.getItem().copy());
            }
        }

        private boolean sawDrop(Item item) {
            return drops.stream().anyMatch(stack -> stack.is(item));
        }
    }

    /** Cancels only the fixture worker's destruction attempt through the applicable non-player hook. */
    public static final class LivingDestroyBlockCapture {
        private final WorkerEntity worker;
        private final BlockPos target;
        private int calls;

        private LivingDestroyBlockCapture(WorkerEntity worker, BlockPos target) {
            this.worker = worker;
            this.target = target;
        }

        @SubscribeEvent
        public void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
            if (event.getEntity() instanceof WorkerEntity breaker && breaker.getId() == worker.getId()
                    && target.equals(event.getPos())) {
                calls++;
                event.setCanceled(true);
            }
        }
    }

    private static final class MiningFixture {
        private static final BlockPos WORKER_POSITION = new BlockPos(4, 1, 2);
        private static final BlockPos TARGET_POSITION = new BlockPos(4, 1, 5);

        private final GameTestHelper helper;
        private final WorkerEntity worker;
        private final WorkerEntityController controller;
        private final BlockPos target;
        private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        private final List<Object> eventListeners = new ArrayList<>();

        private MiningFixture(
                GameTestHelper helper,
                WorkerEntity worker,
                WorkerEntityController controller,
                BlockPos target
        ) {
            this.helper = helper;
            this.worker = worker;
            this.controller = controller;
            this.target = target;
        }

        private static MiningFixture create(GameTestHelper helper, BlockState targetState) {
            WorkerEntity worker = null;
            MiningFixture fixture = null;
            try {
                worker = WorkerGameTestSupport.spawnWorker(helper);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new AssertionError("Mining fixture requires a live worker runtime");
                }
                WorkerEntityController controller = (WorkerEntityController) runtime.getPlayerContext().playerController();
                worker.detachRuntime();
                BlockPos target = helper.absolutePos(TARGET_POSITION);
                fixture = new MiningFixture(helper, worker, controller, target);
                fixture.buildChamber(targetState);
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

        private void buildChamber(BlockState targetState) {
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
            replace(target, targetState);
        }

        private void placeWorker() {
            BlockPos position = helper.absolutePos(WORKER_POSITION);
            worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            worker.setNoGravity(false);
        }

        private void assertGrounded(GameTestHelper helper) {
            helper.assertTrue(worker.onGround(), "Mining progression must start with a grounded worker");
        }

        private void damageOnce(GameTestHelper helper) {
            helper.assertTrue(attemptDamage(), "A grounded worker must accept the visible target break intent");
        }

        private boolean attemptDamage() {
            worker.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
            HitResult trace = RayTraceUtils.rayTraceTowards(worker,
                    new Rotation(worker.getYRot(), worker.getXRot()), controller.getBlockReachDistance());
            if (!(trace instanceof BlockHitResult hit) || !hit.getBlockPos().equals(target)) {
                throw new AssertionError("Mining fixture must raycast the target; got " + trace);
            }
            return controller.onPlayerDamageBlock(target, hit.getDirection());
        }

        private boolean targetStateIs(net.minecraft.world.level.block.Block block) {
            return helper.getLevel().getBlockState(target).is(block);
        }

        private boolean targetIsAir() {
            return helper.getLevel().getBlockState(target).isAir();
        }

        private boolean inventoryContains(Item item) {
            for (int slot = 0; slot < worker.getContainerSize(); slot++) {
                if (worker.getItem(slot).is(item)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasNearbyDrop(Item item) {
            return !helper.getLevel().getEntities(EntityTypeTest.forClass(ItemEntity.class), new AABB(target).inflate(2.0D),
                    entity -> entity.getItem().is(item)).isEmpty();
        }

        private <T> T register(T listener) {
            NeoForge.EVENT_BUS.register(listener);
            eventListeners.add(listener);
            return listener;
        }

        private <T> T capture(Class<T> captureType) {
            for (Object listener : eventListeners) {
                if (captureType.isInstance(listener)) {
                    return captureType.cast(listener);
                }
            }
            throw new AssertionError("Mining fixture did not register " + captureType.getSimpleName());
        }

        private void replace(BlockPos position, BlockState state) {
            originalBlocks.putIfAbsent(position, helper.getLevel().getBlockState(position));
            helper.getLevel().setBlock(position, state, 3);
        }

        private void close() {
            for (Object listener : eventListeners) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            eventListeners.clear();
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                helper.getLevel().setBlock(entry.getKey(), entry.getValue(), 3);
            }
            WorkerGameTestSupport.discardWorker(worker);
        }
    }
}
