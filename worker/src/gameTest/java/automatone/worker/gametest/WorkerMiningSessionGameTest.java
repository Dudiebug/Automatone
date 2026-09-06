package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerEntityController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.stream.IntStream;

@GameTestHolder("automatone_worker_m4_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerMiningSessionGameTest {
    @GameTest(template = "worker_native_mining", batch = "worker_m4_quantity", timeoutTicks = 520)
    public static void finiteOneStopsAfterOneSourceBlock(GameTestHelper helper) {
        runQuantity(helper, 1, 1);
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_quantity", timeoutTicks = 520)
    public static void finiteThreeLeavesTwoOfFiveTargets(GameTestHelper helper) {
        runQuantity(helper, 3, 3);
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_quantity", timeoutTicks = 520)
    public static void unlimitedContinuesPastThreeDespiteExistingItems(GameTestHelper helper) {
        runQuantity(helper, 0, 4);
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_obstructions", timeoutTicks = 520)
    public static void stoneObstructionsDoNotCountAsRequestedOre(GameTestHelper helper) {
        runQuantity(helper, 3, 3, Blocks.IRON_ORE, true);
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_drops", timeoutTicks = 520)
    public static void multipleDropsStillCountOnlySourceBlocks(GameTestHelper helper) {
        runQuantity(helper, 3, 3, Blocks.REDSTONE_ORE, false);
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_cancel", timeoutTicks = 520)
    public static void stopAfterFirstSourceBlockPreventsFurtherMining(GameTestHelper helper) {
        runCancellation(helper, false);
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_cancel", timeoutTicks = 520)
    public static void stopDuringSlowBlockLeavesItIntact(GameTestHelper helper) {
        runCancellation(helper, true);
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_validation", timeoutTicks = 100)
    public static void busyStartAndRepeatedStopKeepOneJob(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.worker.startMining(BuiltInRegistries.BLOCK.getKey(Blocks.IRON_ORE), 3);
            MiningSession.Snapshot before = fixture.worker.miningStatus();
            boolean rejected = false;
            try {
                fixture.worker.startMining(BuiltInRegistries.BLOCK.getKey(Blocks.DIAMOND_ORE), 1);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected && before.equals(fixture.worker.miningStatus()),
                    "Busy Start must preserve the one active job");
            fixture.worker.stopMining();
            fixture.worker.stopMining();
            helper.assertTrue(fixture.worker.miningStatus().state() == MiningSession.State.CANCELLED
                            && !fixture.worker.runtime().getMineProcess().isActive(),
                    "Repeated Stop must leave native mining cancelled");
        }
        helper.succeed();
    }

    private static void runQuantity(GameTestHelper helper, int requested, int expected) {
        runQuantity(helper, requested, expected, Blocks.IRON_ORE, false);
    }

    private static void runQuantity(GameTestHelper helper, int requested, int expected, Block target, boolean obstruction) {
        Fixture fixture = new Fixture(helper, target, obstruction);
        try {
            fixture.worker.setItem(1, new ItemStack(target.asItem(), 64));
            fixture.worker.setItem(2, new ItemStack(Items.RAW_IRON, 64));
            helper.runAfterDelay(2, () -> {
                try {
                    fixture.worker.startMining(BuiltInRegistries.BLOCK.getKey(target), requested);
                    observeQuantity(helper, fixture, requested, expected, 0);
                } catch (RuntimeException | AssertionError failure) {
                    fixture.close();
                    throw failure;
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void observeQuantity(GameTestHelper helper, Fixture fixture, int requested, int expected, int ticks) {
        try {
            yieldNativeWork();
            MiningSession.Snapshot state = fixture.worker.miningStatus();
            helper.assertTrue(ticks < 480 && state.state() != MiningSession.State.FAILED,
                    "Native source-block job did not finish: " + state);
            if (state.completed() >= expected) {
                helper.assertTrue(fixture.destroyed() == state.completed(),
                        "Progress must equal actual source blocks, not inventory or drops");
                helper.assertTrue(fixture.obstructions.isEmpty() || fixture.obstructions.stream()
                                .anyMatch(pos -> fixture.worker.level().getBlockState(pos).isAir()),
                        "The obstruction case must include actual non-target destruction");
                if (fixture.target == Blocks.REDSTONE_ORE) {
                    int redstone = fixture.worker.level().getEntitiesOfClass(ItemEntity.class,
                                    fixture.worker.getBoundingBox().inflate(32), entity -> entity.getItem().is(Items.REDSTONE))
                            .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
                    for (int slot = 0; slot < fixture.worker.getContainerSize(); slot++) {
                        ItemStack stack = fixture.worker.getItem(slot);
                        if (stack.is(Items.REDSTONE)) {
                            redstone += stack.getCount();
                        }
                    }
                    helper.assertTrue(redstone >= expected * 4, "The multi-drop case must produce multiple actual items per block");
                }
                if (requested > 0) {
                    helper.assertTrue(state.state() == MiningSession.State.COMPLETED && state.completed() == requested,
                            "Finite work must complete at exactly the requested source count");
                    helper.assertTrue(!fixture.worker.runtime().getMineProcess().isActive(),
                            "The Nth destruction must synchronously cancel native mining");
                    helper.runAfterDelay(20, () -> {
                        try {
                            helper.assertTrue(fixture.destroyed() == requested,
                                    "No additional source block may break after finite completion");
                            helper.succeed();
                        } finally {
                            fixture.close();
                        }
                    });
                } else {
                    helper.assertTrue(state.state() == MiningSession.State.RUNNING,
                            "Unlimited work must continue beyond three blocks");
                    fixture.close();
                    helper.succeed();
                }
                return;
            }
            helper.runAfterDelay(1, () -> observeQuantity(helper, fixture, requested, expected, ticks + 1));
        } catch (RuntimeException | AssertionError failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void runCancellation(GameTestHelper helper, boolean duringBreak) {
        Fixture fixture = new Fixture(helper);
        if (duringBreak) {
            fixture.worker.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.05D);
        }
        fixture.worker.startMining(BuiltInRegistries.BLOCK.getKey(Blocks.IRON_ORE), 0);
        observeUntilStop(helper, fixture, duringBreak, 0);
    }

    private static void observeUntilStop(GameTestHelper helper, Fixture fixture, boolean duringBreak, int ticks) {
        try {
            yieldNativeWork();
            helper.assertTrue(ticks < 480, "Native mining never reached the cancellation checkpoint");
            WorkerEntityController controller = (WorkerEntityController) fixture.worker.runtime().getPlayerContext().playerController();
            boolean ready = duringBreak ? controller.breakProgress() > 0 && controller.breakProgress() < 1
                    : fixture.worker.miningStatus().completed() > 0;
            if (ready) {
                long destroyed = fixture.destroyed();
                if (duringBreak) {
                    helper.assertTrue(destroyed == 0, "Mid-break Stop must happen before the first source destruction");
                }
                fixture.worker.stopMining();
                fixture.worker.stopMining();
                helper.assertTrue(controller.breakingBlock() == null && controller.breakStage() == -1,
                        "Stop must clear native block damage and crack state immediately");
                observeStopped(helper, fixture, destroyed, 0);
            } else {
                helper.runAfterDelay(1, () -> observeUntilStop(helper, fixture, duringBreak, ticks + 1));
            }
        } catch (RuntimeException | AssertionError failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void observeStopped(GameTestHelper helper, Fixture fixture, long destroyed, int ticks) {
        try {
            yieldNativeWork();
            helper.assertTrue(fixture.destroyed() == destroyed
                            && fixture.worker.miningStatus().completed() == destroyed
                            && fixture.worker.miningStatus().state() == MiningSession.State.CANCELLED
                            && !fixture.worker.runtime().getMineProcess().isActive(),
                    "Cancelled work must not resume or destroy a later source block");
            if (ticks == 20) {
                fixture.close();
                helper.succeed();
            } else {
                helper.runAfterDelay(1, () -> observeStopped(helper, fixture, destroyed, ticks + 1));
            }
        } catch (RuntimeException | AssertionError failure) {
            fixture.close();
            throw failure;
        }
    }

    static void yieldNativeWork() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    static final class Fixture implements AutoCloseable {
        final WorkerNativeMineProcessGameTest.MiningChamber chamber;
        final WorkerEntity worker;
        final List<BlockPos> targets;
        final List<BlockPos> obstructions;
        final Block target;

        Fixture(GameTestHelper helper) {
            this(helper, Blocks.IRON_ORE, false);
        }

        Fixture(GameTestHelper helper, Block target, boolean obstruction) {
            this.target = target;
            chamber = new WorkerNativeMineProcessGameTest.MiningChamber(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
            chamber.build();
            targets = IntStream.range(-2, 3).mapToObj(offset -> chamber.target().offset(0, 0, offset)).toList();
            targets.forEach(pos -> chamber.replace(pos, target.defaultBlockState()));
            obstructions = obstruction ? IntStream.range(1, 8).boxed().flatMap(z -> IntStream.range(1, 5)
                    .mapToObj(y -> chamber.workerPosition().offset(4, y - 1, z - 4))).toList() : List.of();
            obstructions.forEach(pos -> chamber.replace(pos, Blocks.STONE.defaultBlockState()));
            worker = WorkerNativeMineProcessGameTest.spawnWorker(helper.getLevel(), chamber.workerPosition());
            worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        }

        long destroyed() {
            return targets.stream().filter(pos -> worker.level().getBlockState(pos).isAir()).count();
        }

        @Override
        public void close() {
            WorkerGameTestSupport.discardWorker(worker);
            chamber.clearDrops();
            chamber.restore();
        }
    }
}
