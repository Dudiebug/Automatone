package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerControllerItem;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMenu;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerNetwork;
import automatone.worker.WorkerRelocation;
import automatone.worker.WorkerRoster;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Dedicated-server contract tests for the M5.5 menu and networking boundary. */
@GameTestHolder("automatone_worker_m5_menu_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerMenuGameTest {
    private static final ResourceLocation IRON_ORE = ResourceLocation.withDefaultNamespace("iron_ore");

    private WorkerMenuGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_menu_scope", timeoutTicks = 120)
    public static void controllerOpensScopedMenusAndCrossDimensionAccess(GameTestHelper helper) {
        withPreparedNether(helper, () -> scopedMenusAndCrossDimensionAccess(helper));
    }

    private static void scopedMenusAndCrossDimensionAccess(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerEntity overworld = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "The dedicated server must provide the Nether");
            WorkerEntity crossWorld = fixture.spawnOwned(owner, nether, new BlockPos(0, 80, 0));

            WorkerControllerItem controller = WorkerMod.CONTROLLER.get();
            controller.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
            WorkerMenu rosterMenu = requireMenu(owner);
            helper.assertTrue(rosterMenu.worker() == null && rosterMenu.session() != null,
                    "Using the real controller must open the owner's roster session");
            int rosterId = rosterMenu.containerId;
            UUID rosterSession = rosterMenu.session();

            WorkerMenu.open(owner, crossWorld.getUUID(), false, 0);
            WorkerMenu selected = requireMenu(owner);
            CompoundTag selectedData = selected.snapshot().getCompound("Selected");
            helper.assertTrue(selected.containerId != rosterId && !selected.session().equals(rosterSession)
                            && selected.worker().equals(crossWorld.getUUID())
                            && selectedData.getString("Dimension").equals(Level.NETHER.location().toString()),
                    "Selecting an owned Nether worker must open a new scoped menu across dimensions");
            helper.assertTrue(selectedData.getUUID("Worker").equals(crossWorld.getUUID())
                            && selectedData.getBoolean("Retired") == false,
                    "The cross-dimension snapshot must retain the selected worker identity and active state");

            ServerPlayer stranger = fixture.player();
            expectFailure(helper, () -> WorkerMenu.open(stranger, crossWorld.getUUID(), false, 0), "NOT_OWNER",
                    "A second real player must not open another owner's worker");
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            expectFailure(helper, () -> WorkerMenu.open(owner, overworld.getUUID(), false, 0), "CONTROLLER_REQUIRED",
                    "Opening a worker without the controller must be rejected");
            owner.getInventory().setItem(0, new ItemStack(WorkerMod.CONTROLLER.get()));
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_menu_relocation", timeoutTicks = 160)
    public static void relocatedWorkerRebindsMenuInventoryAcrossDimensions(GameTestHelper helper) {
        withPreparedNether(helper, () -> relocatedMenuInventory(helper));
    }

    private static void relocatedMenuInventory(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "The dedicated server must provide the Nether");
            WorkerEntity source = fixture.spawnOwned(owner, helper.getLevel(),
                    helper.absolutePos(new BlockPos(0, 1, 0)));
            source.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
            source.startMining(IRON_ORE, 8);
            source.pauseMining();
            WorkerRoster.View beforeRelocation = roster.view(owner.getUUID(), source.getUUID());
            helper.assertTrue(source.miningStatus().state() == MiningSession.State.PAUSED,
                    "The transfer fixture must pause the worker before relocating it");

            WorkerMenu.open(owner, source.getUUID(), false, 0);
            WorkerMenu oldMenu = requireMenu(owner);
            oldMenu.showInventory(true);
            WorkerEntity replacement = source.relocateTo(nether, new Vec3(0.5D, 80.0D, 0.5D));
            if (replacement != null) {
                fixture.trackWorker(replacement);
            }
            helper.assertTrue(replacement != null && !Objects.equals(replacement, source)
                            && Objects.equals(replacement.level(), nether),
                    "A paused owned worker must transfer to the destination dimension");
            helper.assertTrue(Objects.equals(roster.active(owner.getUUID(), source.getUUID()), replacement)
                            && roster.view(owner.getUUID(), source.getUUID()).revision() >= beforeRelocation.revision(),
                    "The roster must track the replacement entity under the original worker identity");

            int oldState = oldMenu.getStateId();
            ServerboundContainerClickPacket staleSourceClick = new ServerboundContainerClickPacket(
                    oldMenu.containerId, oldState, 0, 0, ClickType.PICKUP, ItemStack.EMPTY,
                    new Int2ObjectOpenHashMap<>());
            staleSourceClick.handle(owner.connection);
            ItemStack obsoleteWithdraw = oldMenu.quickMoveStack(owner, 0);
            helper.assertTrue(obsoleteWithdraw.isEmpty() && oldMenu.getCarried().isEmpty()
                            && source.getItem(0).is(Items.IRON_INGOT) && source.getItem(0).getCount() == 5
                            && replacement.getItem(0).is(Items.IRON_INGOT)
                            && replacement.getItem(0).getCount() == 5,
                    "A packet or direct quick-move through the obsolete menu must not withdraw its old inventory");

            oldMenu.broadcastChanges();
            WorkerMenu freshMenu = requireMenu(owner);
            freshMenu.showInventory(true);
            helper.assertTrue(freshMenu != oldMenu && freshMenu.containerId != oldMenu.containerId
                            && !freshMenu.session().equals(oldMenu.session())
                            && freshMenu.worker().equals(source.getUUID()),
                    "Broadcasting after transfer must rebind the same worker identity to a new scoped menu");
            ItemStack withdrawn = freshMenu.quickMoveStack(owner, 0);
            helper.assertTrue(withdrawn.is(Items.IRON_INGOT) && withdrawn.getCount() == 5
                            && replacement.getItem(0).isEmpty()
                            && itemCount(replacement, owner, freshMenu, Items.IRON_INGOT) == 5,
                    "The rebound menu must withdraw from the live destination inventory exactly once");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_menu_inventory", timeoutTicks = 160)
    public static void activeAndArchivedSlotsConserveItemsAndRejectArchiveWrites(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer player = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity worker = fixture.spawnOwned(player, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            worker.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
            player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 2));

            WorkerMenu.open(player, worker.getUUID(), false, 0);
            WorkerMenu active = requireMenu(player);
            active.showInventory(true);
            ItemStack moved = active.quickMoveStack(player, 0);
            helper.assertTrue(moved.is(Items.IRON_INGOT) && moved.getCount() == 5
                            && worker.getItem(0).isEmpty() && itemCount(worker, player, active, Items.IRON_INGOT) == 7,
                    "Quick-moving an active worker stack must conserve every item and empty its source slot");

            ItemStack returned = active.quickMoveStack(player, 9);
            helper.assertTrue(returned.is(Items.IRON_INGOT) && returned.getCount() == 7
                            && worker.getItem(0).is(Items.IRON_INGOT) && worker.getItem(0).getCount() == 7,
                    "Quick-moving a merged player stack back must conserve the merged count");

            player.getInventory().setItem(1, new ItemStack(Items.GOLD_INGOT, 3));
            active.clicked(0, 1, ClickType.SWAP, player);
            helper.assertTrue(worker.getItem(0).is(Items.GOLD_INGOT) && worker.getItem(0).getCount() == 3
                            && player.getInventory().getItem(1).is(Items.IRON_INGOT)
                            && player.getInventory().getItem(1).getCount() == 7,
                    "A native active-slot hotbar swap must exchange stacks without duplication");
            ItemStack beforeInvalid = worker.getItem(0).copy();
            active.clicked(0, 99, ClickType.PICKUP, player);
            boolean wasCreative = player.getAbilities().instabuild;
            player.getAbilities().instabuild = true;
            try {
                active.clicked(0, 2, ClickType.CLONE, player);
            } finally {
                player.getAbilities().instabuild = wasCreative;
            }
            helper.assertTrue(ItemStack.matches(worker.getItem(0), beforeInvalid) && active.getCarried().isEmpty(),
                    "Malformed native buttons and creative cloning must not alter an active worker slot");

            worker.setItem(2, new ItemStack(Items.DIRT, 5));
            long revision = roster.view(player.getUUID(), worker.getUUID()).revision();
            roster.retire(player.getUUID(), worker.getUUID(), revision);
            WorkerMenu.open(player, worker.getUUID(), true, 0);
            WorkerMenu archive = requireMenu(player);
            archive.showInventory(true);
            player.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 1));
            ItemStack archiveMoved = archive.quickMoveStack(player, 0);
            helper.assertTrue(archiveMoved.is(Items.GOLD_INGOT) && archiveMoved.getCount() == 3
                            && roster.archivedInventory(player.getUUID(), worker.getUUID()).get(0).isEmpty()
                            && player.getInventory().getItem(9).getCount() == 4,
                    "Archived quick-move must withdraw and merge stacks while conserving the count");

            player.getInventory().setItem(10, new ItemStack(Items.DIRT, 2));
            ItemStack archiveInsert = archive.quickMoveStack(player, 10);
            helper.assertTrue(archiveInsert.isEmpty() && player.getInventory().getItem(10).is(Items.DIRT)
                            && player.getInventory().getItem(10).getCount() == 2
                            && roster.archivedInventory(player.getUUID(), worker.getUUID()).get(2).is(Items.DIRT)
                            && roster.archivedInventory(player.getUUID(), worker.getUUID()).get(2).getCount() == 5,
                    "Quick-moving a matching player stack must never merge it into the retired archive");

            player.getInventory().setItem(2, new ItemStack(Items.DIRT, 2));
            ItemStack hotbarBefore = player.getInventory().getItem(2).copy();
            archive.clicked(1, 2, ClickType.SWAP, player);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(2), hotbarBefore)
                            && roster.archivedInventory(player.getUUID(), worker.getUUID()).get(1).isEmpty(),
                    "A nonempty hotbar SWAP must never insert into a retired archive slot");

            archive.setCarried(new ItemStack(Items.EMERALD));
            archive.clicked(1, 0, ClickType.PICKUP, player);
            helper.assertTrue(archive.getCarried().is(Items.EMERALD)
                            && roster.archivedInventory(player.getUUID(), worker.getUUID()).get(1).isEmpty(),
                    "Ordinary clicks must not insert carried items into an archive slot");
            returnCarried(archive, player, 11);
            wasCreative = player.getAbilities().instabuild;
            player.getAbilities().instabuild = true;
            try {
                archive.clicked(2, 2, ClickType.CLONE, player);
            } finally {
                player.getAbilities().instabuild = wasCreative;
            }
            helper.assertTrue(archive.getCarried().isEmpty()
                            && roster.archivedInventory(player.getUUID(), worker.getUUID()).get(2).getCount() == 5,
                    "Creative CLONE must not copy an archived stack into the carried slot");

            CompoundTag saved = roster.save(new CompoundTag(), helper.getLevel().getServer().registryAccess());
            WorkerRoster loaded = WorkerRoster.load(helper.getLevel().getServer(), saved);
            helper.assertTrue(loaded.archivedInventory(player.getUUID(), worker.getUUID()).get(0).isEmpty()
                            && loaded.archivedInventory(player.getUUID(), worker.getUUID()).get(2).is(Items.DIRT)
                            && loaded.archivedInventory(player.getUUID(), worker.getUUID()).get(2).getCount() == 5,
                    "Archive save/load must retain the remaining native inventory");

            archive.clicked(2, 0, ClickType.PICKUP, player);
            ItemStack withdrawn = archive.getCarried().copy();
            helper.assertTrue(withdrawn.is(Items.DIRT) && withdrawn.getCount() == 5
                            && roster.archivedInventory(player.getUUID(), worker.getUUID()).get(2).isEmpty(),
                    "A normal archive withdrawal must remove exactly the selected saved stack");
            returnCarried(archive, player, 12);
            helper.assertTrue(itemCount(worker, player, archive, Items.IRON_INGOT) == 7
                            && itemCount(worker, player, archive, Items.GOLD_INGOT) == 4
                            && itemCount(worker, player, archive, Items.DIRT) == 9
                            && itemCount(worker, player, archive, Items.EMERALD) == 1,
                    "Active swaps, archive merges and withdrawals must conserve all fixture items");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_menu_validation", timeoutTicks = 160)
    public static void staleClicksAndMalformedIntentsLeaveAuthoritativeStateUnchanged(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity worker = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            worker.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
            WorkerMenu.open(owner, worker.getUUID(), false, 0);
            WorkerMenu menu = requireMenu(owner);
            menu.showInventory(true);
            int staleState = menu.getStateId();
            menu.incrementStateId();
            ServerboundContainerClickPacket stale = new ServerboundContainerClickPacket(menu.containerId, staleState,
                    0, 0, ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>());
            stale.handle(owner.connection);
            helper.assertTrue(worker.getItem(0).is(Items.IRON_INGOT) && worker.getItem(0).getCount() == 3
                            && menu.getCarried().isEmpty(),
                    "The real stale ServerboundContainerClickPacket path must reject before slot mutation");

            int currentState = menu.getStateId();
            ServerboundContainerClickPacket fresh = new ServerboundContainerClickPacket(menu.containerId, currentState,
                    0, 0, ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>());
            fresh.handle(owner.connection);
            helper.assertTrue(worker.getItem(0).isEmpty() && menu.getCarried().is(Items.IRON_INGOT)
                            && menu.getCarried().getCount() == 3,
                    "A current native packet must prove that the controlled worker slot is actually clickable");
            ItemStack restored = menu.getCarried().copy();
            menu.setCarried(ItemStack.EMPTY);
            worker.setItem(0, restored);
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            int controllerLostState = menu.getStateId();
            ServerboundContainerClickPacket controllerLost = new ServerboundContainerClickPacket(
                    menu.containerId, controllerLostState, 0, 0, ClickType.PICKUP, ItemStack.EMPTY,
                    new Int2ObjectOpenHashMap<>());
            controllerLost.handle(owner.connection);
            helper.assertTrue(worker.getItem(0).is(Items.IRON_INGOT) && worker.getItem(0).getCount() == 3
                            && menu.getCarried().isEmpty(),
                    "A native click must be rejected when the controller disappears");
            owner.getInventory().setItem(0, new ItemStack(WorkerMod.CONTROLLER.get()));

            long revision = roster.view(owner.getUUID(), worker.getUUID()).revision();
            MiningSession.Snapshot beforeJob = worker.miningStatus();
            int beforeSelected = worker.selectedSlot();
            send(owner, menu, 1, WorkerNetwork.Action.CONFIGURE_JOB,
                    jobData(revision, "minecraft:not_a_registered_block", 1));
            assertError(helper, menu, "INVALID_BLOCK");
            send(owner, menu, 2, WorkerNetwork.Action.CONFIGURE_JOB,
                    jobData(revision, IRON_ORE.toString(), MiningSession.MAX_REQUESTED_BLOCKS + 1));
            assertError(helper, menu, "INVALID_QUANTITY");
            CompoundTag extra = jobData(revision, IRON_ORE.toString(), 1);
            extra.putString("Unexpected", "field");
            send(owner, menu, 3, WorkerNetwork.Action.CONFIGURE_JOB, extra);
            assertError(helper, menu, "INVALID_REQUEST");
            CompoundTag staleRevision = new CompoundTag();
            staleRevision.putLong("Revision", revision - 1);
            staleRevision.putInt("Slot", 1);
            send(owner, menu, 4, WorkerNetwork.Action.SELECT_TOOL, staleRevision);
            assertError(helper, menu, "STALE_REVISION");
            CompoundTag validSelection = new CompoundTag();
            validSelection.putLong("Revision", revision);
            validSelection.putInt("Slot", 2);
            send(owner, menu, 4, WorkerNetwork.Action.SELECT_TOOL, validSelection);
            assertError(helper, menu, "INVALID_SEQUENCE");
            send(owner, menu, 5, WorkerNetwork.Action.REFRESH, new CompoundTag(), menu.containerId + 1, menu.session());
            send(owner, menu, 5, WorkerNetwork.Action.REFRESH, new CompoundTag(), menu.containerId, UUID.randomUUID());
            helper.assertTrue(worker.miningStatus().equals(beforeJob) && worker.selectedSlot() == beforeSelected
                            && roster.view(owner.getUUID(), worker.getUUID()).revision() == revision,
                    "Wrong menu/session, duplicate sequence and malformed actions must leave all worker state unchanged");

            ServerPlayer stranger = fixture.player();
            CompoundTag strangerData = new CompoundTag();
            strangerData.putLong("Revision", revision);
            strangerData.putInt("Slot", 3);
            send(stranger, menu, 5, WorkerNetwork.Action.SELECT_TOOL, strangerData);
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            send(owner, menu, 5, WorkerNetwork.Action.SELECT_TOOL, strangerData);
            owner.getInventory().setItem(0, new ItemStack(WorkerMod.CONTROLLER.get()));
            helper.assertTrue(worker.selectedSlot() == beforeSelected
                            && roster.view(owner.getUUID(), worker.getUUID()).revision() == revision,
                    "Non-owner and controller-less intents must not reach authoritative worker mutation");

            WorkerRoster.ProfileView profileBefore = roster.profile(owner.getUUID());
            CompoundTag staleProfile = revisionData(profileBefore.revision() - 1);
            CompoundTag staleValues = new CompoundTag();
            staleValues.putString("allowBreak", "false");
            staleProfile.put("Values", staleValues);
            send(owner, menu, 5, WorkerNetwork.Action.PERSONAL_SETTINGS, staleProfile);
            assertError(helper, menu, "STALE_REVISION");
            helper.assertTrue(roster.profile(owner.getUUID()).equals(profileBefore),
                    "A stale personal profile revision must leave settings unchanged");

            CompoundTag invalidBoolean = revisionData(profileBefore.revision());
            CompoundTag invalidValues = new CompoundTag();
            invalidValues.putString("allowBreak", "not-a-boolean");
            invalidBoolean.put("Values", invalidValues);
            send(owner, menu, 6, WorkerNetwork.Action.PERSONAL_SETTINGS, invalidBoolean);
            assertError(helper, menu, "INVALID_BOOLEAN");
            helper.assertTrue(roster.profile(owner.getUUID()).equals(profileBefore),
                    "An invalid boolean personal setting must leave the profile unchanged");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_menu_codec", timeoutTicks = 120)
    public static void intentCodecRoundTripsAndEnforcesTrueNbtQuota(GameTestHelper helper) {
        UUID session = UUID.randomUUID();
        CompoundTag data = new CompoundTag();
        data.putString("Value", "round-trip");
        WorkerNetwork.Intent original = new WorkerNetwork.Intent(17, session, 9,
                WorkerNetwork.Action.REFRESH, data);
        ByteBuf raw = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
            WorkerNetwork.Intent.CODEC.encode(buffer, original);
            raw.readerIndex(0);
            WorkerNetwork.Intent decoded = WorkerNetwork.Intent.CODEC.decode(buffer);
            helper.assertTrue(decoded.menuId() == original.menuId() && decoded.session().equals(session)
                            && decoded.sequence() == original.sequence() && decoded.action() == original.action()
                            && decoded.data().getString("Value").equals("round-trip"),
                    "The bounded intent codec must preserve its complete header and compound payload");
            CompoundTag copy = decoded.data();
            copy.putString("Value", "caller mutation");
            helper.assertTrue(decoded.data().getString("Value").equals("round-trip"),
                    "Decoded intent data must not expose mutable codec storage");
        } finally {
            raw.release();
        }

        ByteBuf malformed = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(malformed, helper.getLevel().registryAccess());
            writeHeader(buffer, session, 1);
            buffer.writeNbt(StringTag.valueOf("not a compound"));
            malformed.readerIndex(0);
            expectFailure(helper, () -> WorkerNetwork.Intent.CODEC.decode(buffer), "MISSING_DATA",
                    "A non-compound intent root must be rejected by the decoder");
        } finally {
            malformed.release();
        }

        ByteBuf oversized = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(oversized, helper.getLevel().registryAccess());
            writeHeader(buffer, session, 2);
            CompoundTag body = new CompoundTag();
            body.put("Bytes", new ByteArrayTag(new byte[(int) WorkerNetwork.MAX_INTENT_NBT + 1]));
            buffer.writeNbt(body);
            oversized.readerIndex(0);
            expectFailure(helper, () -> WorkerNetwork.Intent.CODEC.decode(buffer), "too big",
                    "A payload beyond the declared NBT quota must fail during decoding");
        } finally {
            oversized.release();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_menu_pending", timeoutTicks = 140)
    public static void pendingRelocationBlocksJobAndInventoryMutations(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            WorkerEntity worker = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            worker.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
            worker.startMining(IRON_ORE, 8);
            MiningSession.Snapshot before = worker.miningStatus();
            UUID request = UUID.randomUUID();
            WorkerRelocation relocation = WorkerRelocation.get(helper.getLevel().getServer());
            long revision = roster.view(owner.getUUID(), worker.getUUID()).revision();
            WorkerRelocation.Status pending = relocation.relocate(owner.getUUID(), request, worker.getUUID(), revision,
                    Level.OVERWORLD);
            fixture.trackPending(owner.getUUID(), request);
            helper.assertTrue(pending.state() == WorkerRelocation.State.PENDING
                            && worker.miningStatus().state() == MiningSession.State.PAUSED,
                    "A real relocation request must reserve the worker and pause it immediately");

            WorkerMenu.open(owner, worker.getUUID(), false, 0);
            WorkerMenu menu = requireMenu(owner);
            menu.showInventory(true);
            CompoundTag selected = menu.snapshot().getCompound("Selected");
            helper.assertTrue(selected.getBoolean("Pending") && menu.snapshot().getBoolean("Pending"),
                    "A selected worker's menu must expose its pending relocation state");
            long pendingRevision = roster.view(owner.getUUID(), worker.getUUID()).revision();
            send(owner, menu, 1, WorkerNetwork.Action.RESUME, revisionData(pendingRevision));
            assertError(helper, menu, "WORKER_PENDING");
            ItemStack blocked = menu.quickMoveStack(owner, 0);
            helper.assertTrue(blocked.isEmpty() && worker.getItem(0).is(Items.IRON_INGOT)
                            && worker.getItem(0).getCount() == 4
                            && worker.miningStatus().equals(new MiningSession.Snapshot(before.targets(), before.requested(),
                            before.completed(), MiningSession.State.PAUSED, before.error(), before.runId())),
                    "Pending relocation must block job and native inventory mutations while preserving the paused job");

            WorkerRelocation.Status cancelled = relocation.cancel(owner.getUUID(), request);
            helper.assertTrue(cancelled.state() == WorkerRelocation.State.CANCELLED
                            && !relocation.pending(worker.getUUID()),
                    "Cancelling the pending request must release the worker reservation");
            ItemStack withdrawn = menu.quickMoveStack(owner, 0);
            helper.assertTrue(owner.containerMenu == menu && withdrawn.is(Items.IRON_INGOT)
                            && withdrawn.getCount() == 4 && worker.getItem(0).isEmpty(),
                    "The same menu must withdraw the worker stack after cancellation");
            long resumedRevision = roster.view(owner.getUUID(), worker.getUUID()).revision();
            send(owner, menu, 2, WorkerNetwork.Action.RESUME, revisionData(resumedRevision));
            helper.assertTrue(response(menu).getString("Kind").equals("Success")
                            && worker.miningStatus().state() == MiningSession.State.RUNNING,
                    "The same menu must resume the paused job after cancellation");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_menu_batch", timeoutTicks = 180)
    public static void busyBatchPreviewPreflightsAllRecipientsAndPreservesOverrides(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer owner = fixture.player();
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            roster.applyPersonal(owner.getUUID(), 0, Map.of("allowBreak", "true"));
            WorkerEntity first = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity second = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 0)));
            long secondRevision = roster.view(owner.getUUID(), second.getUUID()).revision();
            roster.applyOverrides(owner.getUUID(), second.getUUID(), secondRevision, Map.of("allowBreak", "false"));
            second.startMining(IRON_ORE, 8);
            second.pauseMining();
            helper.assertTrue(second.miningStatus().state() == MiningSession.State.PAUSED,
                    "Batch setup must have a genuinely busy paused recipient");

            WorkerMenu.open(owner, null, false, 0);
            WorkerMenu menu = requireMenu(owner);
            long firstRevision = roster.view(owner.getUUID(), first.getUUID()).revision();
            secondRevision = roster.view(owner.getUUID(), second.getUUID()).revision();
            CompoundTag previewRequest = batchData(first, firstRevision, second, secondRevision, false, 3);
            send(owner, menu, 1, WorkerNetwork.Action.PREVIEW_APPLY_JOB, previewRequest);
            CompoundTag preview = response(menu);
            UUID confirmation = preview.getUUID("Confirmation");
            helper.assertTrue(preview.getString("Kind").equals("Preview")
                            && preview.getList("Recipients", 10).size() == 2
                            && preview.getList("Recipients", 10).getCompound(1).getBoolean("Busy"),
                    "Batch preview must capture all recipients and identify the busy replacement");

            MiningSession.Snapshot firstBefore = first.miningStatus();
            MiningSession.Snapshot secondBefore = second.miningStatus();
            expectNoMutationApply(owner, menu, 2, first, second,
                    firstBefore, secondBefore, roster);

            long renamedRevision = roster.view(owner.getUUID(), second.getUUID()).revision();
            roster.rename(owner.getUUID(), second.getUUID(), renamedRevision, "Changed after preview");
            send(owner, menu, 3, WorkerNetwork.Action.APPLY_JOB, confirmationData(confirmation));
            assertError(helper, menu, "STALE_REVISION");
            helper.assertTrue(first.miningStatus().equals(firstBefore) && second.miningStatus().equals(secondBefore)
                            && roster.overrides(owner.getUUID(), second.getUUID()).get("allowbreak").equals("false"),
                    "A changed later recipient must abort before the first recipient is mutated");

            firstRevision = roster.view(owner.getUUID(), first.getUUID()).revision();
            secondRevision = roster.view(owner.getUUID(), second.getUUID()).revision();
            send(owner, menu, 4, WorkerNetwork.Action.PREVIEW_APPLY_JOB,
                    batchData(first, firstRevision, second, secondRevision, false, 3));
            UUID applyOnlyToken = response(menu).getUUID("Confirmation");
            send(owner, menu, 5, WorkerNetwork.Action.APPLY_JOB, confirmationData(applyOnlyToken));
            CompoundTag applyOnly = response(menu);
            helper.assertTrue(applyOnly.getString("Kind").equals("BatchResult")
                            && noBatchErrors(applyOnly)
                            && first.miningStatus().state() == MiningSession.State.IDLE
                            && second.miningStatus().state() == MiningSession.State.IDLE
                            && first.miningStatus().targets().equals(List.of(IRON_ORE.toString()))
                            && second.miningStatus().requested() == 3
                            && second.effectiveSettings().allowBreak.value
                            == false
                            && roster.overrides(owner.getUUID(), second.getUUID()).get("allowbreak").equals("false"),
                    "Apply-only must configure every recipient while preserving worker overrides");

            firstRevision = roster.view(owner.getUUID(), first.getUUID()).revision();
            secondRevision = roster.view(owner.getUUID(), second.getUUID()).revision();
            send(owner, menu, 6, WorkerNetwork.Action.PREVIEW_APPLY_JOB,
                    batchData(first, firstRevision, second, secondRevision, true, 2));
            UUID applyAndStartToken = response(menu).getUUID("Confirmation");
            send(owner, menu, 7, WorkerNetwork.Action.APPLY_JOB, confirmationData(applyAndStartToken));
            CompoundTag started = response(menu);
            helper.assertTrue(started.getString("Kind").equals("BatchResult") && noBatchErrors(started)
                            && first.miningStatus().state() == MiningSession.State.RUNNING
                            && second.miningStatus().state() == MiningSession.State.RUNNING
                            && first.miningStatus().runId() != null
                            && second.miningStatus().runId() != null
                            && second.effectiveSettings().allowBreak.value == false,
                    "Apply-and-start must replace both busy jobs once and retain their isolated overrides");

            MiningSession.Snapshot firstStarted = first.miningStatus();
            MiningSession.Snapshot secondStarted = second.miningStatus();
            send(owner, menu, 7, WorkerNetwork.Action.APPLY_JOB, confirmationData(applyAndStartToken));
            assertError(helper, menu, "INVALID_SEQUENCE");
            helper.assertTrue(first.miningStatus().equals(firstStarted)
                            && second.miningStatus().equals(secondStarted),
                    "A duplicate batch sequence must preserve both run identities and quantities");
            send(owner, menu, 8, WorkerNetwork.Action.APPLY_JOB, confirmationData(applyAndStartToken));
            assertError(helper, menu, "INVALID_CONFIRMATION");
            helper.assertTrue(first.miningStatus().equals(firstStarted)
                            && second.miningStatus().equals(secondStarted),
                    "A valid batch token replay must preserve both run identities and quantities");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    private static WorkerMenu requireMenu(ServerPlayer player) {
        if (!(player.containerMenu instanceof WorkerMenu menu)) {
            throw new AssertionError("Expected the real player container to be a WorkerMenu");
        }
        return menu;
    }

    private static void send(ServerPlayer holder, WorkerMenu menu, long sequence,
                              WorkerNetwork.Action action, CompoundTag data) {
        send(holder, menu, sequence, action, data, menu.containerId, menu.session());
    }

    private static void send(ServerPlayer holder, WorkerMenu menu, long sequence,
                             WorkerNetwork.Action action, CompoundTag data, int menuId, UUID session) {
        menu.handle(holder, new WorkerNetwork.Intent(menuId, session, sequence, action, data));
    }

    private static CompoundTag response(WorkerMenu menu) {
        return menu.snapshot().getCompound("Response");
    }

    private static void assertError(GameTestHelper helper, WorkerMenu menu, String code) {
        CompoundTag response = response(menu);
        helper.assertTrue(response.getString("Kind").equals("Error") && response.getString("Error").equals(code),
                "Expected menu response " + code + " but received " + response);
    }

    private static CompoundTag revisionData(long revision) {
        CompoundTag data = new CompoundTag();
        data.putLong("Revision", revision);
        return data;
    }

    private static CompoundTag jobData(long revision, String target, int quantity) {
        CompoundTag data = revisionData(revision);
        net.minecraft.nbt.ListTag targets = new net.minecraft.nbt.ListTag();
        targets.add(StringTag.valueOf(target));
        data.put("Targets", targets);
        data.putInt("Quantity", quantity);
        return data;
    }

    private static CompoundTag batchData(WorkerEntity first, long firstRevision, WorkerEntity second,
                                         long secondRevision, boolean start, int quantity) {
        CompoundTag data = new CompoundTag();
        net.minecraft.nbt.ListTag recipients = new net.minecraft.nbt.ListTag();
        CompoundTag firstRef = new CompoundTag();
        firstRef.putUUID("Worker", first.getUUID());
        firstRef.putLong("Revision", firstRevision);
        recipients.add(firstRef);
        CompoundTag secondRef = new CompoundTag();
        secondRef.putUUID("Worker", second.getUUID());
        secondRef.putLong("Revision", secondRevision);
        recipients.add(secondRef);
        data.put("Recipients", recipients);
        net.minecraft.nbt.ListTag targets = new net.minecraft.nbt.ListTag();
        targets.add(StringTag.valueOf(IRON_ORE.toString()));
        data.put("Targets", targets);
        data.putInt("Quantity", quantity);
        data.putBoolean("Start", start);
        return data;
    }

    private static CompoundTag confirmationData(UUID token) {
        CompoundTag data = new CompoundTag();
        data.putUUID("Confirmation", token);
        return data;
    }

    private static void expectNoMutationApply(ServerPlayer owner, WorkerMenu menu, long sequence,
                                               WorkerEntity first,
                                               WorkerEntity second, MiningSession.Snapshot firstBefore,
                                               MiningSession.Snapshot secondBefore, WorkerRoster roster) {
        send(owner, menu, sequence, WorkerNetwork.Action.APPLY_JOB, confirmationData(UUID.randomUUID()));
        if (!response(menu).getString("Error").equals("INVALID_CONFIRMATION")) {
            throw new AssertionError("Invalid batch token must be rejected");
        }
        if (!first.miningStatus().equals(firstBefore) || !second.miningStatus().equals(secondBefore)
                || roster.overrides(owner.getUUID(), second.getUUID()).get("allowbreak") == null) {
            throw new AssertionError("Invalid batch token changed authoritative state");
        }
    }

    private static boolean noBatchErrors(CompoundTag result) {
        for (net.minecraft.nbt.Tag value : result.getList("Recipients", 10)) {
            if (!((CompoundTag) value).getString("Error").isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void writeHeader(RegistryFriendlyByteBuf buffer, UUID session, long sequence) {
        buffer.writeVarInt(17);
        buffer.writeUUID(session);
        buffer.writeLong(sequence);
        buffer.writeEnum(WorkerNetwork.Action.REFRESH);
    }

    private static void returnCarried(WorkerMenu menu, ServerPlayer player, int slot) {
        ItemStack carried = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(slot, carried);
    }

    private static int itemCount(WorkerEntity worker, ServerPlayer player, WorkerMenu menu,
                                 net.minecraft.world.item.Item item) {
        int count = menu.getCarried().is(item) ? menu.getCarried().getCount() : 0;
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            if (worker.getItem(slot).is(item)) {
                count += worker.getItem(slot).getCount();
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void expectFailure(GameTestHelper helper, Runnable action, String code, String message) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            helper.assertTrue(failure.getMessage() != null && failure.getMessage().contains(code),
                    message + "; expected " + code + " but was " + failure);
            return;
        }
        helper.fail(message + "; action unexpectedly succeeded");
    }

    /** Match the production relocation service's asynchronous destination preparation. */
    private static void withPreparedNether(GameTestHelper helper, Runnable test) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The dedicated server must provide the Nether");
        ChunkPos chunk = new ChunkPos(0, 0);
        UUID ticket = UUID.randomUUID();
        int deadline = nether.getServer().getTickCount() + 100;
        boolean[] finished = {false};
        nether.getChunkSource().addRegionTicket(WorkerRelocation.PREPARATION, chunk, 1, ticket);
        // Generation is fixture setup; allow real tick-thread visibility to settle below.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) { nether.getChunk(x, z); }
        }
        helper.onEachTick(() -> {
            if (finished[0]) { return; }
            boolean ready = WorkerRelocation.prepared(nether, chunk);
            if (!ready && nether.getServer().getTickCount() < deadline) { return; }
            finished[0] = true;
            try {
                helper.assertTrue(ready, "The fixture destination must finish preparation before menu checks");
                test.run();
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
            // The GameTest mock is a real server connection without a negotiated client;
            // let it act as the permitted outbound transport sink for menu payloads.
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
            workers.add(worker);
        }

        private void trackPending(UUID owner, UUID request) {
            pending.add(new Pending(owner, request));
        }

        private void close() {
            WorkerRelocation relocation = WorkerRelocation.get(server);
            for (ServerPlayer player : players) {
                player.closeContainer();
            }
            for (Pending request : pending) {
                relocation.cancel(request.owner(), request.request());
            }
            for (WorkerEntity worker : workers) {
                if (!worker.isRemoved() && worker.isAlive()
                        && worker.miningStatus().state() == MiningSession.State.RUNNING) {
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

        private record Pending(UUID owner, UUID request) {
        }
    }
}
