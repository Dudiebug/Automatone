package baritone.gametest;

import baritone.Baritone;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.selection.ISelection;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.behavior.PathingBehavior;
import baritone.cache.CachedChunk;
import baritone.process.GetToBlockProcess;
import baritone.utils.schematic.SchematicSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class RuntimeOwnershipGameTest {

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void cachedChunkOwnsInputsAndPublishedViews(GameTestHelper helper) {
        BlockState[] sourceOverview = new BlockState[256];
        sourceOverview[0] = Blocks.STONE.defaultBlockState();
        byte[] expectedData;
        BitSet sourceData = new BitSet(CachedChunk.size(16));
        sourceData.set(0);
        expectedData = sourceData.toByteArray();

        String blockType = "minecraft:chest";
        BlockPos.MutableBlockPos mutableLocation = new BlockPos.MutableBlockPos(1, 2, 3);
        List<BlockPos> sourceLocations = new ArrayList<>(List.of(mutableLocation));
        Map<String, List<BlockPos>> sourceRelativeBlocks = new HashMap<>();
        sourceRelativeBlocks.put(blockType, sourceLocations);
        CachedChunk chunk = newCachedChunk(sourceData, sourceOverview, sourceRelativeBlocks);

        sourceData.set(1);
        mutableLocation.set(4, 5, 6);
        helper.assertTrue(chunk.getRelativeBlocks().get(blockType).equals(List.of(new BlockPos(1, 2, 3))),
                "CachedChunk must snapshot mutable block coordinates");
        helper.assertTrue(chunk.getAbsoluteBlocks(blockType).equals(List.of(new BlockPos(1, 2, 3))),
                "CachedChunk derived locations must retain constructor coordinates");

        sourceOverview[0] = Blocks.DIRT.defaultBlockState();
        sourceLocations.clear();
        sourceRelativeBlocks.put("minecraft:furnace", List.of(new BlockPos(4, 5, 6)));

        BlockState[] overviewView = chunk.getOverview();
        overviewView[0] = Blocks.DIRT.defaultBlockState();
        Map<String, List<BlockPos>> relativeBlocksView = chunk.getRelativeBlocks();
        boolean outerMutationRejected = false;
        try {
            relativeBlocksView.remove(blockType);
        } catch (UnsupportedOperationException expected) {
            outerMutationRejected = true;
        }
        helper.assertTrue(outerMutationRejected, "CachedChunk relative-block map must be immutable");

        List<BlockPos> locationsView = relativeBlocksView.get(blockType);
        helper.assertTrue(locationsView != null, "CachedChunk must retain its relative-block entry");
        boolean nestedMutationRejected = false;
        try {
            locationsView.clear();
        } catch (UnsupportedOperationException expected) {
            nestedMutationRejected = true;
        }
        helper.assertTrue(nestedMutationRejected, "CachedChunk relative-block lists must be immutable");
        helper.assertTrue(Arrays.equals(expectedData, chunk.toByteArray()),
                "CachedChunk must snapshot packed block data");
        helper.assertTrue(chunk.getOverview()[0].equals(Blocks.STONE.defaultBlockState()),
                "CachedChunk must snapshot overview state");
        helper.assertTrue(chunk.getRelativeBlocks().get(blockType).equals(List.of(new BlockPos(1, 2, 3))),
                "CachedChunk published views must retain the original location");
        helper.assertTrue(chunk.getRelativeBlocks().size() == 1,
                "CachedChunk published views must retain the original map entries");
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void builderFileBuildClosesParserInputStream(GameTestHelper helper) {
        Path root = null;
        Baritone baritone = null;
        InputStream input = null;
        AtomicReference<InputStream> openedInput = new AtomicReference<>();
        ISchematicFormat format = new ISchematicFormat() {
            @Override
            public IStaticSchematic parse(InputStream input) {
                openedInput.set(input);
                return new MinimalSchematic();
            }

            @Override
            public boolean isFileType(File file) {
                return file.getName().endsWith(".cleanup-schem");
            }

            @Override
            public List<String> getFileExtensions() {
                return List.of("cleanup-schem");
            }
        };
        try {
            root = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "runtime-builder-");
            Path schematicFile = root.resolve("fixture.cleanup-schem");
            Files.write(schematicFile, new byte[]{1});
            SchematicSystem.INSTANCE.getRegistry().register(format);
            baritone = new Baritone(emptyPlayerContext(), root.resolve("runtime"));
            helper.assertTrue(baritone.getBuilderProcess().build(
                    "stream-lifetime",
                    schematicFile.toFile(),
                    new Vec3i(0, 0, 0)
            ), "BuilderProcess must accept the registered schematic format");
            input = openedInput.get();
            helper.assertTrue(input != null, "BuilderProcess must pass the file stream to the parser");
            if (input != null) {
                boolean closed = false;
                try {
                    input.read();
                } catch (IOException expected) {
                    closed = true;
                }
                helper.assertTrue(closed, "BuilderProcess must close the schematic parser input stream");
            }
        } catch (IOException ex) {
            throw new AssertionError("Unable to create BuilderProcess GameTest fixture", ex);
        } finally {
            InputStream capturedInput = input == null ? openedInput.get() : input;
            if (capturedInput != null) {
                closeInput(capturedInput);
            }
            if (baritone != null) {
                baritone.dispose();
            }
            SchematicSystem.INSTANCE.getRegistry().unregister(format);
            deleteRecursively(root);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void selectionArrayIsolatedFromManager(GameTestHelper helper) {
        Path root = null;
        Baritone baritone = null;
        try {
            root = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "runtime-selection-");
            baritone = new Baritone(emptyPlayerContext(), root.resolve("runtime"));
            ISelection expected = baritone.getSelectionManager().addSelection(
                    new BetterBlockPos(0, 0, 0),
                    new BetterBlockPos(1, 1, 1)
            );
            ISelection[] returned = baritone.getSelectionManager().getSelections();
            returned[0] = null;
            helper.assertTrue(baritone.getSelectionManager().getSelections()[0] == expected,
                    "SelectionManager must isolate its published array");
        } catch (IOException ex) {
            throw new AssertionError("Unable to create SelectionManager GameTest fixture", ex);
        } finally {
            if (baritone != null) {
                baritone.dispose();
            }
            deleteRecursively(root);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void getToBlockCompletionAndFailureProtocol(GameTestHelper helper) {
        Path root = null;
        Baritone baritone = null;
        try {
            root = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "runtime-get-to-");
            baritone = new Baritone(emptyPlayerContext(), root.resolve("runtime"));
            GetToBlockProcess process = baritone.getGetToBlockProcess();
            BlockOptionalMeta activeTarget = new BlockOptionalMeta(Blocks.STONE);
            BlockOptionalMeta staleTarget = new BlockOptionalMeta(Blocks.DIRT);
            setField(process, "gettingTo", activeTarget);
            setField(process, "blacklist", new ArrayList<>());
            setField(process, "rescanGeneration", 7L);

            Method publish = GetToBlockProcess.class.getDeclaredMethod(
                    "publishRescanResult", long.class, BlockOptionalMeta.class, List.class, RuntimeException.class
            );
            publish.setAccessible(true);
            BlockPos activePosition = new BlockPos(2, 3, 4);
            publish.invoke(process, 7L, activeTarget, new ArrayList<>(List.of(activePosition)), null);
            helper.assertTrue(List.of(activePosition).equals(getField(process, "knownLocations")),
                    "Current GetToBlock completion must publish its locations");

            publish.invoke(process, 6L, activeTarget,
                    new ArrayList<>(List.of(new BlockPos(6, 7, 8))), null);
            publish.invoke(process, 6L, activeTarget, new ArrayList<>(),
                    new IllegalStateException("stale same-target scan failure"));
            publish.invoke(process, 6L, staleTarget,
                    new ArrayList<>(List.of(new BlockPos(8, 9, 10))), null);
            publish.invoke(process, 6L, staleTarget, new ArrayList<>(),
                    new IllegalStateException("stale scan failure"));
            publish.invoke(process, 7L, staleTarget,
                    new ArrayList<>(List.of(new BlockPos(11, 12, 13))), null);
            publish.invoke(process, 7L, staleTarget, new ArrayList<>(),
                    new IllegalStateException("wrong-target scan failure"));
            helper.assertTrue(List.of(activePosition).equals(getField(process, "knownLocations")),
                    "Stale or wrong-target success and failure must not replace current locations");

            publish.invoke(process, 7L, activeTarget, new ArrayList<>(),
                    new IllegalStateException("current scan failure"));
            helper.assertTrue(process.onTick(false, true).commandType == PathingCommandType.CANCEL_AND_SET_GOAL,
                    "Current scan failure must cancel from the owner tick");

            process.onLostControl();
            publish.invoke(process, 7L, activeTarget, new ArrayList<>(List.of(activePosition)), null);
            helper.assertTrue(!process.isActive() && getField(process, "knownLocations") == null,
                    "Canceled GetToBlock scans must not republish after cancellation");
        } catch (ReflectiveOperationException | IOException ex) {
            throw new AssertionError("Unable to exercise GetToBlock completion protocol", ex);
        } finally {
            if (baritone != null) {
                baritone.dispose();
            }
            deleteRecursively(root);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void pathingEtaSnapshotUsesPlanLock(GameTestHelper helper) {
        Path root = null;
        Baritone baritone = null;
        try {
            root = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "runtime-eta-");
            baritone = new Baritone(playerContextAt(new BetterBlockPos(5, 0, 0)), root.resolve("runtime"));
            PathingBehavior behavior = baritone.getPathingBehavior();
            setField(behavior, "goal", new GoalBlock(10, 0, 0));
            setField(behavior, "startPosition", new BetterBlockPos(0, 0, 0));
            setField(behavior, "ticksElapsedSoFar", 5);

            helper.assertTrue(behavior.estimatedTicksToGoal().isPresent()
                            && Math.abs(behavior.estimatedTicksToGoal().get() - 5.0D) < 0.0001D,
                    "ETA must preserve the existing estimate calculation");

            Object planLock = getField(behavior, "pathPlanLock");
            assertBlocksWhilePlanLocked(helper, planLock, () -> behavior.estimatedTicksToGoal(),
                    "ETA snapshots must wait for the path-plan lock");
            Method reset = PathingBehavior.class.getDeclaredMethod(
                    "resetEstimatedTicksToGoal", BetterBlockPos.class
            );
            reset.setAccessible(true);
            assertBlocksWhilePlanLocked(helper, planLock, () -> {
                try {
                    reset.invoke(behavior, new BetterBlockPos(1, 0, 0));
                } catch (ReflectiveOperationException ex) {
                    throw new AssertionError("Unable to invoke ETA reset helper", ex);
                }
            }, "ETA resets must wait for the path-plan lock");
        } catch (ReflectiveOperationException | IOException ex) {
            throw new AssertionError("Unable to exercise ETA state protocol", ex);
        } finally {
            if (baritone != null) {
                baritone.dispose();
            }
            deleteRecursively(root);
        }
        helper.succeed();
    }

    private static IPlayerContext emptyPlayerContext() {
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static IPlayerContext playerContextAt(BetterBlockPos feet) {
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> method.getName().equals("playerFeet")
                        ? feet
                        : defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return null;
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void assertBlocksWhilePlanLocked(
            GameTestHelper helper,
            Object planLock,
            Runnable operation,
            String message
    ) {
        CountDownLatch started = new CountDownLatch(1);
        FutureTask<Void> task = new FutureTask<>(() -> {
            started.countDown();
            operation.run();
            return null;
        });
        Thread thread = new Thread(task, "runtime-eta-lock-control");
        try {
            synchronized (planLock) {
                thread.start();
                helper.assertTrue(started.await(1, TimeUnit.SECONDS), "ETA lock control thread must start");
                boolean blocked;
                try {
                    task.get(250, TimeUnit.MILLISECONDS);
                    blocked = false;
                } catch (TimeoutException expected) {
                    blocked = true;
                } catch (ExecutionException ex) {
                    throw new AssertionError("ETA lock control operation failed", ex.getCause());
                }
                helper.assertTrue(blocked, message);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while checking ETA lock control", ex);
        } finally {
            try {
                task.get(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while joining ETA lock control", ex);
            } catch (ExecutionException | TimeoutException ex) {
                throw new AssertionError("ETA lock control thread did not finish", ex);
            }
        }
    }

    private static CachedChunk newCachedChunk(
            BitSet data,
            BlockState[] overview,
            Map<String, List<BlockPos>> relativeBlocks
    ) {
        try {
            Constructor<CachedChunk> constructor = CachedChunk.class.getDeclaredConstructor(
                    int.class,
                    int.class,
                    int.class,
                    BitSet.class,
                    BlockState[].class,
                    Map.class,
                    long.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(0, 0, 16, data, overview, relativeBlocks, 1L);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to construct package-private CachedChunk fixture", ex);
        }
    }

    private static void closeInput(InputStream input) {
        try {
            input.close();
        } catch (IOException ex) {
            throw new AssertionError("Unable to close BuilderProcess fixture input", ex);
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            throw new AssertionError("Unable to remove isolated GameTest fixture directory", ex);
        }
    }

    private static final class MinimalSchematic implements IStaticSchematic {

        @Override
        public BlockState getDirect(int x, int y, int z) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public int widthX() {
            return 1;
        }

        @Override
        public int heightY() {
            return 1;
        }

        @Override
        public int lengthZ() {
            return 1;
        }
    }
}
