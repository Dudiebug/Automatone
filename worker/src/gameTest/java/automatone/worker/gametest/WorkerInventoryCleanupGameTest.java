package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMenu;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@GameTestHolder("automatone_worker_m5_inventory_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerInventoryCleanupGameTest {
    private static final ResourceLocation DIRT = BuiltInRegistries.BLOCK.getKey(Blocks.DIRT);
    private static final ResourceLocation WALL_TORCH = BuiltInRegistries.BLOCK.getKey(Blocks.WALL_TORCH);
    private static final ResourceLocation PODZOL = BuiltInRegistries.BLOCK.getKey(Blocks.PODZOL);
    private static final ResourceLocation STONE = BuiltInRegistries.BLOCK.getKey(Blocks.STONE);
    private static final List<String> DEFAULT_DISCARD_BLOCKS = List.of(
            "minecraft:dirt", "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:gravel",
            "minecraft:sand", "minecraft:red_sand", "minecraft:netherrack", "minecraft:tuff",
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite");

    private WorkerInventoryCleanupGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void newWorkerStartsWithDisabledDefaultCleanup(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        try {
            CompoundTag settings = worker.inventoryManagementSettings();
            helper.assertFalse(settings.getBoolean("Enabled"),
                    "New workers must start with automatic inventory cleanup disabled");
            helper.assertTrue(settings.getInt("Keep") == 64,
                    "New workers must use the default retained count of 64");
            helper.assertTrue(settings.getList("Blocks", Tag.TAG_STRING).size() == DEFAULT_DISCARD_BLOCKS.size()
                            && stringSet(settings.getList("Blocks", Tag.TAG_STRING)).equals(
                            new HashSet<>(DEFAULT_DISCARD_BLOCKS)),
                    "New workers must expose the complete default discard block list");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void keepQuota63CollectsOnly63AndLeavesRemainderInWorld(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity drop = null;
        try {
            worker.applyInventoryManagement(true, 63, List.of(DIRT));
            drop = new ItemEntity(helper.getLevel(), worker.getX(), worker.getY(), worker.getZ(),
                    new ItemStack(Items.DIRT, 64));
            helper.assertTrue(helper.getLevel().addFreshEntity(drop), "The real test drop must enter the server world");
            drop.setNoPickUpDelay();
            worker.aiStep();
            helper.assertTrue(countItems(worker, Items.DIRT) == 63,
                    "A keep quota of 63 must retain exactly 63 dirt items");
            helper.assertTrue(!drop.isRemoved() && drop.getItem().is(Items.DIRT) && drop.getItem().getCount() == 1,
                    "The one item above the quota must remain as a real world remainder");
            worker.aiStep();
            helper.assertTrue(!drop.isRemoved() && drop.getItem().getCount() == 1,
                    "A second pickup tick must not consume the retained-quota remainder");
        } finally {
            if (drop != null) {
                drop.discard();
            }
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void fullInventoryEjectsOneWholeJunkStackForWantedDrop(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity wanted = null;
        try {
            worker.applyInventoryManagement(true, 64, List.of(DIRT));
            fillFullWithJunk(worker);
            wanted = spawnImmediateDrop(helper.getLevel(), worker, Items.DIAMOND, 1);
            worker.aiStep();

            List<ItemEntity> ejected = nearbyItems(helper.getLevel(), worker, Items.DIRT);
            helper.assertTrue(countItems(worker, Items.DIRT) >= 64,
                    "Making room must retain at least the configured 64-item dirt reserve");
            helper.assertTrue(worker.getItem(0).is(Items.IRON_PICKAXE),
                    "Cleanup must preserve the worker's selected tool");
            helper.assertTrue(countItems(worker, Items.DIAMOND) == 1 && wanted.isRemoved(),
                    "A wanted item must be collected after only the needed junk slot is ejected");
            helper.assertTrue(ejected.size() == 1 && ejected.getFirst().getItem().getCount() == 64,
                    "Full cleanup must eject one complete excess dirt stack as a world ItemEntity");
            helper.assertTrue(countItems(worker, Items.DIRT)
                            + ejected.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 35 * 64,
                    "Whole-stack cleanup must conserve every dirt item across inventory and world entities");
        } finally {
            if (wanted != null) {
                wanted.discard();
            }
            discardNearbyItems(helper.getLevel(), worker);
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 120)
    public static void openInventoryMenuDefersCleanupUntilClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = null;
        WorkerEntity worker = null;
        ItemEntity wanted = null;
        try {
            player = helper.makeMockServerPlayerInLevel();
            WorkerInventoryGameTest.configurePlayerTransport(player);
            helper.assertTrue(Objects.equals(server.getPlayerList().getPlayer(player.getUUID()), player),
                    "The cleanup guard must observe a player registered in the server PlayerList");
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new ItemStack(WorkerMod.CONTROLLER.get()));

            worker = WorkerGameTestSupport.spawnWorker(helper);
            worker.claim(player.getUUID());
            worker.applyInventoryManagement(true, 64, List.of(DIRT));
            fillFullWithJunk(worker);

            WorkerMenu.open(player, worker.getUUID(), false, 0);
            helper.assertTrue(player.containerMenu instanceof WorkerMenu,
                    "The owned worker menu must bind before cleanup is tested");
            wanted = spawnImmediateDrop(level, worker, Items.DIAMOND, 1);
            worker.aiStep();
            helper.assertTrue(!wanted.isRemoved() && wanted.getItem().is(Items.DIAMOND)
                            && nearbyItems(level, worker, Items.DIRT).isEmpty(),
                    "An open worker inventory menu must defer cleanup ejection and wanted pickup");

            player.closeContainer();
            worker.aiStep();
            List<ItemEntity> ejected = nearbyItems(level, worker, Items.DIRT);
            helper.assertTrue(wanted.isRemoved() && countItems(worker, Items.DIAMOND) == 1
                            && ejected.size() == 1 && ejected.getFirst().getItem().getCount() == 64,
                    "Closing the menu must allow one real excess dirt entity to be ejected and collect the diamond");
        } finally {
            if (wanted != null) {
                wanted.discard();
            }
            if (worker != null) {
                discardNearbyItems(level, worker);
            }
            if (player != null) {
                player.closeContainer();
            }
            WorkerGameTestSupport.discardWorker(worker);
            if (player != null) {
                if (Objects.equals(server.getPlayerList().getPlayer(player.getUUID()), player)) {
                    server.getPlayerList().remove(player);
                } else if (!player.isRemoved()) {
                    player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void partialTrashStacksMergeBeforeWholeExcessEjection(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity wanted = null;
        try {
            worker.applyInventoryManagement(true, 64, List.of(DIRT));
            for (int slot = 0; slot < 34; slot++) {
                worker.setItem(slot, new ItemStack(Items.RAW_IRON, 64));
            }
            worker.setItem(34, new ItemStack(Items.DIRT, 48));
            worker.setItem(35, new ItemStack(Items.DIRT, 48));
            wanted = spawnImmediateDrop(helper.getLevel(), worker, Items.DIAMOND, 1);
            worker.aiStep();

            List<ItemEntity> ejected = nearbyItems(helper.getLevel(), worker, Items.DIRT);
            helper.assertTrue(worker.getItem(34).is(Items.DIRT) && worker.getItem(34).getCount() == 64,
                    "Identical partial discard stacks must merge into a full retained stack");
            helper.assertTrue(worker.getItem(35).is(Items.DIAMOND) && worker.getItem(35).getCount() == 1,
                    "The merged stack's freed slot must receive the wanted item");
            helper.assertTrue(ejected.size() == 1 && ejected.getFirst().getItem().getCount() == 32,
                    "Only the whole 32-item excess stack must be ejected after merging");
            helper.assertTrue(countItems(worker, Items.RAW_IRON) == 34 * 64,
                    "Merging cleanup must preserve all protected ordinary items");
            helper.assertTrue(countItems(worker, Items.RAW_IRON) + countItems(worker, Items.DIRT)
                            + countItems(worker, Items.DIAMOND)
                            + ejected.stream().mapToInt(entity -> entity.getItem().getCount()).sum()
                            == 34 * 64 + 48 + 48 + 1,
                    "Partial-stack cleanup must conserve all item counts across inventory and world entities");
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
    public static void cleanupPreservesToolsCustomComponentsAndTargetItems(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity wanted = null;
        try {
            worker.applyInventoryManagement(true, 64, List.of(DIRT, WALL_TORCH));
            worker.configureMining(List.of(WALL_TORCH), 0);
            worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            ItemStack customDirt = new ItemStack(Items.DIRT, 64);
            CompoundTag customTag = new CompoundTag();
            customTag.putString("marker", "protected");
            customDirt.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
            worker.setItem(1, customDirt);
            worker.setItem(2, new ItemStack(Items.TORCH, 64));
            worker.setItem(3, new ItemStack(Items.RAW_IRON, 64));
            for (int slot = 4; slot < worker.getContainerSize(); slot++) {
                worker.setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            wanted = spawnImmediateDrop(helper.getLevel(), worker, Items.DIAMOND, 1);
            worker.aiStep();

            helper.assertTrue(worker.getItem(0).is(Items.IRON_PICKAXE),
                    "Tools must never be selected as automatic cleanup candidates");
            helper.assertTrue(worker.getItem(1).is(Items.DIRT)
                            && !worker.getItem(1).getComponentsPatch().isEmpty()
                            && worker.getItem(1).get(DataComponents.CUSTOM_DATA).matchedBy(customTag),
                    "A dirt stack with custom components must remain protected");
            helper.assertTrue(worker.getItem(2).is(Items.TORCH),
                    "The selected wall-torch target's shared torch item form must remain protected");
            helper.assertTrue(worker.getItem(3).is(Items.RAW_IRON),
                    "Non-block items must remain protected");
            helper.assertTrue(countItems(worker, Items.DIAMOND) == 1 && wanted.isRemoved(),
                    "Protected stacks must not prevent a wanted item from using one ordinary dirt slot");
            List<ItemEntity> ejected = nearbyItems(helper.getLevel(), worker, Items.DIRT);
            helper.assertTrue(ejected.size() == 1 && ejected.getFirst().getItem().getCount() == 64,
                    "Only ordinary component-free dirt may be ejected");
            helper.assertTrue(nearbyItems(helper.getLevel(), worker, Items.TORCH).isEmpty()
                            && nearbyItems(helper.getLevel(), worker, Items.RAW_IRON).isEmpty(),
                    "Cleanup must never eject shared target item forms or non-block items");
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
    public static void cancelledEjectionSpawnLeavesInventoryAndWantedStackUnchanged(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity wanted = null;
        CancelDirtJoin listener = new CancelDirtJoin(worker);
        NeoForge.EVENT_BUS.register(listener);
        try {
            worker.applyInventoryManagement(true, 64, List.of(DIRT));
            fillFullWithJunk(worker);
            wanted = spawnImmediateDrop(helper.getLevel(), worker, Items.DIAMOND, 1);
            worker.aiStep();
            helper.assertTrue(listener.attempts > 0,
                    "Full cleanup must attempt to publish an excess dirt ItemEntity");
            helper.assertTrue(countItems(worker, Items.DIRT) == 35 * 64
                            && worker.getItem(0).is(Items.IRON_PICKAXE),
                    "A canceled ejection must leave every source inventory stack unchanged");
            helper.assertTrue(!wanted.isRemoved() && wanted.getItem().is(Items.DIAMOND)
                            && wanted.getItem().getCount() == 1,
                    "A wanted pickup must remain intact when its required ejection is canceled");
            helper.assertTrue(nearbyItems(helper.getLevel(), worker, Items.DIRT).isEmpty(),
                    "A canceled EntityJoinLevelEvent must publish no replacement junk entity");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            if (wanted != null) {
                wanted.discard();
            }
            discardNearbyItems(helper.getLevel(), worker);
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void workerTrashEjectionCannotBeRecollectedAfterPickupDelay(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity wanted = null;
        try {
            worker.applyInventoryManagement(true, 64, List.of(DIRT));
            fillFullWithJunk(worker);
            wanted = spawnImmediateDrop(helper.getLevel(), worker, Items.DIAMOND, 1);
            worker.aiStep();
            List<ItemEntity> ejected = nearbyItems(helper.getLevel(), worker, Items.DIRT);
            helper.assertTrue(ejected.size() == 1 && ejected.getFirst().getItem().getCount() == 64,
                    "The self-recollection regression needs one identified ejected dirt stack");
            ItemEntity ownTrash = ejected.getFirst();
            worker.setItem(1, ItemStack.EMPTY);
            int dirtAfterExplicitRemoval = countItems(worker, Items.DIRT);
            ItemEntity finalWanted = wanted;
            helper.runAfterDelay(45, () -> {
                try {
                    helper.assertTrue(!ownTrash.isRemoved() && ownTrash.getItem().is(Items.DIRT)
                                    && ownTrash.getItem().getCount() == 64,
                            "A worker must not recollect its own cleanup drop after the pickup delay");
                    helper.assertTrue(countItems(worker, Items.DIRT) == dirtAfterExplicitRemoval,
                            "The free slot must remain free of the worker's previously ejected trash");
                    helper.assertTrue(finalWanted.isRemoved(),
                            "The wanted pickup must remain collected while cleanup trash stays in the world");
                    helper.succeed();
                } catch (Throwable failure) {
                    helper.fail("Worker cleanup self-recollection observation failed: " + failure);
                } finally {
                    finalWanted.discard();
                    discardNearbyItems(helper.getLevel(), worker);
                    WorkerGameTestSupport.discardWorker(worker);
                }
            });
        } catch (Throwable failure) {
            if (wanted != null) {
                wanted.discard();
            }
            discardNearbyItems(helper.getLevel(), worker);
            WorkerGameTestSupport.discardWorker(worker);
            helper.fail("Worker cleanup self-recollection setup failed: " + failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_cleanup", timeoutTicks = 100)
    public static void malformedOrInvalidSavedPolicyDisablesCleanupWithoutLosingItems(GameTestHelper helper) {
        WorkerEntity source = WorkerGameTestSupport.spawnWorker(helper);
        WorkerEntity malformed = null;
        WorkerEntity invalid = null;
        WorkerEntity invalidIdentifier = null;
        try {
            source.setItem(0, new ItemStack(Items.DIRT, 17));
            source.setItem(35, new ItemStack(Items.DIAMOND, 3));
            CompoundTag malformedSave = source.saveWithoutId(new CompoundTag());
            malformedSave.getCompound("AutomatoneWorker").getCompound("InventoryManagement").remove("Blocks");
            malformed = newWorker(helper.getLevel());
            malformed.readAdditionalSaveData(malformedSave);
            assertDisabledPolicyAndItems(helper, malformed, "malformed");

            CompoundTag invalidSave = source.saveWithoutId(new CompoundTag());
            ListTag invalidBlocks = new ListTag();
            invalidBlocks.add(StringTag.valueOf("minecraft:not_a_registered_block"));
            invalidSave.getCompound("AutomatoneWorker").getCompound("InventoryManagement")
                    .put("Blocks", invalidBlocks);
            invalid = newWorker(helper.getLevel());
            invalid.readAdditionalSaveData(invalidSave);
            assertDisabledPolicyAndItems(helper, invalid, "invalid");

            CompoundTag invalidIdentifierSave = source.saveWithoutId(new CompoundTag());
            ListTag malformedIdentifier = new ListTag();
            malformedIdentifier.add(StringTag.valueOf("NOT VALID:block"));
            invalidIdentifierSave.getCompound("AutomatoneWorker").getCompound("InventoryManagement")
                    .put("Blocks", malformedIdentifier);
            invalidIdentifier = newWorker(helper.getLevel());
            invalidIdentifier.readAdditionalSaveData(invalidIdentifierSave);
            assertDisabledPolicyAndItems(helper, invalidIdentifier, "malformed-identifier");
        } finally {
            WorkerGameTestSupport.discardWorker(source);
            WorkerGameTestSupport.discardWorker(malformed);
            WorkerGameTestSupport.discardWorker(invalid);
            WorkerGameTestSupport.discardWorker(invalidIdentifier);
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_inventory_cleanup", timeoutTicks = 900)
    public static void learnedTargetProtectionSurvivesReloadAndClearsWhenTargetsChange(GameTestHelper helper) {
        TargetDropFixture fixture = null;
        try {
            fixture = TargetDropFixture.create(helper);
            fixture.worker.applyInventoryManagement(true, 0, List.of(DIRT));
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
                    "Native podzol completion did not finish within the focused cleanup proof window");
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
                    "The real BlockDropsEvent must observe the podzol's dirt output exactly once");
            CompoundTag saved = fixture.worker.saveWithoutId(new CompoundTag());
            CompoundTag management = saved.getCompound("AutomatoneWorker").getCompound("InventoryManagement");
            helper.assertTrue(management.getList("Targets", Tag.TAG_STRING).contains(StringTag.valueOf(PODZOL.toString()))
                            && management.getList("ProtectedItems", Tag.TAG_STRING)
                            .contains(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(Items.DIRT).toString())),
                    "The final completion drop must persist the target set and learned dirt protection");

            reloaded = newWorker(helper.getLevel());
            reloaded.readAdditionalSaveData(saved);
            helper.assertTrue(reloaded.miningStatus().state() == MiningSession.State.COMPLETED
                            && reloaded.wantsToPickUp(new ItemStack(Items.DIRT)),
                    "A reloaded worker must still treat its learned target output as protected");
            reloaded.configureMining(List.of(STONE), 0);
            helper.assertFalse(reloaded.wantsToPickUp(new ItemStack(Items.DIRT)),
                    "Changing the target set must clear learned protection for the old target output");
            CompoundTag changed = reloaded.saveWithoutId(new CompoundTag())
                    .getCompound("AutomatoneWorker").getCompound("InventoryManagement");
            helper.assertTrue(changed.getList("ProtectedItems", Tag.TAG_STRING).isEmpty(),
                    "A target-set change must persist the cleared learned protection");
            closeTargetFixture(fixture);
            helper.succeed();
        } catch (Throwable failure) {
            closeTargetFixture(fixture);
            helper.fail("Target-drop protection persistence assertion failed: " + failure);
        } finally {
            WorkerGameTestSupport.discardWorker(reloaded);
        }
    }

    private static void assertDisabledPolicyAndItems(
            GameTestHelper helper, WorkerEntity worker, String policyKind) {
        CompoundTag settings = worker.inventoryManagementSettings();
        helper.assertFalse(settings.getBoolean("Enabled"),
                "A " + policyKind + " saved policy must fail closed with cleanup disabled");
        helper.assertTrue(countItems(worker, Items.DIRT) == 17 && countItems(worker, Items.DIAMOND) == 3,
                "A " + policyKind + " saved policy must not lose inventory items");
    }

    private static WorkerEntity newWorker(ServerLevel level) {
        WorkerEntity worker = WorkerMod.WORKER.get().create(level);
        if (worker == null) {
            throw new AssertionError("Registered worker entity type did not create a test worker");
        }
        return worker;
    }

    private static void fillFullWithJunk(WorkerEntity worker) {
        worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int slot = 1; slot < worker.getContainerSize(); slot++) {
            worker.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
    }

    private static ItemEntity spawnImmediateDrop(ServerLevel level, WorkerEntity worker, Item item, int count) {
        ItemEntity drop = new ItemEntity(level, worker.getX(), worker.getY(), worker.getZ(), new ItemStack(item, count));
        if (!level.addFreshEntity(drop)) {
            throw new AssertionError("The real wanted pickup entity was rejected by the ServerLevel");
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

    private static void yieldNativeWork() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Native cleanup fixture pacing was interrupted", interrupted);
        }
    }

    private static void closeTargetFixture(TargetDropFixture fixture) {
        if (fixture != null) {
            fixture.close();
        }
    }

    public static final class CancelDirtJoin {
        private final WorkerEntity worker;
        private int attempts;

        private CancelDirtJoin(WorkerEntity worker) {
            this.worker = worker;
        }

        @SubscribeEvent
        public void onEntityJoin(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.DIRT)
                    && event.getLevel().equals(worker.level()) && item.distanceToSqr(worker) < 1.0D) {
                attempts++;
                event.setCanceled(true);
            }
        }
    }

    public static final class DropCapture {
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
