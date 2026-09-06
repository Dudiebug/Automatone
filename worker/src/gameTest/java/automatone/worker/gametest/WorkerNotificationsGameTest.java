package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMenu;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerNetwork;
import automatone.worker.WorkerNotifications;
import automatone.worker.WorkerRoster;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Dedicated-server contract tests for M5.7 completion history and delivery. */
@GameTestHolder("automatone_worker_m5_notifications_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerNotificationsGameTest {
    private static final ResourceLocation IRON_ORE = ResourceLocation.withDefaultNamespace("iron_ore");

    private WorkerNotificationsGameTest() {
    }

    @GameTest(template = "worker_native_mining", batch = "worker_m5_notifications_completion", timeoutTicks = 600)
    public static void realFiniteCompletionIsRecordedAndDeliveredOnce(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        WorkerNativeMineProcessGameTest.MiningChamber chamber =
                new WorkerNativeMineProcessGameTest.MiningChamber(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        chamber.build();
        try {
            ServerPlayer owner = fixture.player();
            ServerPlayer otherOwner = fixture.player();
            WorkerEntity worker = WorkerNativeMineProcessGameTest.spawnWorker(
                    helper.getLevel(), chamber.workerPosition());
            fixture.track(worker);
            worker.claim(owner.getUUID());
            worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            drainNotices(owner);
            drainNotices(otherOwner);

            helper.runAfterDelay(2, () -> {
                try {
                    worker.startMining(IRON_ORE, 1);
                    worker.pauseMining();
                    helper.assertTrue(worker.miningStatus().state() == MiningSession.State.PAUSED,
                            "The real native job must pause before its completion replan");
                    worker.applySettings(worker.effectiveSettings());
                    helper.assertTrue(worker.miningStatus().state() == MiningSession.State.PAUSED,
                            "Applying settings while paused must preserve the paused job");
                    worker.resumeMining();
                    observeCompletion(helper, fixture, chamber, owner, otherOwner, worker, 0);
                } catch (Throwable failure) {
                    finish(helper, fixture, chamber, failure);
                }
            });
        } catch (Throwable failure) {
            finish(helper, fixture, chamber, failure);
        }
    }

    private static void observeCompletion(GameTestHelper helper, Fixture fixture,
                                          WorkerNativeMineProcessGameTest.MiningChamber chamber,
                                          ServerPlayer owner, ServerPlayer otherOwner, WorkerEntity worker,
                                          int ticks) {
        try {
            WorkerMiningSessionGameTest.yieldNativeWork();
            MiningSession.Snapshot state = worker.miningStatus();
            helper.assertTrue(ticks < 540 && state.state() != MiningSession.State.FAILED,
                    "The real finite notification job did not complete: " + state);
            if (state.state() != MiningSession.State.COMPLETED) {
                helper.runAfterDelay(1, () -> observeCompletion(helper, fixture, chamber, owner, otherOwner,
                        worker, ticks + 1));
                return;
            }

            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            List<WorkerRoster.Completion> history = roster.notifications(owner.getUUID());
            helper.assertTrue(helper.getLevel().getBlockState(chamber.target()).isAir()
                            && state.completed() == 1 && state.requested() == 1
                            && history.size() == 1,
                    "A real finite destruction must create exactly one completed inbox entry");
            WorkerRoster.Completion completion = history.getFirst();
            helper.assertTrue(completion.run().equals(state.runId())
                            && completion.worker().equals(worker.getUUID())
                            && completion.targets().equals(List.of(IRON_ORE.toString()))
                            && completion.amount() == 1 && completion.time() > 0 && !completion.read(),
                    "The completion record must retain the run, worker, target, amount and unread state");
            List<WorkerNetwork.Notice> delivered = drainNotices(owner);
            helper.assertTrue(delivered.size() == 1 && !delivered.getFirst().summary()
                            && delivered.getFirst().eventId().equals(state.runId())
                            && delivered.getFirst().unread() == 1
                            && delivered.getFirst().workerName().equals(worker.getName().getString())
                            && delivered.getFirst().amount() == 1
                            && delivered.getFirst().toast() && delivered.getFirst().sound(),
                    "The owner must receive one authoritative completion notice with default presentation flags");
            helper.assertTrue(drainNotices(otherOwner).isEmpty()
                            && roster.notifications(otherOwner.getUUID()).isEmpty(),
                    "Another owner must receive neither the completion history nor its delivery");

            WorkerNotifications.completed(worker);
            worker.applySettings(worker.effectiveSettings());
            helper.assertTrue(roster.notifications(owner.getUUID()).size() == 1 && drainNotices(owner).isEmpty(),
                    "Repeated completion hooks, settings replan and snapshots must not replay a completed run");
            WorkerMenu.open(owner, worker.getUUID(), false, 0);
            WorkerMenu menu = requireMenu(owner);
            menu.snapshot();
            helper.assertTrue(roster.notifications(owner.getUUID()).size() == 1 && drainNotices(owner).isEmpty(),
                    "Opening and snapshotting the inbox must not create a duplicate completion");

            CompoundTag saved = roster.save(new CompoundTag(), helper.getLevel().getServer().registryAccess());
            WorkerRoster loaded = WorkerRoster.load(helper.getLevel().getServer(), saved);
            helper.assertTrue(loaded.notifications(owner.getUUID()).size() == 1
                            && loaded.notifications(owner.getUUID()).getFirst().run().equals(state.runId())
                            && loaded.recordCompletion(worker).isEmpty(),
                    "A saved roster must retain the completion marker and reject replay after reload");
            finish(helper, fixture, chamber, null);
        } catch (Throwable failure) {
            finish(helper, fixture, chamber, failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_notifications_persistence", timeoutTicks = 160)
    public static void historyCapReadStatePreferencesAndLegacyDefaultsRoundTrip(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            UUID owner = UUID.randomUUID();
            WorkerEntity worker = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
            worker.setCustomName(net.minecraft.network.chat.Component.literal("History worker"));
            List<UUID> runs = new ArrayList<>();
            for (int index = 1; index <= WorkerRoster.NOTIFICATION_LIMIT + 1; index++) {
                UUID run = UUID.randomUUID();
                runs.add(run);
                restoreJob(worker, run, index, MiningSession.State.COMPLETED, "");
                WorkerNotifications.completed(worker);
            }
            List<WorkerRoster.Completion> history = roster.notifications(owner);
            helper.assertTrue(history.size() == WorkerRoster.NOTIFICATION_LIMIT
                            && history.getFirst().run().equals(runs.get(1))
                            && history.getLast().run().equals(runs.get(100)),
                    "Completion history must retain exactly the latest 100 runs in oldest-first order");
            for (int index = 0; index < history.size(); index++) {
                WorkerRoster.Completion completion = history.get(index);
                helper.assertTrue(completion.worker().equals(worker.getUUID())
                                && completion.name().equals("History worker")
                                && completion.targets().equals(List.of(IRON_ORE.toString()))
                                && completion.amount() == index + 2 && completion.time() > 0 && !completion.read(),
                        "Each capped history entry must retain its exact source completion record");
            }
            roster.markRead(owner, runs.get(1));
            roster.applyNotificationPreferences(owner, 0, false, true);
            expectFailure(helper, () -> roster.applyNotificationPreferences(owner, 0, true, false), "STALE_REVISION",
                    "A stale notification preference revision must leave preferences unchanged");
            CompoundTag saved = roster.save(new CompoundTag(), helper.getLevel().getServer().registryAccess());
            WorkerRoster loaded = WorkerRoster.load(helper.getLevel().getServer(), saved);
            WorkerRoster.Completion first = loaded.notifications(owner).getFirst();
            WorkerRoster.NotificationPreferences preferences = loaded.notificationPreferences(owner);
            helper.assertTrue(loaded.notifications(owner).equals(roster.notifications(owner))
                            && loaded.notifications(owner).size() == WorkerRoster.NOTIFICATION_LIMIT
                            && first.run().equals(runs.get(1)) && first.read()
                            && !loaded.notifications(owner).getLast().read() && loaded.unread(owner) == 99
                            && preferences.revision() == 1 && !preferences.toasts() && preferences.sounds()
                            && loaded.recordCompletion(worker).isEmpty(),
                    "History read state, preferences and the last-notified marker must survive roster save/load");

            CompoundTag legacy = new CompoundTag();
            legacy.putInt("Version", 1);
            ListTag profiles = new ListTag();
            CompoundTag legacyProfile = new CompoundTag();
            UUID legacyOwner = UUID.randomUUID();
            legacyProfile.putUUID("Owner", legacyOwner);
            legacyProfile.putLong("Revision", 0);
            legacyProfile.put("Settings", new CompoundTag());
            profiles.add(legacyProfile);
            legacy.put("Profiles", profiles);
            legacy.put("Workers", new ListTag());
            WorkerRoster legacyRoster = WorkerRoster.load(helper.getLevel().getServer(), legacy);
            WorkerRoster.NotificationPreferences defaults = legacyRoster.notificationPreferences(legacyOwner);
            helper.assertTrue(legacyRoster.notifications(legacyOwner).isEmpty()
                            && defaults.revision() == 0 && defaults.toasts() && defaults.sounds(),
                    "Version-one rosters without notification fields must use empty history and enabled defaults");
        } catch (Throwable failure) {
            helper.fail("Notification persistence test failed: " + failure);
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_notifications_delivery", timeoutTicks = 220)
    public static void offlineSummaryMenuPagingAndPresentationFlagsAreOwnerScoped(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            ServerPlayer owner = fixture.player();
            ServerPlayer offlineOwner = fixture.player();
            WorkerEntity onlineWorker = fixture.spawnOwned(owner.getUUID(), helper.getLevel(),
                    helper.absolutePos(new BlockPos(0, 1, 0)));
            WorkerEntity offlineWorker = fixture.spawnOwned(offlineOwner.getUUID(), helper.getLevel(),
                    helper.absolutePos(new BlockPos(3, 1, 0)));
            List<UUID> onlineRuns = new ArrayList<>();
            drainNotices(owner);
            for (int index = 1; index <= 6; index++) {
                UUID run = UUID.randomUUID();
                onlineRuns.add(run);
                restoreJob(onlineWorker, run, index, MiningSession.State.COMPLETED, "");
                WorkerNotifications.completed(onlineWorker);
            }
            helper.assertTrue(roster.notifications(owner.getUUID()).size() == 6 && drainNotices(owner).size() == 6,
                    "Online completion history must be durable even when six notices are delivered");

            helper.getLevel().getServer().getPlayerList().remove(offlineOwner);
            UUID offlineRun = UUID.randomUUID();
            restoreJob(offlineWorker, offlineRun, 2, MiningSession.State.COMPLETED, "");
            WorkerNotifications.completed(offlineWorker);
            helper.assertTrue(roster.notifications(offlineOwner.getUUID()).size() == 1
                            && drainNotices(offlineOwner).isEmpty(),
                    "An offline completion must persist without attempting individual online delivery");
            WorkerNotifications.login(new PlayerEvent.PlayerLoggedInEvent(offlineOwner));
            List<WorkerNetwork.Notice> firstLogin = drainNotices(offlineOwner);
            helper.assertTrue(firstLogin.size() == 1 && firstLogin.getFirst().summary()
                            && firstLogin.getFirst().unread() == 1 && firstLogin.getFirst().workerName().isEmpty()
                            && firstLogin.getFirst().amount() == 0 && firstLogin.getFirst().toast()
                            && firstLogin.getFirst().sound(),
                    "The first offline-owner login must receive one unread summary with no replayed completion toast");
            WorkerNotifications.login(new PlayerEvent.PlayerLoggedInEvent(offlineOwner));
            helper.assertTrue(drainNotices(offlineOwner).isEmpty(),
                    "A duplicate login event for one connection must not duplicate its summary");
            WorkerNotifications.logout(new PlayerEvent.PlayerLoggedOutEvent(offlineOwner));
            WorkerNotifications.login(new PlayerEvent.PlayerLoggedInEvent(offlineOwner));
            List<WorkerNetwork.Notice> reconnect = drainNotices(offlineOwner);
            helper.assertTrue(reconnect.size() == 1 && reconnect.getFirst().summary()
                            && reconnect.getFirst().unread() == 1,
                    "A logout followed by reconnect may send one fresh unread summary");

            WorkerMenu.open(owner, onlineWorker.getUUID(), false, 0);
            WorkerMenu menu = requireMenu(owner);
            CompoundTag pageZero = menu.snapshot();
            ListTag newest = pageZero.getList("Notifications", Tag.TAG_COMPOUND);
            helper.assertTrue(newest.size() == 5 && newest.getCompound(0).getUUID("Run").equals(onlineRuns.get(5))
                            && newest.stream().noneMatch(value -> ((CompoundTag) value).getUUID("Worker").equals(offlineWorker.getUUID())),
                    "The owner inbox must show newest-first pages of at most five rows without another owner's entry");
            send(owner, menu, 1, WorkerNetwork.Action.NOTIFICATION_PAGE, intData("Page", 1));
            ListTag oldest = menu.snapshot().getList("Notifications", Tag.TAG_COMPOUND);
            helper.assertTrue(oldest.size() == 1 && oldest.getCompound(0).getUUID("Run").equals(onlineRuns.getFirst()),
                    "The second inbox page must expose only the remaining oldest entry");
            send(owner, menu, 2, WorkerNetwork.Action.READ_NOTIFICATION, uuidData("Run", onlineRuns.getFirst()));
            helper.assertTrue(roster.unread(owner.getUUID()) == 5,
                    "A real menu read intent must mark only its owner's selected completion");
            send(owner, menu, 3, WorkerNetwork.Action.READ_ALL_NOTIFICATIONS, new CompoundTag());
            helper.assertTrue(roster.unread(owner.getUUID()) == 0 && roster.unread(offlineOwner.getUUID()) == 1,
                    "Read-all must remain owner-scoped and preserve another owner's unread entry");
            WorkerRoster.NotificationPreferences before = roster.notificationPreferences(owner.getUUID());
            send(owner, menu, 4, WorkerNetwork.Action.NOTIFICATION_PREFERENCES,
                    preferenceData(before.revision(), false, true));
            WorkerRoster.NotificationPreferences quiet = roster.notificationPreferences(owner.getUUID());
            helper.assertTrue(quiet.revision() == before.revision() + 1 && !quiet.toasts() && quiet.sounds(),
                    "A real menu preference intent must persist independent toast and sound toggles");
            send(owner, menu, 5, WorkerNetwork.Action.READ_NOTIFICATION, uuidData("Run", offlineRun));
            assertMenuError(helper, menu, "NOTIFICATION_NOT_FOUND");
            helper.assertTrue(roster.unread(owner.getUUID()) == 0 && roster.unread(offlineOwner.getUUID()) == 1,
                    "A foreign-owner read intent must be rejected without changing either owner's unread count");

            UUID quietRun = UUID.randomUUID();
            restoreJob(onlineWorker, quietRun, 7, MiningSession.State.COMPLETED, "");
            WorkerNotifications.completed(onlineWorker);
            List<WorkerNetwork.Notice> quietNotice = drainNotices(owner);
            helper.assertTrue(quietNotice.size() == 1 && !quietNotice.getFirst().toast() && quietNotice.getFirst().sound(),
                    "Disabling toasts must keep the completion notice and sound flag independent");
            WorkerRoster.NotificationPreferences loud = roster.notificationPreferences(owner.getUUID());
            roster.applyNotificationPreferences(owner.getUUID(), loud.revision(), true, false);
            UUID silentRun = UUID.randomUUID();
            restoreJob(onlineWorker, silentRun, 8, MiningSession.State.COMPLETED, "");
            WorkerNotifications.completed(onlineWorker);
            List<WorkerNetwork.Notice> silentNotice = drainNotices(owner);
            helper.assertTrue(silentNotice.size() == 1 && silentNotice.getFirst().toast() && !silentNotice.getFirst().sound()
                            && roster.notifications(owner.getUUID()).size() == 8,
                    "Disabling sounds must keep inbox history and toast presentation independent");
            WorkerRoster.NotificationPreferences bothOff = roster.notificationPreferences(owner.getUUID());
            roster.applyNotificationPreferences(owner.getUUID(), bothOff.revision(), false, false);
            UUID bothOffRun = UUID.randomUUID();
            restoreJob(onlineWorker, bothOffRun, 9, MiningSession.State.COMPLETED, "");
            WorkerNotifications.completed(onlineWorker);
            List<WorkerNetwork.Notice> bothOffNotice = drainNotices(owner);
            helper.assertTrue(bothOffNotice.size() == 1 && !bothOffNotice.getFirst().toast()
                            && !bothOffNotice.getFirst().sound() && roster.notifications(owner.getUUID()).size() == 9
                            && roster.notifications(owner.getUUID()).getLast().run().equals(bothOffRun),
                    "Disabling both presentation flags must retain the inbox entry while delivering false/false flags");
        } catch (Throwable failure) {
            helper.fail("Notification delivery test failed: " + failure);
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_notifications_negative", timeoutTicks = 120)
    public static void unownedRunningCancelledAndFailedJobsNeverNotify(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        try {
            WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
            UUID owner = UUID.randomUUID();
            WorkerEntity unowned = WorkerGameTestSupport.spawnWorker(helper);
            fixture.track(unowned);
            restoreJob(unowned, UUID.randomUUID(), 1, MiningSession.State.COMPLETED, "");
            WorkerNotifications.completed(unowned);
            helper.assertTrue(unowned.ownerUUID().isEmpty()
                            && unowned.miningStatus().state() == MiningSession.State.COMPLETED,
                    "The first negative fixture must be an unowned completed worker");

            WorkerEntity running = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 0)));
            WorkerEntity cancelled = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 0)));
            WorkerEntity failed = fixture.spawnOwned(owner, helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 0)));
            restoreJob(running, UUID.randomUUID(), 1, MiningSession.State.RUNNING, "");
            restoreJob(cancelled, UUID.randomUUID(), 1, MiningSession.State.CANCELLED, "");
            restoreJob(failed, UUID.randomUUID(), 1, MiningSession.State.FAILED, "NATIVE_STOPPED");
            helper.assertTrue(running.miningStatus().state() == MiningSession.State.RUNNING
                            && cancelled.miningStatus().state() == MiningSession.State.CANCELLED
                            && failed.miningStatus().state() == MiningSession.State.FAILED,
                    "The owned negative fixtures must retain their running, cancelled and failed states");
            WorkerNotifications.completed(running);
            WorkerNotifications.completed(cancelled);
            WorkerNotifications.completed(failed);
            helper.assertTrue(roster.notifications(owner).isEmpty()
                            && roster.notifications(UUID.randomUUID()).isEmpty(),
                    "Unowned, running, cancelled and failed jobs must never create completion history");
        } catch (Throwable failure) {
            helper.fail("Negative notification test failed: " + failure);
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    private static void restoreJob(WorkerEntity worker, UUID run, int amount,
                                   MiningSession.State state, String error) {
        CompoundTag saved = worker.saveWithoutId(new CompoundTag());
        CompoundTag product = saved.getCompound("AutomatoneWorker");
        product.putInt("Version", 2);
        CompoundTag job = new CompoundTag();
        job.putString("Target", IRON_ORE.toString());
        ListTag targets = new ListTag();
        targets.add(StringTag.valueOf(IRON_ORE.toString()));
        job.put("Targets", targets);
        job.putUUID("RunId", run);
        job.putInt("Requested", amount);
        job.putLong("Completed", state == MiningSession.State.COMPLETED ? amount : 0);
        job.putString("State", state.name());
        job.putString("Error", error);
        product.put("Job", job);
        saved.put("AutomatoneWorker", product);
        worker.load(saved);
    }

    private static WorkerMenu requireMenu(ServerPlayer player) {
        if (!(player.containerMenu instanceof WorkerMenu menu)) {
            throw new AssertionError("Expected a real WorkerMenu");
        }
        return menu;
    }

    private static void send(ServerPlayer player, WorkerMenu menu, long sequence,
                             WorkerNetwork.Action action, CompoundTag data) {
        menu.handle(player, new WorkerNetwork.Intent(menu.containerId, menu.session(), sequence, action, data));
    }

    private static void assertMenuError(GameTestHelper helper, WorkerMenu menu, String code) {
        CompoundTag response = menu.snapshot().getCompound("Response");
        helper.assertTrue(response.getString("Kind").equals("Error") && response.getString("Error").equals(code),
                "Expected menu response " + code + " but received " + response);
    }

    private static CompoundTag intData(String key, int value) {
        CompoundTag data = new CompoundTag();
        data.putInt(key, value);
        return data;
    }

    private static CompoundTag uuidData(String key, UUID value) {
        CompoundTag data = new CompoundTag();
        data.putUUID(key, value);
        return data;
    }

    private static CompoundTag preferenceData(long revision, boolean toasts, boolean sounds) {
        CompoundTag data = new CompoundTag();
        data.putLong("Revision", revision);
        data.putBoolean("Toasts", toasts);
        data.putBoolean("Sounds", sounds);
        return data;
    }

    private static List<WorkerNetwork.Notice> drainNotices(ServerPlayer player) {
        if (!(player.connection.getConnection().channel() instanceof EmbeddedChannel channel)) {
            throw new AssertionError("GameTest player transport must be an EmbeddedChannel");
        }
        List<WorkerNetwork.Notice> notices = new ArrayList<>();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof WorkerNetwork.Notice notice) {
                notices.add(notice);
            }
            if (message instanceof ReferenceCounted referenceCounted) {
                referenceCounted.release();
            }
        }
        return notices;
    }

    private static void expectFailure(GameTestHelper helper, Runnable action, String code, String message) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            helper.assertTrue(failure.getMessage() != null && failure.getMessage().contains(code),
                    message + "; expected " + code + " but received " + failure);
            return;
        }
        helper.fail(message + "; operation unexpectedly succeeded");
    }

    private static void finish(GameTestHelper helper, Fixture fixture,
                               WorkerNativeMineProcessGameTest.MiningChamber chamber, Throwable failure) {
        try {
            fixture.close();
        } catch (Throwable cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else {
                failure.addSuppressed(cleanupFailure);
            }
        }
        try {
            chamber.restore();
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
            helper.fail("Native notification test failed: " + failure);
        }
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<WorkerEntity> workers = new ArrayList<>();

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
            ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                    .add(WorkerNetwork.Notice.TYPE.id());
            players.add(player);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, new ItemStack(WorkerMod.CONTROLLER.get()));
            return player;
        }

        private WorkerEntity spawnOwned(UUID owner, ServerLevel level, BlockPos position) {
            WorkerEntity worker = WorkerMod.WORKER.get().create(level);
            if (worker == null) {
                throw new AssertionError("Registered worker entity did not create");
            }
            worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            worker.setNoGravity(true);
            if (!level.addFreshEntity(worker)) {
                throw new AssertionError("Dedicated server rejected the fixture worker");
            }
            worker.claim(owner);
            workers.add(worker);
            return worker;
        }

        private void track(WorkerEntity worker) {
            if (worker != null && !workers.contains(worker)) {
                workers.add(worker);
            }
        }

        private void close() {
            for (ServerPlayer player : players) {
                WorkerNotifications.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                player.closeContainer();
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
    }
}
