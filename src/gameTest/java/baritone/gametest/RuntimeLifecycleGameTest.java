package baritone.gametest;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.Settings;
import baritone.api.cache.IWorldData;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.IPlayerController;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.input.Input;
import baritone.cache.CachedWorld;
import baritone.cache.WorldData;
import baritone.process.MineProcess;
import baritone.utils.ToolSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class RuntimeLifecycleGameTest {

    @GameTest(template = "provider_smoke", timeoutTicks = 180)
    public static void realEntityRuntimeLifecycle(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            helper.fail("Runtime lifecycle test must run on the physical dedicated-server distribution");
            return;
        }

        RuntimeFixture fixture = null;
        try {
            fixture = RuntimeFixture.create(helper);
            fixture.exerciseHostContract(helper);
            fixture.startRuntime(helper);
            fixture.observeNativeMineAndDispose(helper);
        } catch (Throwable failure) {
            if (fixture != null) {
                fixture.close();
            }
            helper.fail("Real entity runtime lifecycle failed: " + failure);
        }
    }

    private static final class RuntimeFixture {
        private final IBaritoneProvider provider;
        private final ServerLevel level;
        private final ArmorStand host;
        private final SimpleContainer inventory;
        private final FixturePlayerContext context;
        private final AtomicReference<IBaritone> ownedRuntime;
        private final Path dataDirectory;
        private final Settings settings;
        private final boolean originalAllowBreak;
        private final boolean originalExploreForBlocks;
        private final boolean originalLegitMine;
        private final int originalMineGoalUpdateInterval;
        private final int originalMineMaxOreLocationsCount;
        private final boolean originalAllowInventory;
        private final boolean originalAutoTool;
        private final BlockPos targetPosition;
        private final BlockState originalTargetState;
        private IWorldData firstWorldData;
        private IBaritone runtime;
        private boolean closed;

        private RuntimeFixture(
                IBaritoneProvider provider,
                ServerLevel level,
                ArmorStand host,
                SimpleContainer inventory,
                FixturePlayerContext context,
                AtomicReference<IBaritone> ownedRuntime,
                Path dataDirectory,
                Settings settings,
                BlockPos targetPosition
        ) {
            this.provider = provider;
            this.level = level;
            this.host = host;
            this.inventory = inventory;
            this.context = context;
            this.ownedRuntime = ownedRuntime;
            this.dataDirectory = dataDirectory;
            this.settings = settings;
            this.originalAllowBreak = settings.allowBreak.value;
            this.originalExploreForBlocks = settings.exploreForBlocks.value;
            this.originalLegitMine = settings.legitMine.value;
            this.originalMineGoalUpdateInterval = settings.mineGoalUpdateInterval.value;
            this.originalMineMaxOreLocationsCount = settings.mineMaxOreLocationsCount.value;
            this.originalAllowInventory = settings.allowInventory.value;
            this.originalAutoTool = settings.autoTool.value;
            this.targetPosition = targetPosition;
            this.originalTargetState = level.getBlockState(targetPosition);
        }

        private static RuntimeFixture create(GameTestHelper helper) throws IOException {
            IBaritoneProvider provider = BaritoneAPI.getProvider();
            provider.disposeAll();
            ServerLevel level = helper.getLevel();
            var spawn = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
            ArmorStand host = new ArmorStand(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            host.setNoGravity(true);
            host.setYRot(0.0F);
            host.setXRot(0.0F);
            level.addFreshEntity(host);

            SimpleContainer inventory = new SimpleContainer(9);
            inventory.setItem(0, new ItemStack(Items.DIRT));
            inventory.setItem(1, new ItemStack(Items.STICK));
            FailFastController controller = new FailFastController(host);
            AtomicReference<IBaritone> ownedRuntime = new AtomicReference<>();
            FixturePlayerContext context = new FixturePlayerContext(host, inventory, controller, () -> {
                IBaritone runtime = ownedRuntime.get();
                return runtime == null ? null : runtime.getWorldProvider().getCurrentWorld();
            });
            Path dataDirectory = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "m1-5-runtime-lifecycle-");
            return new RuntimeFixture(provider, level, host, inventory, context, ownedRuntime, dataDirectory,
                    BaritoneAPI.getSettings(), helper.absolutePos(BlockPos.ZERO));
        }

        private void exerciseHostContract(GameTestHelper helper) {
            helper.setBlock(BlockPos.ZERO, Blocks.STONE);
            helper.assertTrue(Objects.equals(context.player(), host), "Context must expose the exact real LivingEntity host");
            helper.assertTrue(Objects.equals(context.world(), level), "Context must expose the host's real ServerLevel");
            helper.assertTrue(Objects.equals(context.inventory(), inventory), "Context must expose the supplied generic Container");
            helper.assertTrue(context.entitiesStream().anyMatch(entity -> Objects.equals(entity, host)),
                    "Context entity enumeration must include the real host");

            context.playerController().swapContainerSlots(inventory, 0, 1);
            helper.assertTrue(inventory.getItem(0).is(Items.STICK) && inventory.getItem(1).is(Items.DIRT),
                    "Controller must swap generic container indices directly");
            context.setSelectedSlot(1);
            helper.assertTrue(context.selectedSlot() == 1 && host.getMainHandItem().is(Items.DIRT),
                    "Selected slot must update the host hand from the generic container");
            context.setSelectedSlot(0);

            Container shortContainer = new SimpleContainer(3);
            FixturePlayerContext shortContext = new FixturePlayerContext(host, shortContainer, context.playerController(), () -> null);
            helper.assertTrue(shortContext.inventory().getContainerSize() == 3,
                    "Short containers must remain visible without player-menu assumptions");
            boolean rejectedOutOfRange = false;
            try {
                shortContext.setSelectedSlot(3);
            } catch (IllegalArgumentException expected) {
                rejectedOutOfRange = true;
            }
            helper.assertTrue(rejectedOutOfRange, "Selected slot must be bounded by a short container");

            FixturePlayerContext noInventory = new FixturePlayerContext(host, null, context.playerController(), () -> null);
            helper.assertTrue(noInventory.inventory() == null, "Nullable inventory must remain supported");

            settings.allowBreak.value = true;
            settings.exploreForBlocks.value = true;
            settings.legitMine.value = false;
            settings.mineGoalUpdateInterval.value = 100;
            settings.mineMaxOreLocationsCount.value = 1;
            settings.allowInventory.value = false;
            exerciseToolSetBounds(helper, context.playerController());
        }

        private void exerciseToolSetBounds(GameTestHelper helper, IPlayerController controller) {
            settings.autoTool.value = false;

            FixturePlayerContext emptyContext = new FixturePlayerContext(host, new SimpleContainer(0), controller, () -> null);
            ToolSet emptyTools = new ToolSet(emptyContext);
            helper.assertTrue(Double.isFinite(emptyTools.getStrVsBlock(Blocks.STONE.defaultBlockState())),
                    "ToolSet must evaluate an empty container without reading slot zero");
            helper.assertValueEqual(0, emptyTools.getBestSlot(Blocks.STONE, false, true),
                    "Empty container pathing selection must remain bounded at slot zero");

            SimpleContainer shortInventory = new SimpleContainer(3);
            FixturePlayerContext shortContext = new FixturePlayerContext(host, shortInventory, controller, () -> null);
            shortContext.setSelectedSlot(2);
            ToolSet shortTools = new ToolSet(shortContext);
            shortContext.setSelectedSlot(0);
            helper.assertValueEqual(2, shortTools.getBestSlot(Blocks.STONE, false, true),
                    "ToolSet must snapshot the selected slot at calculation construction");
        }

        private void startRuntime(GameTestHelper helper) {
            runtime = provider.createBaritone(context, dataDirectory);
            ownedRuntime.set(runtime);
            helper.assertTrue(runtime != null, "Provider must create a runtime for the real entity context");
            helper.assertTrue(runtime instanceof Baritone, "Provider must create the concrete Baritone runtime");
            helper.assertTrue(runtime.getMineProcess() instanceof MineProcess,
                    "Runtime must expose the concrete native MineProcess");
            helper.assertTrue(provider.getAllBaritones().size() == 1,
                    "Provider must own exactly one runtime for the context");
            IBaritone deduplicated = provider.createBaritone(context, dataDirectory.resolve("deduplicated"));
            helper.assertTrue(Objects.equals(deduplicated, runtime),
                    "Creating twice with the same context must return the first owned runtime");
            firstWorldData = runtime.getWorldProvider().getCurrentWorld();
            helper.assertTrue(firstWorldData != null && Objects.equals(context.worldData(), firstWorldData),
                    "Context worldData must bind to the created runtime's owned world");

            BlockOptionalMetaLookup target = new BlockOptionalMetaLookup(Blocks.STONE);
            helper.assertTrue(!target.blocks().isEmpty(), "Native mine fixture must use a usable non-null target filter");

            runtime.getMineProcess().mine((BlockOptionalMetaLookup) null);
            helper.assertFalse(runtime.getMineProcess().isActive(),
                    "Null native mine filter must remain inactive and cannot vacuously pass AC3");
            assertTargetUnchanged(helper);
            runtime.getMineProcess().mine(target);
            helper.assertTrue(runtime.getMineProcess().isActive(),
                    "Native mine must be active immediately after a non-null usable filter");
        }

        private void observeNativeMineAndDispose(GameTestHelper helper) {
            Observation observation = new Observation();
            observation.runtime = runtime;
            runtime.getGameEventHandler().registerEventListener(observation);
            final IBaritone firstRuntime = runtime;
            final BlockOptionalMetaLookup target = new BlockOptionalMetaLookup(Blocks.STONE);
            final float originalYaw = host.getYRot();
            runtime.getLookBehavior().updateTarget(new baritone.api.utils.Rotation(90.0F, 0.0F), false);
            observation.collecting = true;
            int startTicks = observation.inTicks;
            int startPostTicks = observation.postTicks;

            helper.runAfterDelay(1, () -> {
                try {
                    helper.assertTrue(Math.abs(host.getYRot() - originalYaw) > 0.01F,
                            "Server look target must be consumed by the real runtime tick");
                    helper.assertTrue(observation.ownerTicks > 0,
                            "The first native MineProcess tick must own pathing control");
                    helper.assertTrue(firstRuntime.getMineProcess().isActive(),
                            "Native mine must remain active after its first owning tick");
                    firstRuntime.getMineProcess().cancel();
                    helper.assertFalse(firstRuntime.getMineProcess().isActive(),
                            "Explicit native cancel must make MineProcess inactive at the first owning-tick checkpoint");
                    assertTargetUnchanged(helper);
                    int ownerAtCancel = observation.ownerTicks;
                    helper.runAfterDelay(19, () -> {
                        try {
                            observation.collecting = false;
                            helper.assertValueEqual(20, observation.inTicks - startTicks,
                                    "Provider must deliver exactly one runtime IN tick per actual server tick");
                            helper.assertValueEqual(20, observation.postTicks - startPostTicks,
                                    "Provider must deliver exactly one runtime POST event per actual server tick");
                            helper.assertTrue(observation.ownerTicks > 0 && observation.ownerTicks == ownerAtCancel,
                                    "Only the first native MineProcess tick may own control before cancellation");
                            helper.assertValueEqual(19, observation.idleTicks,
                                    "The 19 ticks after native cancellation must remain idle-owned runtime ticks");
                            assertTargetUnchanged(helper);

                            firstRuntime.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                            helper.assertTrue(firstRuntime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD),
                                    "Dispose fixture must begin with an owned input override asserted");
                            firstRuntime.getMineProcess().mine(target);
                            helper.assertTrue(firstRuntime.getMineProcess().isActive(),
                                    "A second non-null native mine must be active before disposal");
                            disposeLiveAndRecreate(helper, firstRuntime, target, observation);
                        } catch (Throwable failure) {
                            close();
                            helper.fail("Native mine observation failed: " + failure);
                        }
                    });
                } catch (Throwable failure) {
                    close();
                    helper.fail("Server runtime tick observation failed: " + failure);
                }
            });
        }

        private void disposeLiveAndRecreate(
                GameTestHelper helper,
                IBaritone firstRuntime,
                BlockOptionalMetaLookup target,
                Observation observation
        ) {
            try {
                helper.assertTrue(firstRuntime.getMineProcess().isActive(),
                        "Dispose observation must begin with a live native process");
                CachedWorld firstCache = ((WorldData) firstWorldData).cache;
                List<FutureTask<?>> workers = new ArrayList<>();
                List<CountDownLatch> completions = new ArrayList<>();
                for (String name : List.of("packerTask", "periodicSaveTask")) {
                    Field taskField = CachedWorld.class.getDeclaredField(name);
                    taskField.setAccessible(true);
                    FutureTask<?> worker = (FutureTask<?>) taskField.get(firstCache);
                    workers.add(worker);
                    Field stoppedField = worker.getClass().getDeclaredField("stopped");
                    stoppedField.setAccessible(true);
                    completions.add((CountDownLatch) stoppedField.get(worker));
                }
                helper.assertTrue(workers.size() == 2 && workers.stream().noneMatch(FutureTask::isDone)
                                && completions.stream().allMatch(latch -> latch.getCount() == 1),
                        "First runtime must retain two live cache worker tasks and completion latches before disposal");
                helper.assertTrue(provider.destroyBaritone(firstRuntime),
                        "Provider must remove and dispose the live runtime");
                helper.assertTrue(workers.stream().allMatch(FutureTask::isDone)
                                && completions.stream().allMatch(latch -> latch.getCount() == 0),
                        "Provider destruction must complete both retained first-runtime cache worker bodies before returning");
                assertTargetUnchanged(helper);
                helper.assertTrue(firstRuntime.isDisposed(), "Destroyed runtime must be disposed");
                helper.assertFalse(firstRuntime.getMineProcess().isActive(),
                        "Disposing a live runtime must cancel its native process");
                helper.assertFalse(firstRuntime.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD),
                        "Disposal must clear the seeded owned input override");
                helper.assertTrue(host.xxa == 0.0F && host.yya == 0.0F && host.zza == 0.0F,
                        "Disposal must release host movement inputs");
                helper.assertTrue(provider.getAllBaritones().isEmpty(),
                        "Destroyed runtime must not remain provider-owned");
                firstRuntime.dispose();
                firstRuntime.dispose();

                observation.collecting = true;
                int disposedInTicks = observation.inTicks;
                int disposedPostTicks = observation.postTicks;
                firstRuntime.tick();
                observation.collecting = false;
                helper.assertValueEqual(disposedInTicks, observation.inTicks,
                        "A disposed runtime tick must not emit another IN event");
                helper.assertValueEqual(disposedPostTicks, observation.postTicks,
                        "A disposed runtime tick must not emit another POST event");
                helper.assertTrue(provider.getAllBaritones().isEmpty(),
                        "A disposed runtime tick must not revive provider ownership");

                IBaritone recreated = provider.createBaritone(context, dataDirectory);
                helper.assertTrue(!Objects.equals(recreated, firstRuntime) && !recreated.isDisposed(),
                        "Recreation with the same context must produce a fresh runtime identity");
                helper.assertTrue(recreated instanceof Baritone
                                && recreated.getMineProcess() instanceof MineProcess,
                        "Recreation must produce concrete Baritone and MineProcess instances");
                helper.assertTrue(!Objects.equals(recreated.getMineProcess(), firstRuntime.getMineProcess())
                                && !recreated.getMineProcess().isActive(),
                        "Recreated runtime must own a fresh inactive native process");
                ownedRuntime.set(recreated);
                IWorldData recreatedWorldData = context.worldData();
                helper.assertTrue(recreatedWorldData != null && !Objects.equals(recreatedWorldData, firstWorldData),
                        "Recreated context must bind fresh world data to the recreated runtime");

                Observation recreatedObservation = new Observation();
                recreatedObservation.runtime = recreated;
                recreated.getGameEventHandler().registerEventListener(recreatedObservation);
                recreatedObservation.collecting = true;
                helper.runAfterDelay(1, () -> finishRecreatedTick(
                        helper,
                        recreated,
                        recreatedWorldData,
                        recreatedObservation,
                        target
                ));
            } catch (Throwable failure) {
                close();
                helper.fail("Live disposal/recreation failed: " + failure);
            }
        }

        private void finishRecreatedTick(
                GameTestHelper helper,
                IBaritone recreated,
                IWorldData recreatedWorldData,
                Observation observation,
                BlockOptionalMetaLookup target
        ) {
            try {
                observation.collecting = false;
                helper.assertValueEqual(1, observation.inTicks,
                        "Recreated runtime must receive one real IN tick before host removal");
                helper.assertValueEqual(1, observation.postTicks,
                        "Recreated runtime must receive one real POST tick before host removal");
                helper.assertTrue(Objects.equals(context.worldData(), recreatedWorldData),
                        "Recreated context worldData must remain bound to fresh owned world data");
                assertTargetUnchanged(helper);
                recreated.getMineProcess().mine(target);
                helper.assertTrue(recreated.getMineProcess().isActive(),
                        "Recreated runtime must accept the real native mine filter");
                host.remove(Entity.RemovalReason.DISCARDED);
                helper.runAfterDelay(1, () -> finishRemovedHost(helper, recreated));
            } catch (Throwable failure) {
                close();
                helper.fail("Recreated runtime tick failed: " + failure);
            }
        }

        private void finishRemovedHost(GameTestHelper helper, IBaritone recreated) {
            try {
                helper.assertTrue(provider.getAllBaritones().isEmpty(),
                        "Provider must remove a runtime whose real host was removed");
                helper.assertTrue(recreated.isDisposed(), "Removed-host cleanup must dispose the runtime");
                helper.assertFalse(recreated.getMineProcess().isActive(),
                        "Removed-host cleanup must cancel the active native process");
                assertTargetUnchanged(helper);
                recreated.dispose();
                close();
                helper.succeed();
            } catch (Throwable failure) {
                close();
                helper.fail("Removed-host cleanup failed: " + failure);
            }
        }

        private static void assertTargetUnchanged(GameTestHelper helper) {
            helper.assertTrue(helper.getBlockState(BlockPos.ZERO).equals(Blocks.STONE.defaultBlockState()),
                    "Known STONE target must exist and remain unchanged in the fixture at relative (0,0,0)");
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            ownedRuntime.set(null);
            provider.disposeAll();
            if (!host.isRemoved()) {
                host.remove(Entity.RemovalReason.DISCARDED);
            }
            settings.allowBreak.value = originalAllowBreak;
            settings.exploreForBlocks.value = originalExploreForBlocks;
            settings.legitMine.value = originalLegitMine;
            settings.mineGoalUpdateInterval.value = originalMineGoalUpdateInterval;
            settings.mineMaxOreLocationsCount.value = originalMineMaxOreLocationsCount;
            settings.allowInventory.value = originalAllowInventory;
            settings.autoTool.value = originalAutoTool;
            level.setBlock(targetPosition, originalTargetState, 3);
            if (!level.getBlockState(targetPosition).equals(originalTargetState)) {
                throw new AssertionError("Runtime fixture must restore the original target blockstate during cleanup");
            }
            try {
                deleteRecursively(dataDirectory);
            } catch (IOException ex) {
                throw new AssertionError("Unable to clean runtime lifecycle directory", ex);
            }
        }

        private static void deleteRecursively(Path root) throws IOException {
            if (!Files.exists(root)) {
                return;
            }
            List<Path> paths;
            try (var stream = Files.walk(root)) {
                paths = stream.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class FixturePlayerContext implements IPlayerContext {
        private final LivingEntity host;
        @Nullable
        private final Container inventory;
        private final IPlayerController controller;
        private final Supplier<IWorldData> worldDataSupplier;
        private int selectedSlot;

        private FixturePlayerContext(
                LivingEntity host,
                @Nullable Container inventory,
                IPlayerController controller,
                Supplier<IWorldData> worldDataSupplier
        ) {
            this.host = Objects.requireNonNull(host, "host");
            this.inventory = inventory;
            this.controller = Objects.requireNonNull(controller, "controller");
            this.worldDataSupplier = Objects.requireNonNull(worldDataSupplier, "worldDataSupplier");
        }

        @Override
        public LivingEntity player() {
            return host;
        }

        @Override
        @Nullable
        public Container inventory() {
            return inventory;
        }

        @Override
        public int selectedSlot() {
            return selectedSlot;
        }

        @Override
        public void setSelectedSlot(int slot) {
            int available = inventory == null ? 9 : Math.min(9, inventory.getContainerSize());
            if (slot < 0 || slot >= available) {
                throw new IllegalArgumentException("Selected slot " + slot + " outside hotbar size " + available);
            }
            selectedSlot = slot;
            if (inventory != null) {
                host.setItemInHand(InteractionHand.MAIN_HAND, inventory.getItem(slot));
            }
        }

        @Override
        public IPlayerController playerController() {
            return controller;
        }

        @Override
        public Level world() {
            return host.level();
        }

        @Override
        @Nullable
        public IWorldData worldData() {
            return worldDataSupplier.get();
        }

        @Override
        public HitResult objectMouseOver() {
            return RayTraceUtils.rayTraceTowards(host, playerRotations(), controller.getBlockReachDistance());
        }
    }

    private static final class FailFastController implements IPlayerController {
        private final net.minecraft.world.entity.LivingEntity host;

        private FailFastController(net.minecraft.world.entity.LivingEntity host) {
            this.host = Objects.requireNonNull(host, "host");
        }

        @Override
        public void syncHeldItem() {
            // Selected-hand synchronization is owned by EntityContext.
        }

        @Override
        public boolean hasBrokenBlock() {
            return false;
        }

        @Override
        public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
            throw unexpected("damage block");
        }

        @Override
        public void resetBlockRemoving() {
        }

        @Override
        public void swapContainerSlots(Container container, int firstSlot, int secondSlot) {
            Objects.requireNonNull(container, "container");
            checkSlot(container, firstSlot);
            checkSlot(container, secondSlot);
            if (firstSlot == secondSlot) {
                return;
            }
            ItemStack first = container.getItem(firstSlot);
            ItemStack second = container.getItem(secondSlot);
            container.setItem(firstSlot, second);
            container.setItem(secondSlot, first);
        }

        private static void checkSlot(Container container, int slot) {
            if (slot < 0 || slot >= container.getContainerSize()) {
                throw new IndexOutOfBoundsException("Container slot " + slot + " outside size " + container.getContainerSize());
            }
        }

        @Override
        public GameType getGameType() {
            return GameType.SURVIVAL;
        }

        @Override
        public InteractionResult processRightClickBlock(LivingEntity player, Level world, InteractionHand hand, BlockHitResult result) {
            throw unexpected("right-click block");
        }

        @Override
        public InteractionResult processRightClick(LivingEntity player, Level world, InteractionHand hand) {
            throw unexpected("right-click item");
        }

        @Override
        public boolean clickBlock(BlockPos loc, Direction face) {
            throw unexpected("click block");
        }

        @Override
        public void setHittingBlock(boolean hittingBlock) {
        }

        @Override
        public void resetDestroyDelay() {
        }

        private AssertionError unexpected(String action) {
            return new AssertionError("Native runtime unexpectedly attempted to " + action + " for host " + this.host.getUUID());
        }
    }

    private static final class Observation implements AbstractGameEventListener {
        private boolean collecting;
        private int inTicks;
        private int postTicks;
        private int ownerTicks;
        private int idleTicks;
        private IBaritone runtime;

        @Override
        public void onTick(TickEvent event) {
            if (collecting && event.getState() == EventState.PRE && event.getType() == TickEvent.Type.IN) {
                inTicks++;
            }
        }

        @Override
        public void onPostTick(TickEvent event) {
            if (!collecting || event.getType() != TickEvent.Type.IN) {
                return;
            }
            postTicks++;
            if (runtime != null
                    && runtime.getPathingControlManager().mostRecentInControl()
                    .filter(process -> Objects.equals(process, runtime.getMineProcess()))
                    .isPresent()) {
                ownerTicks++;
            } else {
                idleTicks++;
            }
        }

        private Observation() {
        }
    }
}
