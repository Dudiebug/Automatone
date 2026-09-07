package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerChunkLoading;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMenu;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerNetwork;
import automatone.worker.WorkerRelocation;
import automatone.worker.WorkerRoster;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Dedicated-server contract tests for M5.13 batch deployment and fleet actions. */
@GameTestHolder("automatone_worker_m5_batch_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerBatchGameTest {
    private static final net.minecraft.resources.ResourceLocation IRON_ORE =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_ore");
    private static final net.minecraft.resources.ResourceLocation GOLD_ORE =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("gold_ore");

    private WorkerBatchGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_batch_capacity", timeoutTicks = 420)
    public static void batchReservesTenWorkersAndUsesFourPreparers(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerLevel level = helper.getLevel();
            List<BlockPos> columns = columns(helper, 10);
            for (BlockPos column : columns) {
                prepareColumn(blocks, level, column, Blocks.STONE.defaultBlockState());
                ensurePrepared(level, column);
            }
            fixture.installRelocation(columns, new WorkerRelocation.Limits(8, 220, 60));

            for (int slot = 1; slot <= 10; slot++) {
                owner.getInventory().setItem(slot, new ItemStack(Items.IRON_PICKAXE));
                owner.getInventory().setItem(slot + 10, new ItemStack(Items.COBBLESTONE));
            }
            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            long supplyRevision = menu.snapshot().getLong("SupplyRevision");
            CompoundTag request = batchData(List.of(), List.of(IRON_ORE), 4, false, 10,
                    intList(range(1, 10)), materials(material(11, 1)), supplyRevision);
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_BATCH, request);
            CompoundTag preview = response(menu);
            helper.assertTrue(preview.getString("Kind").equals("DeploymentPreview")
                            && preview.getBoolean("CanSubmit")
                            && preview.getInt("ToolsRequired") == 10
                            && preview.getInt("ToolsSelected") == 10
                            && preview.getList("Supplies", Tag.TAG_COMPOUND).size() == 11,
                    "A ten-worker preview must require ten real tools and the aggregate material kit");
            UUID confirmation = preview.getUUID("Confirmation");

            send(owner, menu, 2, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(confirmation));
            CompoundTag submitted = response(menu);
            helper.assertTrue(submitted.getString("Kind").equals("DeploymentResult")
                            && submitted.getInt("Queued") == 10
                            && menu.snapshot().getInt("FreeSlots") == 0
                            && menu.snapshot().getList("Batches", Tag.TAG_COMPOUND).size() == 10,
                    "Submitting ten kits must reserve every free roster slot before preparation begins");

            int[] maxPreparers = {0};
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            awaitBatch(helper, fixture, blocks, owner, menu, 380,
                    snapshot -> {
                        maxPreparers[0] = Math.max(maxPreparers[0],
                                fixture.relocation().pendingRequests(owner.getUUID()).size());
                        return roster.list(owner.getUUID(), false).size() == 10
                                && allStates(snapshot.getList("Batches", Tag.TAG_COMPOUND), "DEPLOYED");
                    }, () -> {
                        helper.assertTrue(maxPreparers[0] == WorkerRelocation.MAX_CONCURRENT,
                                "The batch queue must expose exactly four concurrent terrain preparations");
                        helper.assertTrue(playerCount(owner, Items.IRON_PICKAXE) == 0
                                        && playerCount(owner, Items.COBBLESTONE) == 0,
                                "Each successful child must consume one actual tool and one actual material");
                        for (CompoundTag row : rows(menu.snapshot())) {
                            WorkerEntity worker = roster.active(owner.getUUID(), row.getUUID("Worker"));
                            helper.assertTrue(worker.getItem(0).is(Items.IRON_PICKAXE)
                                            && worker.getItem(1).is(Items.COBBLESTONE)
                                            && worker.miningStatus().requested() == 4
                                            && worker.miningStatus().state() == MiningSession.State.IDLE,
                                    "A deploy-only child must retain its exact kit and configured quantity");
                        }
                    });
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_batch_kits", timeoutTicks = 360)
    public static void batchCopiesComponentsAndQuantityToExistingAndNewWorkers(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerLevel level = helper.getLevel();
            WorkerEntity existing = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(0, 1, 0)));
            List<BlockPos> columns = columns(helper, 2);
            for (BlockPos column : columns) {
                prepareColumn(blocks, level, column, Blocks.STONE.defaultBlockState());
                ensurePrepared(level, column);
            }
            fixture.installRelocation(columns, new WorkerRelocation.Limits(8, 220, 60));

            ItemStack firstTool = named(Items.IRON_PICKAXE, 1, "iron-kit");
            ItemStack secondTool = named(Items.DIAMOND_PICKAXE, 1, "diamond-kit");
            ItemStack firstMaterial = named(Items.DIRT, 6, "dirt-kit");
            ItemStack secondMaterial = named(Items.STONE, 6, "stone-kit");
            owner.getInventory().setItem(1, firstTool.copy());
            owner.getInventory().setItem(2, secondTool.copy());
            owner.getInventory().setItem(11, firstMaterial.copy());
            owner.getInventory().setItem(12, secondMaterial.copy());

            WorkerRoster roster = WorkerRoster.get(level.getServer());
            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            long supplyRevision = menu.snapshot().getLong("SupplyRevision");
            CompoundTag existingRef = ref(existing, roster.view(owner.getUUID(), existing.getUUID()).revision());
            CompoundTag request = batchData(List.of(existingRef), List.of(IRON_ORE, GOLD_ORE), 7, false, 2,
                    intList(1, 2), materials(material(11, 3), material(12, 3)), supplyRevision);
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_BATCH, request);
            CompoundTag preview = response(menu);
            helper.assertTrue(preview.getBoolean("CanSubmit")
                            && required(helper, preview, firstMaterial, 6) && required(helper, preview, secondMaterial, 6),
                    "Preview must report aggregate per-worker material requirements with components intact");
            UUID confirmation = preview.getUUID("Confirmation");
            send(owner, menu, 2, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(confirmation));
            helper.assertTrue(response(menu).getInt("Queued") == 2
                            && existing.miningStatus().state() == MiningSession.State.IDLE
                            && existing.miningStatus().requested() == 7
                            && existing.miningStatus().targets().equals(List.of(IRON_ORE.toString(), GOLD_ORE.toString())),
                    "Existing recipients must be configured immediately with the same target list and quantity");

            awaitBatch(helper, fixture, blocks, owner, menu, 320,
                    snapshot -> roster.list(owner.getUUID(), false).size() == 3
                            && countState(snapshot.getList("Batches", Tag.TAG_COMPOUND), "DEPLOYED") == 2,
                    () -> {
                        List<WorkerEntity> created = roster.list(owner.getUUID(), false).stream()
                                .map(view -> view.worker())
                                .filter(id -> !id.equals(existing.getUUID()))
                                .map(id -> roster.active(owner.getUUID(), id)).toList();
                        helper.assertTrue(created.size() == 2, "Both new reservations must become owned workers");
                        boolean sawFirstTool = false;
                        boolean sawSecondTool = false;
                        for (WorkerEntity worker : created) {
                            helper.assertTrue(worker.miningStatus().requested() == 7
                                            && worker.miningStatus().targets().equals(
                                            List.of(IRON_ORE.toString(), GOLD_ORE.toString()))
                                            && worker.miningStatus().state() == MiningSession.State.IDLE,
                                    "New workers must receive the configured target list and per-worker quantity");
                            if (matches(worker.getItem(0), firstTool.copyWithCount(1))) {
                                sawFirstTool = true;
                                helper.assertTrue(matches(worker.getItem(1), firstMaterial.copyWithCount(3))
                                                && matches(worker.getItem(2), secondMaterial.copyWithCount(3)),
                                        "The first kit must preserve both component-bearing material variants");
                            } else if (matches(worker.getItem(0), secondTool.copyWithCount(1))) {
                                sawSecondTool = true;
                                helper.assertTrue(matches(worker.getItem(1), firstMaterial.copyWithCount(3))
                                                && matches(worker.getItem(2), secondMaterial.copyWithCount(3)),
                                        "The second kit must preserve both component-bearing material variants");
                            } else {
                                helper.fail("A new worker received an unexpected tool or component set");
                            }
                        }
                        helper.assertTrue(sawFirstTool && sawSecondTool
                                        && playerCount(owner, firstTool) == 0
                                        && playerCount(owner, secondTool) == 0
                                        && playerCount(owner, firstMaterial) == 0
                                        && playerCount(owner, secondMaterial) == 0,
                                "Successful commits must consume every selected stack exactly once");
                    });
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_batch_validation", timeoutTicks = 260)
    public static void batchRejectsShortageChangesForeignStaleAndRepeatedRequests(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            ServerPlayer stranger = fixture.player();
            ServerLevel level = helper.getLevel();
            WorkerEntity existing = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity foreign = fixture.spawnOwned(stranger, level, helper.absolutePos(new BlockPos(4, 1, 0)));
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            owner.getInventory().setItem(1, new ItemStack(Items.IRON_PICKAXE));
            owner.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 2));
            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            long sequence = 0;
            long revision = menu.snapshot().getLong("SupplyRevision");
            int initialFree = menu.snapshot().getInt("FreeSlots");

            menu.handle(stranger, new WorkerNetwork.Intent(menu.containerId, menu.session(), 99,
                    WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 1, false, 1, intList(1),
                            materials(material(2, 1)), revision)));
            helper.assertTrue(menu.snapshot().getInt("FreeSlots") == initialFree
                            && menu.snapshot().getList("Batches", Tag.TAG_COMPOUND).isEmpty(),
                    "A foreign connected player must not use the owner's live menu to reserve a worker");

            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 1, false, 2, intList(1),
                            materials(material(2, 1)), revision));
            CompoundTag shortage = response(menu);
            helper.assertTrue(shortage.getString("Kind").equals("DeploymentPreview")
                            && !shortage.getBoolean("CanSubmit")
                            && shortage.getString("Error").equals("KIT_SHORTAGE"),
                    "Fewer selected tools than new workers must block the whole submission as a shortage");
            send(owner, menu, ++sequence, WorkerNetwork.Action.SUBMIT_BATCH,
                    confirmationData(shortage.getUUID("Confirmation")));
            assertError(helper, menu, "INVALID_CONFIRMATION");
            helper.assertTrue(menu.snapshot().getInt("FreeSlots") == initialFree
                            && playerCount(owner, Items.IRON_PICKAXE) == 1
                            && playerCount(owner, Items.COBBLESTONE) == 2,
                    "A shortage preview and its rejected token must perform no reservation or inventory mutation");

            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 1, false, 1, intList(1),
                            materials(material(2, 1)), menu.snapshot().getLong("SupplyRevision")));
            UUID changedSupplies = response(menu).getUUID("Confirmation");
            owner.getInventory().setItem(2, new ItemStack(Items.STONE, 2));
            menu.snapshot();
            send(owner, menu, ++sequence, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(changedSupplies));
            assertError(helper, menu, "STALE_SUPPLIES");
            helper.assertTrue(menu.snapshot().getInt("FreeSlots") == initialFree
                            && roster.list(owner.getUUID(), false).size() == 1,
                    "Changing inventory after preview must reject before reservation or worker mutation");

            long currentSupplyRevision = menu.snapshot().getLong("SupplyRevision");
            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(ref(foreign, 0)), List.of(IRON_ORE), 1, false, 0,
                            intList(), materials(), currentSupplyRevision));
            assertError(helper, menu, "NOT_OWNER");
            helper.assertTrue(roster.list(owner.getUUID(), false).size() == 1,
                    "A foreign recipient must not be exposed or changed through the owner's batch menu");

            long existingRevision = roster.view(owner.getUUID(), existing.getUUID()).revision();
            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(ref(existing, existingRevision)), List.of(IRON_ORE), 3, false, 0,
                            intList(), materials(), currentSupplyRevision));
            UUID stalePreview = response(menu).getUUID("Confirmation");
            roster.rename(owner.getUUID(), existing.getUUID(), existingRevision, "changed after preview");
            MiningSession.Snapshot beforeStaleApply = existing.miningStatus();
            send(owner, menu, ++sequence, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(stalePreview));
            assertError(helper, menu, "STALE_PREVIEW");
            helper.assertTrue(existing.miningStatus().equals(beforeStaleApply)
                            && menu.snapshot().getInt("FreeSlots") == initialFree,
                    "A changed owned recipient must abort the whole request before existing-worker mutation");

            owner.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 1));
            currentSupplyRevision = menu.snapshot().getLong("SupplyRevision");
            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 1, false, 1, intList(1),
                            materials(material(2, 1)), currentSupplyRevision));
            UUID accepted = response(menu).getUUID("Confirmation");
            send(owner, menu, ++sequence, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(accepted));
            helper.assertTrue(response(menu).getString("Kind").equals("DeploymentResult")
                            && menu.snapshot().getInt("FreeSlots") == initialFree - 1,
                    "A valid batch must reserve one slot before its asynchronous deployment");
            send(owner, menu, ++sequence, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(accepted));
            assertError(helper, menu, "INVALID_CONFIRMATION");
            helper.assertTrue(menu.snapshot().getList("Batches", Tag.TAG_COMPOUND).size() == 1
                            && menu.snapshot().getInt("FreeSlots") == initialFree - 1,
                    "Repeating a successful confirmation must not create a second child or consume again");
            finish(helper, fixture, new BlockFixture(), null);
        } catch (Throwable failure) {
            finish(helper, fixture, new BlockFixture(), failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_batch_partial_failure", timeoutTicks = 300)
    public static void batchRetainsSuccessfulChildWhenAnotherDestinationFails(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerLevel level = helper.getLevel();
            BlockPos good = helper.absolutePos(new BlockPos(8, 80, 8));
            BlockPos outside = new BlockPos(40_000_000, good.getY(), good.getZ());
            prepareColumn(blocks, level, good, Blocks.STONE.defaultBlockState());
            ensurePrepared(level, good);
            fixture.installRelocation(List.of(good, outside), new WorkerRelocation.Limits(1, 120, 30));
            ItemStack goodTool = named(Items.IRON_PICKAXE, 1, "successful-kit");
            ItemStack failedTool = named(Items.DIAMOND_PICKAXE, 1, "failed-kit");
            ItemStack material = named(Items.COBBLESTONE, 2, "batch-material");
            owner.getInventory().setItem(1, goodTool.copy());
            owner.getInventory().setItem(2, failedTool.copy());
            owner.getInventory().setItem(11, material.copy());

            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            long supplyRevision = menu.snapshot().getLong("SupplyRevision");
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 2, false, 2, intList(1, 2),
                            materials(material(11, 1)), supplyRevision));
            UUID confirmation = response(menu).getUUID("Confirmation");
            send(owner, menu, 2, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(confirmation));
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            awaitBatch(helper, fixture, blocks, owner, menu, 260,
                    snapshot -> roster.list(owner.getUUID(), false).size() == 1
                            && countState(snapshot.getList("Batches", Tag.TAG_COMPOUND), "DEPLOYED") == 1
                            && countState(snapshot.getList("Batches", Tag.TAG_COMPOUND), "FAILED") == 1,
                    () -> {
                        List<CompoundTag> rows = rows(menu.snapshot());
                        CompoundTag failed = rows.stream().filter(row -> row.getString("State").equals("FAILED"))
                                .findFirst().orElseThrow();
                        CompoundTag successful = rows.stream().filter(row -> row.getString("State").equals("DEPLOYED"))
                                .findFirst().orElseThrow();
                        WorkerEntity worker = roster.active(owner.getUUID(), successful.getUUID("Worker"));
                        helper.assertTrue(failed.getString("Error").equals("NO_SAFE_DESTINATION")
                                        && worker.getItem(0).is(goodTool.getItem())
                                        && worker.getItem(1).is(Items.COBBLESTONE)
                                        && matches(worker.getItem(0), goodTool)
                                        && playerCount(owner, failedTool) == 1
                                        && playerCount(owner, material) == 1
                                        && roster.freeSlots(owner.getUUID()) == 9,
                                "A failed child must release its reservation and leave its unused kit with the player");
                    });
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_batch_lifecycle", timeoutTicks = 420)
    public static void closingMenuContinuesAndLogoutCancelsOnlyUnfinishedChildren(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerLevel level = helper.getLevel();
            List<BlockPos> columns = columns(helper, 2);
            BlockPos bad = new BlockPos(40_000_000, columns.get(1).getY(), columns.get(1).getZ());
            prepareColumn(blocks, level, columns.get(0), Blocks.STONE.defaultBlockState());
            prepareColumn(blocks, level, columns.get(1), Blocks.STONE.defaultBlockState());
            ensurePrepared(level, columns.get(0));
            ensurePrepared(level, columns.get(1));
            fixture.installRelocation(List.of(columns.get(0), columns.get(1), bad),
                    new WorkerRelocation.Limits(32, 160, 40));
            owner.getInventory().setItem(1, named(Items.IRON_PICKAXE, 1, "close-kit"));
            owner.getInventory().setItem(11, named(Items.DIRT, 1, "close-material"));
            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            long supplyRevision = menu.snapshot().getLong("SupplyRevision");
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 1, false, 1, intList(1),
                            materials(material(11, 1)), supplyRevision));
            UUID firstConfirmation = response(menu).getUUID("Confirmation");
            send(owner, menu, 2, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(firstConfirmation));
            owner.closeContainer();
            WorkerRoster roster = WorkerRoster.get(level.getServer());

            awaitUntil(helper, fixture, blocks, owner, menu, 300,
                    snapshot -> roster.list(owner.getUUID(), false).size() == 1
                            && countState(snapshot.getList("Batches", Tag.TAG_COMPOUND), "DEPLOYED") == 1,
                    () -> {
                        helper.assertTrue(playerCount(owner, Items.IRON_PICKAXE) == 0
                                        && playerCount(owner, Items.DIRT) == 0,
                                "Closing the menu must preserve the server-lifetime batch until its child succeeds");
                        owner.getInventory().setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
                        owner.getInventory().setItem(2, new ItemStack(Items.NETHERITE_PICKAXE));
                        owner.getInventory().setItem(11, new ItemStack(Items.STONE, 2));
                        WorkerMenu.open(owner, null, false, 0);
                        WorkerMenu secondMenu = requireMenu(owner);
                        long secondRevision = secondMenu.snapshot().getLong("SupplyRevision");
                        send(owner, secondMenu, 1, WorkerNetwork.Action.PREVIEW_BATCH,
                                batchData(List.of(), List.of(IRON_ORE), 1, false, 2, intList(1, 2),
                                        materials(material(11, 1)), secondRevision));
                        UUID secondConfirmation = response(secondMenu).getUUID("Confirmation");
                        send(owner, secondMenu, 2, WorkerNetwork.Action.SUBMIT_BATCH,
                                confirmationData(secondConfirmation));
                        awaitUntil(helper, fixture, blocks, owner, secondMenu, 100,
                                snapshotAfterSecond -> roster.list(owner.getUUID(), false).size() == 2
                                        && fixture.relocation().pendingRequests(owner.getUUID()).size() == 1,
                                () -> {
                                    invokeBatchLifecycle("logout", new PlayerEvent.PlayerLoggedOutEvent(owner));
                                    if (Objects.equals(level.getServer().getPlayerList().getPlayer(owner.getUUID()), owner)) {
                                        level.getServer().getPlayerList().remove(owner);
                                    }
                                    List<CompoundTag> afterLogout = rows(secondMenu.snapshot());
                                    helper.assertTrue(countState(afterLogout, "DEPLOYED") == 2
                                                    && countState(afterLogout, "CANCELLED") == 1
                                                    && afterLogout.stream().anyMatch(row ->
                                                    row.getString("State").equals("CANCELLED")
                                                            && row.getString("Error").equals("OWNER_DISCONNECTED"))
                                                    && fixture.relocation().pendingRequests(owner.getUUID()).isEmpty()
                                                    && roster.freeSlots(owner.getUUID()) == 8
                                                    && playerCount(owner, Items.NETHERITE_PICKAXE) == 1
                                                    && playerCount(owner, Items.STONE) == 1,
                                            "Logout must cancel the unfinished child, release its reservation, and retain prior success");
                                    finish(helper, fixture, blocks, null);
                                });
                    });
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_batch_shutdown", timeoutTicks = 240)
    public static void shutdownCancelsQueuedChildAndReleasesItsPreparationTicket(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerLevel level = helper.getLevel();
            BlockPos farColumn = helper.absolutePos(new BlockPos(160, 80, 160));
            fixture.installRelocation(List.of(farColumn), new WorkerRelocation.Limits(8, 220, 100));
            owner.getInventory().setItem(1, new ItemStack(Items.IRON_PICKAXE));
            owner.getInventory().setItem(11, new ItemStack(Items.COBBLESTONE));
            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            long supplyRevision = menu.snapshot().getLong("SupplyRevision");
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 1, false, 1, intList(1),
                            materials(material(11, 1)), supplyRevision));
            UUID confirmation = response(menu).getUUID("Confirmation");
            send(owner, menu, 2, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(confirmation));
            Object batchService = batchService(level.getServer());
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            awaitUntil(helper, fixture, blocks, owner, menu, 80,
                    snapshot -> countState(snapshot.getList("Batches", Tag.TAG_COMPOUND), "PREPARING") == 1
                            && fixture.relocation().pendingRequests(owner.getUUID()).size() == 1
                            && fixture.relocation().pendingRequests(owner.getUUID()).getFirst().attempts() > 0,
                    () -> {
                        UUID request = rows(menu.snapshot()).getFirst().getUUID("Request");
                        helper.assertTrue(preparationTickets(level, request) > 0,
                                "A preparing child must hold its real relocation preparation ticket");
                        invokeBatchLifecycle("stop", new ServerStoppingEvent(level.getServer()));
                        ListTag history = invokeBatchSnapshot(batchService, owner.getUUID());
                        helper.assertTrue(history.size() == 1 && history.getCompound(0).getString("State").equals("CANCELLED")
                                        && history.getCompound(0).getString("Error").equals("SERVER_STOPPING")
                                        && fixture.relocation().pendingRequests(owner.getUUID()).isEmpty()
                                        && preparationTickets(level, request) == 0
                                        && roster.freeSlots(owner.getUUID()) == 10
                                        && roster.list(owner.getUUID(), false).isEmpty()
                                        && playerCount(owner, Items.IRON_PICKAXE) == 1
                                        && playerCount(owner, Items.COBBLESTONE) == 1,
                                "Shutdown must cancel the pending child, release its ticket and preserve unused supplies");
                        finish(helper, fixture, blocks, null);
                    });
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_fleet_actions", timeoutTicks = 220)
    public static void fleetReportsEligibilityAppliesActionsAndPreservesRetirement(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            ServerPlayer stranger = fixture.player();
            ServerLevel level = helper.getLevel();
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            WorkerEntity unavailable = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(0, 1, 0)));
            unavailable.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            releaseFixtureTickets(unavailable);
            WorkerEntity noJob = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(3, 1, 0)));
            WorkerEntity configured = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(6, 1, 0)));
            WorkerEntity busy = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(9, 1, 0)));
            configured.configureMining(List.of(IRON_ORE), 4);
            busy.configureMining(List.of(IRON_ORE), 4);
            busy.startMining(List.of(IRON_ORE), 4);
            busy.pauseMining();
            busy.setItem(0, named(Items.DIAMOND, 3, "retirement-content"));
            long noJobRevision = roster.view(owner.getUUID(), noJob.getUUID()).revision();
            long configuredRevision = roster.view(owner.getUUID(), configured.getUUID()).revision();
            long busyRevision = roster.view(owner.getUUID(), busy.getUUID()).revision();

            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            List<CompoundTag> startRefs = List.of(
                    ref(unavailable, roster.view(owner.getUUID(), unavailable.getUUID()).revision()),
                    ref(noJob, noJobRevision), ref(configured, configuredRevision));
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(startRefs, "START"));
            CompoundTag preview = response(menu);
            ListTag eligibility = preview.getList("Recipients", Tag.TAG_COMPOUND);
            helper.assertTrue(preview.getString("Kind").equals("FleetPreview")
                            && !preview.getBoolean("ConfirmationRequired")
                            && eligibility.size() == 3
                            && eligibility.getCompound(0).getString("Error").equals("WORKER_UNAVAILABLE")
                            && eligibility.getCompound(1).getString("Error").equals("NO_JOB")
                            && eligibility.getCompound(2).getBoolean("Eligible"),
                    "Fleet preview must report unavailable, no-job and eligible workers separately");
            UUID startConfirmation = preview.getUUID("Confirmation");
            send(owner, menu, 2, WorkerNetwork.Action.APPLY_FLEET, confirmationData(startConfirmation));
            CompoundTag started = response(menu);
            helper.assertTrue(started.getString("Kind").equals("FleetResult")
                            && outcomeError(started, unavailable).equals("WORKER_UNAVAILABLE")
                            && outcomeError(started, noJob).equals("NO_JOB")
                            && outcomeError(started, configured).isEmpty()
                            && configured.miningStatus().state() == MiningSession.State.RUNNING,
                    "Fleet apply must preserve per-worker failures while starting eligible stored jobs");

            send(owner, menu, 3, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(busy, busyRevision)), "START"));
            CompoundTag busyPreview = response(menu);
            helper.assertTrue(busyPreview.getBoolean("ConfirmationRequired")
                            && busyPreview.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0).getBoolean("Busy"),
                    "Starting a busy worker must require one replacement confirmation");
            UUID busyStart = busyPreview.getUUID("Confirmation");
            send(owner, menu, 4, WorkerNetwork.Action.APPLY_FLEET, confirmationData(busyStart));
            helper.assertTrue(outcomeError(response(menu), busy).isEmpty()
                            && busy.miningStatus().state() == MiningSession.State.RUNNING,
                    "Confirmed START must replace the busy worker's run");

            long latestBusyRevision = roster.view(owner.getUUID(), busy.getUUID()).revision();
            send(owner, menu, 5, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(busy, latestBusyRevision)), "PAUSE"));
            UUID pause = response(menu).getUUID("Confirmation");
            send(owner, menu, 6, WorkerNetwork.Action.APPLY_FLEET, confirmationData(pause));
            helper.assertTrue(outcomeError(response(menu), busy).isEmpty()
                            && busy.miningStatus().state() == MiningSession.State.PAUSED,
                    "Fleet PAUSE must act only on a running worker");

            latestBusyRevision = roster.view(owner.getUUID(), busy.getUUID()).revision();
            send(owner, menu, 7, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(busy, latestBusyRevision)), "RESUME"));
            UUID resume = response(menu).getUUID("Confirmation");
            send(owner, menu, 8, WorkerNetwork.Action.APPLY_FLEET, confirmationData(resume));
            helper.assertTrue(outcomeError(response(menu), busy).isEmpty()
                            && busy.miningStatus().state() == MiningSession.State.RUNNING,
                    "Fleet RESUME must act only on a paused worker");

            latestBusyRevision = roster.view(owner.getUUID(), busy.getUUID()).revision();
            send(owner, menu, 9, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(busy, latestBusyRevision)), "STOP"));
            UUID stop = response(menu).getUUID("Confirmation");
            send(owner, menu, 10, WorkerNetwork.Action.APPLY_FLEET, confirmationData(stop));
            helper.assertTrue(outcomeError(response(menu), busy).isEmpty()
                            && busy.miningStatus().state() == MiningSession.State.CANCELLED,
                    "Fleet STOP must cancel a running worker synchronously");

            latestBusyRevision = roster.view(owner.getUUID(), busy.getUUID()).revision();
            send(owner, menu, 11, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(busy, latestBusyRevision)), "RETIRE"));
            CompoundTag retirePreview = response(menu);
            helper.assertTrue(retirePreview.getBoolean("ConfirmationRequired"),
                    "Retirement must always require confirmation");
            UUID retire = retirePreview.getUUID("Confirmation");
            send(owner, menu, 12, WorkerNetwork.Action.APPLY_FLEET, confirmationData(retire));
            helper.assertTrue(outcomeError(response(menu), busy).isEmpty()
                            && roster.list(owner.getUUID(), false).stream().noneMatch(view -> view.worker().equals(busy.getUUID()))
                            && roster.list(owner.getUUID(), true).stream().anyMatch(view -> view.worker().equals(busy.getUUID()))
                            && roster.archivedInventory(owner.getUUID(), busy.getUUID()).get(0).getCount() == 3
                            && roster.archivedInventory(owner.getUUID(), busy.getUUID()).get(0)
                            .get(DataComponents.CUSTOM_NAME) != null,
                    "Fleet RETIRE must stop and archive the worker's identity and component-bearing contents");

            menu.handle(stranger, new WorkerNetwork.Intent(menu.containerId, menu.session(), 99,
                    WorkerNetwork.Action.PREVIEW_FLEET, fleetData(List.of(ref(noJob, noJobRevision)), "STOP")));
            helper.assertTrue(roster.list(owner.getUUID(), false).stream().anyMatch(view -> view.worker().equals(noJob.getUUID())),
                    "A foreign player must not mutate the owner's fleet through its menu");
            finish(helper, fixture, new BlockFixture(), null);
        } catch (Throwable failure) {
            finish(helper, fixture, new BlockFixture(), failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_fleet_relocation", timeoutTicks = 420)
    public static void fleetRelocatesExistingWorkersThroughBoundedQueueAndPreservesState(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerLevel level = helper.getLevel();
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            List<BlockPos> destinations = columns(helper, 6);
            for (BlockPos destination : destinations) {
                prepareColumn(blocks, level, destination, Blocks.STONE.defaultBlockState());
                ensurePrepared(level, destination);
            }
            fixture.installRelocation(destinations, new WorkerRelocation.Limits(8, 220, 60));

            List<WorkerEntity> originals = new ArrayList<>();
            List<ItemStack> tools = new ArrayList<>();
            List<ItemStack> storage = new ArrayList<>();
            for (int index = 0; index < destinations.size(); index++) {
                WorkerEntity worker = fixture.spawnOwned(owner, level,
                        helper.absolutePos(new BlockPos(index * 3, 1, 0)));
                ItemStack tool = named(Items.IRON_PICKAXE, 1, "fleet-tool-" + index);
                tool.setDamageValue(index + 1);
                ItemStack contents = named(Items.DIAMOND, index + 1, "fleet-storage-" + index);
                worker.setItem(0, tool.copy());
                worker.setItem(2, contents.copy());
                worker.setItem(WorkerEntity.INVENTORY_SIZE - 1,
                        named(Items.EMERALD, 2 + index, "fleet-reserve-" + index));
                worker.setSelectedSlot(2);
                worker.startMining(List.of(IRON_ORE), 4 + index);
                originals.add(worker);
                tools.add(tool);
                storage.add(contents);
            }
            int initialRosterSize = roster.list(owner.getUUID(), false).size();
            int initialFreeSlots = roster.freeSlots(owner.getUUID());
            ItemStack controller = owner.getInventory().getItem(0).copy();

            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            List<CompoundTag> refs = originals.stream()
                    .map(worker -> ref(worker, roster.view(owner.getUUID(), worker.getUUID()).revision()))
                    .toList();
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(refs, "RELOCATE", Level.OVERWORLD.location().toString()));
            CompoundTag preview = response(menu);
            ListTag previewRows = preview.getList("Recipients", Tag.TAG_COMPOUND);
            helper.assertTrue(preview.getString("Kind").equals("FleetPreview")
                            && preview.getString("Operation").equals("RELOCATE")
                            && preview.getString("Dimension").equals(Level.OVERWORLD.location().toString())
                            && preview.getBoolean("ConfirmationRequired")
                            && previewRows.size() == originals.size()
                            && previewRows.stream().allMatch(row -> ((CompoundTag) row).getBoolean("Eligible")
                            && ((CompoundTag) row).getString("Error").isEmpty()),
                    "Relocation preview must confirm the destination and expose every eligible owned worker");

            send(owner, menu, 2, WorkerNetwork.Action.APPLY_FLEET,
                    confirmationData(preview.getUUID("Confirmation")));
            CompoundTag applied = response(menu);
            ListTag outcomes = applied.getList("Recipients", Tag.TAG_COMPOUND);
            helper.assertTrue(applied.getString("Kind").equals("FleetResult")
                            && applied.getString("Operation").equals("RELOCATE")
                            && applied.getInt("Queued") == originals.size()
                            && outcomes.size() == originals.size()
                            && outcomes.stream().allMatch(row -> ((CompoundTag) row).getString("State").equals("QUEUED")
                            && ((CompoundTag) row).getString("Error").isEmpty())
                            && originals.stream().allMatch(worker ->
                            worker.miningStatus().state() == MiningSession.State.PAUSED)
                            && roster.list(owner.getUUID(), false).size() == initialRosterSize
                            && roster.freeSlots(owner.getUUID()) == initialFreeSlots
                            && matches(owner.getInventory().getItem(0), controller),
                    "Applying relocation must pause and queue existing workers without reservations or kits");
            List<MiningSession.Snapshot> pausedJobs = originals.stream().map(WorkerEntity::miningStatus).toList();

            int[] maxPreparers = {0};
            awaitBatch(helper, fixture, blocks, owner, menu, 380,
                    snapshot -> {
                        maxPreparers[0] = Math.max(maxPreparers[0],
                                fixture.relocation().pendingRequests(owner.getUUID()).size());
                        return allStates(snapshot.getList("Batches", Tag.TAG_COMPOUND), "RELOCATED");
                    }, () -> {
                        helper.assertTrue(maxPreparers[0] == WorkerRelocation.MAX_CONCURRENT,
                                "Relocation children must share the four-preparer limit");
                        for (int index = 0; index < originals.size(); index++) {
                            WorkerEntity moved = roster.active(owner.getUUID(), originals.get(index).getUUID());
                            MiningSession.Snapshot job = moved.miningStatus();
                            helper.assertTrue(moved.getUUID().equals(originals.get(index).getUUID())
                                            && job.equals(pausedJobs.get(index))
                                            && job.state() == MiningSession.State.PAUSED
                                            && matches(moved.getItem(0), tools.get(index))
                                            && matches(moved.getItem(2), storage.get(index))
                                            && moved.getItem(WorkerEntity.INVENTORY_SIZE - 1)
                                            .get(DataComponents.CUSTOM_NAME) != null,
                                    "Relocation must preserve identity, components, storage and paused job progress");
                        }
                        helper.assertTrue(roster.list(owner.getUUID(), false).size() == initialRosterSize
                                        && roster.freeSlots(owner.getUUID()) == initialFreeSlots
                                        && fixture.relocation().pendingRequests(owner.getUUID()).isEmpty(),
                                "Completed relocation must not create roster reservations or leave searches pending");
                    });
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_fleet_relocation_validation", timeoutTicks = 220)
    public static void fleetRelocationRejectsDuplicateStaleAndForeignRequests(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            ServerPlayer stranger = fixture.player();
            ServerLevel level = helper.getLevel();
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            WorkerEntity first = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity second = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(3, 1, 0)));
            WorkerEntity foreign = fixture.spawnOwned(stranger, level, helper.absolutePos(new BlockPos(6, 1, 0)));
            first.startMining(List.of(IRON_ORE), 3);
            MiningSession.Snapshot secondBefore = second.miningStatus();
            MiningSession.Snapshot foreignBefore = foreign.miningStatus();
            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            int sequence = 0;

            long firstRevision = roster.view(owner.getUUID(), first.getUUID()).revision();
            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(first, firstRevision)), "RELOCATE", Level.OVERWORLD.location().toString()));
            send(owner, menu, ++sequence, WorkerNetwork.Action.APPLY_FLEET,
                    confirmationData(response(menu).getUUID("Confirmation")));
            helper.assertTrue(response(menu).getString("Kind").equals("FleetResult")
                            && rows(menu.snapshot()).size() == 1
                            && fixture.relocation().pendingRequests(owner.getUUID()).isEmpty()
                            && first.miningStatus().state() == MiningSession.State.PAUSED,
                    "The first relocation must queue one existing worker before duplicate validation");

            long pendingRevision = roster.view(owner.getUUID(), first.getUUID()).revision();
            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(first, pendingRevision)), "RELOCATE", Level.OVERWORLD.location().toString()));
            CompoundTag duplicatePreview = response(menu);
            helper.assertTrue(duplicatePreview.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0)
                            .getString("Error").equals("WORKER_PENDING")
                            && !duplicatePreview.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0)
                            .getBoolean("Eligible"),
                    "A queued worker must be reported as WORKER_PENDING in a duplicate relocation preview");
            send(owner, menu, ++sequence, WorkerNetwork.Action.APPLY_FLEET,
                    confirmationData(duplicatePreview.getUUID("Confirmation")));
            helper.assertTrue(outcomeError(response(menu), first).equals("WORKER_PENDING")
                            && rows(menu.snapshot()).size() == 1
                            && fixture.relocation().pendingRequests(owner.getUUID()).isEmpty(),
                    "A duplicate relocation must never append a second batch child or native request");

            long secondRevision = roster.view(owner.getUUID(), second.getUUID()).revision();
            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(second, secondRevision)), "RELOCATE", Level.OVERWORLD.location().toString()));
            UUID staleConfirmation = response(menu).getUUID("Confirmation");
            roster.rename(owner.getUUID(), second.getUUID(), secondRevision, "changed after relocation preview");
            send(owner, menu, ++sequence, WorkerNetwork.Action.APPLY_FLEET,
                    confirmationData(staleConfirmation));
            assertError(helper, menu, "STALE_PREVIEW");
            helper.assertTrue(second.miningStatus().equals(secondBefore)
                            && rows(menu.snapshot()).size() == 1,
                    "A stale relocation confirmation must not pause or queue its worker");

            long foreignRevision = roster.view(stranger.getUUID(), foreign.getUUID()).revision();
            send(owner, menu, ++sequence, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(foreign, foreignRevision)), "RELOCATE", Level.OVERWORLD.location().toString()));
            assertError(helper, menu, "NOT_OWNER");
            helper.assertTrue(foreign.miningStatus().equals(foreignBefore)
                            && rows(menu.snapshot()).size() == 1
                            && roster.list(owner.getUUID(), false).size() == 2,
                    "A foreign relocation reference must be rejected before any owner state mutation");
            finish(helper, fixture, new BlockFixture(), null);
        } catch (Throwable failure) {
            finish(helper, fixture, new BlockFixture(), failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_fleet_relocation_lifecycle", timeoutTicks = 360)
    public static void closingFleetRelocationContinuesAndLifecycleCancelsPendingChildren(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerPlayer secondOwner = fixture.player();
            ServerLevel level = helper.getLevel();
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            List<BlockPos> destinations = columns(helper, 2);
            for (BlockPos destination : destinations) {
                prepareColumn(blocks, level, destination, Blocks.STONE.defaultBlockState());
                ensurePrepared(level, destination);
            }
            fixture.installRelocation(destinations, new WorkerRelocation.Limits(8, 220, 60));
            WorkerEntity success = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity queued = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(3, 1, 0)));
            WorkerEntity queuedSibling = fixture.spawnOwned(owner, level, helper.absolutePos(new BlockPos(6, 1, 0)));
            WorkerEntity shutdown = fixture.spawnOwned(secondOwner, level, helper.absolutePos(new BlockPos(9, 1, 0)));
            ItemStack successContents = named(Items.DIAMOND, 2, "successful-relocation");
            ItemStack queuedContents = named(Items.EMERALD, 3, "queued-relocation");
            ItemStack siblingContents = named(Items.GOLD_INGOT, 4, "queued-sibling");
            ItemStack shutdownContents = named(Items.REDSTONE, 5, "shutdown-relocation");
            success.setItem(0, successContents.copy());
            queued.setItem(0, queuedContents.copy());
            queuedSibling.setItem(0, siblingContents.copy());
            shutdown.setItem(0, shutdownContents.copy());

            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu firstMenu = requireMenu(owner);
            send(owner, firstMenu, 1, WorkerNetwork.Action.PREVIEW_FLEET,
                    fleetData(List.of(ref(success, roster.view(owner.getUUID(), success.getUUID()).revision())),
                            "RELOCATE", Level.OVERWORLD.location().toString()));
            send(owner, firstMenu, 2, WorkerNetwork.Action.APPLY_FLEET,
                    confirmationData(response(firstMenu).getUUID("Confirmation")));
            owner.closeContainer();
            awaitUntil(helper, fixture, blocks, owner, firstMenu, 180,
                    snapshot -> allStates(snapshot.getList("Batches", Tag.TAG_COMPOUND), "RELOCATED"),
                    () -> {
                        WorkerMenu secondMenu = null;
                        try {
                            WorkerMenu.open(owner, null, false, 0);
                            secondMenu = requireMenu(owner);
                            List<CompoundTag> queuedRefs = List.of(
                                    ref(queued, roster.view(owner.getUUID(), queued.getUUID()).revision()),
                                    ref(queuedSibling, roster.view(owner.getUUID(), queuedSibling.getUUID()).revision()));
                            send(owner, secondMenu, 1, WorkerNetwork.Action.PREVIEW_FLEET,
                                    fleetData(queuedRefs, "RELOCATE", Level.OVERWORLD.location().toString()));
                            send(owner, secondMenu, 2, WorkerNetwork.Action.APPLY_FLEET,
                                    confirmationData(response(secondMenu).getUUID("Confirmation")));
                            owner.closeContainer();
                            invokeBatchLifecycle("logout", new PlayerEvent.PlayerLoggedOutEvent(owner));
                            List<CompoundTag> afterLogout = rows(secondMenu.snapshot());
                            helper.assertTrue(afterLogout.size() == 3
                                            && countState(afterLogout, "RELOCATED") == 1
                                            && countState(afterLogout, "CANCELLED") == 2
                                            && afterLogout.stream().filter(row -> row.getUUID("Worker").equals(success.getUUID()))
                                            .allMatch(row -> row.getString("State").equals("RELOCATED"))
                                            && afterLogout.stream().filter(row -> row.getUUID("Worker").equals(queued.getUUID())
                                            || row.getUUID("Worker").equals(queuedSibling.getUUID()))
                                            .allMatch(row -> row.getString("State").equals("CANCELLED")
                                            && row.getString("Error").equals("OWNER_DISCONNECTED"))
                                            && matches(roster.active(owner.getUUID(), queued.getUUID()).getItem(0), queuedContents)
                                            && matches(roster.active(owner.getUUID(), queuedSibling.getUUID()).getItem(0), siblingContents)
                                            && matches(roster.active(owner.getUUID(), success.getUUID()).getItem(0), successContents),
                                    "Logout must cancel queued relocation children while preserving items and successful siblings");

                            WorkerMenu.open(secondOwner, null, false, 0);
                            WorkerMenu shutdownMenu = requireMenu(secondOwner);
                            send(secondOwner, shutdownMenu, 1, WorkerNetwork.Action.PREVIEW_FLEET,
                                    fleetData(List.of(ref(shutdown, roster.view(secondOwner.getUUID(), shutdown.getUUID()).revision())),
                                            "RELOCATE", Level.OVERWORLD.location().toString()));
                            send(secondOwner, shutdownMenu, 2, WorkerNetwork.Action.APPLY_FLEET,
                                    confirmationData(response(shutdownMenu).getUUID("Confirmation")));
                            Object service = batchService(level.getServer());
                            Method tick = service.getClass().getDeclaredMethod("tick");
                            tick.setAccessible(true);
                            tick.invoke(service);
                            helper.assertTrue(rows(shutdownMenu.snapshot()).stream()
                                            .anyMatch(row -> row.getUUID("Worker").equals(shutdown.getUUID())
                                            && row.getString("State").equals("PREPARING")),
                                    "Shutdown setup must expose a real preparing relocation child");
                            invokeBatchLifecycle("stop", new ServerStoppingEvent(level.getServer()));
                            ListTag history = invokeBatchSnapshot(service, secondOwner.getUUID());
                            helper.assertTrue(history.size() == 1
                                            && history.getCompound(0).getString("State").equals("CANCELLED")
                                            && history.getCompound(0).getString("Error").equals("SERVER_STOPPING")
                                            && fixture.relocation().pendingRequests(secondOwner.getUUID()).isEmpty()
                                            && matches(roster.active(secondOwner.getUUID(), shutdown.getUUID()).getItem(0), shutdownContents),
                                    "Shutdown must cancel a preparing relocation without removing its worker or contents");
                            finish(helper, fixture, blocks, null);
                        } catch (Throwable failure) {
                            finish(helper, fixture, blocks, failure);
                        }
                    });
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_batch_commit_failure", timeoutTicks = 360)
    public static void lateKitShortageAndPostCommitFailurePreserveSuppliesAndSuccessfulSibling(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        BlockFixture blocks = new BlockFixture();
        try {
            ServerPlayer owner = fixture.player();
            ServerLevel level = helper.getLevel();
            List<BlockPos> columns = columns(helper, 3);
            for (BlockPos column : columns) {
                prepareColumn(blocks, level, column, Blocks.STONE.defaultBlockState());
                ensurePrepared(level, column);
            }
            fixture.installRelocation(columns, new WorkerRelocation.Limits(8, 220, 60));
            ItemStack first = named(Items.IRON_PICKAXE, 1, "post-commit-tool");
            first.setDamageValue(13);
            ItemStack second = named(Items.DIAMOND_PICKAXE, 1, "withdrawn-tool");
            ItemStack third = named(Items.NETHERITE_PICKAXE, 1, "successful-tool");
            ItemStack material = named(Items.DIRT, 3, "shared-material");
            owner.getInventory().setItem(1, first.copy());
            owner.getInventory().setItem(2, second.copy());
            owner.getInventory().setItem(3, third.copy());
            owner.getInventory().setItem(11, material.copy());
            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_BATCH,
                    batchData(List.of(), List.of(IRON_ORE), 4, true, 3, intList(1, 2, 3),
                            materials(material(11, 1)), menu.snapshot().getLong("SupplyRevision")));
            helper.assertTrue(response(menu).getBoolean("CanSubmit"), "The original complete inventory must authorize all three kits");
            send(owner, menu, 2, WorkerNetwork.Action.SUBMIT_BATCH, confirmationData(response(menu).getUUID("Confirmation")));
            List<CompoundTag> accepted = rows(menu.snapshot());
            UUID firstRequest = accepted.getFirst().getUUID("Request");
            UUID firstWorker = accepted.getFirst().getUUID("Worker");
            UUID secondWorker = accepted.get(1).getUUID("Worker");
            UUID thirdWorker = accepted.get(2).getUUID("Worker");
            CompoundTag bypass = new CompoundTag();
            bypass.putUUID("Request", firstRequest); bypass.putString("Dimension", Level.OVERWORLD.location().toString());
            send(owner, menu, 3, WorkerNetwork.Action.DEPLOY, bypass);
            assertError(helper, menu, "BATCH_REQUEST");
            // Inventory legitimately changes after confirmation. A future child must revalidate it.
            ItemStack withdrawn = owner.getInventory().removeItem(2, 1);
            Object service = batchService(level.getServer());
            Method tick = service.getClass().getDeclaredMethod("tick");
            tick.setAccessible(true); tick.invoke(service);
            java.util.concurrent.atomic.AtomicBoolean equipped = new java.util.concurrent.atomic.AtomicBoolean();
            failAfterRealEquip(fixture.relocation(), firstRequest, worker -> {
                helper.assertTrue(worker.miningStatus().state() == MiningSession.State.RUNNING
                                && matches(worker.getItem(0), first)
                                && playerCount(owner, first) == 0,
                        "Real deployment must equip, consume once, and start before the injected failure");
                equipped.set(true);
            });
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            awaitBatch(helper, fixture, blocks, owner, menu, 320,
                    snapshot -> countState(snapshot.getList("Batches", Tag.TAG_COMPOUND), "FAILED") == 2
                            && countState(snapshot.getList("Batches", Tag.TAG_COMPOUND), "RUNNING") == 1,
                    () -> {
                        List<CompoundTag> outcomes = rows(menu.snapshot());
                        helper.assertTrue(equipped.get()
                                        && outcomes.stream().anyMatch(row -> row.getUUID("Worker").equals(firstWorker)
                                        && row.getString("Error").equals("TEST_COMMIT_FAILURE"))
                                        && outcomes.stream().anyMatch(row -> row.getUUID("Worker").equals(secondWorker)
                                        && row.getString("Error").equals("KIT_SHORTAGE")),
                                "Each failure must report its own commit or changed-inventory cause");
                        helper.assertTrue(roster.view(owner.getUUID(), firstWorker).retired()
                                        && matches(roster.archivedInventory(owner.getUUID(), firstWorker).get(0), first)
                                        && matches(roster.archivedInventory(owner.getUUID(), firstWorker).get(1), material.copyWithCount(1))
                                        && level.getEntity(firstWorker) == null,
                                "A failure after transfer must retire the worker with every exact component and remainder");
                        WorkerEntity success = roster.active(owner.getUUID(), thirdWorker);
                        helper.assertTrue(success.miningStatus().requested() == 4 && success.miningStatus().runId() != null
                                        && success.getItem(0).is(Items.NETHERITE_PICKAXE)
                                        && playerCount(owner, material) == 1 && matches(withdrawn, second)
                                        && roster.freeSlots(owner.getUUID()) == 9
                                        && fixture.relocation().pendingRequests(owner.getUUID()).isEmpty()
                                        && preparationTickets(level, firstRequest) == 0,
                                "The successful sibling must retain its job/kit while the failed unused kit stays unconsumed");
                    });
        } catch (Throwable failure) { finish(helper, fixture, blocks, failure); }
    }

    private static void failAfterRealEquip(WorkerRelocation relocation, UUID request, Consumer<WorkerEntity> assertion)
            throws ReflectiveOperationException {
        Map<?, ?> jobs = (Map<?, ?>) field(relocation, "jobs").get(relocation);
        Object job = Objects.requireNonNull(jobs.get(request));
        Field commitField = field(job, "commit");
        Object actual = Objects.requireNonNull(commitField.get(job));
        Class<?> contract = commitField.getType();
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract},
                (ignored, method, args) -> {
                    method.setAccessible(true);
                    try { method.invoke(actual, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                    if (method.getName().equals("equip")) {
                        assertion.accept((WorkerEntity) args[0]);
                        throw new IllegalStateException("TEST_COMMIT_FAILURE");
                    }
                    return null;
                });
        commitField.set(job, proxy);
    }

    private static WorkerMenu requireMenu(ServerPlayer player) {
        if (!(player.containerMenu instanceof WorkerMenu menu)) {
            throw new AssertionError("Expected the real player container to be a WorkerMenu");
        }
        return menu;
    }

    private static void send(ServerPlayer holder, WorkerMenu menu, long sequence,
                             WorkerNetwork.Action action, CompoundTag data) {
        menu.handle(holder, new WorkerNetwork.Intent(menu.containerId, menu.session(), sequence, action, data));
    }

    private static CompoundTag response(WorkerMenu menu) {
        return menu.snapshot().getCompound("Response");
    }

    private static void assertError(GameTestHelper helper, WorkerMenu menu, String expected) {
        CompoundTag actual = response(menu);
        helper.assertTrue(actual.getString("Kind").equals("Error") && actual.getString("Error").equals(expected),
                "Expected batch error " + expected + " but received " + actual);
    }

    private static CompoundTag confirmationData(UUID confirmation) {
        CompoundTag data = new CompoundTag();
        data.putUUID("Confirmation", confirmation);
        return data;
    }

    private static CompoundTag ref(WorkerEntity worker, long revision) {
        CompoundTag ref = new CompoundTag();
        ref.putUUID("Worker", worker.getUUID());
        ref.putLong("Revision", revision);
        return ref;
    }

    private static CompoundTag batchData(List<CompoundTag> recipients, List<net.minecraft.resources.ResourceLocation> targets,
                                         int quantity, boolean start, int newCount, ListTag toolSlots,
                                         ListTag materials, long supplyRevision) {
        CompoundTag data = new CompoundTag();
        ListTag recipientList = new ListTag();
        recipients.forEach(recipientList::add);
        data.put("Recipients", recipientList);
        ListTag targetList = new ListTag();
        targets.forEach(target -> targetList.add(StringTag.valueOf(target.toString())));
        data.put("Targets", targetList);
        data.putInt("Quantity", quantity);
        data.putBoolean("Start", start);
        data.putInt("NewCount", newCount);
        data.putString("Dimension", Level.OVERWORLD.location().toString());
        data.put("ToolSlots", toolSlots);
        data.put("Materials", materials);
        data.putLong("SupplyRevision", supplyRevision);
        return data;
    }

    private static CompoundTag fleetData(List<CompoundTag> recipients, String operation) {
        CompoundTag data = new CompoundTag();
        ListTag recipientList = new ListTag();
        recipients.forEach(recipientList::add);
        data.put("Recipients", recipientList);
        data.putString("Operation", operation);
        return data;
    }

    private static CompoundTag fleetData(List<CompoundTag> recipients, String operation, String dimension) {
        CompoundTag data = fleetData(recipients, operation);
        data.putString("Dimension", dimension);
        return data;
    }

    private static ListTag materials(CompoundTag... materials) {
        ListTag result = new ListTag();
        for (CompoundTag material : materials) {
            result.add(material);
        }
        return result;
    }

    private static CompoundTag material(int slot, int count) {
        CompoundTag result = new CompoundTag();
        result.putInt("Slot", slot);
        result.putInt("Count", count);
        return result;
    }

    private static ListTag intList(int... values) {
        ListTag result = new ListTag();
        for (int value : values) {
            result.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        return result;
    }

    private static List<Integer> range(int from, int to) {
        List<Integer> result = new ArrayList<>();
        for (int value = from; value <= to; value++) {
            result.add(value);
        }
        return result;
    }

    private static ListTag intList(List<Integer> values) {
        return intList(values.stream().mapToInt(Integer::intValue).toArray());
    }

    private static ItemStack named(Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static boolean matches(ItemStack actual, ItemStack expected) {
        return ItemStack.matches(actual, expected);
    }

    private static int playerCount(ServerPlayer player, Item expected) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(expected)) {
                total += stack.getCount();
            }
        }
        return total;
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

    private static boolean required(GameTestHelper helper, CompoundTag preview, ItemStack expected, int count) {
        for (Tag value : preview.getList("Supplies", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            if (row.getInt("Required") == count && row.getInt("Available") >= count
                    && ItemStack.isSameItemSameComponents(ItemStack.parseOptional(helper.getLevel().registryAccess(), row.getCompound("Stack")), expected)) {
                return true;
            }
        }
        return false;
    }

    private static List<CompoundTag> rows(CompoundTag snapshot) {
        List<CompoundTag> result = new ArrayList<>();
        for (Tag value : snapshot.getList("Batches", Tag.TAG_COMPOUND)) {
            result.add((CompoundTag) value);
        }
        return result;
    }

    private static int countState(Iterable<? extends Tag> rows, String state) {
        int count = 0;
        for (Tag value : rows) {
            if (((CompoundTag) value).getString("State").equals(state)) {
                count++;
            }
        }
        return count;
    }

    private static boolean allStates(ListTag rows, String state) {
        return !rows.isEmpty() && countState(rows, state) == rows.size();
    }

    private static String outcomeError(CompoundTag result, WorkerEntity worker) {
        for (Tag value : result.getList("Recipients", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            if (row.getUUID("Worker").equals(worker.getUUID())) {
                return row.getString("Error");
            }
        }
        throw new AssertionError("Fleet result omitted worker " + worker.getUUID());
    }

    private static List<BlockPos> columns(GameTestHelper helper, int count) {
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 80, 8));
        List<BlockPos> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new BlockPos(anchor.getX() + index * 8, anchor.getY(), anchor.getZ()));
        }
        return result;
    }

    private static void prepareColumn(BlockFixture fixture, ServerLevel level, BlockPos floorPosition, BlockState floor) {
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

    private static void releaseFixtureTickets(WorkerEntity worker) {
        if (worker == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        UUID identity = worker.getUUID();
        ChunkPos center = worker.chunkPosition();
        new TicketController(WorkerChunkLoading.CENTER_CONTROLLER_ID)
                .forceChunk(level, identity, center.x, center.z, false, true);
        TicketController ring = new TicketController(WorkerChunkLoading.WORKING_RING_CONTROLLER_ID);
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int z = center.z - 1; z <= center.z + 1; z++) {
                if (x != center.x || z != center.z) {
                    ring.forceChunk(level, identity, x, z, false, true);
                }
            }
        }
    }

    private static void awaitBatch(GameTestHelper helper, Fixture fixture, BlockFixture blocks,
                                   ServerPlayer owner, WorkerMenu menu,
                                   int remaining, Predicate<CompoundTag> complete, Runnable assertions) {
        awaitUntil(helper, fixture, blocks, owner, menu, remaining, complete, () -> {
            assertions.run();
            finish(helper, fixture, blocks, null);
        });
    }

    private static void awaitUntil(GameTestHelper helper, Fixture fixture, BlockFixture blocks,
                                   ServerPlayer owner, WorkerMenu menu,
                                   int remaining, Predicate<CompoundTag> complete, Runnable onComplete) {
        try {
            CompoundTag snapshot = menu.snapshot();
            if (complete.test(snapshot)) {
                onComplete.run();
                return;
            }
            if (remaining <= 0) {
                throw new AssertionError("Batch remained pending after focused deadline: " + snapshot);
            }
            helper.runAfterDelay(1, () -> awaitUntil(helper, fixture, blocks, owner, menu,
                    remaining - 1, complete, onComplete));
        } catch (Throwable failure) {
            finish(helper, fixture, blocks, failure);
        }
    }

    private static void finish(GameTestHelper helper, Fixture fixture, BlockFixture blocks, Throwable failure) {
        Throwable outcome = failure;
        try {
            fixture.close();
        } catch (Throwable cleanupFailure) {
            if (outcome == null) {
                outcome = cleanupFailure;
            } else {
                outcome.addSuppressed(cleanupFailure);
            }
        }
        try {
            blocks.close();
        } catch (Throwable cleanupFailure) {
            if (outcome == null) {
                outcome = cleanupFailure;
            } else {
                outcome.addSuppressed(cleanupFailure);
            }
        }
        if (outcome == null) {
            helper.succeed();
        } else {
            helper.fail("Worker batch test failed: " + outcome);
        }
    }

    private static WorkerRelocation deterministicRelocation(ServerLevel level, WorkerRoster roster,
                                                              List<BlockPos> columns, WorkerRelocation.Limits limits) {
        List<BlockPos> copy = List.copyOf(columns);
        int[] next = {0};
        BiFunction<ServerLevel, net.minecraft.util.RandomSource, BlockPos> sampler = (ignored, random) ->
                copy.get(Math.min(next[0]++, copy.size() - 1));
        try {
            Constructor<WorkerRelocation> constructor = WorkerRelocation.class.getDeclaredConstructor(
                    MinecraftServer.class, WorkerRoster.class, BiFunction.class, WorkerRelocation.Limits.class);
            constructor.setAccessible(true);
            return constructor.newInstance(level.getServer(), roster, sampler, limits);
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("The internal relocation fixture constructor changed", failure);
        }
    }

    private static int preparationTickets(ServerLevel level, UUID request) {
        try {
            Object distance = field(level.getChunkSource(), "distanceManager").get(level.getChunkSource());
            Object tickets = field(distance, "tickets").get(distance);
            if (!(tickets instanceof Map<?, ?> ticketMap)) {
                throw new AssertionError("DistanceManager ticket map is not a Map");
            }
            int count = 0;
            for (Object bucket : ticketMap.values()) {
                if (!(bucket instanceof Iterable<?> iterable)) {
                    throw new AssertionError("DistanceManager ticket bucket is not iterable");
                }
                for (Object ticket : iterable) {
                    if (Objects.equals(field(ticket, "type").get(ticket), WorkerRelocation.PREPARATION)
                            && Objects.equals(field(ticket, "key").get(ticket), request)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("NeoForge DistanceManager no longer exposes raw ticket state", failure);
        }
    }

    private static Object batchService(MinecraftServer server) {
        try {
            Object services = field(Class.forName("automatone.worker.WorkerBatch"), "SERVICES").get(null);
            if (!(services instanceof Map<?, ?> map) || !map.containsKey(server)) {
                throw new AssertionError("Batch service was not created by the real menu submission");
            }
            return map.get(server);
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("The internal batch service seam changed", failure);
        }
    }

    private static ListTag invokeBatchSnapshot(Object service, UUID owner) {
        try {
            Method snapshot = service.getClass().getDeclaredMethod("snapshot", UUID.class);
            snapshot.setAccessible(true);
            return (ListTag) snapshot.invoke(service, owner);
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("The internal batch snapshot seam changed", failure);
        }
    }

    private static void invokeBatchLifecycle(String methodName, Object event) {
        try {
            Class<?> type = Class.forName("automatone.worker.WorkerBatch");
            Method method = type.getDeclaredMethod(methodName, event.getClass());
            method.setAccessible(true);
            method.invoke(null, event);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new LinkageError("Batch lifecycle invocation failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("The internal batch lifecycle seam changed", failure);
        }
    }

    private static Field field(Object instance, String name) throws NoSuchFieldException {
        Class<?> type = instance instanceof Class<?> clazz ? clazz : instance.getClass();
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

    private static final class Fixture {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<WorkerEntity> workers = new ArrayList<>();
        private WorkerRelocation relocation;
        private WorkerRelocation previousRelocation;
        private final Object previousBatch;
        private boolean closed;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            previousBatch = batchServices().remove(server);
        }

        private ServerPlayer player() {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                    .add(AdvancedOpenScreenPayload.TYPE.id());
            ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                    .add(WorkerNetwork.Snapshot.TYPE.id());
            ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection()).add(WorkerNetwork.Notice.TYPE.id());
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

        private void installRelocation(List<BlockPos> columns, WorkerRelocation.Limits limits) {
            WorkerRoster roster = WorkerRoster.get(server);
            Map<MinecraftServer, WorkerRelocation> services = relocationServices();
            previousRelocation = services.put(server, deterministicRelocation(helper.getLevel(), roster, columns, limits));
            relocation = services.get(server);
        }

        private WorkerRelocation relocation() {
            if (relocation == null) {
                relocation = WorkerRelocation.get(server);
            }
            return relocation;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (ServerPlayer player : players) {
                player.closeContainer();
                invokeBatchLifecycle("logout", new PlayerEvent.PlayerLoggedOutEvent(player));
            }
            if (relocation != null) {
                for (ServerPlayer player : players) {
                    for (WorkerRelocation.Status status : List.copyOf(relocation.requests(player.getUUID()))) {
                        if (status.state() == WorkerRelocation.State.PENDING) {
                            try {
                                relocation.cancel(player.getUUID(), status.request());
                            } catch (RuntimeException ignored) {
                                // Preserve the test assertion if a lifecycle event already canceled it.
                            }
                        }
                    }
                }
            }
            WorkerRoster roster = WorkerRoster.get(server);
            for (ServerPlayer player : players) {
                for (WorkerRoster.View view : roster.list(player.getUUID(), false)) {
                    try {
                        WorkerEntity worker = roster.active(player.getUUID(), view.worker());
                        if (worker.miningStatus().state() == MiningSession.State.RUNNING
                                || worker.miningStatus().state() == MiningSession.State.PAUSED) {
                            worker.stopMining();
                        }
                        WorkerGameTestSupport.discardWorker(worker);
                    } catch (RuntimeException ignored) {
                        // Unloaded fixture workers are already detached from the live world.
                    }
                }
            }
            for (WorkerEntity worker : workers) {
                if (!worker.isRemoved()) {
                    WorkerGameTestSupport.discardWorker(worker);
                }
            }
            for (ServerPlayer player : players) {
                if (Objects.equals(server.getPlayerList().getPlayer(player.getUUID()), player)) {
                    server.getPlayerList().remove(player);
                } else if (!player.isRemoved()) {
                    player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
            }
            if (previousBatch != null) { batchServices().put(server, previousBatch); }
            else { batchServices().remove(server); }
            if (previousRelocation != null) {
                relocationServices().put(server, previousRelocation);
            } else {
                relocationServices().remove(server);
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<MinecraftServer, Object> batchServices() {
            try {
                return (Map<MinecraftServer, Object>) field(Class.forName("automatone.worker.WorkerBatch"), "SERVICES").get(null);
            } catch (ReflectiveOperationException failure) {
                throw new LinkageError("The internal batch service seam changed", failure);
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<MinecraftServer, WorkerRelocation> relocationServices() {
            try {
                return (Map<MinecraftServer, WorkerRelocation>) field(WorkerRelocation.class, "SERVICES").get(null);
            } catch (ReflectiveOperationException failure) {
                throw new LinkageError("The internal relocation service seam changed", failure);
            }
        }
    }

    private static final class BlockFixture implements AutoCloseable {
        private final Map<WorldPos, BlockState> original = new LinkedHashMap<>();

        private void set(ServerLevel level, BlockPos position, BlockState state) {
            original.putIfAbsent(new WorldPos(level, position), level.getBlockState(position));
            level.setBlock(position, state, 3);
        }

        @Override
        public void close() {
            for (Map.Entry<WorldPos, BlockState> entry : original.entrySet()) {
                entry.getKey().level().setBlock(entry.getKey().position(), entry.getValue(), 3);
            }
        }
    }

    private record WorldPos(ServerLevel level, BlockPos position) {
    }
}
