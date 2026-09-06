package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerChunkLoading;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerRelocation;
import automatone.worker.WorkerRoster;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/** Dedicated-server tests for bounded deployment, relocation, and reactivation. */
@GameTestHolder("automatone_worker_m5_relocation_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerRelocationGameTest {
    private static final ResourceLocation IRON_ORE = ResourceLocation.withDefaultNamespace("iron_ore");
    private static final ResourceLocation WORKING_RING = WorkerChunkLoading.WORKING_RING_CONTROLLER_ID;
    private static final ResourceLocation CENTER = WorkerChunkLoading.CENTER_CONTROLLER_ID;

    private WorkerRelocationGameTest() {
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_relocation_deploy", timeoutTicks = 300)
    public static void deploymentReplaysCapsAndReactivatesWithEmptyOrArchivedInventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorkerRoster roster = WorkerRoster.get(level.getServer());
        BlockFixture fixture = new BlockFixture();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos column = new BlockPos(anchor.getX(), 80, anchor.getZ());
        prepareColumn(fixture, level, column, Blocks.STONE.defaultBlockState());
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        WorkerRelocation service = relocation(level, roster, column, new WorkerRelocation.Limits(8, 120, 40));
        WorkerEntity[] current = new WorkerEntity[1];
        try {
            WorkerRelocation.Status pending = service.deploy(owner, request, Level.OVERWORLD, null);
            fixture.trackPending(service, owner, request);
            helper.assertTrue(pending.state() == WorkerRelocation.State.PENDING,
                    "A deployment must start in PENDING state");
            helper.assertTrue(pending.worker() != null, "A deployment must reserve a worker identity immediately");
            helper.assertTrue(service.deploy(owner, request, Level.OVERWORLD, null).equals(pending),
                    "Replaying one deployment request must return the original pending status");
            awaitTerminal(helper, service, owner, request, 120, fixture, status -> {
                try {
                    helper.assertTrue(status.state() == WorkerRelocation.State.SUCCEEDED,
                            "Prepared Overworld deployment must succeed: " + status);
                    current[0] = roster.active(owner, status.worker());
                    fixture.track(current[0]);
                    helper.assertTrue(Objects.equals(current[0].level(), level)
                                    && current[0].blockPosition().getX() == column.getX()
                                    && current[0].blockPosition().getZ() == column.getZ()
                                    && WorkerRelocation.isSafe(level, current[0].blockPosition()),
                            "Deployment must commit the reserved worker at a safe in-border destination");
                    helper.assertTrue(isEmpty(current[0]) && current[0].getMainHandItem().isEmpty(),
                            "A new worker must expose 36 empty inventory slots and no starter equipment");
                    helper.assertTrue(preparationTickets(level, request) == 0,
                            "A completed deployment must release its preparation ticket");
                    helper.assertTrue(service.deploy(owner, request, Level.OVERWORLD, null).equals(status),
                            "Replaying a completed deployment must not create a second worker");

                    List<UUID> capped = new ArrayList<>();
                    try {
                        for (int index = 0; index < WorkerRelocation.MAX_CONCURRENT; index++) {
                            UUID cappedRequest = UUID.randomUUID();
                            capped.add(cappedRequest);
                            WorkerRelocation.Status cappedStatus = service.deploy(owner, cappedRequest,
                                    Level.OVERWORLD, null);
                            fixture.trackPending(service, owner, cappedRequest);
                            helper.assertTrue(cappedStatus.state() == WorkerRelocation.State.PENDING,
                                    "Each available search slot must accept one pending deployment");
                        }
                        expectFailure(helper, () -> service.deploy(owner, UUID.randomUUID(), Level.OVERWORLD, null),
                                "DESTINATION_SEARCH_BUSY", "A fifth concurrent destination search must be rejected");
                        helper.assertTrue(service.pendingRequests(owner).size() == WorkerRelocation.MAX_CONCURRENT,
                                "Concurrent search cap must count pending requests exactly");
                        helper.assertTrue(service.deploy(owner, capped.get(0), Level.OVERWORLD, null)
                                        .equals(service.status(owner, capped.get(0))),
                                "Replaying one capped request must not consume another search slot");
                    } finally {
                        for (UUID cappedRequest : capped) {
                            cancelIfPending(service, owner, cappedRequest);
                        }
                    }

                    current[0].setItem(0, new ItemStack(Items.DIRT, 4));
                    current[0].setItem(WorkerEntity.INVENTORY_SIZE - 1, new ItemStack(Items.EMERALD, 6));
                    UUID workerId = current[0].getUUID();
                    long revision = roster.view(owner, workerId).revision();
                    roster.retire(owner, workerId, revision);
                    helper.assertTrue(roster.list(owner, true).stream()
                                    .anyMatch(view -> view.worker().equals(workerId)),
                            "Retirement must leave the worker in the owner's archive");
                    UUID reactivationRequest = UUID.randomUUID();
                    WorkerRelocation.Status reactivation = service.deploy(owner, reactivationRequest,
                            Level.OVERWORLD, workerId);
                    fixture.trackPending(service, owner, reactivationRequest);
                    helper.assertTrue(reactivation.state() == WorkerRelocation.State.PENDING
                                    && reactivation.worker().equals(workerId),
                            "Reactivation must reserve the archived worker identity");
                    awaitTerminal(helper, service, owner, reactivationRequest, 120, fixture, restored -> {
                        try {
                            helper.assertTrue(restored.state() == WorkerRelocation.State.SUCCEEDED,
                                    "A safe archived-worker reactivation must succeed: " + restored);
                            current[0] = roster.active(owner, workerId);
                            fixture.track(current[0]);
                            helper.assertTrue(current[0].getUUID().equals(workerId)
                                            && current[0].getItem(0).is(Items.DIRT)
                                            && current[0].getItem(0).getCount() == 4
                                            && current[0].getItem(WorkerEntity.INVENTORY_SIZE - 1).is(Items.EMERALD)
                                            && current[0].getItem(WorkerEntity.INVENTORY_SIZE - 1).getCount() == 6
                                            && isEmptyExcept(current[0], 0, WorkerEntity.INVENTORY_SIZE - 1),
                                    "Reactivation must restore the same identity and remaining inventory without grants");
                            helper.assertTrue(preparationTickets(level, reactivationRequest) == 0,
                                    "A completed reactivation must release its preparation ticket");
                            finish(helper, fixture, null);
                        } catch (Throwable failure) {
                            finish(helper, fixture, failure);
                        }
                    });
                } catch (Throwable failure) {
                    finish(helper, fixture, failure);
                }
            });
        } catch (Throwable failure) {
            finish(helper, fixture, failure);
        }
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_relocation_cancel", timeoutTicks = 160)
    public static void cancellationLeavesOriginalWorkerPausedAndCleansPreparation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorkerRoster roster = WorkerRoster.get(level.getServer());
        BlockFixture fixture = new BlockFixture();
        BlockPos anchor = helper.absolutePos(new BlockPos(32, 0, 4));
        BlockPos column = new BlockPos(anchor.getX(), 80, anchor.getZ());
        prepareColumn(fixture, level, column, Blocks.STONE.defaultBlockState());
        UUID owner = UUID.randomUUID();
        WorkerEntity worker = null;
        UUID request = UUID.randomUUID();
        try {
            worker = directWorker(roster, owner, level, initialFeet(helper));
            fixture.track(worker);
            worker.setItem(2, new ItemStack(Items.COBBLESTONE, 5));
            worker.setItem(WorkerEntity.INVENTORY_SIZE - 1, new ItemStack(Items.EMERALD, 6));
            worker.setSelectedSlot(2);
            worker.startMining(IRON_ORE, 4);
            MiningSession.Snapshot running = worker.miningStatus();
            UUID workerId = worker.getUUID();
            Vec3 originalPosition = worker.position();
            ChunkPos originalChunk = worker.chunkPosition();
            helper.assertTrue(running.state() == MiningSession.State.RUNNING,
                    "Relocation cancellation setup must have a running product job");
            WorkerRelocation service = relocation(level, roster, column, new WorkerRelocation.Limits(8, 120, 40));
            long revision = roster.view(owner, worker.getUUID()).revision();
            expectFailure(helper, () -> service.relocate(UUID.randomUUID(), UUID.randomUUID(), workerId,
                    revision, Level.OVERWORLD), "NOT_OWNER", "A stranger must not relocate the worker");
            expectFailure(helper, () -> service.relocate(owner, UUID.randomUUID(), workerId,
                    revision - 1, Level.OVERWORLD), "STALE_REVISION", "A stale roster revision must be rejected");
            expectFailure(helper, () -> service.deploy(owner, UUID.randomUUID(), Level.END, null),
                    "INVALID_DIMENSION", "The End must be rejected as a relocation destination");
            WorkerRelocation.Status pending = service.relocate(owner, request, workerId, revision,
                    Level.OVERWORLD);
            fixture.trackPending(service, owner, request);
            helper.assertTrue(pending.state() == WorkerRelocation.State.PENDING
                            && worker.miningStatus().state() == MiningSession.State.PAUSED
                            && worker.miningStatus().completed() == running.completed()
                            && worker.miningStatus().runId().equals(running.runId()),
                    "Relocation must pause immediately while retaining progress and run identity");
            helper.assertTrue(worker.runtime() != null && !worker.runtime().getMineProcess().isActive(),
                    "Immediate relocation pause must cancel native work synchronously");
            helper.assertTrue(WorkerChunkLoadingGameTest.tickets(level, WORKING_RING, worker.getUUID()).isEmpty(),
                    "Immediate relocation pause must release the eight working-ring tickets");
            service.tick();
            helper.assertTrue(preparationTickets(level, request) > 0,
                    "The first relocation tick must install a real preparation ticket");
            WorkerRelocation.Status cancelled = service.cancel(owner, request);
            helper.assertTrue(cancelled.state() == WorkerRelocation.State.CANCELLED
                            && service.pendingRequests(owner).isEmpty()
                            && preparationTickets(level, request) == 0,
                    "Cancellation before generation must finish the request and release preparation state");
            helper.assertTrue(worker.isAlive() && !worker.isRemoved() && Objects.equals(worker.level(), level)
                            && worker.position().equals(originalPosition)
                            && worker.chunkPosition().equals(originalChunk)
                            && worker.miningStatus().state() == MiningSession.State.PAUSED
                            && worker.miningStatus().runId().equals(running.runId())
                            && worker.getItem(2).is(Items.COBBLESTONE)
                            && worker.getItem(2).getCount() == 5
                            && worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).is(Items.EMERALD)
                            && worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).getCount() == 6
                            && worker.selectedSlot() == 2,
                    "Cancelled relocation must leave the original paused worker, position, inventory and run intact");
            finish(helper, fixture, null);
        } catch (Throwable failure) {
            finish(helper, fixture, failure);
        }
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_relocation_nether", timeoutTicks = 300)
    public static void netherRelocationCreatesFreshRuntimeAndPreservesPausedState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        BlockFixture fixture = new BlockFixture();
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        if (nether == null) {
            helper.fail("Dedicated GameTest server did not provide the Nether");
            return;
        }
        BlockPos column = new BlockPos(4, 80, 4);
        prepareColumn(fixture, nether, column, Blocks.NETHERRACK.defaultBlockState());
        try {
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            WorkerEntity worker = directWorker(roster, owner, level, initialFeet(helper));
            fixture.track(worker);
            worker.setItem(3, new ItemStack(Items.REDSTONE, 9));
            worker.setItem(WorkerEntity.INVENTORY_SIZE - 1, new ItemStack(Items.EMERALD, 6));
            worker.setSelectedSlot(3);
            worker.startMining(IRON_ORE, 2);
            UUID workerId = worker.getUUID();
            UUID runId = worker.miningStatus().runId();
            WorkerRelocation service = relocation(level, roster, column, new WorkerRelocation.Limits(8, 160, 50));
            WorkerRelocation.Status pending = service.relocate(owner, request, workerId,
                    roster.view(owner, workerId).revision(), Level.NETHER);
            fixture.trackPending(service, owner, request);
            helper.assertTrue(pending.state() == WorkerRelocation.State.PENDING
                            && worker.miningStatus().state() == MiningSession.State.PAUSED,
                    "Nether relocation must pause the source before cross-dimension preparation");
            WorkerEntity original = worker;
            ServerLevel sourceLevel = level;
            awaitTerminal(helper, service, owner, request, 180, fixture, status -> {
                try {
                    helper.assertTrue(status.state() == WorkerRelocation.State.SUCCEEDED,
                            "A prepared Nether chamber must accept relocation: " + status);
                    WorkerEntity moved = roster.active(owner, workerId);
                    fixture.track(moved);
                    helper.assertTrue(moved != original && original.isRemoved() && original.runtime() == null
                                    && moved.getUUID().equals(workerId)
                                    && Objects.equals(moved.level(), nether)
                                    && moved.runtime() != null
                                    && moved.blockPosition().getX() == column.getX()
                                    && moved.blockPosition().getZ() == column.getZ(),
                            "Cross-dimension relocation must replace the source with the same identity and a fresh runtime");
                    helper.assertTrue(moved.miningStatus().state() == MiningSession.State.PAUSED
                                    && moved.miningStatus().runId().equals(runId)
                                    && moved.miningStatus().completed() == 0
                                    && moved.getItem(3).is(Items.REDSTONE)
                                    && moved.getItem(3).getCount() == 9
                                    && moved.getItem(WorkerEntity.INVENTORY_SIZE - 1).is(Items.EMERALD)
                                    && moved.getItem(WorkerEntity.INVENTORY_SIZE - 1).getCount() == 6
                                    && moved.selectedSlot() == 3,
                            "Cross-dimension relocation must preserve paused job state, progress and inventory");
                    helper.assertTrue(WorkerChunkLoadingGameTest.tickets(sourceLevel, CENTER, workerId).isEmpty()
                                    && WorkerChunkLoadingGameTest.tickets(sourceLevel, WORKING_RING, workerId).isEmpty()
                                    && WorkerChunkLoadingGameTest.tickets(nether, CENTER, workerId)
                                    .equals(singletonChunk(moved.chunkPosition()))
                                    && WorkerChunkLoadingGameTest.tickets(nether, WORKING_RING, workerId).isEmpty()
                                    && preparationTickets(nether, request) == 0,
                            "Cross-dimension relocation must release source and preparation tickets");
                    finish(helper, fixture, null);
                } catch (Throwable failure) {
                    finish(helper, fixture, failure);
                }
            });
        } catch (Throwable failure) {
            finish(helper, fixture, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_relocation_timeout", timeoutTicks = 220)
    public static void timeoutAndNoSafeAttemptsCancelReservationsAndPreparation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorkerRoster roster = WorkerRoster.get(level.getServer());
        BlockFixture fixture = new BlockFixture();
        UUID timeoutOwner = UUID.randomUUID();
        UUID timeoutRequest = UUID.randomUUID();
        BlockPos timeoutColumn = helper.absolutePos(new BlockPos(96, 0, 96));
        WorkerRelocation timeoutService = relocation(level, roster, timeoutColumn,
                new WorkerRelocation.Limits(8, 1, 40));
        try {
            WorkerRelocation.Status pending = timeoutService.deploy(timeoutOwner, timeoutRequest,
                    Level.OVERWORLD, null);
            fixture.trackPending(timeoutService, timeoutOwner, timeoutRequest);
            timeoutService.tick();
            helper.assertTrue(pending.state() == WorkerRelocation.State.PENDING
                            && preparationTickets(level, timeoutRequest) > 0,
                    "Timeout setup must install a pending real preparation ticket");
            helper.runAfterDelay(1, () -> {
                try {
                    timeoutService.tick();
                    WorkerRelocation.Status timeout = timeoutService.status(timeoutOwner, timeoutRequest);
                    helper.assertTrue(timeout.state() == WorkerRelocation.State.FAILED
                                    && timeout.error().equals("DESTINATION_TIMEOUT")
                                    && preparationTickets(level, timeoutRequest) == 0
                                    && roster.list(timeoutOwner, false).isEmpty(),
                            "A bounded destination timeout must fail closed and release reservation and ticket state: "
                                    + timeout);

                    UUID noSafeOwner = UUID.randomUUID();
                    UUID noSafeRequest = UUID.randomUUID();
                    BlockPos noSafeAnchor = helper.absolutePos(new BlockPos(128, 0, 128));
                    BlockPos noSafeColumn = new BlockPos(noSafeAnchor.getX(), 80, noSafeAnchor.getZ());
                    prepareColumn(fixture, level, noSafeColumn, Blocks.AIR.defaultBlockState());
                    WorkerRelocation noSafeService = relocation(level, roster, noSafeColumn,
                            new WorkerRelocation.Limits(2, 100, 40));
                    ensurePrepared(level, noSafeColumn);
                    noSafeService.deploy(noSafeOwner, noSafeRequest, Level.OVERWORLD, null);
                    fixture.trackPending(noSafeService, noSafeOwner, noSafeRequest);
                    awaitTerminal(helper, noSafeService, noSafeOwner, noSafeRequest, 40, fixture, noSafe -> {
                        try {
                            helper.assertTrue(noSafe.state() == WorkerRelocation.State.FAILED
                                            && noSafe.error().equals("NO_SAFE_DESTINATION")
                                            && noSafe.attempts() == 2
                                            && preparationTickets(level, noSafeRequest) == 0
                                            && roster.list(noSafeOwner, false).isEmpty(),
                                    "Exhausting bounded all-air attempts must fail without a worker or leaked ticket: " + noSafe);
                            finish(helper, fixture, null);
                        } catch (Throwable failure) {
                            finish(helper, fixture, failure);
                        }
                    });
                } catch (Throwable failure) {
                    finish(helper, fixture, failure);
                }
            });
        } catch (Throwable failure) {
            finish(helper, fixture, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_relocation_safety", timeoutTicks = 100)
    public static void safeDestinationRejectsHazardsCollisionAndBounds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockFixture fixture = new BlockFixture();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos feet = new BlockPos(anchor.getX(), 81, anchor.getZ());
        WorldBorder border = level.getWorldBorder();
        double oldCenterX = border.getCenterX();
        double oldCenterZ = border.getCenterZ();
        double oldSize = border.getSize();
        try {
            prepareColumn(fixture, level, feet.below(), Blocks.STONE.defaultBlockState());
            ensurePrepared(level, feet);
            helper.assertTrue(WorkerRelocation.isSafe(level, feet),
                    "A clear body over a sturdy non-hazardous floor must be safe");

            BlockPos hazard = feet.offset(1, 0, 0);
            fixture.set(level, hazard.below(), Blocks.STONE.defaultBlockState());
            fixture.set(level, hazard, Blocks.FIRE.defaultBlockState());
            helper.assertTrue(!WorkerRelocation.isSafe(level, feet),
                    "Adjacent fire must make a destination unsafe");
            fixture.set(level, hazard, Blocks.AIR.defaultBlockState());
            fixture.set(level, hazard.below(), Blocks.AIR.defaultBlockState());
            fixture.set(level, hazard, Blocks.WATER.defaultBlockState());
            helper.assertTrue(!WorkerRelocation.isSafe(level, feet),
                    "Adjacent fluid must make a destination unsafe");
            fixture.set(level, hazard, Blocks.AIR.defaultBlockState());
            fixture.set(level, hazard, Blocks.MAGMA_BLOCK.defaultBlockState());
            helper.assertTrue(!WorkerRelocation.isSafe(level, feet),
                    "Adjacent magma must make a destination unsafe");
            fixture.set(level, hazard, Blocks.AIR.defaultBlockState());
            fixture.set(level, feet, Blocks.STONE.defaultBlockState());
            helper.assertTrue(!WorkerRelocation.isSafe(level, feet),
                    "A colliding body block must make a destination unsafe");
            fixture.set(level, feet, Blocks.AIR.defaultBlockState());
            fixture.set(level, feet.below(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(!WorkerRelocation.isSafe(level, feet),
                    "A missing sturdy floor must make a destination unsafe");
            fixture.set(level, feet.below(), Blocks.STONE.defaultBlockState());

            border.setCenter(feet.getX() + 0.5D, feet.getZ() + 0.5D);
            border.setSize(20_000.0D);
            RandomSource samples = RandomSource.create(1234L);
            int sides = 0;
            for (int sample = 0; sample < 128; sample++) {
                BlockPos sampled = WorkerRelocation.sampleColumn(level, samples);
                sides |= sampled.getX() < feet.getX() ? 1 : 2;
                sides |= sampled.getZ() < feet.getZ() ? 4 : 8;
                helper.assertTrue(WorkerRelocation.insideBorder(level, sampled),
                        "Seeded border sample must keep the worker body inside the border: " + sampled);
            }
            helper.assertTrue(sides == 15 && WorkerRelocation.insideBorder(level, feet)
                            && !WorkerRelocation.insideBorder(level, feet.offset(20_000, 0, 0))
                            && !WorkerRelocation.isSafe(level, feet.offset(20_000, 0, 0)),
                    "Sampling and safety checks must keep worker bodies inside the world border");
            helper.assertTrue(!WorkerRelocation.isSafe(level,
                            new BlockPos(feet.getX(), level.getMinBuildHeight(), feet.getZ()))
                            && !WorkerRelocation.isSafe(level,
                            new BlockPos(feet.getX(), level.getMaxBuildHeight() - 1, feet.getZ())),
                    "Safety checks must reject both build-limit edges");
            finish(helper, fixture, null);
        } catch (Throwable failure) {
            finish(helper, fixture, failure);
        } finally {
            border.setCenter(oldCenterX, oldCenterZ);
            border.setSize(oldSize);
        }
    }

    private static WorkerRelocation relocation(ServerLevel level, WorkerRoster roster, BlockPos column,
                                               WorkerRelocation.Limits limits) {
        BiFunction<ServerLevel, RandomSource, BlockPos> deterministic = (ignored, random) -> column;
        try {
            var constructor = WorkerRelocation.class.getDeclaredConstructor(
                    net.minecraft.server.MinecraftServer.class, WorkerRoster.class, BiFunction.class,
                    WorkerRelocation.Limits.class);
            constructor.setAccessible(true);
            return constructor.newInstance(level.getServer(), roster, deterministic, limits);
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("The internal relocation fixture constructor changed", failure);
        }
    }

    private static WorkerEntity directWorker(WorkerRoster roster, UUID owner, ServerLevel level, BlockPos feet) {
        UUID request = UUID.randomUUID();
        roster.reserve(owner, request, null);
        return roster.deploy(owner, request, level, Vec3.atBottomCenterOf(feet));
    }

    private static BlockPos initialFeet(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 0, 0));
        return new BlockPos(anchor.getX(), 81, anchor.getZ());
    }

    private static void awaitTerminal(GameTestHelper helper, WorkerRelocation service, UUID owner, UUID request,
                                      int remaining, BlockFixture fixture, StatusObserver observer) {
        try {
            service.tick();
            WorkerRelocation.Status status = service.status(owner, request);
            if (status.state() != WorkerRelocation.State.PENDING) {
                observer.observe(status);
                return;
            }
            if (remaining <= 0) {
                finish(helper, fixture, new AssertionError(
                        "Relocation request remained pending after its focused deadline: " + status));
                return;
            }
            helper.runAfterDelay(1, () -> awaitTerminal(helper, service, owner, request, remaining - 1,
                    fixture, observer));
        } catch (Throwable failure) {
            finish(helper, fixture, failure);
        }
    }

    /** Prepare one sampled column; the argument names the exact block supporting the worker. */
    private static void prepareColumn(BlockFixture fixture, ServerLevel level, BlockPos floorPosition,
                                      BlockState floor) {
        int min = level.getMinBuildHeight();
        int ceiling = level.dimension().equals(Level.NETHER) ? 126 : level.getMaxBuildHeight() - 2;
        for (int x = floorPosition.getX() - 1; x <= floorPosition.getX() + 1; x++) {
            for (int z = floorPosition.getZ() - 1; z <= floorPosition.getZ() + 1; z++) {
                for (int y = floorPosition.getY(); y <= Math.min(ceiling, floorPosition.getY() + 2); y++) {
                    fixture.set(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int y = floorPosition.getY() + 1; y <= ceiling; y++) {
            fixture.set(level, new BlockPos(floorPosition.getX(), y, floorPosition.getZ()),
                    Blocks.AIR.defaultBlockState());
        }
        if (floor.isAir()) {
            for (int y = min; y <= ceiling; y++) {
                fixture.set(level, new BlockPos(floorPosition.getX(), y, floorPosition.getZ()),
                        Blocks.AIR.defaultBlockState());
            }
        } else {
            fixture.set(level, floorPosition, floor);
        }
    }

    private static void ensurePrepared(ServerLevel level, BlockPos column) {
        ChunkPos center = new ChunkPos(column);
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int z = center.z - 1; z <= center.z + 1; z++) {
                level.getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            }
        }
    }

    private static int preparationTickets(ServerLevel level, UUID request) {
        try {
            Object cache = level.getChunkSource();
            Object distance = field(cache, "distanceManager").get(cache);
            Object tickets = field(distance, "tickets").get(distance);
            if (!(tickets instanceof Map<?, ?> ticketMap)) {
                throw new AssertionError("DistanceManager ticket map is not a Map");
            }
            int count = 0;
            for (Object set : ticketMap.values()) {
                if (!(set instanceof Iterable<?> iterable)) {
                    throw new AssertionError("DistanceManager ticket bucket is not iterable");
                }
                for (Object ticket : iterable) {
                    Object type = field(ticket, "type").get(ticket);
                    Object key = field(ticket, "key").get(ticket);
                    if (Objects.equals(type, WorkerRelocation.PREPARATION) && Objects.equals(key, request)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("NeoForge DistanceManager no longer exposes raw ticket state", failure);
        }
    }

    private static Field field(Object instance, String name) throws NoSuchFieldException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static LongSet singletonChunk(ChunkPos chunk) {
        LongSet result = new LongOpenHashSet();
        result.add(chunk.toLong());
        return result;
    }

    private static void expectFailure(GameTestHelper helper, Runnable action, String expectedCode, String message) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            helper.assertTrue(failure.getMessage() != null && failure.getMessage().contains(expectedCode),
                    message + "; expected " + expectedCode + " but was " + failure);
            return;
        }
        helper.fail(message + "; operation unexpectedly succeeded");
    }

    private static void cancelIfPending(WorkerRelocation service, UUID owner, UUID request) {
        try {
            if (service.status(owner, request).state() == WorkerRelocation.State.PENDING) {
                service.cancel(owner, request);
            }
        } catch (RuntimeException ignored) {
            // Cleanup must not hide the assertion that caused the fixture to fail.
        }
    }

    private static boolean isEmpty(WorkerEntity worker) {
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            if (!worker.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmptyExcept(WorkerEntity worker, int... filledSlots) {
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            boolean expectedFilled = false;
            for (int filled : filledSlots) {
                expectedFilled |= slot == filled;
            }
            if (!expectedFilled && !worker.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void finish(GameTestHelper helper, BlockFixture fixture, Throwable failure) {
        try {
            fixture.close();
        } catch (Throwable cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else {
                failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure == null) {
            helper.succeed();
        } else {
            helper.fail("Worker relocation test failed: " + failure);
        }
    }

    @FunctionalInterface
    private interface StatusObserver {
        void observe(WorkerRelocation.Status status);
    }

    private static final class BlockFixture implements AutoCloseable {
        private final Map<WorldPos, BlockState> originalBlocks = new LinkedHashMap<>();
        private final List<WorkerEntity> workers = new ArrayList<>();
        private final List<PendingRequest> pending = new ArrayList<>();

        private void set(ServerLevel level, BlockPos position, BlockState state) {
            WorldPos key = new WorldPos(level, position);
            originalBlocks.putIfAbsent(key, level.getBlockState(position));
            level.setBlock(position, state, 3);
        }

        private void track(WorkerEntity worker) {
            if (worker != null && !workers.contains(worker)) {
                workers.add(worker);
            }
        }

        private void trackPending(WorkerRelocation service, UUID owner, UUID request) {
            pending.add(new PendingRequest(service, owner, request));
        }

        @Override
        public void close() {
            for (PendingRequest request : pending) {
                cancelIfPending(request.service(), request.owner(), request.request());
            }
            for (WorkerEntity worker : workers) {
                WorkerGameTestSupport.discardWorker(worker);
            }
            for (Map.Entry<WorldPos, BlockState> entry : originalBlocks.entrySet()) {
                entry.getKey().level().setBlock(entry.getKey().position(), entry.getValue(), 3);
            }
        }
    }

    private record WorldPos(ServerLevel level, BlockPos position) {
    }

    private record PendingRequest(WorkerRelocation service, UUID owner, UUID request) {
    }

}
