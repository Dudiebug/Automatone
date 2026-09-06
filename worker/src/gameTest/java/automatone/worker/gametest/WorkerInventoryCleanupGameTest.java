package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import baritone.api.IBaritone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder("automatone_worker_m5_inventory_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerInventoryCleanupGameTest {
    private static final ResourceLocation DIRT = BuiltInRegistries.BLOCK.getKey(Blocks.DIRT);
    private static final ResourceLocation COBBLESTONE = BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE);
    private static final ResourceLocation GRAVEL = BuiltInRegistries.BLOCK.getKey(Blocks.GRAVEL);
    private static final ResourceLocation SAND = BuiltInRegistries.BLOCK.getKey(Blocks.SAND);
    private static final ResourceLocation PODZOL = BuiltInRegistries.BLOCK.getKey(Blocks.PODZOL);
    private static final ResourceLocation STONE = BuiltInRegistries.BLOCK.getKey(Blocks.STONE);
    private static final List<String> DEFAULT_IGNORED_BLOCKS = List.of(
            "minecraft:dirt", "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:gravel",
            "minecraft:sand", "minecraft:red_sand", "minecraft:netherrack", "minecraft:tuff",
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite");

    private WorkerInventoryCleanupGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void newWorkerExposesDefaultIgnoredBlocksWithoutLegacyQuotaKnobs(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        try {
            CompoundTag settings = worker.inventoryManagementSettings();
            helper.assertTrue(settings.getAllKeys().equals(Set.of("Blocks", "Override")),
                    "The policy snapshot must expose only Blocks and Override");
            helper.assertFalse(settings.getBoolean("Override"),
                    "A new worker must inherit the default ignored-block list");
            helper.assertTrue(settings.getList("Blocks", Tag.TAG_STRING).size() == DEFAULT_IGNORED_BLOCKS.size()
                            && stringSet(settings.getList("Blocks", Tag.TAG_STRING))
                            .equals(new HashSet<>(DEFAULT_IGNORED_BLOCKS)),
                    "The inherited ignored-block list must retain every existing common block");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void cobblestoneReserveAcceptsOneAt63RejectsAt64AndRefillsAfterUse(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity remainder = null;
        ItemEntity atReserve = null;
        ItemEntity refill = null;
        try {
            worker.setItem(0, new ItemStack(Items.COBBLESTONE, 63));
            remainder = spawnImmediateDrop(helper.getLevel(), worker, Items.COBBLESTONE, 64);
            worker.aiStep();
            helper.assertTrue(countItems(worker, Items.COBBLESTONE) == 64,
                    "A 63-item ordinary cobblestone stock must accept exactly one pickup");
            helper.assertTrue(!remainder.isRemoved() && remainder.getItem().getCount() == 63,
                    "The pickup above the 64-item working reserve must remain in the world");
            remainder.discard();

            atReserve = spawnImmediateDrop(helper.getLevel(), worker, Items.COBBLESTONE, 1);
            worker.aiStep();
            helper.assertTrue(countItems(worker, Items.COBBLESTONE) == 64
                            && !atReserve.isRemoved() && atReserve.getItem().getCount() == 1,
                    "An ordinary cobblestone reserve already at 64 must reject another pickup");
            atReserve.discard();

            worker.removeItem(0, 1);
            refill = spawnImmediateDrop(helper.getLevel(), worker, Items.COBBLESTONE, 1);
            worker.aiStep();
            helper.assertTrue(countItems(worker, Items.COBBLESTONE) == 64 && refill.isRemoved(),
                    "Using one working cobblestone must allow one pickup to refill the reserve");
        } finally {
            if (remainder != null) {
                remainder.discard();
            }
            if (atReserve != null) {
                atReserve.discard();
            }
            if (refill != null) {
                refill.discard();
            }
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void otherDefaultIgnoredBlocksAreRejectedEntirely(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity dirt = null;
        ItemEntity gravel = null;
        try {
            dirt = spawnImmediateDrop(helper.getLevel(), worker, Items.DIRT, 64);
            gravel = spawnImmediateDrop(helper.getLevel(), worker, Items.GRAVEL, 32);
            worker.aiStep();
            helper.assertTrue(countItems(worker, Items.DIRT) == 0 && countItems(worker, Items.GRAVEL) == 0,
                    "Ignored blocks other than cobblestone must have zero pickup quota");
            helper.assertTrue(!dirt.isRemoved() && dirt.getItem().getCount() == 64
                            && !gravel.isRemoved() && gravel.getItem().getCount() == 32,
                    "Ignored dirt and gravel pickups must remain intact in the world");
        } finally {
            if (dirt != null) {
                dirt.discard();
            }
            if (gravel != null) {
                gravel.discard();
            }
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void fullInventoryLeavesIgnoredStacksAndWantedDropUntouched(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity wanted = null;
        try {
            fillFullWithIgnoredJunk(worker);
            wanted = spawnImmediateDrop(helper.getLevel(), worker, Items.DIAMOND, 1);
            worker.aiStep();
            helper.assertTrue(countItems(worker, Items.DIRT) == 35 * 64
                            && worker.getItem(0).is(Items.IRON_PICKAXE),
                    "A full worker inventory must retain every held ignored stack and tool");
            helper.assertTrue(!wanted.isRemoved() && wanted.getItem().is(Items.DIAMOND)
                            && wanted.getItem().getCount() == 1,
                    "A wanted item that cannot fit must remain as its original world entity");
            helper.assertTrue(nearbyItems(helper.getLevel(), worker, Items.DIRT).isEmpty(),
                    "The fixed pickup policy must never eject or delete held inventory");
        } finally {
            if (wanted != null) {
                wanted.discard();
            }
            discardNearbyItems(helper.getLevel(), worker);
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void toolsComponentsAndExplicitCobblestoneTargetsRemainProtected(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity tool = null;
        ItemEntity component = null;
        ItemEntity targetFirst = null;
        ItemEntity targetSecond = null;
        CompoundTag customTag = new CompoundTag();
        try {
            worker.configureMining(List.of(COBBLESTONE), 0);
            tool = spawnImmediateDrop(helper.getLevel(), worker, Items.IRON_PICKAXE, 1);
            worker.aiStep();
            helper.assertTrue(tool.isRemoved() && countItems(worker, Items.IRON_PICKAXE) == 1,
                    "Tool pickups must remain protected by the fixed policy");

            customTag.putString("marker", "protected");
            ItemStack customDirt = new ItemStack(Items.DIRT, 3);
            customDirt.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
            component = spawnImmediateDrop(helper.getLevel(), worker, customDirt);
            worker.aiStep();
            helper.assertTrue(component.isRemoved() && hasMatchingCustomStack(worker, Items.DIRT, customTag),
                    "Component-bearing ignored-block stacks must be collected without losing components");

            targetFirst = spawnImmediateDrop(helper.getLevel(), worker, Items.COBBLESTONE, 64);
            worker.aiStep();
            targetSecond = spawnImmediateDrop(helper.getLevel(), worker, Items.COBBLESTONE, 32);
            worker.aiStep();
            helper.assertTrue(targetFirst.isRemoved() && targetSecond.isRemoved()
                            && countItems(worker, Items.COBBLESTONE) == 96,
                    "An explicitly targeted cobblestone output may exceed the ordinary 64-item reserve");
        } finally {
            if (tool != null) {
                tool.discard();
            }
            if (component != null) {
                component.discard();
            }
            if (targetFirst != null) {
                targetFirst.discard();
            }
            if (targetSecond != null) {
                targetSecond.discard();
            }
            discardNearbyItems(helper.getLevel(), worker);
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void customPolicyAndLegacyKnobsMigrateWithExactInventory(GameTestHelper helper) {
        WorkerEntity source = WorkerGameTestSupport.spawnWorker(helper);
        WorkerEntity restored = null;
        try {
            source.applyInventoryManagement(List.of(GRAVEL, SAND));
            source.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            source.setItem(8, new ItemStack(Items.RAW_IRON, 11));
            source.setItem(9, new ItemStack(Items.DIAMOND, 2));
            source.setItem(35, new ItemStack(Items.EMERALD, 7));
            CompoundTag saved = source.saveWithoutId(new CompoundTag());
            CompoundTag policy = saved.getCompound("AutomatoneWorker").getCompound("InventoryManagement");
            assertPolicy(helper, policy, List.of(GRAVEL.toString(), SAND.toString()), true,
                    "The customized worker policy");

            // This mirrors a pre-M5.11 policy: retain its custom Blocks list while ignoring old controls.
            policy.remove("Version");
            policy.remove("Override");
            policy.putBoolean("Enabled", false);
            policy.putInt("Keep", 0);
            restored = newWorker(helper.getLevel());
            restored.readAdditionalSaveData(saved);
            CompoundTag migrated = restored.inventoryManagementSettings();
            helper.assertTrue(migrated.getAllKeys().equals(Set.of("Blocks", "Override")),
                    "Migrated policy snapshots must omit legacy Enable and Keep controls");
            assertPolicy(helper, migrated, List.of(GRAVEL.toString(), SAND.toString()), true,
                    "The migrated customized worker policy");
            assertExactInventory(helper, restored, "Migrated inventory");
        } finally {
            WorkerGameTestSupport.discardWorker(source);
            WorkerGameTestSupport.discardWorker(restored);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void malformedPolicyFallsBackToDefaultWithoutLosingInventory(GameTestHelper helper) {
        WorkerEntity source = WorkerGameTestSupport.spawnWorker(helper);
        WorkerEntity restored = null;
        try {
            source.setItem(0, new ItemStack(Items.DIRT, 17));
            source.setItem(35, new ItemStack(Items.DIAMOND, 3));
            CompoundTag saved = source.saveWithoutId(new CompoundTag());
            ListTag malformedBlocks = new ListTag();
            malformedBlocks.add(StringTag.valueOf("NOT VALID:block"));
            saved.getCompound("AutomatoneWorker").getCompound("InventoryManagement")
                    .put("Blocks", malformedBlocks);
            restored = newWorker(helper.getLevel());
            restored.readAdditionalSaveData(saved);
            CompoundTag settings = restored.inventoryManagementSettings();
            helper.assertTrue(settings.getAllKeys().equals(Set.of("Blocks", "Override")),
                    "Malformed policy fallback must retain the approved snapshot shape");
            helper.assertFalse(settings.getBoolean("Override"),
                    "Malformed policy data must fall back to inherited defaults");
            helper.assertTrue(settings.getList("Blocks", Tag.TAG_STRING).size() == DEFAULT_IGNORED_BLOCKS.size()
                            && stringSet(settings.getList("Blocks", Tag.TAG_STRING))
                            .equals(new HashSet<>(DEFAULT_IGNORED_BLOCKS)),
                    "Malformed policy data must fall back to the complete default ignored list");
            helper.assertTrue(restored.getItem(0).is(Items.DIRT) && restored.getItem(0).getCount() == 17
                            && restored.getItem(35).is(Items.DIAMOND) && restored.getItem(35).getCount() == 3,
                    "Malformed-policy fallback must preserve the original dirt and diamonds");
        } finally {
            WorkerGameTestSupport.discardWorker(source);
            WorkerGameTestSupport.discardWorker(restored);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_inventory_cleanup", timeoutTicks = 900)
    public static void learnedNativeTargetDropsPersistAcrossReloadAndClearOnTargetChange(GameTestHelper helper) {
        TargetDropFixture fixture = null;
        try {
            fixture = TargetDropFixture.create(helper);
            fixture.worker.startMining(List.of(PODZOL), 1);
            TargetDropFixture started = fixture;
            helper.runAfterDelay(1, () -> observeTargetCompletion(helper, started, 1));
        } catch (Throwable failure) {
            closeTargetFixture(fixture);
            helper.fail("Native target-drop protection setup failed: " + failure);
        }
    }

    private static void observeTargetCompletion(GameTestHelper helper, TargetDropFixture fixture, int elapsedTicks) {
        try {
            yieldNativeWork();
            if (fixture.worker.miningStatus().state() == MiningSession.State.COMPLETED) {
                helper.runAfterDelay(12, () -> assertTargetProtection(helper, fixture));
                return;
            }
            helper.assertTrue(elapsedTicks < 760,
                    "Native podzol completion did not finish within the focused pickup proof window");
            helper.runAfterDelay(1, () -> observeTargetCompletion(helper, fixture, elapsedTicks + 1));
        } catch (Throwable failure) {
            closeTargetFixture(fixture);
            helper.fail("Native target-drop protection observation failed: " + failure);
        }
    }

    private static void assertTargetProtection(GameTestHelper helper, TargetDropFixture fixture) {
        WorkerEntity reloaded = null;
        try {
            helper.assertTrue(helper.getLevel().getBlockState(fixture.chamber.target()).isAir(),
                    "The real native job must destroy the podzol target");
            helper.assertTrue(fixture.capture.calls == 1 && fixture.capture.sawDrop(Items.DIRT),
                    "The real BlockDropsEvent must observe the podzol dirt output exactly once");
            CompoundTag saved = fixture.worker.saveWithoutId(new CompoundTag());
            CompoundTag management = saved.getCompound("AutomatoneWorker").getCompound("InventoryManagement");
            helper.assertTrue(management.getList("Targets", Tag.TAG_STRING)
                            .contains(StringTag.valueOf(PODZOL.toString()))
                            && management.getList("ProtectedItems", Tag.TAG_STRING)
                            .contains(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(Items.DIRT).toString())),
                    "The final native target drop must persist its target set and learned dirt protection");

            reloaded = newWorker(helper.getLevel());
            reloaded.readAdditionalSaveData(saved);
            helper.assertTrue(reloaded.miningStatus().state() == MiningSession.State.COMPLETED
                            && reloaded.wantsToPickUp(new ItemStack(Items.DIRT)),
                    "A reloaded worker must still protect its learned native target output");
            reloaded.configureMining(List.of(STONE), 0);
            helper.assertFalse(reloaded.wantsToPickUp(new ItemStack(Items.DIRT)),
                    "Changing targets must clear learned protection for the old native output");
            CompoundTag changed = reloaded.saveWithoutId(new CompoundTag())
                    .getCompound("AutomatoneWorker").getCompound("InventoryManagement");
            helper.assertTrue(changed.getList("ProtectedItems", Tag.TAG_STRING).isEmpty(),
                    "A target change must persist cleared learned protection");
            closeTargetFixture(fixture);
            helper.succeed();
        } catch (Throwable failure) {
            closeTargetFixture(fixture);
            helper.fail("Target-drop protection persistence assertion failed: " + failure);
        } finally {
            WorkerGameTestSupport.discardWorker(reloaded);
        }
    }

    private static void assertPolicy(
            GameTestHelper helper,
            CompoundTag policy,
            List<String> expectedBlocks,
            boolean expectedOverride,
            String description
    ) {
        helper.assertTrue(stringList(policy.getList("Blocks", Tag.TAG_STRING)).equals(expectedBlocks)
                        && policy.getBoolean("Override") == expectedOverride,
                description + " must preserve its exact blocks and override state");
    }

    private static void assertExactInventory(GameTestHelper helper, WorkerEntity worker, String description) {
        helper.assertTrue(worker.getItem(0).is(Items.IRON_PICKAXE)
                        && worker.getItem(8).is(Items.RAW_IRON) && worker.getItem(8).getCount() == 11
                        && worker.getItem(9).is(Items.DIAMOND) && worker.getItem(9).getCount() == 2
                        && worker.getItem(35).is(Items.EMERALD) && worker.getItem(35).getCount() == 7,
                description + " must preserve distinct stacks in slots 0, 8, 9, and 35");
    }

    private static void fillFullWithIgnoredJunk(WorkerEntity worker) {
        worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int slot = 1; slot < worker.getContainerSize(); slot++) {
            worker.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
    }

    private static WorkerEntity newWorker(ServerLevel level) {
        WorkerEntity worker = WorkerMod.WORKER.get().create(level);
        if (worker == null) {
            throw new AssertionError("Registered worker entity type did not create a test worker");
        }
        return worker;
    }

    private static ItemEntity spawnImmediateDrop(ServerLevel level, WorkerEntity worker, Item item, int count) {
        return spawnImmediateDrop(level, worker, new ItemStack(item, count));
    }

    private static ItemEntity spawnImmediateDrop(ServerLevel level, WorkerEntity worker, ItemStack stack) {
        ItemEntity drop = new ItemEntity(level, worker.getX(), worker.getY(), worker.getZ(), stack);
        if (!level.addFreshEntity(drop)) {
            throw new AssertionError("The real pickup entity was rejected by the ServerLevel");
        }
        drop.setNoPickUpDelay();
        return drop;
    }

    private static int countItems(WorkerEntity worker, Item item) {
        int count = 0;
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            ItemStack stack = worker.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasMatchingCustomStack(WorkerEntity worker, Item item, CompoundTag expected) {
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            ItemStack stack = worker.getItem(slot);
            if (stack.is(item) && !stack.getComponentsPatch().isEmpty()
                    && stack.get(DataComponents.CUSTOM_DATA).matchedBy(expected)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemEntity> nearbyItems(ServerLevel level, WorkerEntity worker, Item item) {
        return level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                new AABB(worker.blockPosition()).inflate(4.0D), entity -> entity.getItem().is(item));
    }

    private static void discardNearbyItems(ServerLevel level, WorkerEntity worker) {
        for (ItemEntity item : level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                new AABB(worker.blockPosition()).inflate(5.0D), entity -> true)) {
            item.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    private static Set<String> stringSet(ListTag list) {
        Set<String> values = new HashSet<>();
        for (Tag value : list) {
            values.add(value.getAsString());
        }
        return values;
    }

    private static List<String> stringList(ListTag list) {
        List<String> values = new ArrayList<>();
        for (Tag value : list) {
            values.add(value.getAsString());
        }
        return values;
    }

    private static void yieldNativeWork() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Native pickup fixture pacing was interrupted", interrupted);
        }
    }

    private static void closeTargetFixture(TargetDropFixture fixture) {
        if (fixture != null) {
            fixture.close();
        }
    }

    private static final class DropCapture {
        private final WorkerEntity worker;
        private final BlockPos target;
        private int calls;
        private final List<ItemStack> drops = new ArrayList<>();

        private DropCapture(WorkerEntity worker, BlockPos target) {
            this.worker = worker;
            this.target = target;
        }

        @SubscribeEvent
        public void onBlockDrops(BlockDropsEvent event) {
            if (!(event.getBreaker() instanceof WorkerEntity breaker)
                    || breaker.getId() != worker.getId() || !target.equals(event.getPos())) {
                return;
            }
            calls++;
            for (ItemEntity drop : event.getDrops()) {
                drops.add(drop.getItem().copy());
            }
        }

        private boolean sawDrop(Item item) {
            return drops.stream().anyMatch(stack -> stack.is(item));
        }
    }

    private static final class TargetDropFixture {
        private final WorkerNativeMineProcessGameTest.MiningChamber chamber;
        private final WorkerEntity worker;
        private final IBaritone runtime;
        private final DropCapture capture;
        private boolean closed;

        private TargetDropFixture(
                WorkerNativeMineProcessGameTest.MiningChamber chamber,
                WorkerEntity worker,
                IBaritone runtime,
                DropCapture capture
        ) {
            this.chamber = chamber;
            this.worker = worker;
            this.runtime = runtime;
            this.capture = capture;
        }

        private static TargetDropFixture create(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            WorkerNativeMineProcessGameTest.MiningChamber chamber =
                    new WorkerNativeMineProcessGameTest.MiningChamber(level, helper.absolutePos(BlockPos.ZERO));
            WorkerEntity worker = null;
            DropCapture capture = null;
            try {
                chamber.build();
                chamber.replace(chamber.target(), Blocks.PODZOL.defaultBlockState());
                worker = WorkerNativeMineProcessGameTest.spawnWorker(level, chamber.workerPosition());
                worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                worker.setSelectedSlot(0);
                IBaritone runtime = worker.runtime();
                if (runtime == null) {
                    throw new AssertionError("Target-drop fixture requires a live native runtime");
                }
                capture = new DropCapture(worker, chamber.target());
                NeoForge.EVENT_BUS.register(capture);
                return new TargetDropFixture(chamber, worker, runtime, capture);
            } catch (Throwable failure) {
                if (capture != null) {
                    NeoForge.EVENT_BUS.unregister(capture);
                }
                WorkerGameTestSupport.discardWorker(worker);
                chamber.clearDrops();
                chamber.restore();
                throw failure;
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                runtime.getMineProcess().cancel();
                NeoForge.EVENT_BUS.unregister(capture);
                WorkerGameTestSupport.discardWorker(worker);
            } finally {
                chamber.clearDrops();
                chamber.restore();
            }
        }
    }
}
