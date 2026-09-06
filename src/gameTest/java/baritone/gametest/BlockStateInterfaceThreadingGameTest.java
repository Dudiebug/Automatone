package baritone.gametest;

import baritone.Baritone;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.cache.WorldScanner;
import baritone.utils.BlockStateInterface;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class BlockStateInterfaceThreadingGameTest {

    private static final long WORKER_TIMEOUT_SECONDS = 2L;

    @GameTest(template = "provider_smoke", batch = "off_thread_loaded_chunk_access", timeoutTicks = 100)
    public static void alreadyLoadedChunksAreReadableWithoutWaitingForTheServerThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean previousPathThroughCachedOnly = Baritone.settings().pathThroughCachedOnly.value;
        Map<BlockPos, BlockState> originalStates = Map.of();
        try {
            Baritone.settings().pathThroughCachedOnly.value = false;
            List<BlockPos> loadedPositions = loadedPositions(helper);
            originalStates = originalStates(level, loadedPositions);
            seedLoadedChunks(level, loadedPositions, List.of(
                    Blocks.REDSTONE_BLOCK.defaultBlockState(),
                    Blocks.LAPIS_BLOCK.defaultBlockState(),
                    Blocks.EMERALD_BLOCK.defaultBlockState()
            ));

            BlockPos absentPosition = absentPosition(helper);
            ChunkPos absentChunk = new ChunkPos(absentPosition);
            helper.assertTrue(!level.getChunkSource().hasChunk(absentChunk.x, absentChunk.z),
                    "The absent-chunk assertion must start with a chunk the server has not loaded");

            IPlayerContext context = context(level, loadedPositions.getFirst());
            BlockStateInterface blocks = new BlockStateInterface(context, true);
            BlockStateRead read = awaitWorker(() -> new BlockStateRead(
                    loadedPositions.stream().map(blocks::get0).toList(),
                    blocks.get0(absentPosition),
                    blocks.isLoaded(loadedPositions.getFirst().getX(), loadedPositions.getFirst().getZ()),
                    blocks.isLoaded(absentPosition.getX(), absentPosition.getZ())
            ));

            helper.assertTrue(read.loadedStates().get(0).is(Blocks.REDSTONE_BLOCK)
                            && read.loadedStates().get(1).is(Blocks.LAPIS_BLOCK)
                            && read.loadedStates().get(2).is(Blocks.EMERALD_BLOCK),
                    "A threaded BlockStateInterface must read every already-loaded chunk without scheduling server work");
            helper.assertTrue(read.loadedChunkReportedLoaded(),
                    "A threaded BlockStateInterface must report an already-loaded chunk as loaded");
            helper.assertTrue(read.absentState().isAir() && !read.absentChunkReportedLoaded(),
                    "A threaded BlockStateInterface must treat an absent chunk as unloaded air");
            helper.assertTrue(!level.getChunkSource().hasChunk(absentChunk.x, absentChunk.z),
                    "A threaded BlockStateInterface lookup must not load an absent chunk");
        } finally {
            restore(level, originalStates);
            Baritone.settings().pathThroughCachedOnly.value = previousPathThroughCachedOnly;
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "off_thread_loaded_chunk_access", timeoutTicks = 100)
    public static void worldScannerReadsLoadedChunksWithoutWaitingForTheServerThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos loadedPosition = loadedPositions(helper).getFirst();
        Map<BlockPos, BlockState> originalStates = originalStates(level, List.of(loadedPosition));
        try {
            seedLoadedChunks(level, List.of(loadedPosition), List.of(Blocks.DIAMOND_BLOCK.defaultBlockState()));
            ChunkPos loadedChunk = new ChunkPos(loadedPosition);
            BlockPos absentPosition = absentPosition(helper);
            ChunkPos absentChunk = new ChunkPos(absentPosition);
            helper.assertTrue(!level.getChunkSource().hasChunk(absentChunk.x, absentChunk.z),
                    "The scanner must be asked about a chunk the server has not loaded");

            IPlayerContext context = context(level, loadedPosition);
            BlockOptionalMetaLookup diamonds = new BlockOptionalMetaLookup(Blocks.DIAMOND_BLOCK);
            ScanResult scan = awaitWorker(() -> new ScanResult(
                    WorldScanner.INSTANCE.scanChunk(context, diamonds, loadedChunk, 16, 64),
                    WorldScanner.INSTANCE.scanChunk(context, diamonds, absentChunk, 16, 64)
            ));

            helper.assertTrue(scan.loadedMatches().contains(loadedPosition),
                    "WorldScanner must find a matching block in a loaded chunk from its worker thread");
            helper.assertTrue(scan.absentMatches().isEmpty(),
                    "WorldScanner must return no matches for an absent chunk");
            helper.assertTrue(!level.getChunkSource().hasChunk(absentChunk.x, absentChunk.z),
                    "WorldScanner must not load an absent chunk from its worker thread");
        } finally {
            restore(level, originalStates);
        }
        helper.succeed();
    }

    private static List<BlockPos> loadedPositions(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int y = origin.getY();
        return List.of(
                blockInChunk(chunkX, chunkZ, y),
                blockInChunk(chunkX + 1, chunkZ, y),
                blockInChunk(chunkX + 2, chunkZ, y)
        );
    }

    private static BlockPos absentPosition(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        return blockInChunk((origin.getX() >> 4) + 128, (origin.getZ() >> 4) + 128, origin.getY());
    }

    private static BlockPos blockInChunk(int chunkX, int chunkZ, int y) {
        return new BlockPos((chunkX << 4) + 1, y, (chunkZ << 4) + 1);
    }

    private static Map<BlockPos, BlockState> originalStates(ServerLevel level, List<BlockPos> positions) {
        return positions.stream().collect(java.util.stream.Collectors.toMap(
                position -> position,
                level::getBlockState
        ));
    }

    private static void seedLoadedChunks(ServerLevel level, List<BlockPos> positions, List<BlockState> states) {
        for (int index = 0; index < positions.size(); index++) {
            BlockPos position = positions.get(index);
            ChunkPos chunkPos = new ChunkPos(position);
            level.getChunk(chunkPos.x, chunkPos.z);
            LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
            if (chunk == null) {
                throw new AssertionError("Unable to seed a loaded chunk for the off-thread access regression");
            }
            level.setBlock(position, states.get(index), 3);
        }
    }

    private static IPlayerContext context(ServerLevel level, BlockPos playerFeet) {
        BetterBlockPos feet = new BetterBlockPos(playerFeet);
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "world" -> level;
                    case "worldData" -> (IWorldData) null;
                    case "playerFeet" -> feet;
                    default -> throw new UnsupportedOperationException(method.toString());
                }
        );
    }

    private static <T> T awaitWorker(Callable<T> action) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "off-thread-loaded-chunk-access");
            thread.setDaemon(true);
            return thread;
        });
        Future<T> result = executor.submit(action);
        try {
            return result.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            result.cancel(true);
            throw new AssertionError("The worker waited for the server thread while reading an already-loaded chunk", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("The worker failed while reading an already-loaded chunk", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the worker chunk lookup", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originalStates) {
        originalStates.forEach((position, state) -> level.setBlock(position, state, 3));
    }

    private record BlockStateRead(
            List<BlockState> loadedStates,
            BlockState absentState,
            boolean loadedChunkReportedLoaded,
            boolean absentChunkReportedLoaded
    ) {
    }

    private record ScanResult(List<BlockPos> loadedMatches, List<BlockPos> absentMatches) {
    }
}
