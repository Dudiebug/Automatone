package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.IPlayerController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


@GameTestHolder("automatone_worker_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerRuntimeGameTest {

    @GameTest(template = "provider_smoke", batch = "worker_runtime_ownership", timeoutTicks = 100)
    public static void oneWorkerOwnsOneRuntimeAndStableContext(GameTestHelper helper) {
        IBaritone providerRuntime = null;
        WorkerEntity worker = null;
        int baselineRuntimeCount = BaritoneAPI.getProvider().getAllBaritones().size();
        try {
            worker = WorkerGameTestSupport.spawnWorker(helper);
            providerRuntime = worker.runtime();
            helper.assertTrue(providerRuntime != null, "A loaded worker must own one runtime");
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount + 1
                            && BaritoneAPI.getProvider().getAllBaritones().contains(providerRuntime),
                    "One loaded worker must register exactly one runtime");

            IPlayerContext context = providerRuntime.getPlayerContext();
            helper.assertTrue(context.player() == worker,
                    "The runtime context must expose the exact worker entity");
            helper.assertTrue(context.inventory() == worker,
                    "The runtime context must expose the worker container boundary");
            IPlayerController controller = context.playerController();

            worker.attachRuntime();
            helper.assertTrue(worker.runtime() == providerRuntime,
                    "Repeated attachment must preserve the existing runtime identity");
            helper.assertTrue(worker.runtime().getPlayerContext() == context,
                    "Repeated attachment must preserve the existing context identity");
            helper.assertTrue(worker.runtime().getPlayerContext().playerController() == controller,
                    "Repeated attachment must preserve the existing controller identity");
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount + 1,
                    "Repeated attachment must not register a duplicate runtime");
            helper.assertTrue(context.worldData() == providerRuntime.getWorldProvider().getCurrentWorld(),
                    "Context worldData must be supplied by the same held runtime");
        } catch (Throwable failure) {
            helper.fail("Worker runtime ownership failed: " + failure);
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_runtime_ticks", timeoutTicks = 100)
    public static void runtimeReceivesExactlyOnePreAndPostEventPerServerTick(GameTestHelper helper) {
        WorkerEntity worker = null;
        int baselineRuntimeCount = BaritoneAPI.getProvider().getAllBaritones().size();
        try {
            worker = WorkerGameTestSupport.spawnWorker(helper);
            IBaritone runtime = worker.runtime();
            helper.assertTrue(runtime != null, "Tick fixture requires a live worker runtime");
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount + 1,
                    "Tick fixture must add only the worker runtime to provider ownership");
            TickObservation observation = new TickObservation();
            runtime.getGameEventHandler().registerEventListener(observation);
            WorkerEntity tickWorker = worker;
            helper.runAfterDelay(1, () -> {
                try {
                    observation.collecting = true;
                    helper.runAfterDelay(20, () -> {
                        try {
                            observation.collecting = false;
                            helper.assertTrue(tickWorker.runtime() == runtime,
                                    "The worker runtime must remain live during the bounded tick window");
                            helper.assertTrue(observation.preTicks == 20,
                                    "Exactly 20 runtime PRE IN events must accompany 20 server ticks, got "
                                            + observation.preTicks);
                            helper.assertTrue(observation.postTicks == 20,
                                    "Exactly 20 runtime POST IN events must accompany 20 server ticks, got "
                                            + observation.postTicks);
                            helper.succeed();
                        } catch (Throwable failure) {
                            helper.fail("Worker runtime tick observation failed: " + failure);
                        } finally {
                            WorkerGameTestSupport.discardWorker(tickWorker);
                        }
                    });
                } catch (Throwable failure) {
                    WorkerGameTestSupport.discardWorker(tickWorker);
                    helper.fail("Unable to schedule worker runtime tick observation: " + failure);
                }
            });
            return;
        } catch (Throwable failure) {
            WorkerGameTestSupport.discardWorker(worker);
            helper.fail("Worker runtime tick fixture failed: " + failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_runtime_removal", timeoutTicks = 100)
    public static void removalDisposesAndUnregistersRuntimeIdempotently(GameTestHelper helper) {
        WorkerEntity worker = null;
        int baselineRuntimeCount = BaritoneAPI.getProvider().getAllBaritones().size();
        try {
            worker = WorkerGameTestSupport.spawnWorker(helper);
            IBaritone runtime = worker.runtime();
            helper.assertTrue(runtime != null, "Removal fixture requires a live worker runtime");
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount + 1,
                    "Removal fixture must add only the worker runtime to provider ownership");
            TickObservation observation = new TickObservation();
            runtime.getGameEventHandler().registerEventListener(observation);
            WorkerEntity removalWorker = worker;
            observation.collecting = true;
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(observation.preTicks > 0 && observation.postTicks > 0,
                            "Removal control must observe live PRE and POST events before unloading");
                    int preTicks = observation.preTicks;
                    int postTicks = observation.postTicks;
                    removalWorker.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    helper.assertTrue(removalWorker.isRemoved(), "Unload removal must mark the worker removed");
                    helper.assertTrue(removalWorker.getRemovalReason()
                                    == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK,
                            "Removal fixture must exercise the UNLOADED_TO_CHUNK reason");
                    helper.assertTrue(removalWorker.runtime() == null,
                            "Unload removal must clear worker runtime ownership");
                    helper.assertTrue(runtime.isDisposed(), "Unload removal must dispose the owned runtime");
                    helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount,
                            "Unload removal must unregister only the worker runtime");

                    removalWorker.detachRuntime();
                    removalWorker.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    helper.assertTrue(removalWorker.runtime() == null && runtime.isDisposed(),
                            "Repeated removal and detach must remain idempotent");
                    helper.runAfterDelay(2, () -> {
                        try {
                            helper.assertTrue(observation.preTicks == preTicks && observation.postTicks == postTicks,
                                    "A removed runtime must receive no later tick events across real server ticks");
                            helper.succeed();
                        } catch (Throwable failure) {
                            WorkerGameTestSupport.discardWorker(removalWorker);
                            helper.fail("Worker runtime removal observation failed: " + failure);
                        }
                    });
                } catch (Throwable failure) {
                    WorkerGameTestSupport.discardWorker(removalWorker);
                    helper.fail("Worker runtime removal failed: " + failure);
                }
            });
            return;
        } catch (Throwable failure) {
            WorkerGameTestSupport.discardWorker(worker);
            helper.fail("Worker runtime removal failed: " + failure);
        }
    }

    @GameTest(template = "provider_smoke", batch = "worker_runtime_reload", timeoutTicks = 100)
    public static void saveRemoveLoadCreatesFreshIdleRuntime(GameTestHelper helper) {
        WorkerEntity original = null;
        WorkerEntity loaded = null;
        int baselineRuntimeCount = BaritoneAPI.getProvider().getAllBaritones().size();
        try {
            original = WorkerGameTestSupport.spawnWorker(helper);
            IBaritone firstRuntime = original.runtime();
            helper.assertTrue(firstRuntime != null, "Reload fixture requires an initial runtime");
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount + 1,
                    "Reload fixture must add only the original worker runtime");
            IPlayerController firstController = firstRuntime.getPlayerContext().playerController();
            helper.assertTrue(firstRuntime.getPlayerContext().worldData()
                            == firstRuntime.getWorldProvider().getCurrentWorld(),
                    "Original context worldData must come from its held runtime");
            firstRuntime.getMineProcess().mine(new BlockOptionalMetaLookup(Blocks.STONE));
            helper.assertTrue(firstRuntime.getMineProcess().isActive(),
                    "Reload fixture must save a runtime with transient work active");
            CompoundTag saved = original.saveWithoutId(new CompoundTag());

            original.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            helper.assertTrue(firstRuntime.isDisposed(), "Removing the original must dispose its runtime");
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount,
                    "Removing the original must leave baseline provider ownership");

            loaded = WorkerMod.WORKER.get().create(helper.getLevel());
            helper.assertTrue(loaded != null, "Registered worker type must create the reload entity");
            loaded.load(saved);
            loaded.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(loaded),
                    "Reload entity must be accepted by the server level");
            IBaritone freshRuntime = loaded.runtime();
            helper.assertTrue(freshRuntime != null && freshRuntime != firstRuntime,
                    "Reload must create one fresh runtime identity");
            helper.assertTrue(freshRuntime.getPlayerContext() != firstRuntime.getPlayerContext(),
                    "Reload must create a fresh transient context identity");
            helper.assertTrue(freshRuntime.getPlayerContext().playerController() != firstController,
                    "Reload must create a fresh controller identity");
            helper.assertTrue(freshRuntime.getPlayerContext().worldData()
                            == freshRuntime.getWorldProvider().getCurrentWorld(),
                    "Reloaded context worldData must come from its held runtime");
            helper.assertTrue(!freshRuntime.isDisposed(), "Reloaded runtime must be live");
            helper.assertTrue(!freshRuntime.getMineProcess().isActive(),
                    "Reload must not restore active mining process state");
            helper.assertTrue(freshRuntime.getPathingBehavior().getGoal() == null
                            && !freshRuntime.getPathingBehavior().hasPath()
                            && freshRuntime.getPathingBehavior().getInProgress().isEmpty(),
                    "Reload must not restore active goal, path, or path calculation state");
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount + 1
                            && BaritoneAPI.getProvider().getAllBaritones().contains(freshRuntime),
                    "Reloaded worker must own exactly one runtime");
        } catch (Throwable failure) {
            helper.fail("Worker runtime reload failed: " + failure);
        } finally {
            WorkerGameTestSupport.discardWorker(original);
            WorkerGameTestSupport.discardWorker(loaded);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_runtime_controller", timeoutTicks = 100)
    public static void controllerPreservesBoundedWorkerProtocol(GameTestHelper helper) {
        WorkerEntity worker = null;
        int baselineRuntimeCount = BaritoneAPI.getProvider().getAllBaritones().size();
        try {
            worker = WorkerGameTestSupport.spawnWorker(helper);
            helper.assertTrue(BaritoneAPI.getProvider().getAllBaritones().size() == baselineRuntimeCount + 1,
                    "Controller fixture must add only the worker runtime to provider ownership");
            IPlayerController controller = worker.runtime().getPlayerContext().playerController();
            Container inventory = worker.inventory();
            ItemStack first = new ItemStack(Items.DIRT);
            ItemStack second = new ItemStack(Items.COBBLESTONE);
            inventory.setItem(0, first);
            inventory.setItem(1, second);
            controller.swapContainerSlots(inventory, 0, 1);
            controller.syncHeldItem();
            helper.assertTrue(inventory.getItem(0) == second && inventory.getItem(1) == first,
                    "Controller must swap slots in the worker-owned container");
            helper.assertTrue(worker.getMainHandItem() == second,
                    "Selected slot zero hand must follow the controller swap immediately");

            SimpleContainer foreign = new SimpleContainer(2);
            ItemStack foreignFirst = new ItemStack(Items.STICK);
            ItemStack foreignSecond = new ItemStack(Items.STRING);
            foreign.setItem(0, foreignFirst);
            foreign.setItem(1, foreignSecond);
            expectIllegalArgument(() -> controller.swapContainerSlots(foreign, 0, 1));
            helper.assertTrue(foreign.getItem(0) == foreignFirst && foreign.getItem(1) == foreignSecond,
                    "Foreign container rejection must not mutate the foreign container");

            expectIndexFailure(() -> controller.swapContainerSlots(inventory, -1, 0));
            expectIndexFailure(() -> controller.swapContainerSlots(inventory, 0, 9));
            helper.assertTrue(inventory.getItem(0) == second && inventory.getItem(1) == first,
                    "Invalid slot indices must not mutate the worker inventory");

            worker.setSelectedSlot(1);
            controller.syncHeldItem();
            helper.assertTrue(worker.getMainHandItem() == first,
                    "Selected inventory slot must drive the worker main hand");
            helper.assertTrue(controller.getGameType() == GameType.SURVIVAL,
                    "Worker controller must expose survival game mode");

            BlockPos target = helper.absolutePos(new BlockPos(1, 1, 0));
            helper.getLevel().setBlock(target, Blocks.STONE.defaultBlockState(), 3);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
            helper.assertFalse(controller.hasBrokenBlock(), "Worker controller must report no broken block");
            helper.assertFalse(controller.onPlayerDamageBlock(target, Direction.UP),
                    "Worker controller must reject block damage");
            helper.assertFalse(controller.clickBlock(target, Direction.UP),
                    "Worker controller must reject block clicks");
            helper.assertTrue(controller.processRightClickBlock(worker, helper.getLevel(), InteractionHand.MAIN_HAND, hit)
                            == InteractionResult.FAIL,
                    "Worker controller must reject block interaction");
            helper.assertTrue(controller.processRightClick(worker, helper.getLevel(), InteractionHand.MAIN_HAND)
                            == InteractionResult.FAIL,
                    "Worker controller must reject item interaction");
            controller.resetBlockRemoving();
            controller.setHittingBlock(true);
            controller.resetDestroyDelay();
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.STONE),
                    "Unsupported controller operations must leave terrain unchanged");
        } catch (Throwable failure) {
            helper.fail("Worker controller protocol failed: " + failure);
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void expectIndexFailure(Runnable operation) {
        try {
            operation.run();
        } catch (IndexOutOfBoundsException expected) {
            return;
        }
        throw new AssertionError("Expected IndexOutOfBoundsException");
    }

    private static final class TickObservation implements AbstractGameEventListener {
        private boolean collecting;
        private int preTicks;
        private int postTicks;

        @Override
        public void onTick(TickEvent event) {
            if (collecting && event.getState() == EventState.PRE && event.getType() == TickEvent.Type.IN) {
                preTicks++;
            }
        }

        @Override
        public void onPostTick(TickEvent event) {
            if (collecting && event.getState() == EventState.POST && event.getType() == TickEvent.Type.IN) {
                postTicks++;
            }
        }
    }
}
