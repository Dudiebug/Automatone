package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerChunkLoading;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerMenu;
import automatone.worker.WorkerNetwork;
import automatone.worker.WorkerRelocation;
import automatone.worker.WorkerRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Dedicated-server contract tests for M5.12 global collection and retirement. */
@GameTestHolder("automatone_worker_m5_collection_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerCollectionGameTest {
    private static final int ITEM_PAGE_SIZE = 36;

    private WorkerCollectionGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_collection_query", timeoutTicks = 240)
    public static void collectionAggregatesCrossDimensionVariantsAndProtectsActiveContents(GameTestHelper helper) {
        withPreparedNether(helper, () -> {
            Fixture fixture = new Fixture(helper);
            WorkerEntity unloaded = null;
            try {
                ServerPlayer owner = fixture.player();
                ServerPlayer foreignOwner = fixture.player();
                ServerLevel overworld = helper.getLevel();
                ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
                helper.assertTrue(nether != null, "The dedicated server must provide the Nether");
                WorkerEntity first = fixture.spawnOwned(owner, overworld,
                        helper.absolutePos(new BlockPos(0, 1, 0)));
                WorkerEntity second = fixture.spawnOwned(owner, nether, new BlockPos(0, 80, 0));
                WorkerEntity foreign = fixture.spawnOwned(foreignOwner, overworld,
                        helper.absolutePos(new BlockPos(4, 1, 0)));
                unloaded = first;

                ItemStack sharedFirst = named(Items.DIRT, 3, "shared component");
                ItemStack sharedSecond = named(Items.DIRT, 4, "shared component");
                ItemStack separate = named(Items.DIRT, 2, "separate component");
                first.setItem(0, new ItemStack(Items.IRON_PICKAXE));
                first.setItem(1, sharedFirst);
                first.setItem(2, separate);
                first.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
                first.setItem(4, new ItemStack(Items.COBBLESTONE, 5));
                first.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                first.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                second.setItem(1, sharedSecond);
                second.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
                second.setItem(3, new ItemStack(Items.COBBLESTONE, 6));
                foreign.setItem(0, new ItemStack(Items.QUARTZ, 8));

                WorkerMenu.open(owner, first.getUUID(), false, 0);
                if (!(owner.containerMenu instanceof WorkerMenu selectedMenu)
                        || !first.getUUID().equals(selectedMenu.worker())) {
                    throw new AssertionError("Expected the worker detail menu before opening global collection");
                }
                send(owner, selectedMenu, 1, WorkerNetwork.Action.OPEN_COLLECTION, new CompoundTag());
                if (!(owner.containerMenu instanceof WorkerMenu menu)
                        || menu.worker() != null
                        || !menu.snapshot().getBoolean("OpenCollection")
                        || !menu.snapshot().contains("Collection", Tag.TAG_COMPOUND)
                        || menu.snapshot().getCompound("Collection").getInt("Mode") != 0) {
                    throw new AssertionError("OPEN_COLLECTION must replace a worker menu with the default global collection");
                }
                send(owner, menu, 1, WorkerNetwork.Action.COLLECTION_QUERY,
                        queryData(0, "", "", "", 0));
                CompoundTag collection = collection(menu);
                assertCollectionShape(helper, collection);
                helper.assertTrue(collection.getInt("Count") == 3
                                && collection.getInt("Selected") == 0
                                && collection.getLong("SelectedAmount") == 0
                                && collection.getInt("SourceCount") == 2
                                && collection.getInt("Unavailable") == 0,
                        "Active collection must aggregate only the three eligible item variants from both dimensions");

                CompoundTag shared = findVariant(helper, collection, named(Items.DIRT, 1, "shared component"));
                CompoundTag separateRow = findVariant(helper, collection, named(Items.DIRT, 1, "separate component"));
                CompoundTag cobblestone = findVariant(helper, collection, new ItemStack(Items.COBBLESTONE));
                helper.assertTrue(shared.getLong("Count") == 7 && separateRow.getLong("Count") == 2
                                && cobblestone.getLong("Count") == 11,
                        "Exact components must remain separate while equal variants aggregate across dimensions");
                helper.assertTrue(sourceAmount(shared, first.getUUID()) == 3
                                && sourceAmount(shared, second.getUUID()) == 4
                                && sourceAmount(shared, first.getUUID()) + sourceAmount(shared, second.getUUID()) == 7,
                        "The shared variant must expose the real per-worker source quantities");
                helper.assertTrue(!containsItem(collection, Items.IRON_PICKAXE)
                                && !containsItem(collection, Items.SHIELD)
                                && !containsItem(collection, Items.IRON_HELMET)
                                && !containsItem(collection, Items.QUARTZ)
                                && source(collection, foreign.getUUID()) == null,
                        "Active tools and equipment must never be collectible");

                send(owner, menu, 2, WorkerNetwork.Action.COLLECTION_QUERY,
                        queryData(0, "", foreign.getUUID().toString(), "", 0));
                assertError(helper, menu, "NOT_OWNER");
                collection = collection(menu);
                helper.assertTrue(collection.getInt("SourceCount") == 2
                                && !containsItem(collection, Items.QUARTZ),
                        "A foreign worker filter must be rejected without exposing or changing collection state");

                long revision = collection.getLong("Revision");
                send(owner, menu, 3, WorkerNetwork.Action.COLLECTION_SELECT,
                        selectData(revision, shared.getUUID("Variant"), true));
                collection = collection(menu);
                helper.assertTrue(collection.getInt("Selected") == 1
                                && collection.getLong("SelectedAmount") == 7,
                        "Selecting one exact component variant must preserve its aggregate amount");
                send(owner, menu, 4, WorkerNetwork.Action.COLLECTION_CLEAR,
                        revisionData(collection.getLong("Revision")));
                collection = collection(menu);
                helper.assertTrue(collection.getInt("Selected") == 0
                                && collection.getLong("SelectedAmount") == 0,
                        "Clear must remove every selected variant");

                send(owner, menu, 5, WorkerNetwork.Action.COLLECTION_SOURCE_PAGE, pageData(1));
                collection = collection(menu);
                helper.assertTrue(collection.getInt("SourcePage") == 0
                                && collection.getInt("WorkerOptionCount") == 2
                                && collection.getList("WorkerOptions", Tag.TAG_COMPOUND).size() == 2,
                        "The bounded worker filter page must clamp an out-of-range page while preserving both workers");

                send(owner, menu, 6, WorkerNetwork.Action.COLLECTION_QUERY,
                        queryData(0, Level.NETHER.location().toString(), "", "", 0));
                collection = collection(menu);
                helper.assertTrue(collection.getInt("SourceCount") == 1
                                && collection.getList("Sources", Tag.TAG_COMPOUND).size() == 1
                                && collection.getList("Sources", Tag.TAG_COMPOUND).getCompound(0)
                                .getUUID("Worker").equals(second.getUUID())
                                && collection.getInt("Count") == 2,
                        "The dimension filter must scope both sources and aggregate variants to the Nether worker");

                send(owner, menu, 7, WorkerNetwork.Action.COLLECTION_QUERY,
                        queryData(0, "", first.getUUID().toString(), "minecraft:dirt", 0));
                collection = collection(menu);
                helper.assertTrue(collection.getInt("SourceCount") == 1
                                && collection.getInt("Count") == 2
                                && collection.getString("Worker").equals(first.getUUID().toString()),
                        "The worker and registry-id filters must retain only the requested owner's worker variants");

                UUID pendingRequest = UUID.randomUUID();
                WorkerRelocation relocation = WorkerRelocation.get(helper.getLevel().getServer());
                WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
                WorkerRoster.View firstView = roster.view(owner.getUUID(), first.getUUID());
                relocation.relocate(owner.getUUID(), pendingRequest, first.getUUID(), firstView.revision(),
                        Level.NETHER);
                fixture.trackPending(relocation, owner.getUUID(), pendingRequest);
                send(owner, menu, 8, WorkerNetwork.Action.COLLECTION_QUERY,
                        queryData(0, "", "", "", 0));
                collection = collection(menu);
                CompoundTag pendingSource = source(collection, first.getUUID());
                helper.assertTrue(collection.getInt("Unavailable") == 1
                                && pendingSource != null && !pendingSource.getBoolean("Available")
                                && !containsItem(collection, Items.IRON_PICKAXE)
                                && collection.getInt("Count") == 2,
                        "A pending active relocation must be explicit and must not expose saved stale contents");

                relocation.cancel(owner.getUUID(), pendingRequest);
                unloaded.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                UUID unloadedId = unloaded.getUUID();
                helper.assertTrue(unloaded.isRemoved()
                                && roster.list(owner.getUUID(), false).stream()
                                .anyMatch(view -> view.worker().equals(unloadedId)),
                        "A healthy unloaded worker must remain an owned offline roster source");
                releaseFixtureTickets(unloaded);
                send(owner, menu, 9, WorkerNetwork.Action.COLLECTION_QUERY,
                        queryData(0, "", "", "", 0));
                collection = collection(menu);
                CompoundTag unloadedSource = source(collection, unloaded.getUUID());
                helper.assertTrue(collection.getInt("Unavailable") == 1
                                && unloadedSource != null && !unloadedSource.getBoolean("Available")
                                && collection.getInt("Count") == 2
                                && !hasVariant(helper, collection, separate)
                                && !containsItem(collection, Items.IRON_PICKAXE),
                        "A healthy unloaded worker must be unavailable and must not expose its saved contents");
            } finally {
                if (unloaded != null && unloaded.isRemoved()) {
                    releaseFixtureTickets(unloaded);
                    WorkerRoster.get(helper.getLevel().getServer())
                            .removed(unloaded, Entity.RemovalReason.DISCARDED);
                }
                fixture.close();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_collection_selection", timeoutTicks = 180)
    public static void collectionSearchSelectAllIncludesOffscreenVariantsAndClear(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerEntity first = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity second = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(2, 1, 0)));
            for (int index = 0; index < WorkerEntity.INVENTORY_SIZE; index++) {
                first.setItem(index, named(Items.DIRT, 1, "offscreen-" + index));
            }
            for (int index = 0; index < 4; index++) {
                second.setItem(index, named(Items.DIRT, 1, "offscreen-" + (WorkerEntity.INVENTORY_SIZE + index)));
            }

            WorkerMenu menu = openRoster(owner);
            send(owner, menu, 1, WorkerNetwork.Action.COLLECTION_QUERY,
                    queryData(0, "", "", "minecraft:dirt", 0));
            CompoundTag collection = collection(menu);
            helper.assertTrue(collection.getInt("Count") == 40
                            && collection.getList("Items", Tag.TAG_COMPOUND).size() == ITEM_PAGE_SIZE
                            && collection.getInt("Page") == 0,
                    "Registry-id search must find all 40 exact variants while the first item page stays bounded at 36");

            send(owner, menu, 2, WorkerNetwork.Action.COLLECTION_QUERY,
                    queryData(0, "", "", "offscreen", 0));
            collection = collection(menu);
            helper.assertTrue(collection.getInt("Count") == 40
                            && collection.getList("Items", Tag.TAG_COMPOUND).size() == ITEM_PAGE_SIZE,
                    "Display-name search must retain all 40 matching component variants");
            long revision = collection.getLong("Revision");
            send(owner, menu, 3, WorkerNetwork.Action.COLLECTION_SELECT_ALL,
                    revisionData(revision));
            collection = collection(menu);
            helper.assertTrue(collection.getInt("Selected") == 40
                            && collection.getLong("SelectedAmount") == 40
                            && allSelected(collection.getList("Items", Tag.TAG_COMPOUND)),
                    "Select all must select every filtered variant, including the 4 results beyond page one");

            send(owner, menu, 4, WorkerNetwork.Action.COLLECTION_QUERY,
                    queryData(0, "", "", "offscreen", 1));
            collection = collection(menu);
            helper.assertTrue(collection.getInt("Page") == 1
                            && collection.getList("Items", Tag.TAG_COMPOUND).size() == 4
                            && allSelected(collection.getList("Items", Tag.TAG_COMPOUND))
                            && collection.getInt("Selected") == 40,
                    "Selection must survive a page change and mark the offscreen page as selected");

            send(owner, menu, 5, WorkerNetwork.Action.COLLECTION_CLEAR,
                    revisionData(collection.getLong("Revision")));
            collection = collection(menu);
            helper.assertTrue(collection.getInt("Selected") == 0
                            && collection.getLong("SelectedAmount") == 0
                            && noneSelected(collection.getList("Items", Tag.TAG_COMPOUND)),
                    "Clear must remove offscreen and visible selections together");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_collection_transfer", timeoutTicks = 180)
    public static void collectionTransfersOnlyWhatPlayerCapacityAccepts(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerEntity diamonds = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity emeralds = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(2, 1, 0)));
            diamonds.setItem(0, new ItemStack(Items.DIAMOND, 20));
            emeralds.setItem(0, new ItemStack(Items.EMERALD, 5));
            owner.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 60));
            for (int slot = 2; slot < owner.getInventory().getContainerSize(); slot++) {
                owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }

            WorkerMenu menu = openRoster(owner);
            send(owner, menu, 1, WorkerNetwork.Action.COLLECTION_QUERY,
                    queryData(0, "", "", "", 0));
            CompoundTag collection = collection(menu);
            CompoundTag diamondRow = findVariant(helper, collection, new ItemStack(Items.DIAMOND));
            long revision = collection.getLong("Revision");
            send(owner, menu, 2, WorkerNetwork.Action.COLLECTION_SELECT,
                    selectData(revision, diamondRow.getUUID("Variant"), true));
            collection = collection(menu);
            send(owner, menu, 3, WorkerNetwork.Action.COLLECTION_TRANSFER,
                    revisionData(collection.getLong("Revision")));
            CompoundTag partial = response(menu);
            helper.assertTrue(partial.getString("Kind").equals("CollectionResult")
                            && partial.getLong("Collected") == 4
                            && partial.getLong("Remaining") == 16
                            && diamonds.getItem(0).getCount() == 16
                            && playerCount(owner, new ItemStack(Items.DIAMOND)) == 64,
                    "A partially available matching player stack must accept only its four free slots");

            collection = collection(menu);
            send(owner, menu, 4, WorkerNetwork.Action.COLLECTION_CLEAR,
                    revisionData(collection.getLong("Revision")));
            collection = collection(menu);
            CompoundTag emeraldRow = findVariant(helper, collection, new ItemStack(Items.EMERALD));
            send(owner, menu, 5, WorkerNetwork.Action.COLLECTION_SELECT,
                    selectData(collection.getLong("Revision"), emeraldRow.getUUID("Variant"), true));
            collection = collection(menu);
            send(owner, menu, 6, WorkerNetwork.Action.COLLECTION_TRANSFER,
                    revisionData(collection.getLong("Revision")));
            CompoundTag full = response(menu);
            helper.assertTrue(full.getString("Kind").equals("CollectionResult")
                            && full.getLong("Collected") == 0
                            && full.getLong("Remaining") == 5
                            && emeralds.getItem(0).getCount() == 5
                            && playerCount(owner, new ItemStack(Items.EMERALD)) == 0,
                    "A full player inventory must leave the selected source stack untouched");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_collection_live_updates", timeoutTicks = 180)
    public static void collectionClicksAndTransfersSurviveLivePickupUpdates(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerEntity worker = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            worker.setItem(0, new ItemStack(Items.DIAMOND, 4));
            WorkerMenu menu = openRoster(owner);
            send(owner, menu, 1, WorkerNetwork.Action.COLLECTION_QUERY, queryData(0, "", "", "", 0));
            CompoundTag original = collection(menu);
            long revision = original.getLong("Revision");
            UUID variant = findVariant(helper, original, new ItemStack(Items.DIAMOND)).getUUID("Variant");

            // A mining pickup reaches the server after the client's last rendered snapshot.
            worker.setItem(0, new ItemStack(Items.DIAMOND, 7));
            send(owner, menu, 2, WorkerNetwork.Action.COLLECTION_SELECT, selectData(revision, variant, true));
            CompoundTag selected = collection(menu);
            helper.assertTrue(selected.getInt("Selected") == 1 && selected.getLong("SelectedAmount") == 7
                            && findVariant(helper, selected, new ItemStack(Items.DIAMOND)).getBoolean("Selected"),
                    "Live item count changes must not reject a click or lose its green selected state");

            owner.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 62));
            for (int slot = 2; slot < 36; slot++) { owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64)); }
            worker.setItem(0, new ItemStack(Items.DIAMOND, 9));
            worker.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            send(owner, menu, 3, WorkerNetwork.Action.COLLECTION_TRANSFER, revisionData(revision));
            CompoundTag result = response(menu);
            helper.assertTrue(result.getString("Kind").equals("CollectionResult") && result.getLong("Collected") == 2
                            && result.getLong("Remaining") == 7 && worker.getItem(0).getCount() == 7
                            && worker.getItem(1).getCount() == 64 && playerCount(owner, new ItemStack(Items.DIAMOND)) == 64,
                    "Transfer must use fresh live amounts, move only what fits and preserve the source remainder and working stock");

            send(owner, menu, 4, WorkerNetwork.Action.COLLECTION_QUERY, queryData(0, "", "", "emerald", 0));
            send(owner, menu, 5, WorkerNetwork.Action.COLLECTION_TRANSFER, revisionData(revision));
            assertError(helper, menu, "STALE_COLLECTION");
            helper.assertTrue(worker.getItem(0).getCount() == 7 && playerCount(owner, new ItemStack(Items.DIAMOND)) == 64,
                    "A request from an old query scope must still fail without transferring anything");
        } finally { fixture.close(); }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_collection_archive", timeoutTicks = 220)
    public static void archiveCollectionWithdrawsEquipmentOnceAndReactivationKeepsRemainder(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        WorkerEntity reactivated = null;
        try {
            ServerPlayer owner = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity worker = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(0, 1, 0)));
            ItemStack mainHand = named(Items.IRON_PICKAXE, 1, "archive main hand");
            ItemStack gems = named(Items.EMERALD, 3, "archive gems");
            ItemStack remainder = named(Items.GOLD_INGOT, 5, "keep in archive");
            ItemStack offHand = named(Items.SHIELD, 1, "archive off hand");
            ItemStack head = named(Items.NETHERITE_HELMET, 1, "archive head");
            ItemStack chest = named(Items.NETHERITE_CHESTPLATE, 1, "archive chest");
            ItemStack legs = named(Items.NETHERITE_LEGGINGS, 1, "archive legs");
            ItemStack feet = named(Items.NETHERITE_BOOTS, 1, "archive feet");
            worker.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
            worker.setItem(1, gems);
            worker.setItem(2, remainder);
            worker.setItemSlot(EquipmentSlot.OFFHAND, offHand);
            worker.setItemSlot(EquipmentSlot.HEAD, head);
            worker.setItemSlot(EquipmentSlot.CHEST, chest);
            worker.setItemSlot(EquipmentSlot.LEGS, legs);
            worker.setItemSlot(EquipmentSlot.FEET, feet);
            UUID workerId = worker.getUUID();
            roster.retire(owner.getUUID(), workerId, roster.view(owner.getUUID(), workerId).revision());

            WorkerMenu menu = openRoster(owner);
            send(owner, menu, 1, WorkerNetwork.Action.COLLECTION_QUERY,
                    queryData(1, "", "", "", 0));
            CompoundTag collection = collection(menu);
            CompoundTag mainRow = findVariant(helper, collection, mainHand);
            helper.assertTrue(mainRow.getLong("Count") == 1
                            && countVariant(helper, collection, gems) == 3
                            && countVariant(helper, collection, remainder) == 5
                            && countVariant(helper, collection, offHand) == 1
                            && countVariant(helper, collection, head) == 1
                            && countVariant(helper, collection, chest) == 1
                            && countVariant(helper, collection, legs) == 1
                            && countVariant(helper, collection, feet) == 1,
                    "A retired archive must expose 36 inventory slots and each offhand/armor stack, with one main hand");

            List<ItemStack> withdraw = List.of(mainHand, gems, offHand, head, chest, legs, feet);
            long sequence = 2;
            for (ItemStack expected : withdraw) {
                CompoundTag row = findVariant(helper, collection, expected);
                send(owner, menu, sequence++, WorkerNetwork.Action.COLLECTION_SELECT,
                        selectData(collection.getLong("Revision"), row.getUUID("Variant"), true));
                collection = collection(menu);
            }
            send(owner, menu, sequence++, WorkerNetwork.Action.COLLECTION_TRANSFER,
                    revisionData(collection.getLong("Revision")));
            CompoundTag result = response(menu);
            helper.assertTrue(result.getString("Kind").equals("CollectionResult")
                            && result.getLong("Collected") == 9
                            && result.getLong("Remaining") == 0
                            && playerCount(owner, mainHand) == 1
                            && playerCount(owner, gems) == 3
                            && playerCount(owner, offHand) == 1
                            && playerCount(owner, head) == 1
                            && playerCount(owner, chest) == 1
                            && playerCount(owner, legs) == 1
                            && playerCount(owner, feet) == 1,
                    "Archive withdrawal must transfer each selected inventory/equipment stack exactly once");
            helper.assertTrue(roster.archivedInventory(owner.getUUID(), workerId).get(2).getCount() == 5
                            && roster.archivedInventory(owner.getUUID(), workerId).get(2).is(Items.GOLD_INGOT),
                    "An unselected archive remainder must stay in its original slot");

            collection = collection(menu);
            helper.assertTrue(countVariant(helper, collection, remainder) == 5
                            && !hasVariant(helper, collection, mainHand)
                            && !hasVariant(helper, collection, offHand)
                            && !hasVariant(helper, collection, head),
                    "The collection view must reflect withdrawn equipment without a stale duplicate");

            UUID request = UUID.randomUUID();
            roster.reserve(owner.getUUID(), request, workerId);
            reactivated = roster.deploy(owner.getUUID(), request, helper.getLevel(), destination(helper));
            fixture.trackWorker(reactivated);
            helper.assertTrue(reactivated.getItem(0).isEmpty()
                            && reactivated.getItem(1).isEmpty()
                            && reactivated.getItem(2).is(Items.GOLD_INGOT)
                            && reactivated.getItem(2).getCount() == 5
                            && reactivated.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()
                            && reactivated.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                            && reactivated.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                            && reactivated.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                            && reactivated.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                            && playerCount(owner, mainHand) == 1,
                    "Reactivation must restore only the saved remainder and must not recreate withdrawn contents");
        } finally {
            fixture.close();
            if (reactivated != null && !reactivated.isRemoved()) {
                reactivated.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_collection_retire", timeoutTicks = 220)
    public static void collectionRetirementScopesAllWorkersAndRejectsStaleOrUnauthorizedRequests(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            ServerPlayer stranger = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity first = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity second = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(2, 1, 0)));
            first.setItem(0, new ItemStack(Items.DIAMOND, 10));
            owner.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 60));
            for (int slot = 2; slot < owner.getInventory().getContainerSize(); slot++) {
                owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }

            WorkerMenu menu = openRoster(owner);
            send(owner, menu, 1, WorkerNetwork.Action.COLLECTION_QUERY,
                    queryData(0, "", "", "", 0));
            CompoundTag collection = collection(menu);
            CompoundTag diamondRow = findVariant(helper, collection, new ItemStack(Items.DIAMOND));
            long revision = collection.getLong("Revision");

            CompoundTag malformed = queryData(0, "", "", "", 0);
            malformed.putString("Unexpected", "field");
            send(owner, menu, 2, WorkerNetwork.Action.COLLECTION_QUERY, malformed);
            assertError(helper, menu, "INVALID_REQUEST");
            helper.assertTrue(collection(menu).getInt("Selected") == 0
                            && first.getItem(0).getCount() == 10,
                    "A malformed collection request must leave selection and worker contents unchanged");

            send(stranger, menu, 1, WorkerNetwork.Action.COLLECTION_SELECT,
                    selectData(revision, diamondRow.getUUID("Variant"), true));
            helper.assertTrue(collection(menu).getInt("Selected") == 0
                            && first.getItem(0).getCount() == 10,
                    "A non-owner holder must not use the owner's open menu to select or mutate collection state");

            send(owner, menu, 3, WorkerNetwork.Action.COLLECTION_SELECT,
                    selectData(revision, UUID.randomUUID(), true));
            assertError(helper, menu, "INVALID_VARIANT");
            helper.assertTrue(collection(menu).getInt("Selected") == 0,
                    "An invalid server-owned variant identity must not select anything");

            send(owner, menu, 4, WorkerNetwork.Action.COLLECTION_SELECT,
                    selectData(revision, diamondRow.getUUID("Variant"), true));
            collection = collection(menu);
            helper.assertTrue(collection.getInt("Selected") == 1
                            && collection.getLong("SelectedAmount") == 10,
                    "The valid selection must be the only selected item before retirement preview");
            send(owner, menu, 5, WorkerNetwork.Action.PREVIEW_COLLECTION_RETIRE,
                    revisionData(collection.getLong("Revision")));
            CompoundTag preview = response(menu);
            UUID confirmation = preview.getUUID("Confirmation");
            helper.assertTrue(preview.getString("Kind").equals("CollectionPreview")
                            && preview.getList("Workers", Tag.TAG_COMPOUND).size() == 2
                            && containsWorker(preview.getList("Workers", Tag.TAG_COMPOUND), first.getUUID())
                            && containsWorker(preview.getList("Workers", Tag.TAG_COMPOUND), second.getUUID())
                            && preview.getLong("Amount") == 10,
                    "Retirement preview must include every active worker in scope, including an empty worker");

            first.setItem(1, new ItemStack(Items.DIRT, 1));
            send(owner, menu, 6, WorkerNetwork.Action.COLLECTION_RETIRE, confirmationData(confirmation));
            assertError(helper, menu, "STALE_COLLECTION");
            helper.assertTrue(roster.list(owner.getUUID(), false).size() == 2
                            && first.getItem(0).getCount() == 10
                            && playerCount(owner, new ItemStack(Items.DIAMOND)) == 60,
                    "A stale retirement preview must perform neither transfer nor retirement");

            send(owner, menu, 7, WorkerNetwork.Action.COLLECTION_QUERY,
                    queryData(0, "", "", "", 0));
            collection = collection(menu);
            diamondRow = findVariant(helper, collection, new ItemStack(Items.DIAMOND));
            send(owner, menu, 8, WorkerNetwork.Action.PREVIEW_COLLECTION_RETIRE,
                    revisionData(collection.getLong("Revision")));
            preview = response(menu);
            confirmation = preview.getUUID("Confirmation");
            helper.assertTrue(preview.getList("Workers", Tag.TAG_COMPOUND).size() == 2
                            && containsWorker(preview.getList("Workers", Tag.TAG_COMPOUND), second.getUUID()),
                    "A fresh preview must still include the empty active worker");
            send(owner, menu, 9, WorkerNetwork.Action.COLLECTION_RETIRE, confirmationData(confirmation));
            CompoundTag retired = response(menu);
            helper.assertTrue(retired.getString("Kind").equals("CollectionResult")
                            && retired.getLong("Collected") == 4
                            && retired.getLong("Remaining") == 6
                            && retired.getInt("Retired") == 2
                            && roster.list(owner.getUUID(), false).isEmpty()
                            && roster.list(owner.getUUID(), true).size() == 2
                            && roster.archivedInventory(owner.getUUID(), first.getUUID()).get(0).getCount() == 6
                            && roster.archivedInventory(owner.getUUID(), first.getUUID()).get(1).is(Items.DIRT)
                            && roster.archivedInventory(owner.getUUID(), second.getUUID()).stream().allMatch(ItemStack::isEmpty)
                            && playerCount(owner, new ItemStack(Items.DIAMOND)) == 64,
                    "Valid collect-and-retire must transfer first, retire all scoped workers, and archive every overflow");

            List<ItemStack> firstArchive = roster.archivedInventory(owner.getUUID(), first.getUUID());
            send(owner, menu, 10, WorkerNetwork.Action.COLLECTION_RETIRE, confirmationData(confirmation));
            assertError(helper, menu, "INVALID_CONFIRMATION");
            helper.assertTrue(roster.list(owner.getUUID(), false).isEmpty()
                            && roster.archivedInventory(owner.getUUID(), first.getUUID()).get(0).getCount()
                            == firstArchive.get(0).getCount()
                            && playerCount(owner, new ItemStack(Items.DIAMOND)) == 64,
                    "A repeated retirement confirmation must not duplicate transfer or archive retirement");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    private static WorkerMenu openRoster(ServerPlayer owner) {
        WorkerMenu.open(owner, null, false, 0);
        if (!(owner.containerMenu instanceof WorkerMenu menu)) {
            throw new AssertionError("Expected the real player container to be a WorkerMenu");
        }
        return menu;
    }

    private static CompoundTag collection(WorkerMenu menu) {
        CompoundTag snapshot = menu.snapshot();
        if (!snapshot.contains("Collection", Tag.TAG_COMPOUND)) {
            throw new AssertionError("Collection snapshot missing from menu: " + snapshot);
        }
        return snapshot.getCompound("Collection");
    }

    private static CompoundTag response(WorkerMenu menu) {
        return menu.snapshot().getCompound("Response");
    }

    private static CompoundTag queryData(int mode, String dimension, String worker, String search, int page) {
        CompoundTag data = new CompoundTag();
        data.putInt("Mode", mode);
        data.putString("Dimension", dimension);
        data.putString("Worker", worker);
        data.putString("Search", search);
        data.putInt("Page", page);
        return data;
    }

    private static CompoundTag pageData(int page) {
        CompoundTag data = new CompoundTag();
        data.putInt("Page", page);
        return data;
    }

    private static CompoundTag revisionData(long revision) {
        CompoundTag data = new CompoundTag();
        data.putLong("Revision", revision);
        return data;
    }

    private static CompoundTag selectData(long revision, UUID variant, boolean selected) {
        CompoundTag data = revisionData(revision);
        data.putUUID("Variant", variant);
        data.putBoolean("Selected", selected);
        return data;
    }

    private static CompoundTag confirmationData(UUID confirmation) {
        CompoundTag data = new CompoundTag();
        data.putUUID("Confirmation", confirmation);
        return data;
    }

    private static void send(ServerPlayer holder, WorkerMenu menu, long sequence,
                             WorkerNetwork.Action action, CompoundTag data) {
        menu.handle(holder, new WorkerNetwork.Intent(menu.containerId, menu.session(), sequence, action, data));
    }

    private static void assertError(GameTestHelper helper, WorkerMenu menu, String expected) {
        CompoundTag actual = response(menu);
        helper.assertTrue(actual.getString("Kind").equals("Error")
                        && actual.getString("Error").equals(expected),
                "Expected collection error " + expected + " but received " + actual);
    }

    private static CompoundTag findVariant(GameTestHelper helper, CompoundTag collection, ItemStack expected) {
        for (Tag value : collection.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            ItemStack actual = ItemStack.parseOptional(helper.getLevel().registryAccess(), row.getCompound("Stack"));
            if (ItemStack.isSameItemSameComponents(actual, expected)) {
                return row;
            }
        }
        throw new AssertionError("Variant not present in collection: " + expected);
    }

    private static long countVariant(GameTestHelper helper, CompoundTag collection, ItemStack expected) {
        for (Tag value : collection.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            ItemStack actual = ItemStack.parseOptional(helper.getLevel().registryAccess(), row.getCompound("Stack"));
            if (ItemStack.isSameItemSameComponents(actual, expected)) {
                return row.getLong("Count");
            }
        }
        return -1;
    }

    private static boolean hasVariant(GameTestHelper helper, CompoundTag collection, ItemStack expected) {
        for (Tag value : collection.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            ItemStack actual = ItemStack.parseOptional(helper.getLevel().registryAccess(), row.getCompound("Stack"));
            if (ItemStack.isSameItemSameComponents(actual, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsItem(CompoundTag collection, Item item) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        for (Tag value : collection.getList("Items", Tag.TAG_COMPOUND)) {
            if (((CompoundTag) value).getCompound("Stack").getString("id").equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static long sourceAmount(CompoundTag row, UUID worker) {
        for (Tag value : row.getList("Sources", Tag.TAG_COMPOUND)) {
            CompoundTag source = (CompoundTag) value;
            if (source.getUUID("Worker").equals(worker)) {
                return source.getInt("Count");
            }
        }
        return -1;
    }

    private static CompoundTag source(CompoundTag collection, UUID worker) {
        for (Tag value : collection.getList("Sources", Tag.TAG_COMPOUND)) {
            CompoundTag source = (CompoundTag) value;
            if (source.getUUID("Worker").equals(worker)) {
                return source;
            }
        }
        return null;
    }

    private static boolean containsWorker(ListTag workers, UUID worker) {
        for (Tag value : workers) {
            if (((CompoundTag) value).getUUID("Worker").equals(worker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allSelected(ListTag items) {
        if (items.isEmpty()) {
            return false;
        }
        for (Tag value : items) {
            if (!((CompoundTag) value).getBoolean("Selected")) {
                return false;
            }
        }
        return true;
    }

    private static boolean noneSelected(ListTag items) {
        for (Tag value : items) {
            if (((CompoundTag) value).getBoolean("Selected")) {
                return false;
            }
        }
        return true;
    }

    private static void assertCollectionShape(GameTestHelper helper, CompoundTag collection) {
        helper.assertTrue(collection.contains("Revision", Tag.TAG_LONG)
                        && collection.contains("Items", Tag.TAG_LIST)
                        && collection.contains("Selected", Tag.TAG_INT)
                        && collection.contains("SelectedAmount", Tag.TAG_LONG)
                        && collection.contains("Sources", Tag.TAG_LIST)
                        && collection.contains("Unavailable", Tag.TAG_INT),
                "Collection snapshot must expose the bounded revision, item page, selection, sources and availability fields");
        for (Tag value : collection.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            helper.assertTrue(row.hasUUID("Variant")
                            && row.contains("Stack", Tag.TAG_COMPOUND)
                            && row.contains("Count", Tag.TAG_LONG)
                            && row.contains("Selected", Tag.TAG_BYTE)
                            && row.contains("Sources", Tag.TAG_LIST),
                    "Each collection item must carry its server-owned variant, stack, count, selection and sources");
        }
    }

    private static int playerCount(ServerPlayer player, ItemStack expected) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, expected)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ItemStack named(Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static Vec3 destination(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(4, 1, 4));
        return new Vec3(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
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

    private static void withPreparedNether(GameTestHelper helper, Runnable body) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The dedicated server must provide the Nether");
        ChunkPos chunk = new ChunkPos(0, 0);
        UUID ticket = UUID.randomUUID();
        int deadline = nether.getServer().getTickCount() + 100;
        boolean[] finished = {false};
        nether.getChunkSource().addRegionTicket(WorkerRelocation.PREPARATION, chunk, 1, ticket);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                nether.getChunk(x, z);
            }
        }
        helper.onEachTick(() -> {
            if (finished[0]) {
                return;
            }
            boolean ready = WorkerRelocation.prepared(nether, chunk);
            if (!ready && nether.getServer().getTickCount() < deadline) {
                return;
            }
            finished[0] = true;
            try {
                helper.assertTrue(ready, "Nether fixture chunks must finish preparation before collection checks");
                body.run();
            } finally {
                nether.getChunkSource().removeRegionTicket(WorkerRelocation.PREPARATION, chunk, 1, ticket);
            }
        });
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<WorkerEntity> workers = new ArrayList<>();
        private final List<Pending> pending = new ArrayList<>();

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
        }

        private ServerPlayer player() {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                    .add(AdvancedOpenScreenPayload.TYPE.id());
            ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                    .add(WorkerNetwork.Snapshot.TYPE.id());
            players.add(player);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, new ItemStack(WorkerMod.CONTROLLER.get()));
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
                throw new AssertionError("Dedicated server rejected the fixture worker");
            }
            worker.claim(owner.getUUID());
            workers.add(worker);
            return worker;
        }

        private void trackWorker(WorkerEntity worker) {
            if (worker != null && !workers.contains(worker)) {
                workers.add(worker);
            }
        }

        private void trackPending(WorkerRelocation service, UUID owner, UUID request) {
            pending.add(new Pending(service, owner, request));
        }

        private void close() {
            for (ServerPlayer player : players) {
                player.closeContainer();
            }
            for (Pending request : pending) {
                request.service().cancel(request.owner(), request.request());
            }
            for (WorkerEntity worker : workers) {
                if (!worker.isRemoved() && worker.isAlive()
                        && worker.miningStatus().state() == automatone.worker.MiningSession.State.RUNNING) {
                    worker.stopMining();
                }
                if (!worker.isRemoved()) {
                    worker.remove(Entity.RemovalReason.DISCARDED);
                }
            }
            for (ServerPlayer player : players) {
                if (Objects.equals(server.getPlayerList().getPlayer(player.getUUID()), player)) {
                    server.getPlayerList().remove(player);
                } else if (!player.isRemoved()) {
                    player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
            }
        }

        private record Pending(WorkerRelocation service, UUID owner, UUID request) {
        }
    }
}
