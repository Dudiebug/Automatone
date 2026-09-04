package baritone.gametest;

import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.cache.FasterWorldScanner;
import baritone.cache.WorldScanner;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class WorldScannerRepackEquivalenceGameTest {

    @GameTest(template = "provider_smoke")
    public static void repackImplementationsQueueTheSameCenterChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relativeCenter = new BlockPos(0, 1, 0);
        BlockPos center = helper.absolutePos(relativeCenter);
        helper.setBlock(relativeCenter, Blocks.STONE.defaultBlockState());
        helper.assertTrue(level.getBlockState(center).is(Blocks.STONE),
                "The real center chunk must contain registered stone");

        LevelChunk liveCenterChunk = level.getChunkSource().getChunk(center.getX() >> 4, center.getZ() >> 4, false);
        helper.assertTrue(liveCenterChunk != null, "The seeded center chunk must already be loaded");
        BetterBlockPos playerFeet = new BetterBlockPos(center);
        List<LevelChunk> queuedChunks = new ArrayList<>();
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
        IPlayerContext context = (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "world" -> level;
                    case "playerFeet" -> playerFeet;
                    case "worldData" -> worldData;
                    default -> throw new UnsupportedOperationException(method.toString());
                });

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
}
