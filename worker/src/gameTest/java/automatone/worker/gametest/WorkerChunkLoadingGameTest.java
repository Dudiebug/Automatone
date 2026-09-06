package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerChunkLoading;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/** Runtime contract for explicit worker tickets and persisted worker state. */
@GameTestHolder("automatone_worker_m4_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerChunkLoadingGameTest {
    private static final ResourceLocation TARGET = ResourceLocation.withDefaultNamespace("iron_ore");

    private WorkerChunkLoadingGameTest() {
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_chunks", timeoutTicks = 100)
    public static void idleStartAndStopUseOneNineThenOneExplicitTickets(GameTestHelper helper) {
        WorkerEntity worker = null;
        try {
            worker = WorkerGameTestSupport.spawnWorker(helper);
            ChunkPos center = worker.chunkPosition();
            assertTickets(helper, worker.level(), WorkerChunkLoading.CENTER_CONTROLLER_ID, worker.getUUID(),
                    chunks(center), "Idle worker must own exactly its center ticket");
            assertTickets(helper, worker.level(), WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, worker.getUUID(),
                    new LongOpenHashSet(), "Idle worker must own no working-ring tickets");

            worker.startMining(TARGET, 1);
            assertTickets(helper, worker.level(), WorkerChunkLoading.CENTER_CONTROLLER_ID, worker.getUUID(),
                    chunks(center), "Start must retain the center ticket immediately");
            assertTickets(helper, worker.level(), WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, worker.getUUID(),
                    ring(center), "Start must request the eight working-ring tickets immediately");

            worker.stopMining();
            assertTickets(helper, worker.level(), WorkerChunkLoading.CENTER_CONTROLLER_ID, worker.getUUID(),
                    chunks(center), "Stop must leave exactly the center ticket");
            assertTickets(helper, worker.level(), WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, worker.getUUID(),
                    new LongOpenHashSet(), "Stop must release every working-ring ticket");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    // This move can enter the next template's clearing bounds; run without sibling setup.
    @GameTest(template = "worker_native_mining", batch = "worker_m4_chunk_boundary", timeoutTicks = 100)
    public static void chunkBoundaryMoveReplacesTheThreeByThreeDiff(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        try {
            ChunkPos oldCenter = worker.chunkPosition();
            worker.startMining(TARGET, 1);
            LongSet oldRing = ring(oldCenter);
            ChunkPos newCenter = new ChunkPos(oldCenter.x + 1, oldCenter.z);
            worker.moveTo(newCenter.getMiddleBlockX() + 0.5D, worker.getY(), newCenter.getMiddleBlockZ() + 0.5D,
                    worker.getYRot(), worker.getXRot());
            helper.runAfterDelay(1, () -> {
                try {
                    helper.assertTrue(worker.isAlive() && !worker.isRemoved(),
                            "Boundary fixture worker must survive neighboring structure cleanup");
                    assertTickets(helper, worker.level(), WorkerChunkLoading.CENTER_CONTROLLER_ID, worker.getUUID(),
                            chunks(newCenter), "Moving across a boundary must replace the center ticket");
                    LongSet expected = ring(newCenter);
                    assertTickets(helper, worker.level(), WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, worker.getUUID(),
                            expected, "Moving across a boundary must retain exactly the new working ring");
                    LongSet actual = tickets((ServerLevel) worker.level(),
                            WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, worker.getUUID());
                    for (long oldChunk : oldRing) {
                        if (!expected.contains(oldChunk)) {
                            helper.assertFalse(actual.contains(oldChunk),
                                    "A no-longer-overlapping working-ring ticket must be released");
                        }
                    }
                    worker.stopMining();
                    WorkerGameTestSupport.discardWorker(worker);
                    helper.succeed();
                } catch (Throwable failure) {
                    WorkerGameTestSupport.discardWorker(worker);
                    helper.fail("Chunk-boundary ticket diff failed: " + failure);
                }
            });
        } catch (Throwable failure) {
            WorkerGameTestSupport.discardWorker(worker);
            throw failure;
        }
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_chunks", timeoutTicks = 100)
    public static void overlappingWorkersKeepIndependentUuidTickets(GameTestHelper helper) {
        WorkerEntity first = null;
        WorkerEntity second = null;
        try {
            first = WorkerGameTestSupport.spawnWorker(helper);
            second = WorkerGameTestSupport.spawnWorker(helper);
            second.moveTo(first.getX(), first.getY(), first.getZ(), first.getYRot(), first.getXRot());
            ChunkPos center = first.chunkPosition();
            UUID firstUuid = first.getUUID();
            first.startMining(TARGET, 1);
            second.startMining(TARGET, 1);
            assertWorkerHasNine(helper, first, center, "First overlapping worker");
            assertWorkerHasNine(helper, second, center, "Second overlapping worker");

            WorkerGameTestSupport.discardWorker(first);
            first = null;
            assertNoTickets(helper, second.level(), firstUuid,
                    "Discarding one worker must release only that UUID's tickets");
            assertWorkerHasNine(helper, second, center,
                    "Discarding one overlapping worker must not release the other UUID's tickets");
        } finally {
            WorkerGameTestSupport.discardWorker(first);
            WorkerGameTestSupport.discardWorker(second);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_chunks", timeoutTicks = 100)
    public static void deathReleasesAllWorkerTickets(GameTestHelper helper) {
        WorkerEntity worker = null;
        try {
            worker = WorkerGameTestSupport.spawnWorker(helper);
            worker.startMining(TARGET, 1);
            assertWorkerHasNine(helper, worker, worker.chunkPosition(), "Death cleanup setup");
            DamageSource source = worker.damageSources().genericKill();
            worker.die(source);
            assertNoTickets(helper, worker.level(), worker.getUUID(),
                    "Death must release the center and working-ring tickets");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_chunks", timeoutTicks = 100)
    public static void dimensionTransferReleasesOldTicketsAndAcquiresNewCenter(GameTestHelper helper) {
        WorkerEntity worker = null;
        WorkerEntity transferred = null;
        try {
            worker = WorkerGameTestSupport.spawnWorker(helper);
            ServerLevel oldLevel = (ServerLevel) worker.level();
            worker.startMining(TARGET, 1);
            assertWorkerHasNine(helper, worker, worker.chunkPosition(), "Dimension transfer setup");
            ServerLevel nether = oldLevel.getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "GameTest server must provide the Nether for dimension lifecycle coverage");
            transferred = (WorkerEntity) worker.changeDimension(new DimensionTransition(nether,
                    new Vec3(0.5D, 80.0D, 0.5D), Vec3.ZERO, 0.0F, 0.0F, DimensionTransition.DO_NOTHING));
            helper.assertTrue(transferred != null, "Worker dimension transfer must produce a server worker");
            assertNoTickets(helper, oldLevel, worker.getUUID(),
                    "Dimension transfer must leave no center ticket in the old level");
            assertWorkerHasNine(helper, transferred, transferred.chunkPosition(),
                    "A running dimension transfer must acquire the new 3x3 tickets");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
            WorkerGameTestSupport.discardWorker(transferred);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_persistence", timeoutTicks = 100)
    public static void nbtRoundTripPreservesInventorySelectionOwnerAndRunningJob(GameTestHelper helper) {
        WorkerEntity source = null;
        try {
            source = WorkerGameTestSupport.spawnWorker(helper);
            UUID owner = UUID.randomUUID();
            source.claim(owner);
            source.setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
            source.setItem(7, new ItemStack(Items.RAW_IRON, 11));
            source.setSelectedSlot(7);
            source.startMining(TARGET, 9);
            CompoundTag saved = new CompoundTag();
            source.addAdditionalSaveData(saved);

            WorkerEntity restored = newWorker((ServerLevel) source.level());
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(restored.ownerUUID().filter(owner::equals).isPresent(),
                    "Worker owner UUID must survive the standard entity NBT round trip");
            helper.assertTrue(restored.selectedSlot() == 7 && restored.getItem(2).is(Items.DIAMOND_PICKAXE)
                            && restored.getItem(7).is(Items.RAW_IRON) && restored.getItem(7).getCount() == 11,
                    "Nine-slot inventory and selected slot must survive the standard container NBT round trip");
            MiningSession.Snapshot state = restored.miningStatus();
            helper.assertTrue(state.target().equals(TARGET.toString()) && state.requested() == 9
                            && state.completed() == 0 && state.state() == MiningSession.State.RUNNING,
                    "A saved running job must retain typed target, request, progress, and state");
        } finally {
            WorkerGameTestSupport.discardWorker(source);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m4_persistence", timeoutTicks = 100)
    public static void invalidSavedJobsFailWithoutRetryAndTerminalJobsDoNotResume(GameTestHelper helper) {
        WorkerEntity invalidSchema = null;
        WorkerEntity invalidTarget = null;
        WorkerEntity invalidState = null;
        WorkerEntity cancelled = null;
        WorkerEntity completed = null;
        WorkerEntity failed = null;
        try {
            ServerLevel level = helper.getLevel();
            invalidSchema = addLoadedWorker(level, invalidSchema());
            invalidTarget = addLoadedWorker(level, savedJob("minecraft:not_a_block", 1, 0, "RUNNING", ""));
            invalidState = addLoadedWorker(level, savedJob(TARGET.toString(), 1, 0, "NOT_A_STATE", ""));
            assertInvalidSavedJob(helper, invalidSchema, "missing schema version");
            assertInvalidSavedJob(helper, invalidTarget, "invalid target");
            assertInvalidSavedJob(helper, invalidState, "invalid state");

            cancelled = addLoadedWorker(level, savedJob(TARGET.toString(), 1, 0, "CANCELLED", ""));
            completed = addLoadedWorker(level, savedJob(TARGET.toString(), 1, 1, "COMPLETED", ""));
            failed = addLoadedWorker(level, savedJob(TARGET.toString(), 1, 0, "FAILED", "NATIVE_START_FAILED"));
            WorkerEntity finalInvalidSchema = invalidSchema;
            WorkerEntity finalInvalidTarget = invalidTarget;
            WorkerEntity finalInvalidState = invalidState;
            WorkerEntity finalCancelled = cancelled;
            WorkerEntity finalCompleted = completed;
            WorkerEntity finalFailed = failed;
            helper.runAfterDelay(2, () -> {
                try {
                    assertInvalidSavedJob(helper, finalInvalidSchema, "missing schema version");
                    assertInvalidSavedJob(helper, finalInvalidTarget, "invalid target");
                    assertInvalidSavedJob(helper, finalInvalidState, "invalid state");
                    helper.assertTrue(!finalInvalidSchema.runtime().getMineProcess().isActive()
                                    && !finalInvalidTarget.runtime().getMineProcess().isActive()
                                    && !finalInvalidState.runtime().getMineProcess().isActive(),
                            "Invalid saved jobs must not enter a native retry loop");
                    assertTerminalDidNotResume(helper, finalCancelled, MiningSession.State.CANCELLED);
                    assertTerminalDidNotResume(helper, finalCompleted, MiningSession.State.COMPLETED);
                    assertTerminalDidNotResume(helper, finalFailed, MiningSession.State.FAILED);
                    discardAll(finalInvalidSchema, finalInvalidTarget, finalInvalidState,
                            finalCancelled, finalCompleted, finalFailed);
                    helper.succeed();
                } catch (Throwable failure) {
                    discardAll(finalInvalidSchema, finalInvalidTarget, finalInvalidState,
                            finalCancelled, finalCompleted, finalFailed);
                    helper.fail("Saved-job terminal state check failed: " + failure);
                }
            });
        } catch (Throwable failure) {
            discardAll(invalidSchema, invalidTarget, invalidState, cancelled, completed, failed);
            throw failure;
        }
    }

    private static void assertWorkerHasNine(GameTestHelper helper, WorkerEntity worker, ChunkPos center, String phase) {
        assertTickets(helper, worker.level(), WorkerChunkLoading.CENTER_CONTROLLER_ID, worker.getUUID(), chunks(center),
                phase + " must own its one center ticket");
        assertTickets(helper, worker.level(), WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, worker.getUUID(), ring(center),
                phase + " must own its eight working-ring tickets");
    }

    private static void assertNoTickets(GameTestHelper helper, net.minecraft.world.level.Level level, UUID owner, String message) {
        helper.assertTrue(tickets((ServerLevel) level, WorkerChunkLoading.CENTER_CONTROLLER_ID, owner).isEmpty()
                        && tickets((ServerLevel) level, WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, owner).isEmpty(),
                message);
    }

    private static void assertTickets(
            GameTestHelper helper,
            net.minecraft.world.level.Level level,
            ResourceLocation controller,
            UUID owner,
            LongSet expected,
            String message
    ) {
        LongSet actual = tickets((ServerLevel) level, controller, owner);
        helper.assertTrue(actual.equals(expected), message + "; expected=" + expected + ", actual=" + actual);
    }

    public static LongSet tickets(ServerLevel level, ResourceLocation controller, UUID owner) {
        ForcedChunksSavedData saved = level.getDataStorage().computeIfAbsent(
                ForcedChunksSavedData.factory(), ForcedChunksSavedData.FILE_ID);
        Map<?, LongSet> all = saved.getEntityForcedChunks().getTickingChunks();
        for (Map.Entry<?, LongSet> entry : all.entrySet()) {
            if (controller.equals(ticketOwnerField(entry.getKey(), "id"))
                    && owner.equals(ticketOwnerField(entry.getKey(), "owner"))) {
                return new LongOpenHashSet(entry.getValue());
            }
        }
        return new LongOpenHashSet();
    }

    private static Object ticketOwnerField(Object ticketOwner, String fieldName) {
        try {
            Field field = ticketOwner.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(ticketOwner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("NeoForge entity ticket owner no longer exposes " + fieldName, failure);
        }
    }

    private static LongSet chunks(ChunkPos center) {
        LongSet chunks = new LongOpenHashSet();
        chunks.add(center.toLong());
        return chunks;
    }

    private static LongSet ring(ChunkPos center) {
        LongSet chunks = new LongOpenHashSet();
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int z = center.z - 1; z <= center.z + 1; z++) {
                if (x != center.x || z != center.z) {
                    chunks.add(ChunkPos.asLong(x, z));
                }
            }
        }
        return chunks;
    }

    private static WorkerEntity newWorker(ServerLevel level) {
        WorkerEntity worker = WorkerMod.WORKER.get().create(level);
        if (worker == null) {
            throw new AssertionError("Registered worker entity type did not create an entity");
        }
        return worker;
    }

    private static WorkerEntity loadWorker(ServerLevel level, CompoundTag saved) {
        WorkerEntity worker = newWorker(level);
        worker.readAdditionalSaveData(saved);
        return worker;
    }

    private static WorkerEntity addLoadedWorker(ServerLevel level, CompoundTag saved) {
        WorkerEntity worker = loadWorker(level, saved);
        BlockPos position = new BlockPos(0, 80, 0);
        worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        worker.setNoGravity(true);
        if (!level.addFreshEntity(worker)) {
            throw new AssertionError("Restored worker was rejected by the ServerLevel");
        }
        return worker;
    }

    private static CompoundTag invalidSchema() {
        CompoundTag saved = savedJob(TARGET.toString(), 1, 0, "RUNNING", "");
        saved.getCompound("AutomatoneWorker").remove("Version");
        return saved;
    }

    private static CompoundTag savedJob(String target, int requested, long completed, String state, String error) {
        CompoundTag root = new CompoundTag();
        CompoundTag worker = new CompoundTag();
        worker.putInt("Version", 1);
        worker.put("Inventory", new CompoundTag());
        worker.putInt("SelectedSlot", 0);
        CompoundTag job = new CompoundTag();
        job.putString("Target", target);
        job.putInt("Requested", requested);
        job.putLong("Completed", completed);
        job.putString("State", state);
        job.putString("Error", error);
        worker.put("Job", job);
        root.put("AutomatoneWorker", worker);
        return root;
    }

    private static void assertInvalidSavedJob(GameTestHelper helper, WorkerEntity worker, String caseName) {
        MiningSession.Snapshot state = worker.miningStatus();
        helper.assertTrue(state.state() == MiningSession.State.FAILED && state.error().equals("INVALID_SAVED_JOB"),
                "An " + caseName + " must become FAILED without a retryable RUNNING job: " + state);
    }

    private static void assertTerminalDidNotResume(
            GameTestHelper helper,
            WorkerEntity worker,
            MiningSession.State expected
    ) {
        helper.assertTrue(worker.miningStatus().state() == expected && worker.runtime() != null
                        && !worker.runtime().getMineProcess().isActive(),
                "Saved " + expected + " work must remain terminal and must not start native mining");
    }

    private static void discardAll(WorkerEntity... workers) {
        for (WorkerEntity worker : workers) {
            WorkerGameTestSupport.discardWorker(worker);
        }
    }
}
