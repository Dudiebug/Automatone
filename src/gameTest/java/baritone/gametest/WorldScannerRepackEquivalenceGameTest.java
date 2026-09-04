package baritone.gametest;

import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import baritone.cache.FasterWorldScanner;
import baritone.cache.WorldScanner;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class WorldScannerRepackEquivalenceGameTest {

    @GameTest(template = "provider_smoke", batch = "world_scanner_parity")
    public static void repackImplementationsQueueTheSameCenterChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relativeCenter = new BlockPos(0, 1, 0);
        BlockPos center = helper.absolutePos(relativeCenter);
        helper.setBlock(relativeCenter, Blocks.STONE.defaultBlockState());
        helper.assertTrue(level.getBlockState(center).is(Blocks.STONE),
                "The real center chunk must contain registered stone");

        LevelChunk liveCenterChunk = level.getChunkSource().getChunk(center.getX() >> 4, center.getZ() >> 4, false);
        helper.assertTrue(liveCenterChunk != null, "The seeded center chunk must already be loaded");
        List<LevelChunk> queuedChunks = new ArrayList<>();
        IPlayerContext context = scannerContext(level, center, queuedChunks);

        int worldScannerQueued = WorldScanner.INSTANCE.repack(context, 0);
        helper.assertTrue(worldScannerQueued == 1, "WorldScanner must queue exactly one center chunk");
        helper.assertTrue(queuedChunks.size() == 1 && queuedChunks.get(0) == liveCenterChunk,
                "WorldScanner must queue the live center chunk");

        queuedChunks.clear();
        int fasterScannerQueued = FasterWorldScanner.INSTANCE.repack(context, 0);
        helper.assertTrue(fasterScannerQueued == 1, "FasterWorldScanner must queue exactly one center chunk");
        helper.assertTrue(queuedChunks.size() == 1 && queuedChunks.get(0) == liveCenterChunk,
                "FasterWorldScanner must queue the same live center chunk");

        queuedChunks.clear();
        helper.assertTrue(WorldScanner.INSTANCE.repack(context, -1) == 0 && queuedChunks.isEmpty(),
                "WorldScanner must reject a negative range without queueing");
        helper.assertTrue(FasterWorldScanner.INSTANCE.repack(context, -1) == 0 && queuedChunks.isEmpty(),
                "FasterWorldScanner must reject a negative range without queueing");
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "world_scanner_parity")
    public static void positiveRangesQueueExactlyTheBaselineLoadedSquare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos playerFeet = helper.absolutePos(new BlockPos(1, 1, 1));
        ChunkPos center = new ChunkPos(playerFeet.getX() >> 4, playerFeet.getZ() >> 4);
        List<ChunkPos> seededOffsets = List.of(
                new ChunkPos(-2, -2),
                new ChunkPos(-2, 0),
                new ChunkPos(-1, -1),
                new ChunkPos(-1, 0),
                new ChunkPos(0, -1),
                new ChunkPos(0, 0),
                new ChunkPos(0, 1),
                new ChunkPos(1, 0),
                new ChunkPos(1, 1),
                new ChunkPos(2, 0),
                new ChunkPos(2, 2),
                new ChunkPos(3, 0)
        );
        Map<BlockPos, BlockState> originalStates = new LinkedHashMap<>();
        try {
            for (ChunkPos offset : seededOffsets) {
                BlockPos position = blockInChunk(center, offset, 1);
                originalStates.put(position, level.getBlockState(position));
                level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
                LevelChunk chunk = level.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false);
                helper.assertTrue(chunk != null && !chunk.isEmpty(),
                        "Every seeded scanner parity chunk must be loaded and non-empty");
            }

            for (int range : List.of(1, 2)) {
                Set<ChunkPos> loadedNonEmpty = loadedNonEmptyChunks(level, center, range);
                List<ChunkPos> expected = baselineRepackOrder(center, range, loadedNonEmpty);
                helper.assertTrue(!expected.isEmpty(),
                        "Baseline must observe at least one loaded non-empty chunk in the tested square");
                List<LevelChunk> queuedChunks = new ArrayList<>();
                IPlayerContext context = scannerContext(level, playerFeet, queuedChunks);

                int worldScannerQueued = WorldScanner.INSTANCE.repack(context, range);
                helper.assertTrue(worldScannerQueued == expected.size()
                                && queuedPositions(queuedChunks).equals(expected),
                        "WorldScanner positive range must match baseline square order for range " + range);

                queuedChunks.clear();
                int fasterScannerQueued = FasterWorldScanner.INSTANCE.repack(context, range);
                helper.assertTrue(fasterScannerQueued == expected.size()
                                && queuedPositions(queuedChunks).equals(expected),
                        "FasterWorldScanner positive range must match baseline square order for range " + range);
            }
        } finally {
            restore(level, originalStates);
        }
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "world_scanner_parity")
    public static void scanImplementationsMatchFrozenNegativeHeightSectionsAndLimits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> relativeTargets = List.of(
                new BlockPos(1, 0, 1),
                new BlockPos(1, 16, 1),
                new BlockPos(1, 32, 1),
                new BlockPos(1, 112, 1)
        );
        Map<BlockPos, BlockState> originalStates = new LinkedHashMap<>();
        BlockPos relativePlayerFeet = new BlockPos(1, 32, 1);
        BlockPos playerFeet = helper.absolutePos(relativePlayerFeet);
        ChunkPos targetChunk = new ChunkPos(playerFeet.getX() >> 4, playerFeet.getZ() >> 4);
        List<BlockPos> targets = relativeTargets.stream().map(helper::absolutePos).toList();
        try {
            for (BlockPos target : targets) {
                helper.assertTrue(new ChunkPos(target.getX() >> 4, target.getZ() >> 4).equals(targetChunk),
                        "Every scanner parity target must be in the scanned target chunk");
            }
            for (BlockPos relativeTarget : relativeTargets) {
                BlockPos target = helper.absolutePos(relativeTarget);
                originalStates.put(target, level.getBlockState(target));
                helper.setBlock(relativeTarget, Blocks.DIAMOND_BLOCK.defaultBlockState());
            }

            BlockOptionalMetaLookup filter = new BlockOptionalMetaLookup(Blocks.DIAMOND_BLOCK);
            List<LevelChunk> ignoredQueue = new ArrayList<>();
            IPlayerContext context = scannerContext(level, playerFeet, ignoredQueue);
            List<BlockPos> frozenWorldOrder = List.of(targets.get(0), targets.get(1), targets.get(2), targets.get(3));
            List<BlockPos> frozenFasterOrder = List.of(targets.get(2), targets.get(1), targets.get(0), targets.get(3));

            List<BlockPos> worldResult = WorldScanner.INSTANCE.scanChunk(context, filter, targetChunk, -1, -1);
            helper.assertTrue(worldResult.equals(frozenWorldOrder),
                    "WorldScanner must preserve frozen section order and negative-Y offset; expected "
                            + frozenWorldOrder + " but was " + worldResult);
            List<BlockPos> fasterResult = FasterWorldScanner.INSTANCE.scanChunk(
                    context, filter, targetChunk, -1, -1
            );
            helper.assertTrue(fasterResult.equals(frozenFasterOrder),
                    "FasterWorldScanner must preserve frozen player-relative section order; expected "
                            + frozenFasterOrder + " but was " + fasterResult);

            List<BlockPos> worldLimited = WorldScanner.INSTANCE.scanChunk(context, filter, targetChunk, 2, 20);
            List<BlockPos> expectedWorldLimited = List.of(targets.get(0), targets.get(1), targets.get(2));
            helper.assertTrue(worldLimited.equals(expectedWorldLimited),
                    "WorldScanner must apply the Y-threshold cutoff in world coordinates; expected "
                            + expectedWorldLimited + " but was " + worldLimited);
            List<BlockPos> fasterLimited = FasterWorldScanner.INSTANCE.scanChunk(
                    context, filter, targetChunk, 2, 20
            );
            helper.assertTrue(fasterLimited.equals(List.of(targets.get(2), targets.get(1))),
                    "FasterWorldScanner must preserve frozen max limit behavior; expected "
                            + List.of(targets.get(2), targets.get(1)) + " but was " + fasterLimited);
        } finally {
            restore(level, originalStates);
        }
        helper.succeed();
    }

    static IPlayerContext scannerContext(ServerLevel level, BlockPos playerFeet, List<LevelChunk> queuedChunks) {
        BetterBlockPos feet = new BetterBlockPos(playerFeet);
        ICachedWorld cachedWorld = (ICachedWorld) Proxy.newProxyInstance(
                ICachedWorld.class.getClassLoader(),
                new Class<?>[]{ICachedWorld.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("queueForPacking")) {
                        queuedChunks.add((LevelChunk) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        IWorldData worldData = (IWorldData) Proxy.newProxyInstance(
                IWorldData.class.getClassLoader(),
                new Class<?>[]{IWorldData.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCachedWorld")) {
                        return cachedWorld;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "world" -> level;
                    case "playerFeet" -> feet;
                    case "worldData" -> worldData;
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Set<ChunkPos> loadedNonEmptyChunks(ServerLevel level, ChunkPos center, int range) {
        Set<ChunkPos> loadedNonEmpty = new HashSet<>();
        for (int x = center.x - range; x <= center.x + range; x++) {
            for (int z = center.z - range; z <= center.z + range; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null && !chunk.isEmpty()) {
                    loadedNonEmpty.add(chunk.getPos());
                }
            }
        }
        return loadedNonEmpty;
    }

    private static List<ChunkPos> baselineRepackOrder(ChunkPos center, int range, Set<ChunkPos> loadedNonEmpty) {
        List<ChunkPos> expected = new ArrayList<>();
        for (int x = center.x - range; x <= center.x + range; x++) {
            for (int z = center.z - range; z <= center.z + range; z++) {
                ChunkPos candidate = new ChunkPos(x, z);
                if (loadedNonEmpty.contains(candidate)) {
                    expected.add(candidate);
                }
            }
        }
        return expected;
    }

    private static List<ChunkPos> queuedPositions(List<LevelChunk> queuedChunks) {
        return queuedChunks.stream().map(LevelChunk::getPos).toList();
    }

    private static BlockPos blockInChunk(ChunkPos center, ChunkPos offset, int y) {
        return new BlockPos((center.x + offset.x) * 16 + 1, y, (center.z + offset.z) * 16 + 1);
    }

    static void restore(ServerLevel level, Map<BlockPos, BlockState> originalStates) {
        for (Map.Entry<BlockPos, BlockState> entry : originalStates.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }
}
