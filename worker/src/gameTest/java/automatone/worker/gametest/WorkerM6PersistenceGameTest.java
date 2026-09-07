package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerRoster;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Real serializers and roster boundaries; execution belongs to the human. */
@GameTestHolder("automatone_worker_m6_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerM6PersistenceGameTest {
    private WorkerM6PersistenceGameTest() { }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void supportedVersionsRetainProductStateAndContents(GameTestHelper helper) {
        WorkerEntity original = prepared(helper);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        UUID run = UUID.randomUUID();
        for (int version = 1; version <= 3; version++) {
            CompoundTag input = saved.copy();
            CompoundTag data = input.getCompound("AutomatoneWorker");
            data.putInt("Version", version);
            configureJob(data.getCompound("Job"), run);
            if (version == 1) { data.getCompound("Job").remove("Targets"); data.getCompound("Job").remove("RunId"); }
            WorkerEntity restored = Objects.requireNonNull(WorkerMod.WORKER.get().create(helper.getLevel()));
            restored.load(input);
            assertContents(helper, original, restored);
            MiningSession.Snapshot status = restored.miningStatus();
            helper.assertTrue(status.state() == MiningSession.State.PAUSED && status.requested() == 8
                    && status.completed() == 3 && status.error().isEmpty(), "version " + version + " lost job state");
            helper.assertTrue(status.targets().equals(version == 1 ? List.of("minecraft:iron_ore")
                    : List.of("minecraft:iron_ore", "minecraft:gold_ore")), "version lost targets");
            helper.assertTrue(version == 1 ? status.runId() != null : run.equals(status.runId()), "invalid run identity");
            helper.assertTrue(restored.runtime() == null, "deserialization created a native runtime");
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void invalidJobsPreserveRecoverableIdentityAndInventory(GameTestHelper helper) {
        WorkerEntity original = prepared(helper);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        configureJob(saved.getCompound("AutomatoneWorker").getCompound("Job"), UUID.randomUUID());
        List<Consumer<CompoundTag>> corruptions = List.of(
                data -> data.putInt("Version", 99),
                data -> data.getCompound("Job").remove("Requested"),
                data -> data.getCompound("Job").putString("Completed", "three"),
                data -> data.getCompound("Job").putLong("Completed", -1),
                data -> { ListTag targets = new ListTag(); targets.add(StringTag.valueOf("missing:block"));
                    data.getCompound("Job").put("Targets", targets); });
        for (Consumer<CompoundTag> corrupt : corruptions) {
            CompoundTag input = saved.copy();
            corrupt.accept(input.getCompound("AutomatoneWorker"));
            WorkerEntity restored = Objects.requireNonNull(WorkerMod.WORKER.get().create(helper.getLevel()));
            restored.load(input);
            assertContents(helper, original, restored);
            helper.assertTrue(restored.miningStatus().state() == MiningSession.State.FAILED
                    && restored.miningStatus().error().equals("INVALID_SAVED_JOB"), "invalid job did not fail safely");
            helper.assertTrue(restored.runtime() == null, "invalid job constructed a native runtime");
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void staleAndForeignRemovalCannotReplaceOwnedRecord(GameTestHelper helper) {
        WorkerEntity original = prepared(helper);
        original.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)).getCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(original), "worker spawn failed");
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        UUID owner = original.ownerUUID().orElseThrow();
        CompoundTag entity = original.saveWithoutId(new CompoundTag());
        CompoundTag rosterSave = roster.save(new CompoundTag(), helper.getLevel().registryAccess()).copy();
        try {
            WorkerEntity stale = Objects.requireNonNull(WorkerMod.WORKER.get().create(helper.getLevel()));
            stale.load(entity.copy());
            roster.removed(stale, Entity.RemovalReason.DISCARDED);
            helper.assertTrue(roster.active(owner, original.getUUID()).equals(original), "stale removal deleted live worker");
            CompoundTag foreignSave = entity.copy();
            foreignSave.getCompound("AutomatoneWorker").putUUID("Owner", UUID.randomUUID());
            WorkerEntity foreign = Objects.requireNonNull(WorkerMod.WORKER.get().create(helper.getLevel()));
            foreign.load(foreignSave);
            roster.removed(foreign, Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(rosterSave.equals(roster.save(new CompoundTag(), helper.getLevel().registryAccess())),
                    "foreign removal changed live authoritative contents");
            original.discard();
            WorkerRoster unloaded = WorkerRoster.load(helper.getLevel().getServer(), rosterSave.copy());
            unloaded.removed(foreign, Entity.RemovalReason.DISCARDED);
            helper.assertTrue(unloaded.view(owner, original.getUUID()).owner().equals(owner), "foreign removal deleted unloaded record");
            helper.assertTrue(rosterSave.equals(unloaded.save(new CompoundTag(), helper.getLevel().registryAccess())),
                    "foreign removal changed unloaded authoritative contents");
        } finally { original.discard(); }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void attachedReloadDisposesOldWorkAndAttachesExactlyOnce(GameTestHelper helper) {
        WorkerEntity worker = prepared(helper);
        worker.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)).getCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(worker), "worker spawn failed");
        try {
            baritone.api.IBaritone previous = worker.runtime();
            previous.getMineProcess().mine(net.minecraft.world.level.block.Blocks.IRON_ORE);
            helper.assertTrue(previous.getMineProcess().isActive(), "fixture needs active old work");
            CompoundTag saved = worker.saveWithoutId(new CompoundTag());
            configureJob(saved.getCompound("AutomatoneWorker").getCompound("Job"), UUID.randomUUID());
            worker.readAdditionalSaveData(saved);
            baritone.api.IBaritone fresh = worker.runtime();
            helper.assertTrue(previous.isDisposed() && !previous.getMineProcess().isActive(), "old work survived reload");
            helper.assertTrue(fresh != null && !fresh.equals(previous) && !fresh.getMineProcess().isActive(),
                    "paused reload must attach one fresh idle runtime");
            worker.attachRuntime();
            helper.assertTrue(fresh.equals(worker.runtime()), "repeated attachment duplicated the runtime");
            helper.assertTrue(baritone.api.BaritoneAPI.getProvider().getAllBaritones().stream()
                    .filter(runtime -> runtime.getPlayerContext().equals(fresh.getPlayerContext())).count() == 1,
                    "provider retains duplicate worker runtime");
            helper.assertTrue(worker.miningStatus().state() == MiningSession.State.PAUSED
                    && worker.miningStatus().completed() == 3, "reload changed paused progress");
        } finally { worker.discard(); }
        helper.succeed();
    }

    private static WorkerEntity prepared(GameTestHelper helper) {
        WorkerEntity worker = Objects.requireNonNull(WorkerMod.WORKER.get().create(helper.getLevel()));
        worker.claim(UUID.randomUUID());
        for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
            ItemStack stack = new ItemStack(Items.DIAMOND, slot + 1);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Saved slot " + slot));
            worker.setItem(slot, stack);
        }
        worker.setSelectedSlot(7);
        worker.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        worker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        return worker;
    }

    private static void configureJob(CompoundTag job, UUID run) {
        job.putString("Target", "minecraft:iron_ore");
        ListTag targets = new ListTag();
        targets.add(StringTag.valueOf("minecraft:iron_ore")); targets.add(StringTag.valueOf("minecraft:gold_ore"));
        job.put("Targets", targets); job.putInt("Requested", 8); job.putLong("Completed", 3);
        job.putString("State", "PAUSED"); job.putString("Error", ""); job.putUUID("RunId", run);
    }

    private static void assertContents(GameTestHelper helper, WorkerEntity expected, WorkerEntity actual) {
        helper.assertTrue(expected.getUUID().equals(actual.getUUID()) && expected.ownerUUID().equals(actual.ownerUUID())
                && expected.selectedSlot() == actual.selectedSlot(), "identity/ownership/selected slot changed");
        for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
            helper.assertTrue(ItemStack.matches(expected.getItem(slot), actual.getItem(slot)), "inventory changed at " + slot);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            helper.assertTrue(ItemStack.matches(expected.getItemBySlot(slot), actual.getItemBySlot(slot)), "equipment changed at " + slot);
        }
    }
}
