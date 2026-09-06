package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerChunkLoading;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

/** Dedicated-server contract tests for the M5.2 owner roster and archive. */
@GameTestHolder("automatone_worker_m5_roster_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerRosterGameTest {
    private static final ResourceLocation IRON_ORE = ResourceLocation.withDefaultNamespace("iron_ore");

    private WorkerRosterGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_cap", timeoutTicks = 100)
    public static void reservationsRespectTenActiveSlotsAndCommitOnce(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        List<UUID> requests = new ArrayList<>();
        WorkerEntity deployed = null;
        try {
            UUID firstRequest = UUID.randomUUID();
            requests.add(firstRequest);
            WorkerRoster.Reservation first = roster.reserve(owner, firstRequest, null);
            helper.assertTrue(first.equals(roster.reserve(owner, firstRequest, null)),
                    "Repeating one owner request must return the same reservation");
            expectFailure(helper, () -> roster.reserve(stranger, firstRequest, null), "RESERVATION_CONFLICT",
                    "Another owner must not consume or alter a reservation token");
            expectOffThreadFailure(helper, roster, owner);
            helper.assertTrue(roster.list(owner, false).isEmpty(),
                    "An off-thread reservation attempt must leave the owner roster unchanged");

            WorkerRoster.Reservation deployReservation = first;
            deployed = roster.deploy(owner, firstRequest, helper.getLevel(), destination(helper));
            helper.assertTrue(deployed.getUUID().equals(deployReservation.worker())
                            && roster.list(owner, false).size() == 1
                            && isEmpty(deployed),
                    "A committed creation must retain its reserved identity and start with nine empty slots");
            expectFailure(helper, () -> roster.deploy(owner, firstRequest, helper.getLevel(), destination(helper)),
                    "INVALID_RESERVATION", "A committed request token must not be replayable");

            for (int index = 1; index < WorkerRoster.ACTIVE_LIMIT; index++) {
                UUID request = UUID.randomUUID();
                requests.add(request);
                roster.reserve(owner, request, null);
            }
            expectFailure(helper, () -> roster.reserve(owner, UUID.randomUUID(), null), "ACTIVE_LIMIT",
                    "One active worker plus nine pending creations must consume the active-slot cap");

            UUID cancelledPending = requests.get(1);
            roster.cancelReservation(owner, cancelledPending);
            UUID replacement = UUID.randomUUID();
            requests.add(replacement);
            roster.reserve(owner, replacement, null);
        } finally {
            for (UUID request : requests) {
                roster.cancelReservation(owner, request);
            }
            WorkerGameTestSupport.discardWorker(deployed);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_profile", timeoutTicks = 100)
    public static void personalProfileInheritsOverridesValidatesAndRejectsStaleInputs(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        WorkerEntity worker = null;
        try {
            Map<String, String> callerInput = new java.util.LinkedHashMap<>(Map.of("allowBreak", "false"));
            roster.applyPersonal(owner, 0, callerInput);
            callerInput.put("allowBreak", "true");
            helper.assertTrue(roster.profile(owner).settings().get("allowbreak").equals("false"),
                    "The profile must copy caller input instead of retaining a mutable map");

            UUID request = UUID.randomUUID();
            roster.reserve(owner, request, null);
            worker = roster.deploy(owner, request, helper.getLevel(), destination(helper));
            helper.assertTrue(!worker.effectiveSettings().allowBreak.value,
                    "A newly deployed worker must inherit the owner's personal defaults");

            long revision = roster.view(owner, worker.getUUID()).revision();
            roster.applyOverrides(owner, worker.getUUID(), revision, Map.of("allowBreak", "true"));
            helper.assertTrue(worker.effectiveSettings().allowBreak.value,
                    "A worker override must take precedence over its personal profile");

            long profileRevision = roster.profile(owner).revision();
            roster.applyPersonal(owner, profileRevision, Map.of("allowBreak", "false"));
            helper.assertTrue(worker.effectiveSettings().allowBreak.value,
                    "Changing personal defaults must preserve an explicit worker override");

            long resetRevision = roster.view(owner, worker.getUUID()).revision();
            roster.applyOverrides(owner, worker.getUUID(), resetRevision, Map.of());
            helper.assertTrue(!worker.effectiveSettings().allowBreak.value && roster.overrides(owner, worker.getUUID()).isEmpty(),
                    "Resetting an override must restore profile inheritance");

            CompoundTag savedRoster = roster.save(new CompoundTag(), helper.getLevel().getServer().registryAccess());
            WorkerRoster reloadedRoster = WorkerRoster.load(helper.getLevel().getServer(), savedRoster);
            helper.assertTrue(reloadedRoster.profile(owner).settings().get("allowbreak").equals("false")
                            && reloadedRoster.overrides(owner, worker.getUUID()).isEmpty()
                            && reloadedRoster.view(owner, worker.getUUID()).worker().equals(worker.getUUID()),
                    "An ordinary roster save/load must retain active identity, profile defaults and reset overrides");

            long unchangedProfileRevision = roster.profile(owner).revision();
            expectFailure(helper, () -> roster.applyPersonal(owner, unchangedProfileRevision,
                            Map.of("allowBreak", "banana")), "INVALID_BOOLEAN",
                    "Invalid booleans must be rejected");
            expectFailure(helper, () -> roster.applyPersonal(owner, unchangedProfileRevision,
                            Map.of("costHeuristic", "NaN")), "SETTING_OUT_OF_RANGE",
                    "Non-finite numeric settings must be rejected");
            helper.assertTrue(roster.profile(owner).revision() == unchangedProfileRevision
                            && roster.profile(owner).settings().get("allowbreak").equals("false"),
                    "Rejected profile inputs must leave the previous profile unchanged");
            expectFailure(helper, () -> roster.applyPersonal(owner, unchangedProfileRevision - 1,
                            Map.of("allowBreak", "true")), "STALE_REVISION",
                    "A stale profile revision must be rejected");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_roster_archive", timeoutTicks = 150)
    public static void retirementArchivesPausedJobInventoryAndReactivatesIdentity(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        WorkerEntity worker = null;
        WorkerEntity reactivated = null;
        try {
            UUID request = UUID.randomUUID();
            roster.reserve(owner, request, null);
            worker = roster.deploy(owner, request, helper.getLevel(), destination(helper));
            UUID workerId = worker.getUUID();
            worker.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
            worker.setItem(2, new ItemStack(Items.DIRT, 7));
            worker.setSelectedSlot(2);
            worker.startMining(IRON_ORE, 3);
            UUID runId = worker.miningStatus().runId();
            helper.assertTrue(worker.miningStatus().state() == MiningSession.State.RUNNING
                            && worker.miningStatus().completed() == 0 && runId.equals(worker.miningStatus().runId()),
                    "Retirement setup must begin with a running job and stable run identity");
            long revision = roster.view(owner, workerId).revision();
            expectFailure(helper, () -> roster.retire(stranger, workerId, revision), "NOT_OWNER",
                    "A stranger must not retire an owned worker");
            expectFailure(helper, () -> roster.archivedInventory(stranger, workerId), "NOT_OWNER",
                    "A stranger must not inspect archived inventory");
            WorkerEntity retiredEntity = worker;
            roster.retire(owner, workerId, revision);
            worker = null;
            helper.assertTrue(retiredEntity.isRemoved() && retiredEntity.runtime() == null && isEmpty(retiredEntity),
                    "Retirement must pause and remove the live worker after capturing its inventory");
            helper.assertTrue(WorkerChunkLoadingGameTest.tickets(helper.getLevel(),
                            automatone.worker.WorkerChunkLoading.CENTER_CONTROLLER_ID, workerId).isEmpty()
                            && WorkerChunkLoadingGameTest.tickets(helper.getLevel(),
                            automatone.worker.WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, workerId).isEmpty(),
                    "Retirement must release the worker's center and working-ring tickets");
            helper.assertTrue(roster.list(owner, true).size() == 1
                            && roster.archivedInventory(owner, workerId).get(0).getCount() == 3
                            && roster.archivedInventory(owner, workerId).get(2).getCount() == 7,
                    "Retirement must archive identity, paused job and all nine inventory slots");

            List<ItemStack> archived = roster.archivedInventory(owner, workerId);
            archived.get(0).setCount(0);
            helper.assertTrue(roster.archivedInventory(owner, workerId).get(0).getCount() == 3,
                    "A caller mutation of the archived inventory result must not mutate the saved archive");
            long archiveRevision = roster.view(owner, workerId).revision();
            expectFailure(helper, () -> roster.withdraw(stranger, workerId, archiveRevision, 0, 1), "NOT_OWNER",
                    "A stranger must not withdraw archived items");
            ItemStack withdrawn = roster.withdraw(owner, workerId, archiveRevision, 0, 2);
            helper.assertTrue(withdrawn.is(Items.IRON_INGOT) && withdrawn.getCount() == 2
                            && roster.archivedInventory(owner, workerId).get(0).getCount() == 1,
                    "Withdrawal must return exactly the requested archived subset");
            expectFailure(helper, () -> roster.withdraw(owner, workerId, archiveRevision, 0, 1), "STALE_REVISION",
                    "A repeated withdrawal with a stale revision must be rejected");

            CompoundTag saved = roster.save(new CompoundTag(), helper.getLevel().getServer().registryAccess());
            WorkerRoster loaded = WorkerRoster.load(helper.getLevel().getServer(), saved);
            helper.assertTrue(loaded.archivedInventory(owner, workerId).get(0).getCount() == 1
                            && loaded.archivedInventory(owner, workerId).get(2).getCount() == 7,
                    "Saved and reloaded archives must retain the post-withdrawal inventory");

            UUID reactivateRequest = UUID.randomUUID();
            roster.reserve(owner, reactivateRequest, workerId);
            long pendingRevision = roster.view(owner, workerId).revision();
            expectFailure(helper, () -> roster.withdraw(owner, workerId, pendingRevision, 0, 1), "WORKER_PENDING",
                    "Archived withdrawal must be blocked while reactivation is pending");
            expectFailure(helper, () -> roster.reserve(owner, UUID.randomUUID(), workerId), "WORKER_PENDING",
                    "An archived worker must not receive two pending reactivation reservations");
            reactivated = roster.deploy(owner, reactivateRequest, helper.getLevel(), destination(helper));
            MiningSession.Snapshot restored = reactivated.miningStatus();
            helper.assertTrue(reactivated.getUUID().equals(workerId)
                            && restored.state() == MiningSession.State.PAUSED
                            && runId.equals(restored.runId())
                            && reactivated.getItem(0).is(Items.IRON_INGOT) && reactivated.getItem(0).getCount() == 1
                            && reactivated.getItem(2).is(Items.DIRT) && reactivated.getItem(2).getCount() == 7
                            && reactivated.selectedSlot() == 2
                            && isEmptyExcept(reactivated, 0, 2),
                    "Reactivation must restore the same identity, remaining inventory, selection and paused job without tools");
            helper.assertTrue(!reactivated.runtime().getMineProcess().isActive(),
                    "A reactivated paused job must not start native mining automatically");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
            WorkerGameTestSupport.discardWorker(reactivated);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_legacy", timeoutTicks = 100)
    public static void loadedOwnedEntitiesAdoptProfilesAndExcludeUnownedWorkers(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        WorkerEntity first = null;
        WorkerEntity second = null;
        WorkerEntity unowned = null;
        try {
            roster.applyPersonal(firstOwner, 0, Map.of("allowBreak", "false"));
            roster.applyPersonal(secondOwner, 0, Map.of("allowBreak", "true"));
            first = loadLegacyOwned(helper, firstOwner, new BlockPos(0, 1, 0));
            second = loadLegacyOwned(helper, secondOwner, new BlockPos(2, 1, 0));
            unowned = WorkerGameTestSupport.spawnWorker(helper);
            UUID firstId = first.getUUID();
            UUID secondId = second.getUUID();
            UUID unownedId = unowned.getUUID();
            helper.assertTrue(!first.effectiveSettings().allowBreak.value && second.effectiveSettings().allowBreak.value,
                    "Adopted entities must resolve each owner's isolated profile");
            helper.assertTrue(roster.list(firstOwner, false).stream().anyMatch(view -> view.worker().equals(firstId))
                            && roster.list(secondOwner, false).stream().anyMatch(view -> view.worker().equals(secondId))
                            && roster.list(firstOwner, false).stream().noneMatch(view -> view.worker().equals(unownedId)),
                    "Owned legacy entities must be adopted while unowned entities stay outside every roster");
        } finally {
            WorkerGameTestSupport.discardWorker(first);
            WorkerGameTestSupport.discardWorker(second);
            WorkerGameTestSupport.discardWorker(unowned);
        }
        helper.succeed();
    }

    private static WorkerEntity loadLegacyOwned(GameTestHelper helper, UUID owner, BlockPos relativePosition) {
        ServerLevel level = helper.getLevel();
        WorkerEntity source = WorkerMod.WORKER.get().create(level);
        if (source == null) {
            throw new AssertionError("Registered worker entity type did not create a legacy source");
        }
        source.claim(owner);
        CompoundTag saved = source.saveWithoutId(new CompoundTag());
        CompoundTag workerTag = saved.getCompound("AutomatoneWorker");
        workerTag.putInt("Version", 1);
        CompoundTag legacyJob = workerTag.getCompound("Job");
        legacyJob.remove("Targets");
        legacyJob.remove("RunId");
        legacyJob.putString("Target", IRON_ORE.toString());
        legacyJob.putInt("Requested", 0);
        legacyJob.putLong("Completed", 0);
        legacyJob.putString("State", MiningSession.State.IDLE.name());
        legacyJob.putString("Error", "");
        workerTag.put("Job", legacyJob);
        saved.put("AutomatoneWorker", workerTag);
        WorkerEntity loaded = WorkerMod.WORKER.get().create(level);
        if (loaded == null) {
            throw new AssertionError("Registered worker entity type did not create a legacy load");
        }
        loaded.load(saved);
        helper.assertTrue(loaded.getUUID().equals(source.getUUID())
                        && loaded.miningStatus().targets().equals(List.of(IRON_ORE.toString()))
                        && loaded.ownerUUID().filter(owner::equals).isPresent(),
                "Version-one adoption must retain identity and owner and migrate the single target");
        BlockPos pos = helper.absolutePos(relativePosition);
        loaded.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        loaded.setNoGravity(true);
        if (!level.addFreshEntity(loaded)) {
            throw new AssertionError("Legacy worker was rejected by the ServerLevel");
        }
        return loaded;
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_legacy_cap", timeoutTicks = 100)
    public static void legacyAdoptionAtCapacityArchivesInventoryWithoutStealingReservedSlots(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        List<UUID> requests = new ArrayList<>();
        WorkerEntity legacy = WorkerGameTestSupport.spawnWorker(helper);
        try {
            for (int index = 0; index < WorkerRoster.ACTIVE_LIMIT; index++) {
                UUID request = UUID.randomUUID();
                requests.add(request);
                roster.reserve(owner, request, null);
            }
            legacy.setItem(4, new ItemStack(Items.RAW_IRON, 7));
            legacy.claim(owner);
            helper.assertTrue(legacy.isRemoved() && legacy.runtime() == null && roster.list(owner, false).isEmpty()
                            && roster.view(owner, legacy.getUUID()).retired()
                            && roster.archivedInventory(owner, legacy.getUUID()).get(4).getCount() == 7,
                    "Legacy overflow must preserve identity and items in retirement without exceeding active capacity");
            expectFailure(helper, () -> roster.reserve(owner, UUID.randomUUID(), legacy.getUUID()), "ACTIVE_LIMIT",
                    "Adoption must not steal already reserved active slots");
        } finally {
            requests.forEach(request -> roster.cancelReservation(owner, request));
            WorkerGameTestSupport.discardWorker(legacy);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_recovery", timeoutTicks = 120)
    public static void deadWorkerUnloadReleasesRosterSlotAndSaveLoadDropsRecord(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        List<UUID> requests = new ArrayList<>();
        try {
            worker.claim(owner);
            UUID workerId = worker.getUUID();
            helper.assertTrue(roster.list(owner, false).stream().anyMatch(view -> view.worker().equals(workerId)),
                    "Death cleanup setup must register the owned worker as active");

            worker.setHealth(0.0F);
            worker.die(worker.damageSources().genericKill());
            helper.assertTrue(worker.getHealth() <= 0.0F && !worker.isAlive(),
                    "The real worker death path must mark the worker dead before unload cleanup");
            helper.assertTrue(roster.list(owner, false).stream().noneMatch(view -> view.worker().equals(workerId)),
                    "Death must release the roster slot before the chunk can unload");
            worker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(worker.isRemoved()
                            && worker.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                    "The recovery fixture must exercise an unload before vanilla death completion");
            helper.assertTrue(roster.list(owner, false).stream().noneMatch(view -> view.worker().equals(workerId)),
                    "A dead worker unloaded before death completion must not remain active or occupy the roster");

            for (int index = 0; index < WorkerRoster.ACTIVE_LIMIT; index++) {
                UUID request = UUID.randomUUID();
                requests.add(request);
                roster.reserve(owner, request, null);
            }
            expectFailure(helper, () -> roster.reserve(owner, UUID.randomUUID(), null), "ACTIVE_LIMIT",
                    "The dead worker must release its active slot for a full replacement reservation set");
            requests.forEach(request -> roster.cancelReservation(owner, request));
            requests.clear();

            CompoundTag saved = roster.save(new CompoundTag(), helper.getLevel().getServer().registryAccess());
            WorkerRoster loaded = WorkerRoster.load(helper.getLevel().getServer(), saved);
            helper.assertTrue(loaded.list(owner, false).stream().noneMatch(view -> view.worker().equals(workerId)),
                    "A saved and reloaded roster must not resurrect a dead active worker record");
        } finally {
            requests.forEach(request -> roster.cancelReservation(owner, request));
            releaseFixtureTickets(worker);
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_recovery", timeoutTicks = 120)
    public static void healthyUnloadedWorkerIsRetainedAndDeadReloadCannotReadoptIdentity(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        WorkerEntity deadReload = null;
        try {
            worker.claim(owner);
            UUID workerId = worker.getUUID();
            worker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(worker.isRemoved()
                            && roster.list(owner, false).stream().anyMatch(view -> view.worker().equals(workerId)),
                    "Unloading a healthy worker must retain its live offline roster record");

            CompoundTag saved = roster.save(new CompoundTag(), level.getServer().registryAccess());
            WorkerRoster loaded = WorkerRoster.load(level.getServer(), saved);
            helper.assertTrue(loaded.list(owner, false).stream().anyMatch(view -> view.worker().equals(workerId)),
                    "A healthy unloaded worker must remain manageable after roster save/load");

            CompoundTag deadEntity = worker.saveWithoutId(new CompoundTag());
            deadEntity.putFloat("Health", 0.0F);
            deadReload = WorkerMod.WORKER.get().create(level);
            if (deadReload == null) {
                throw new AssertionError("Registered worker entity did not create the dead reload fixture");
            }
            deadReload.load(deadEntity);
            BlockPos position = helper.absolutePos(new BlockPos(2, 1, 0));
            deadReload.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            deadReload.setNoGravity(true);
            if (!level.addFreshEntity(deadReload)) {
                throw new AssertionError("Dedicated server rejected the dead reload fixture");
            }
            helper.assertTrue(deadReload.getHealth() <= 0.0F,
                    "The loaded reattachment fixture must retain its non-positive health");
            helper.assertTrue(roster.list(owner, false).stream().noneMatch(view -> view.worker().equals(workerId)),
                    "Rejecting a dead reattachment must remove the stale active roster entry");

            CompoundTag afterReject = roster.save(new CompoundTag(), level.getServer().registryAccess());
            WorkerRoster reloadedAfterReject = WorkerRoster.load(level.getServer(), afterReject);
            helper.assertTrue(reloadedAfterReject.list(owner, false).stream()
                            .noneMatch(view -> view.worker().equals(workerId)),
                    "A dead reattachment must not reappear in a subsequent saved roster");
        } finally {
            releaseFixtureTickets(worker);
            WorkerGameTestSupport.discardWorker(worker);
            releaseFixtureTickets(deadReload);
            WorkerGameTestSupport.discardWorker(deadReload);
            roster.removed(worker, Entity.RemovalReason.KILLED);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_recovery", timeoutTicks = 100)
    public static void legacyDeadEntityHealthIsIgnoredWhenLoadingActualRosterSave(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        try {
            worker.claim(owner);
            UUID workerId = worker.getUUID();
            worker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            CompoundTag actualSave = roster.save(new CompoundTag(), helper.getLevel().getServer().registryAccess());
            ListTag workers = actualSave.getList("Workers", Tag.TAG_COMPOUND);
            boolean changed = false;
            for (Tag value : workers) {
                CompoundTag entry = (CompoundTag) value;
                if (entry.getUUID("Worker").equals(workerId)) {
                    CompoundTag entity = entry.getCompound("Entity");
                    entity.putFloat("Health", 0.0F);
                    entry.put("Entity", entity);
                    changed = true;
                    break;
                }
            }
            helper.assertTrue(changed, "The actual roster save must contain the healthy offline worker fixture");

            WorkerRoster loaded = WorkerRoster.load(helper.getLevel().getServer(), actualSave);
            helper.assertTrue(loaded.list(owner, false).stream().noneMatch(view -> view.worker().equals(workerId)),
                    "A legacy stuck non-retired entity with non-positive saved health must be ignored on load");
        } finally {
            releaseFixtureTickets(worker);
            WorkerGameTestSupport.discardWorker(worker);
            roster.removed(worker, Entity.RemovalReason.KILLED);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_roster_recovery", timeoutTicks = 100)
    public static void cancelledLivingDeathPreservesRosterAndTickets(GameTestHelper helper) {
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        AtomicBoolean cancelled = new AtomicBoolean();
        UUID owner = UUID.randomUUID();
        UUID workerId = worker.getUUID();
        Consumer<LivingDeathEvent> listener = event -> {
            if (event.getEntity() instanceof WorkerEntity
                    && event.getEntity().getUUID().equals(workerId)) {
                cancelled.set(true);
                event.setCanceled(true);
                event.getEntity().setHealth(1.0F);
            }
        };
        try {
            worker.claim(owner);
            worker.startMining(IRON_ORE, 1);
            ChunkPos center = worker.chunkPosition();
            helper.assertTrue(WorkerChunkLoadingGameTest.tickets(helper.getLevel(),
                            WorkerChunkLoading.CENTER_CONTROLLER_ID, workerId).contains(center.toLong())
                            && WorkerChunkLoadingGameTest.tickets(helper.getLevel(),
                            WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, workerId).size() == 8,
                    "Death cancellation setup must own the center and eight working-ring tickets");

            NeoForge.EVENT_BUS.addListener(listener);
            worker.setHealth(0.0F);
            worker.die(worker.damageSources().genericKill());
            helper.assertTrue(cancelled.get() && worker.getHealth() == 1.0F && worker.isAlive(),
                    "A cancelled LivingDeathEvent must restore this worker's health and stop death commit");
            helper.assertTrue(roster.list(owner, false).stream().anyMatch(view -> view.worker().equals(workerId))
                            && roster.active(owner, workerId).equals(worker),
                    "A cancelled death must retain the owned worker's active roster identity");
            helper.assertTrue(WorkerChunkLoadingGameTest.tickets(helper.getLevel(),
                            WorkerChunkLoading.CENTER_CONTROLLER_ID, workerId).contains(center.toLong())
                            && WorkerChunkLoadingGameTest.tickets(helper.getLevel(),
                            WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, workerId).size() == 8,
                    "A cancelled death must retain the worker's center and working-ring tickets");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            if (!worker.isRemoved() && worker.isAlive()
                    && worker.miningStatus().state() == MiningSession.State.RUNNING) {
                worker.stopMining();
            }
            releaseFixtureTickets(worker);
            WorkerGameTestSupport.discardWorker(worker);
            roster.removed(worker, Entity.RemovalReason.KILLED);
        }
        helper.succeed();
    }

    /** Remove only this fixture worker's registered center/ring tickets after simulated unload. */
    private static void releaseFixtureTickets(WorkerEntity worker) {
        if (worker == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        UUID owner = worker.getUUID();
        ChunkPos center = worker.chunkPosition();
        new TicketController(WorkerChunkLoading.CENTER_CONTROLLER_ID)
                .forceChunk(level, owner, center.x, center.z, false, true);
        TicketController ring = new TicketController(WorkerChunkLoading.WORKING_RING_CONTROLLER_ID);
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int z = center.z - 1; z <= center.z + 1; z++) {
                if (x != center.x || z != center.z) {
                    ring.forceChunk(level, owner, x, z, false, true);
                }
            }
        }
    }

    private static Vec3 destination(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private static void expectOffThreadFailure(GameTestHelper helper, WorkerRoster roster, UUID owner) {
        try {
            CompletableFuture.runAsync(() -> roster.reserve(owner, UUID.randomUUID(), null))
                    .get(1, TimeUnit.SECONDS);
            helper.fail("Roster mutation unexpectedly succeeded off the server thread");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            helper.assertTrue(cause instanceof IllegalStateException
                            && cause.getMessage() != null
                            && cause.getMessage().contains("server thread"),
                    "Off-thread roster mutation must fail before changing state: " + cause);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            helper.fail("Off-thread roster assertion was interrupted: " + failure);
        } catch (TimeoutException failure) {
            helper.fail("Off-thread roster assertion timed out: " + failure);
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
}
