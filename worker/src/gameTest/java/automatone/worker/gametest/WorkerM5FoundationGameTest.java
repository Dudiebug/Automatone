package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalXZ;
import baritone.pathing.calc.PathNode;
import baritone.pathing.movement.CalculationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("automatone_worker_m5_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerM5FoundationGameTest {
    private static final ResourceLocation IRON_ORE = BuiltInRegistries.BLOCK.getKey(Blocks.IRON_ORE);
    private static final ResourceLocation DEEPSLATE_IRON_ORE =
            BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_IRON_ORE);
    private static final List<ResourceLocation> MIXED_TARGETS = List.of(IRON_ORE, DEEPSLATE_IRON_ORE);
    private static final List<String> MIXED_TARGET_IDS = MIXED_TARGETS.stream()
            .map(ResourceLocation::toString)
            .toList();
    private static final int MAX_NATIVE_OBSERVATION_TICKS = 600;

    private WorkerM5FoundationGameTest() {
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_multi_target", timeoutTicks = 900)
    public static void finiteMixedTargetsStopAtExactAggregateCount(GameTestHelper helper) {
        MixedMiningFixture fixture = null;
        try {
            fixture = MixedMiningFixture.create(helper);
            fixture.worker.startMining(MIXED_TARGETS, 3);
            observeCompletion(helper, fixture, fixture.worker, fixture.worker.miningStatus().runId(), 0,
                    "Finite mixed-target mining");
        } catch (Throwable failure) {
            close(fixture);
            helper.fail("Finite mixed-target setup failed: " + failure);
        }
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_pause", timeoutTicks = 1000)
    public static void pauseAfterOneBreakSuppressesNativeWorkUntilResume(GameTestHelper helper) {
        MixedMiningFixture fixture = null;
        try {
            fixture = MixedMiningFixture.create(helper);
            fixture.worker.startMining(MIXED_TARGETS, 3);
            observeFirstBreakThenPause(helper, fixture, 0);
        } catch (Throwable failure) {
            close(fixture);
            helper.fail("Pause/resume setup failed: " + failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_settings", timeoutTicks = 100)
    public static void workerSettingsAndCalculationSnapshotsRemainIsolated(GameTestHelper helper) {
        WorkerEntity first = null;
        WorkerEntity second = null;
        try {
            first = WorkerGameTestSupport.spawnWorker(helper);
            second = WorkerGameTestSupport.spawnWorker(helper);
            first.setItem(1, new ItemStack(Items.DIRT, 8));
            second.setItem(1, new ItemStack(Items.DIRT, 8));

            Settings firstInput = BaritoneAPI.getSettings().copy();
            firstInput.allowBreak.value = false;
            firstInput.allowPlace.value = true;
            firstInput.costHeuristic.value = 2.0D;
            firstInput.allowBreakAnyway.value = new ArrayList<>(List.of(Blocks.IRON_ORE));
            firstInput.buildSubstitutes.value.put(Blocks.STONE, new ArrayList<>(List.of(Blocks.DIRT)));
            Settings secondInput = BaritoneAPI.getSettings().copy();
            secondInput.allowBreak.value = true;
            secondInput.allowPlace.value = false;
            secondInput.costHeuristic.value = 4.0D;
            secondInput.allowBreakAnyway.value = new ArrayList<>(List.of(Blocks.DEEPSLATE_IRON_ORE));
            first.applySettings(firstInput);
            second.applySettings(secondInput);

            firstInput.allowBreak.value = true;
            firstInput.allowPlace.value = false;
            firstInput.allowBreakAnyway.value.clear();
            firstInput.buildSubstitutes.value.get(Blocks.STONE).clear();
            helper.assertTrue(!first.runtime().getSettings().allowBreak.value
                            && first.runtime().getSettings().allowPlace.value
                            && first.runtime().getSettings().allowBreakAnyway.value.equals(List.of(Blocks.IRON_ORE))
                            && first.runtime().getSettings().buildSubstitutes.value.get(Blocks.STONE).equals(List.of(Blocks.DIRT)),
                    "Applying settings must copy incoming booleans and mutable allowBreakAnyway values");

            CalculationContext firstBeforeChange = new CalculationContext(first.runtime(), true);
            CalculationContext secondBeforeChange = new CalculationContext(second.runtime(), true);
            helper.assertTrue(!firstBeforeChange.allowBreak && firstBeforeChange.hasThrowaway
                            && firstBeforeChange.allowBreakAnyway.equals(List.of(Blocks.IRON_ORE)),
                    "First async calculation snapshot must capture its opposing break/place settings");
            helper.assertTrue(secondBeforeChange.allowBreak && !secondBeforeChange.hasThrowaway
                            && secondBeforeChange.allowBreakAnyway.equals(List.of(Blocks.DEEPSLATE_IRON_ORE)),
                    "Second async calculation snapshot must capture independent break/place settings");

            Settings changedFirst = BaritoneAPI.getSettings().copy();
            changedFirst.allowBreak.value = true;
            changedFirst.allowPlace.value = false;
            changedFirst.allowBreakAnyway.value = new ArrayList<>(List.of(Blocks.STONE));
            first.applySettings(changedFirst);
            changedFirst.allowBreakAnyway.value.add(Blocks.DIRT);

            CalculationContext firstAfterChange = new CalculationContext(first.runtime(), true);
            GoalXZ tenBlocksAway = new GoalXZ(10, 0);
            helper.assertTrue(new PathNode(0, 0, 0, tenBlocksAway, firstBeforeChange.settings).estimatedCostToGoal == 20.0D
                            && new PathNode(0, 0, 0, tenBlocksAway, secondBeforeChange.settings).estimatedCostToGoal == 40.0D,
                    "Delayed path-node evaluation must use each captured heuristic after another profile is applied");
            helper.assertTrue(firstAfterChange.allowBreak && !firstAfterChange.hasThrowaway
                            && firstAfterChange.allowBreakAnyway.equals(List.of(Blocks.STONE)),
                    "First runtime must adopt only its new copied settings");
            helper.assertTrue(!firstBeforeChange.allowBreak && firstBeforeChange.hasThrowaway
                            && firstBeforeChange.allowBreakAnyway.equals(List.of(Blocks.IRON_ORE)),
                    "An existing calculation snapshot must remain unchanged after replanning");
            helper.assertTrue(second.runtime().getSettings().allowBreak.value
                            && !second.runtime().getSettings().allowPlace.value
                            && second.runtime().getSettings().allowBreakAnyway.value.equals(List.of(Blocks.DEEPSLATE_IRON_ORE))
                            && secondBeforeChange.allowBreak && !secondBeforeChange.hasThrowaway
                            && secondBeforeChange.allowBreakAnyway.equals(List.of(Blocks.DEEPSLATE_IRON_ORE)),
                    "Changing one worker must not alter the other runtime or its async snapshot");
        } catch (Throwable failure) {
            helper.fail("Worker settings isolation failed: " + failure);
        } finally {
            WorkerGameTestSupport.discardWorker(first);
            WorkerGameTestSupport.discardWorker(second);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_persistence", timeoutTicks = 900)
    public static void pausedMultiTargetSaveReloadAndLegacyV1ConversionRequireExplicitResume(GameTestHelper helper) {
        MixedMiningFixture fixture = null;
        try {
            fixture = MixedMiningFixture.create(helper);
            fixture.worker.startMining(MIXED_TARGETS, 3);
            observeFirstBreakForSaveReload(helper, fixture, 0);
        } catch (Throwable failure) {
            close(fixture);
            helper.fail("Paused save/reload setup failed: " + failure);
        }
    }

    private static void observeCompletion(
            GameTestHelper helper,
            MixedMiningFixture fixture,
            WorkerEntity worker,
            UUID expectedRunId,
            int ticks,
            String phase
    ) {
        try {
            WorkerMiningSessionGameTest.yieldNativeWork();
            MiningSession.Snapshot state = worker.miningStatus();
            long destroyed = fixture.destroyed(worker);
            helper.assertTrue(state.state() != MiningSession.State.FAILED,
                    phase + " entered FAILED: " + state);
            helper.assertTrue(state.completed() == destroyed,
                    phase + " progress must equal actual source destruction; state=" + state + ", destroyed=" + destroyed);
            helper.assertTrue(expectedRunId != null && expectedRunId.equals(state.runId()),
                    phase + " must retain one run identity: " + state);
            if (state.state() == MiningSession.State.COMPLETED) {
                helper.assertTrue(state.completed() == 3 && destroyed == 3,
                        phase + " must stop at exactly three of four mixed targets: " + state
                                + ", destroyed=" + destroyed);
                helper.assertTrue(fixture.destroyedBy(worker, Blocks.IRON_ORE) > 0
                                && fixture.destroyedBy(worker, Blocks.DEEPSLATE_IRON_ORE) > 0,
                        phase + " must destroy both registered target block types");
                helper.assertFalse(worker.runtime().getMineProcess().isActive(),
                        phase + " must synchronously stop the native MineProcess at the finite limit");
                helper.runAfterDelay(20, () -> {
                    try {
                        helper.assertTrue(fixture.destroyed(worker) == 3
                                        && worker.miningStatus().completed() == 3
                                        && worker.miningStatus().state() == MiningSession.State.COMPLETED,
                                phase + " must not destroy a fourth source after completion");
                        close(fixture);
                        helper.succeed();
                    } catch (Throwable failure) {
                        close(fixture);
                        helper.fail(phase + " completion observation failed: " + failure);
                    }
                });
                return;
            }
            helper.assertTrue(state.state() == MiningSession.State.RUNNING && ticks < MAX_NATIVE_OBSERVATION_TICKS,
                    phase + " did not reach finite completion: " + state + ", destroyed=" + destroyed);
            helper.runAfterDelay(1, () -> observeCompletion(helper, fixture, worker, expectedRunId, ticks + 1, phase));
        } catch (Throwable failure) {
            close(fixture);
            helper.fail(phase + " observation failed: " + failure);
        }
    }

    private static void observeFirstBreakThenPause(GameTestHelper helper, MixedMiningFixture fixture, int ticks) {
        try {
            WorkerMiningSessionGameTest.yieldNativeWork();
            MiningSession.Snapshot running = fixture.worker.miningStatus();
            long destroyed = fixture.destroyed(fixture.worker);
            helper.assertTrue(running.state() == MiningSession.State.RUNNING && running.completed() == destroyed,
                    "Native mixed-target job must still be running before its first pause checkpoint: " + running);
            if (running.completed() == 1) {
                UUID runId = running.runId();
                fixture.worker.pauseMining();
                MiningSession.Snapshot paused = fixture.worker.miningStatus();
                helper.assertTrue(paused.state() == MiningSession.State.PAUSED
                                && paused.completed() == 1
                                && runId.equals(paused.runId()),
                        "Pause must retain one completed source and the run identity: " + paused);
                helper.assertFalse(fixture.worker.runtime().getMineProcess().isActive(),
                        "Pause must cancel native work synchronously");
                Settings pausedSettings = fixture.worker.effectiveSettings().copy();
                pausedSettings.allowParkour.value = !pausedSettings.allowParkour.value;
                fixture.worker.applySettings(pausedSettings);
                helper.assertTrue(fixture.worker.miningStatus().equals(paused),
                        "Applying settings to paused work must retain its paused state and progress");
                helper.runAfterDelay(20, () -> {
                    try {
                        MiningSession.Snapshot stillPaused = fixture.worker.miningStatus();
                        helper.assertTrue(stillPaused.state() == MiningSession.State.PAUSED
                                        && stillPaused.completed() == 1
                                        && fixture.destroyed(fixture.worker) == 1
                                        && runId.equals(stillPaused.runId())
                                        && !fixture.worker.runtime().getMineProcess().isActive(),
                                "Paused native work must remain stopped for 20 server ticks: " + stillPaused);
                        fixture.worker.resumeMining();
                        MiningSession.Snapshot resumed = fixture.worker.miningStatus();
                        helper.assertTrue(resumed.state() == MiningSession.State.RUNNING
                                        && resumed.completed() == 1
                                        && runId.equals(resumed.runId()),
                                "Resume must retain the paused progress and run identity: " + resumed);
                        Settings replanned = fixture.worker.effectiveSettings().copy();
                        replanned.allowParkour.value = !replanned.allowParkour.value;
                        fixture.worker.applySettings(replanned);
                        helper.assertTrue(fixture.worker.miningStatus().state() == MiningSession.State.RUNNING
                                        && fixture.worker.miningStatus().completed() == 1
                                        && runId.equals(fixture.worker.miningStatus().runId()),
                                "Applying settings to running work must replan without resetting progress or run identity");
                        observeCompletion(helper, fixture, fixture.worker, runId, 0,
                                "Resumed mixed-target mining");
                    } catch (Throwable failure) {
                        close(fixture);
                        helper.fail("Pause/resume observation failed: " + failure);
                    }
                });
                return;
            }
            helper.assertTrue(running.completed() == 0 && ticks < MAX_NATIVE_OBSERVATION_TICKS,
                    "Native mixed-target job passed the one-break pause checkpoint: " + running);
            helper.runAfterDelay(1, () -> observeFirstBreakThenPause(helper, fixture, ticks + 1));
        } catch (Throwable failure) {
            close(fixture);
            helper.fail("Unable to observe the first mixed-target break: " + failure);
        }
    }

    private static void observeFirstBreakForSaveReload(GameTestHelper helper, MixedMiningFixture fixture, int ticks) {
        try {
            WorkerMiningSessionGameTest.yieldNativeWork();
            MiningSession.Snapshot running = fixture.worker.miningStatus();
            helper.assertTrue(running.state() == MiningSession.State.RUNNING
                            && running.completed() == fixture.destroyed(fixture.worker),
                    "Save/reload job must be running with source-aligned progress: " + running);
            if (running.completed() == 1) {
                UUID runId = running.runId();
                fixture.worker.pauseMining();
                MiningSession.Snapshot paused = fixture.worker.miningStatus();
                helper.assertTrue(paused.state() == MiningSession.State.PAUSED
                                && paused.completed() == 1
                                && runId.equals(paused.runId()),
                        "Saved job must pause after one source break with its run identity: " + paused);
                CompoundTag saved = new CompoundTag();
                fixture.worker.addAdditionalSaveData(saved);
                WorkerGameTestSupport.discardWorker(fixture.worker);

                ServerLevel level = (ServerLevel) fixture.worker.level();
                WorkerEntity restored = loadWorker(level, saved, fixture.worker.blockPosition(), false);
                fixture.trackReloaded(restored);
                WorkerEntity legacy = loadWorker(level, legacyV1CancelledJob(IRON_ORE),
                        fixture.worker.blockPosition().offset(0, 0, 2), true);
                fixture.trackReloaded(legacy);
                WorkerEntity finalRestored = restored;
                WorkerEntity finalLegacy = legacy;
                helper.runAfterDelay(20, () -> {
                    try {
                        MiningSession.Snapshot restoredState = finalRestored.miningStatus();
                        helper.assertTrue(restoredState.state() == MiningSession.State.PAUSED
                                        && restoredState.targets().equals(MIXED_TARGET_IDS)
                                        && restoredState.requested() == 3
                                        && restoredState.completed() == 1
                                        && runId.equals(restoredState.runId())
                                        && fixture.destroyed(finalRestored) == 1
                                        && !finalRestored.runtime().getMineProcess().isActive(),
                                "Paused V2 reload must retain targets, progress, run identity, and no native work: "
                                        + restoredState);
                        MiningSession.Snapshot legacyState = finalLegacy.miningStatus();
                        helper.assertTrue(legacyState.targets().equals(List.of(IRON_ORE.toString()))
                                        && legacyState.target().equals(IRON_ORE.toString())
                                        && legacyState.requested() == 1
                                        && legacyState.completed() == 0
                                        && legacyState.state() == MiningSession.State.CANCELLED
                                        && legacyState.runId() != null
                                        && !finalLegacy.runtime().getMineProcess().isActive(),
                                "Legacy V1 target must convert to a cancelled singleton target without native resume: "
                                        + legacyState);

                        finalRestored.resumeMining();
                        observeExplicitResume(helper, fixture, finalRestored, finalLegacy, runId, 0);
                    } catch (Throwable failure) {
                        close(fixture);
                        helper.fail("Paused save/reload observation failed: " + failure);
                    }
                });
                return;
            }
            helper.assertTrue(running.completed() == 0 && ticks < MAX_NATIVE_OBSERVATION_TICKS,
                    "Save/reload job passed the one-break checkpoint: " + running);
            helper.runAfterDelay(1, () -> observeFirstBreakForSaveReload(helper, fixture, ticks + 1));
        } catch (Throwable failure) {
            close(fixture);
            helper.fail("Unable to observe the save/reload checkpoint: " + failure);
        }
    }

    private static void observeExplicitResume(
            GameTestHelper helper,
            MixedMiningFixture fixture,
            WorkerEntity restored,
            WorkerEntity legacy,
            UUID runId,
            int ticks
    ) {
        try {
            WorkerMiningSessionGameTest.yieldNativeWork();
            MiningSession.Snapshot state = restored.miningStatus();
            helper.assertTrue(runId.equals(state.runId()) && state.completed() == fixture.destroyed(restored),
                    "Explicit resume must retain the saved run identity and source progress: " + state);
            if (restored.runtime().getMineProcess().isActive() || state.completed() > 1) {
                helper.assertTrue(state.state() == MiningSession.State.RUNNING
                                || state.state() == MiningSession.State.COMPLETED,
                        "Explicit resume must enter native work without failing: " + state);
                helper.assertTrue(legacy.miningStatus().state() == MiningSession.State.CANCELLED
                                && !legacy.runtime().getMineProcess().isActive(),
                        "Unresumed legacy work must remain terminal while another worker resumes");
                restored.stopMining();
                close(fixture);
                helper.succeed();
                return;
            }
            helper.assertTrue(state.state() == MiningSession.State.RUNNING && ticks < 40,
                    "Explicit resume did not start native work: " + state);
            helper.runAfterDelay(1, () -> observeExplicitResume(helper, fixture, restored, legacy, runId, ticks + 1));
        } catch (Throwable failure) {
            close(fixture);
            helper.fail("Explicit resume observation failed: " + failure);
        }
    }

    private static WorkerEntity loadWorker(ServerLevel level, CompoundTag saved, BlockPos position, boolean noGravity) {
        WorkerEntity worker = WorkerMod.WORKER.get().create(level);
        if (worker == null) {
            throw new AssertionError("Registered worker entity type did not create a reload worker");
        }
        worker.readAdditionalSaveData(saved);
        worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        worker.setNoGravity(noGravity);
        if (!level.addFreshEntity(worker)) {
            throw new AssertionError("Reload worker was rejected by the ServerLevel");
        }
        return worker;
    }

    private static CompoundTag legacyV1CancelledJob(ResourceLocation target) {
        CompoundTag root = new CompoundTag();
        CompoundTag worker = new CompoundTag();
        worker.putInt("Version", 1);
        worker.put("Inventory", new CompoundTag());
        worker.putInt("SelectedSlot", 0);
        CompoundTag job = new CompoundTag();
        job.putString("Target", target.toString());
        job.putInt("Requested", 1);
        job.putLong("Completed", 0);
        job.putString("State", MiningSession.State.CANCELLED.name());
        job.putString("Error", "");
        worker.put("Job", job);
        root.put("AutomatoneWorker", worker);
        return root;
    }

    private static void close(MixedMiningFixture fixture) {
        if (fixture != null) {
            fixture.close();
        }
    }

    static final class MixedMiningFixture implements AutoCloseable {
        final WorkerMiningSessionGameTest.Fixture base;
        final WorkerEntity worker;
        final List<BlockPos> targets;
        final List<BlockPos> ironTargets;
        final List<BlockPos> deepslateTargets;
        private final List<WorkerEntity> reloadedWorkers = new ArrayList<>();
        private boolean closed;

        private MixedMiningFixture(WorkerMiningSessionGameTest.Fixture base) {
            this.base = base;
            worker = base.worker;
            targets = new ArrayList<>(base.targets.subList(0, 4));
            ironTargets = new ArrayList<>();
            deepslateTargets = new ArrayList<>();
            for (int index = 0; index < targets.size(); index++) {
                Block block = index % 2 == 0 ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
                base.chamber.replace(targets.get(index), block.defaultBlockState());
                if (block.equals(Blocks.IRON_ORE)) {
                    ironTargets.add(targets.get(index));
                } else {
                    deepslateTargets.add(targets.get(index));
                }
            }
            base.chamber.replace(base.targets.get(4), Blocks.AIR.defaultBlockState());
        }

        static MixedMiningFixture create(GameTestHelper helper) {
            WorkerMiningSessionGameTest.Fixture base = null;
            try {
                base = new WorkerMiningSessionGameTest.Fixture(helper);
                return new MixedMiningFixture(base);
            } catch (Throwable failure) {
                if (base != null) {
                    base.close();
                }
                throw failure;
            }
        }

        long destroyed(WorkerEntity owner) {
            return targets.stream().filter(pos -> owner.level().getBlockState(pos).isAir()).count();
        }

        long destroyedBy(WorkerEntity owner, Block block) {
            List<BlockPos> positions = block.equals(Blocks.IRON_ORE) ? ironTargets : deepslateTargets;
            return positions.stream().filter(pos -> owner.level().getBlockState(pos).isAir()).count();
        }

        void trackReloaded(WorkerEntity worker) {
            reloadedWorkers.add(worker);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                reloadedWorkers.forEach(WorkerGameTestSupport::discardWorker);
                base.close();
            }
        }
    }
}
