package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerMenu;
import automatone.worker.WorkerNetwork;
import automatone.worker.WorkerRoster;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Dedicated-server regressions for the 36-slot worker inventory contract. */
@GameTestHolder("automatone_worker_m5_inventory_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerInventoryGameTest {
    private static final ResourceLocation IRON_ORE = ResourceLocation.withDefaultNamespace("iron_ore");

    private WorkerInventoryGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_migration", timeoutTicks = 120)
    public static void legacyNineSlotSaveMigratesAndLeavesStorageEmpty(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        WorkerEntity reloaded = null;
        try {
            worker.readAdditionalSaveData(legacySave(helper.getLevel(), 8));
            assertLegacyInventory(helper, worker, "legacy entity");

            CompoundTag saved = worker.saveWithoutId(new CompoundTag());
            reloaded = WorkerMod.WORKER.get().create(helper.getLevel());
            if (reloaded == null) {
                throw new AssertionError("Registered worker entity did not create the reload fixture");
            }
            reloaded.load(saved);
            assertLegacyInventory(helper, reloaded, "migrated entity");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
            WorkerGameTestSupport.discardWorker(reloaded);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_migration", timeoutTicks = 120)
    public static void fullInventorySaveReloadPreservesStorageAndComponents(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        WorkerEntity reloaded = null;
        try {
            ItemStack hotbar = new ItemStack(Items.IRON_INGOT, 3);
            ItemStack selected = new ItemStack(Items.DIAMOND_PICKAXE);
            selected.set(DataComponents.CUSTOM_NAME, Component.literal("selected-tool"));
            ItemStack storage = new ItemStack(Items.DIRT, 7);
            ItemStack last = new ItemStack(Items.EMERALD, 11);
            last.set(DataComponents.CUSTOM_NAME, Component.literal("last-slot"));
            worker.setItem(0, hotbar);
            worker.setItem(WorkerEntity.HOTBAR_SIZE - 1, selected);
            worker.setItem(WorkerEntity.HOTBAR_SIZE, storage);
            worker.setItem(WorkerEntity.INVENTORY_SIZE - 1, last);
            worker.setSelectedSlot(WorkerEntity.HOTBAR_SIZE - 1);

            CompoundTag saved = worker.saveWithoutId(new CompoundTag());
            reloaded = WorkerMod.WORKER.get().create(helper.getLevel());
            if (reloaded == null) {
                throw new AssertionError("Registered worker entity did not create the full inventory fixture");
            }
            reloaded.load(saved);
            helper.assertTrue(reloaded.getContainerSize() == WorkerEntity.INVENTORY_SIZE
                            && reloaded.selectedSlot() == WorkerEntity.HOTBAR_SIZE - 1,
                    "A modern save must reload the 36-slot container and selected hotbar index");
            assertMatches(helper, reloaded.getItem(0), hotbar, "hotbar slot 0");
            assertMatches(helper, reloaded.getItem(WorkerEntity.HOTBAR_SIZE - 1), selected, "hotbar slot 8");
            assertMatches(helper, reloaded.getItem(WorkerEntity.HOTBAR_SIZE), storage, "storage slot 9");
            assertMatches(helper, reloaded.getItem(WorkerEntity.INVENTORY_SIZE - 1), last, "storage slot 35");
            assertEmptyExcept(helper, reloaded, 0, WorkerEntity.HOTBAR_SIZE - 1,
                    WorkerEntity.HOTBAR_SIZE, WorkerEntity.INVENTORY_SIZE - 1);
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
            WorkerGameTestSupport.discardWorker(reloaded);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_menu", timeoutTicks = 180)
    public static void realMenuBindsAllSlotsAndMovesStacksAcrossHotbarBoundary(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer player = fixture.player();
            WorkerEntity worker = fixture.spawnOwned(player, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerMenu.open(player, worker.getUUID(), false, 0);
            WorkerMenu menu = fixture.menu(player);
            menu.showInventory(true);
            helper.assertTrue(menu.slots.size() == WorkerEntity.INVENTORY_SIZE + 36,
                    "The real menu must bind 36 worker slots and all 36 player slots");

            int expectedTotal = 0;
            for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
                ItemStack stack = namedCobblestone("worker-slot-" + slot, slot + 1);
                worker.setItem(slot, stack);
                expectedTotal += slot + 1;
            }
            for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
                ItemStack moved = menu.quickMoveStack(player, slot);
                helper.assertTrue(moved.getCount() == slot + 1 && worker.getItem(slot).isEmpty(),
                        "Shift-click must withdraw worker slot " + slot + " into the player inventory");
            }
            helper.assertTrue(totalItems(worker, player, menu) == expectedTotal,
                    "Withdrawing every worker slot must conserve all item counts");

            for (int playerSlot = 0; playerSlot < WorkerEntity.INVENTORY_SIZE; playerSlot++) {
                ItemStack moved = menu.quickMoveStack(player, playerMenuIndex(playerSlot));
                helper.assertFalse(moved.isEmpty(),
                        "Shift-click must return player slot " + playerSlot + " to the worker");
            }
            helper.assertTrue(totalItems(worker, player, menu) == expectedTotal
                            && allWorkerSlotsFilled(worker)
                            && allPlayerMainSlotsEmpty(player),
                    "Round-trip shift-clicks across hotbar and storage must conserve every stack");

            worker.setItem(WorkerEntity.INVENTORY_SIZE - 1, new ItemStack(Items.EMERALD, 2));
            player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
            menu.clicked(WorkerEntity.INVENTORY_SIZE - 1, 0, ClickType.SWAP, player);
            helper.assertTrue(worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).is(Items.GOLD_INGOT)
                            && worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).getCount() == 3
                            && player.getInventory().getItem(0).is(Items.EMERALD)
                            && player.getInventory().getItem(0).getCount() == 2,
                    "A native hotbar swap must exchange the last storage slot without duplication");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_menu", timeoutTicks = 180)
    public static void archivedMenuRejectsInsertionAndCloningAtEveryWorkerSlot(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer player = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity worker = fixture.spawnOwned(player, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
                worker.setItem(slot, namedCobblestone("archive-slot-" + slot, slot + 1));
            }
            UUID workerId = worker.getUUID();
            roster.retire(player.getUUID(), workerId, roster.view(player.getUUID(), workerId).revision());
            WorkerMenu.open(player, workerId, true, 0);
            WorkerMenu archive = fixture.menu(player);
            archive.showInventory(true);
            List<ItemStack> before = roster.archivedInventory(player.getUUID(), workerId);

            for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
                ItemStack carried = new ItemStack(Items.BEACON);
                archive.setCarried(carried);
                archive.clicked(slot, 0, ClickType.PICKUP, player);
                helper.assertTrue(ItemStack.matches(archive.getCarried(), carried)
                                && ItemStack.matches(roster.archivedInventory(player.getUUID(), workerId).get(slot), before.get(slot)),
                        "A carried stack must not insert into archived worker slot " + slot);
                archive.setCarried(ItemStack.EMPTY);
            }

            boolean oldCreative = player.getAbilities().instabuild;
            player.getAbilities().instabuild = true;
            try {
                for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
                    archive.clicked(slot, 2, ClickType.CLONE, player);
                    helper.assertTrue(archive.getCarried().isEmpty()
                                    && ItemStack.matches(roster.archivedInventory(player.getUUID(), workerId).get(slot), before.get(slot)),
                            "Creative clone must not copy archived worker slot " + slot);
                }
            } finally {
                player.getAbilities().instabuild = oldCreative;
            }
            helper.assertTrue(ItemStack.listMatches(roster.archivedInventory(player.getUUID(), workerId), before),
                    "All archived slot insertion and clone attempts must preserve the archive");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_collect", timeoutTicks = 180)
    public static void collectAllMovesOnlyWhatFitsForActiveAndArchivedWorkers(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer player = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity worker = fixture.spawnOwned(player, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            worker.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
            worker.setItem(WorkerEntity.INVENTORY_SIZE - 1, new ItemStack(Items.DIAMOND, 10));
            player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 60));
            for (int slot = 1; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 54));
            WorkerMenu.open(player, worker.getUUID(), false, 0);
            WorkerMenu active = fixture.menu(player);
            active.showInventory(true);
            long revision = roster.view(player.getUUID(), worker.getUUID()).revision();
            send(player, active, 1, WorkerNetwork.Action.COLLECT_ALL, revisionData(revision));
            CompoundTag collected = response(active);
            helper.assertTrue(collected.getString("Kind").equals("Success")
                            && collected.getInt("Collected") == 14
                            && worker.getItem(0).is(Items.IRON_INGOT) && worker.getItem(0).getCount() == 1
                            && worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).isEmpty()
                            && player.getInventory().getItem(0).getCount() == 64,
                    "Collect all must merge partial stacks, move storage-slot drops and report 14 moved items");

            revision = roster.view(player.getUUID(), worker.getUUID()).revision();
            send(player, active, 2, WorkerNetwork.Action.COLLECT_ALL, revisionData(revision));
            helper.assertTrue(response(active).getInt("Collected") == 0 && worker.getItem(0).getCount() == 1,
                    "A repeated collect-all request must be idempotent when no additional item fits");

            for (int slot = 1; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            worker.setItem(WorkerEntity.INVENTORY_SIZE - 1, new ItemStack(Items.GOLD_INGOT, 3));
            revision = roster.view(player.getUUID(), worker.getUUID()).revision();
            send(player, active, 3, WorkerNetwork.Action.COLLECT_ALL, revisionData(revision));
            helper.assertTrue(response(active).getInt("Collected") == 0
                            && worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).is(Items.GOLD_INGOT)
                            && worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).getCount() == 3,
                    "A full player inventory must leave collect-all overflow in the worker");

            player.closeContainer();
            revision = roster.view(player.getUUID(), worker.getUUID()).revision();
            UUID workerId = worker.getUUID();
            roster.retire(player.getUUID(), workerId, revision);
            WorkerMenu.open(player, workerId, true, 0);
            WorkerMenu archive = fixture.menu(player);
            archive.showInventory(true);
            revision = roster.view(player.getUUID(), workerId).revision();
            send(player, archive, 1, WorkerNetwork.Action.COLLECT_ALL, revisionData(revision));
            helper.assertTrue(response(archive).getInt("Collected") == 0
                            && roster.archivedInventory(player.getUUID(), workerId)
                            .get(WorkerEntity.INVENTORY_SIZE - 1).getCount() == 3,
                    "Archived collect-all must leave overflow in the selected archive");
            player.getInventory().setItem(1, new ItemStack(Items.GOLD_INGOT, 61));
            revision = roster.view(player.getUUID(), workerId).revision();
            send(player, archive, 2, WorkerNetwork.Action.COLLECT_ALL, revisionData(revision));
            helper.assertTrue(response(archive).getInt("Collected") == 3
                            && roster.archivedInventory(player.getUUID(), workerId)
                            .get(WorkerEntity.INVENTORY_SIZE - 1).isEmpty(),
                    "Archived collect-all must transfer overflow once player capacity is available");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_inventory_collect", timeoutTicks = 140)
    public static void collectAllKeepsComponentDistinctStacksSeparate(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer player = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity worker = fixture.spawnOwned(player, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            ItemStack playerPaper = new ItemStack(Items.PAPER, 60);
            playerPaper.set(DataComponents.CUSTOM_NAME, Component.literal("player-paper"));
            ItemStack workerPaper = new ItemStack(Items.PAPER, 3);
            workerPaper.set(DataComponents.CUSTOM_NAME, Component.literal("worker-paper"));
            worker.setItem(0, workerPaper);
            player.getInventory().setItem(0, playerPaper);
            WorkerMenu.open(player, worker.getUUID(), false, 0);
            WorkerMenu menu = fixture.menu(player);
            menu.showInventory(true);
            send(player, menu, 1, WorkerNetwork.Action.COLLECT_ALL,
                    revisionData(roster.view(player.getUUID(), worker.getUUID()).revision()));
            helper.assertTrue(response(menu).getInt("Collected") == 3
                            && ItemStack.matches(player.getInventory().getItem(0), playerPaper)
                            && worker.getItem(0).isEmpty()
                            && containsMatching(player, workerPaper),
                    "Collect all must conserve component-distinct stacks instead of merging them");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_inventory_native", timeoutTicks = 900)
    public static void storagePickaxeMovesToHotbarAndNativeMiningUsesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorkerNativeMineProcessGameTest.MiningChamber chamber =
                new WorkerNativeMineProcessGameTest.MiningChamber(level, helper.absolutePos(BlockPos.ZERO));
        ServerPlayer player = null;
        WorkerEntity worker = null;
        try {
            chamber.build();
            player = helper.makeMockServerPlayerInLevel();
            configurePlayerTransport(player);
            clearPlayerInventory(player);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(WorkerMod.CONTROLLER.get()));
            worker = WorkerNativeMineProcessGameTest.spawnWorker(level, chamber.workerPosition());
            worker.claim(player.getUUID());
            var nativeSettings = worker.runtime().getSettings().copy();
            nativeSettings.allowInventory.value = true;
            worker.applySettings(nativeSettings);
            worker.setItem(WorkerEntity.INVENTORY_SIZE - 1, new ItemStack(Items.IRON_PICKAXE));
            worker.setSelectedSlot(0);
            helper.assertTrue(worker.getMainHandItem().isEmpty(),
                    "The native inventory proof must begin with its only pickaxe in storage");
            worker.startMining(IRON_ORE, 1);
            observeNativeToolMining(helper, level, chamber, player, worker, 1);
        } catch (Throwable failure) {
            cleanupNativeToolFixture(player, worker, chamber);
            helper.fail("Storage-tool native mining setup failed: " + failure);
        }
    }

    private static void observeNativeToolMining(GameTestHelper helper,
                                                ServerLevel level,
                                                WorkerNativeMineProcessGameTest.MiningChamber chamber,
                                                ServerPlayer player,
                                                WorkerEntity worker,
                                                int ticks) {
        try {
            Thread.sleep(50L);
            if (level.getBlockState(chamber.target()).isAir()) {
                helper.assertTrue(worker.miningStatus().state() == MiningSession.State.COMPLETED
                                && worker.miningStatus().completed() == 1
                                && worker.getItem(WorkerEntity.INVENTORY_SIZE - 1).isEmpty()
                                && worker.getItem(0).is(Items.IRON_PICKAXE),
                        "Native mining must complete the target with the tool moved from storage; job="
                                + worker.miningStatus() + ", hotbar=" + worker.getItem(0) + ", storage="
                                + worker.getItem(WorkerEntity.INVENTORY_SIZE - 1));
                cleanupNativeToolFixture(player, worker, chamber);
                helper.succeed();
                return;
            }
            helper.assertTrue(ticks < 700,
                    "Native mining did not complete after moving the storage pickaxe to the hotbar; status="
                            + worker.miningStatus());
            helper.runAfterDelay(1, () -> observeNativeToolMining(helper, level, chamber, player, worker, ticks + 1));
        } catch (Throwable failure) {
            cleanupNativeToolFixture(player, worker, chamber);
            helper.fail("Storage-tool native mining observation failed: " + failure);
        }
    }

    private static void cleanupNativeToolFixture(ServerPlayer player, WorkerEntity worker,
                                                  WorkerNativeMineProcessGameTest.MiningChamber chamber) {
        if (player != null) {
            player.closeContainer();
            if (Objects.equals(player.server.getPlayerList().getPlayer(player.getUUID()), player)) {
                player.server.getPlayerList().remove(player);
            } else if (!player.isRemoved()) {
                player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        }
        WorkerGameTestSupport.discardWorker(worker);
        chamber.clearDrops();
        chamber.restore();
    }

    private static CompoundTag legacySave(ServerLevel level, int selectedSlot) {
        NonNullList<ItemStack> legacy = NonNullList.withSize(9, ItemStack.EMPTY);
        legacy.set(0, new ItemStack(Items.IRON_INGOT, 3));
        ItemStack selected = new ItemStack(Items.DIAMOND_PICKAXE);
        selected.set(DataComponents.CUSTOM_NAME, Component.literal("legacy-selected"));
        legacy.set(8, selected);
        CompoundTag inventory = new CompoundTag();
        ContainerHelper.saveAllItems(inventory, legacy, level.getServer().registryAccess());
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", 1);
        saved.put("Inventory", inventory);
        saved.putInt("SelectedSlot", selectedSlot);
        CompoundTag job = new CompoundTag();
        job.putString("Target", IRON_ORE.toString());
        job.putInt("Requested", 0);
        job.putLong("Completed", 0);
        job.putString("State", MiningSession.State.IDLE.name());
        job.putString("Error", "");
        saved.put("Job", job);
        CompoundTag root = new CompoundTag();
        root.put("AutomatoneWorker", saved);
        return root;
    }

    private static void assertLegacyInventory(GameTestHelper helper, WorkerEntity worker, String label) {
        helper.assertTrue(worker.getContainerSize() == WorkerEntity.INVENTORY_SIZE
                        && worker.selectedSlot() == WorkerEntity.HOTBAR_SIZE - 1,
                label + " must expose 36 slots and preserve selected hotbar slot 8");
        helper.assertTrue(worker.getItem(0).is(Items.IRON_INGOT) && worker.getItem(0).getCount() == 3,
                label + " must preserve legacy slot 0");
        ItemStack selected = worker.getItem(WorkerEntity.HOTBAR_SIZE - 1);
        helper.assertTrue(selected.is(Items.DIAMOND_PICKAXE)
                        && selected.get(DataComponents.CUSTOM_NAME) != null
                        && selected.get(DataComponents.CUSTOM_NAME).getString().equals("legacy-selected"),
                label + " must preserve legacy slot 8 and its components");
        assertEmptyExcept(helper, worker, 0, WorkerEntity.HOTBAR_SIZE - 1);
    }

    private static void assertEmptyExcept(GameTestHelper helper, WorkerEntity worker, int... filled) {
        for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
            boolean expected = false;
            for (int allowed : filled) {
                expected |= slot == allowed;
            }
            helper.assertTrue(expected || worker.getItem(slot).isEmpty(),
                    "Unexpected item in worker slot " + slot);
        }
    }

    private static void assertMatches(GameTestHelper helper, ItemStack actual, ItemStack expected, String slot) {
        helper.assertTrue(ItemStack.matches(actual, expected), "Inventory " + slot + " did not survive save/load");
    }

    private static ItemStack namedCobblestone(String name, int count) {
        ItemStack stack = new ItemStack(Items.COBBLESTONE, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static int playerMenuIndex(int playerSlot) {
        return WorkerEntity.INVENTORY_SIZE + (playerSlot < 9 ? 27 + playerSlot : playerSlot - 9);
    }

    private static int totalItems(WorkerEntity worker, ServerPlayer player, WorkerMenu menu) {
        int total = menu.getCarried().getCount();
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            total += worker.getItem(slot).getCount();
        }
        for (ItemStack stack : player.getInventory().items) {
            total += stack.getCount();
        }
        return total;
    }

    private static boolean allWorkerSlotsFilled(WorkerEntity worker) {
        for (int slot = 0; slot < WorkerEntity.INVENTORY_SIZE; slot++) {
            if (worker.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean allPlayerMainSlotsEmpty(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsMatching(ServerPlayer player, ItemStack expected) {
        return player.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, expected));
    }

    private static CompoundTag revisionData(long revision) {
        CompoundTag data = new CompoundTag();
        data.putLong("Revision", revision);
        return data;
    }

    private static CompoundTag response(WorkerMenu menu) {
        return menu.snapshot().getCompound("Response");
    }

    private static void send(ServerPlayer player, WorkerMenu menu, long sequence,
                             WorkerNetwork.Action action, CompoundTag data) {
        menu.handle(player, new WorkerNetwork.Intent(menu.containerId, menu.session(), sequence, action, data));
    }

    private static WorkerMenu requireMenu(ServerPlayer player) {
        if (!(player.containerMenu instanceof WorkerMenu menu)) {
            throw new AssertionError("Expected the real player container to be a WorkerMenu");
        }
        return menu;
    }

    static void configurePlayerTransport(ServerPlayer player) {
        ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                .add(AdvancedOpenScreenPayload.TYPE.id());
        ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                .add(WorkerNetwork.Snapshot.TYPE.id());
        ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                .add(WorkerNetwork.Notice.TYPE.id());
    }

    private static void clearPlayerInventory(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        player.getInventory().selected = 0;
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<WorkerEntity> workers = new ArrayList<>();

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            server = helper.getLevel().getServer();
        }

        private ServerPlayer player() {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            configurePlayerTransport(player);
            clearPlayerInventory(player);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(WorkerMod.CONTROLLER.get()));
            players.add(player);
            return player;
        }

        private WorkerEntity spawnOwned(ServerPlayer owner, ServerLevel level, BlockPos position) {
            WorkerEntity worker = WorkerMod.WORKER.get().create(level);
            if (worker == null) {
                throw new AssertionError("Registered worker entity did not create");
            }
            worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            worker.setNoGravity(true);
            if (!level.addFreshEntity(worker)) {
                throw new AssertionError("Dedicated server rejected the inventory fixture worker");
            }
            worker.claim(owner.getUUID());
            workers.add(worker);
            return worker;
        }

        private WorkerMenu menu(ServerPlayer player) {
            return requireMenu(player);
        }

        private void close() {
            for (ServerPlayer player : players) {
                player.closeContainer();
            }
            for (WorkerEntity worker : workers) {
                if (!worker.isRemoved() && worker.miningStatus().state() == MiningSession.State.RUNNING) {
                    worker.stopMining();
                }
                WorkerGameTestSupport.discardWorker(worker);
            }
            for (ServerPlayer player : players) {
                if (Objects.equals(server.getPlayerList().getPlayer(player.getUUID()), player)) {
                    server.getPlayerList().remove(player);
                } else if (!player.isRemoved()) {
                    player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }
}
