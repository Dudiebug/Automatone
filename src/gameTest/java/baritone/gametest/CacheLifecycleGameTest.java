package baritone.gametest;

import baritone.Baritone;
import baritone.cache.CachedWorld;
import baritone.cache.WorldData;
import baritone.api.BaritoneAPI;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class CacheLifecycleGameTest {

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void worldDataCloseStopsCacheWorkers(GameTestHelper helper) {
        Path root = null;
        WorldData worldData = null;
        try {
            root = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "m1-5-cache-lifecycle-");
            worldData = newWorldData(root, helper.getLevel());
            CachedWorld cache = worldData.cache;
            List<FutureTask<?>> workers = workerTasks(cache);
            helper.assertTrue(workers.size() == 2, "Cache must own exactly two worker tasks");
            helper.assertTrue(workers.stream().noneMatch(FutureTask::isDone),
                    "Cache workers must be live or queued before close");

            LevelChunk chunk = helper.getLevel().getChunk(0, 0);
            cache.queueForPacking(chunk);
            Path cacheRoot = root;
            WorldData data = worldData;
            helper.runAfterDelay(2, () -> awaitPacked(helper, data, cacheRoot, chunk, 60));
        } catch (IOException ex) {
            closeAndDelete(worldData, null, root);
            throw new AssertionError("Unable to create cache lifecycle test directory", ex);
        } catch (RuntimeException | Error ex) {
            closeAndDelete(worldData, null, root);
            throw ex;
        }
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void cacheSaveAndCloseDoNotInvertProviderLock(GameTestHelper helper) {
        Path root = null;
        WorldData worldData = null;
        Thread saveThread = null;
        Thread closeThread = null;
        boolean originalChunkCaching = Baritone.settings().chunkCaching.value;
        boolean originalPruneRegionsFromRam = Baritone.settings().pruneRegionsFromRAM.value;
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch saveFinished = new CountDownLatch(1);
        CountDownLatch operationsFinished = new CountDownLatch(2);
        try {
            Baritone.settings().chunkCaching.value = true;
            Baritone.settings().pruneRegionsFromRAM.value = true;
            root = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "m1-5-cache-lock-");
            worldData = newWorldData(root, helper.getLevel());
            CachedWorld cache = worldData.cache;
            cache.tryLoadFromDisk(0, 0);
            Object provider = BaritoneAPI.getProvider();
            Path cacheRoot = root;
            saveThread = new Thread(() -> {
                saveStarted.countDown();
                try {
                    cache.save();
                } catch (Throwable ex) {
                    workerFailure.compareAndSet(null, ex);
                } finally {
                    saveFinished.countDown();
                    operationsFinished.countDown();
                }
            }, "m1-5-cache-save-lock-test");
            closeThread = new Thread(() -> {
                try {
                    cache.close();
                } catch (Throwable ex) {
                    workerFailure.compareAndSet(null, ex);
                } finally {
                    operationsFinished.countDown();
                }
            }, "m1-5-cache-close-lock-test");

            boolean completedWhileProviderHeld;
            synchronized (provider) {
                saveThread.start();
                await(saveStarted, "Cache save thread did not start");
                awaitSaveToReachPrune(saveThread, saveFinished);
                closeThread.start();
                completedWhileProviderHeld = await(operationsFinished, 2, TimeUnit.SECONDS);
            }
            join(saveThread, 2, TimeUnit.SECONDS);
            join(closeThread, 2, TimeUnit.SECONDS);
            helper.assertTrue(completedWhileProviderHeld,
                    "Cache save and close must finish while the provider monitor is held");
            helper.assertTrue(!saveThread.isAlive() && !closeThread.isAlive(),
                    "Cache save and close threads must terminate after the bounded check");
            if (workerFailure.get() != null) {
                throw new AssertionError("Concurrent cache save/close failed", workerFailure.get());
            }
            helper.assertTrue(Files.exists(cacheRoot.resolve("cache")),
                    "Concurrent cache lifecycle fixture must use the real cache directory");
        } catch (IOException ex) {
            throw new AssertionError("Unable to create cache lock test directory", ex);
        } finally {
            joinQuietly(saveThread);
            joinQuietly(closeThread);
            closeAndDelete(worldData, null, root);
            Baritone.settings().chunkCaching.value = originalChunkCaching;
            Baritone.settings().pruneRegionsFromRAM.value = originalPruneRegionsFromRam;
        }
        helper.succeed();
    }

    private static void awaitSaveToReachPrune(Thread saveThread, CountDownLatch saveFinished) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (saveFinished.getCount() != 0 && saveThread.getState() != Thread.State.BLOCKED) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Cache save did not reach its bounded provider interaction");
            }
            Thread.yield();
        }
    }

    private static void await(CountDownLatch latch, String message) {
        if (!await(latch, 2, TimeUnit.SECONDS)) {
            throw new AssertionError(message);
        }
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for cache lifecycle test", ex);
        }
    }

    private static void join(Thread thread, long timeout, TimeUnit unit) {
        try {
            thread.join(unit.toMillis(timeout));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while joining cache lifecycle test", ex);
        }
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitPacked(
            GameTestHelper helper,
            WorldData worldData,
            Path root,
            LevelChunk chunk,
            int remainingTicks
    ) {
        CachedWorld cache = worldData.cache;
        int centerX = chunk.getPos().x << 4;
        int centerZ = chunk.getPos().z << 4;
        if (!cache.isCached(centerX, centerZ)) {
            if (remainingTicks == 0) {
                try {
                    helper.assertTrue(false, "Queued real chunk was not packed within the bounded window");
                } finally {
                    closeAndDelete(worldData, null, root);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitPacked(helper, worldData, root, chunk, remainingTicks - 1));
            return;
        }

        WorldData recreated = null;
        try {
            List<FutureTask<?>> workers = workerTasks(cache);
            List<CountDownLatch> completions = workerCompletions(workers);
            helper.assertTrue(completions.size() == 2 && completions.stream().allMatch(latch -> latch.getCount() == 1),
                    "Each owned worker must expose a live completion latch before close");
            Path regionFile = root.resolve("cache")
                    .resolve("r." + (chunk.getPos().x >> 5) + "." + (chunk.getPos().z >> 5) + ".bcr");

            worldData.onClose();
            helper.assertTrue(workers.stream().allMatch(FutureTask::isDone),
                    "WorldData.onClose must terminate both cache workers before returning");
            helper.assertTrue(completions.size() == 2 && completions.stream().allMatch(latch -> latch.getCount() == 0),
                    "WorldData.onClose must observe both worker bodies terminated before returning");
            helper.assertTrue(Files.isRegularFile(regionFile),
                    "Closing WorldData must preserve the final cache save");

            int pendingAfterClose = pendingWork(cache);
            cache.queueForPacking(chunk);
            cache.reloadAllFromDisk();
            cache.tryLoadFromDisk((chunk.getPos().x >> 5) + 1, chunk.getPos().z >> 5);
            helper.assertTrue(pendingWork(cache) == pendingAfterClose,
                    "Closed cache must reject new queue work");
            helper.assertTrue(cache.getRegion((chunk.getPos().x >> 5) + 1, chunk.getPos().z >> 5) == null,
                    "Closed cache must reject new region loads");
            helper.assertTrue(cache.getRegion(chunk.getPos().x >> 5, chunk.getPos().z >> 5) == null
                            && !cache.isCached(centerX, centerZ),
                    "Closed cache must fail closed for region reads");
            cache.save();
            worldData.onClose();
            helper.assertTrue(workers.stream().allMatch(FutureTask::isDone),
                    "Repeated WorldData.onClose must remain idempotent");

            int unopenedRegionX = (chunk.getPos().x >> 5) + 1;
            int unopenedRegionZ = chunk.getPos().z >> 5;
            helper.runAfterDelay(5, () -> finishRecreation(
                    helper,
                    worldData,
                    recreated,
                    root,
                    cache,
                    workers,
                    completions,
                    pendingAfterClose,
                    unopenedRegionX,
                    unopenedRegionZ
            ));
            return;
        } catch (RuntimeException | Error ex) {
            throw ex;
        } finally {
            if (recreated != null) {
                closeAndDelete(worldData, recreated, root);
            }
        }
    }

    private static void finishRecreation(
            GameTestHelper helper,
            WorldData worldData,
            WorldData recreated,
            Path root,
            CachedWorld cache,
            List<FutureTask<?>> workers,
            List<CountDownLatch> completions,
            int pendingAfterClose,
            int unopenedRegionX,
            int unopenedRegionZ
    ) {
        try {
            helper.assertTrue(pendingWork(cache) == pendingAfterClose,
                    "Closed cache must not receive stale work after close returns");
            helper.assertTrue(cache.getRegion(unopenedRegionX, unopenedRegionZ) == null,
                    "Closed cache must not create a region after close returns");
            helper.assertTrue(completions.stream().allMatch(latch -> latch.getCount() == 0),
                    "Closed worker bodies must remain terminated during the delayed observation");

            recreated = newWorldData(root, helper.getLevel());
            CachedWorld newCache = recreated.cache;
            List<FutureTask<?>> newWorkers = workerTasks(newCache);
            List<CountDownLatch> newCompletions = workerCompletions(newWorkers);
            helper.assertTrue(newCache != cache, "Recreated WorldData must own a fresh cache");
            helper.assertTrue(newWorkers.size() == 2 && newWorkers.stream().noneMatch(FutureTask::isDone),
                    "Recreated cache must own fresh live or queued workers");
            helper.assertTrue(newCompletions.size() == 2 && newCompletions.stream().allMatch(latch -> latch.getCount() == 1),
                    "Recreated cache must own fresh worker completion latches");
            helper.assertTrue(newWorkers.stream().noneMatch(workers::contains),
                    "Recreated cache must not reuse old worker tasks");
            recreated.onClose();
            helper.assertTrue(newWorkers.stream().allMatch(FutureTask::isDone),
                    "Recreated cache workers must terminate on close");
            helper.assertTrue(newCompletions.stream().allMatch(latch -> latch.getCount() == 0),
                    "Recreated worker bodies must terminate on close");
        } catch (RuntimeException | Error ex) {
            throw ex;
        } finally {
            closeAndDelete(worldData, recreated, root);
        }
        helper.succeed();
    }

    private static WorldData newWorldData(Path root, net.minecraft.server.level.ServerLevel level) {
        try {
            Constructor<WorldData> constructor = WorldData.class.getDeclaredConstructor(Path.class,
                    net.minecraft.world.level.dimension.DimensionType.class);
            constructor.setAccessible(true);
            return constructor.newInstance(root, level.dimensionType());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to construct real package-private WorldData", ex);
        }
    }

    private static List<FutureTask<?>> workerTasks(CachedWorld cache) {
        try {
            List<FutureTask<?>> tasks = new ArrayList<>();
            for (Field field : CachedWorld.class.getDeclaredFields()) {
                if (!FutureTask.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                tasks.add((FutureTask<?>) field.get(cache));
            }
            return tasks;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to observe owned cache worker tasks", ex);
        }
    }

    private static List<CountDownLatch> workerCompletions(List<FutureTask<?>> workers) {
        try {
            List<CountDownLatch> completions = new ArrayList<>();
            for (FutureTask<?> worker : workers) {
                for (Field field : worker.getClass().getDeclaredFields()) {
                    if (field.getType() != CountDownLatch.class) {
                        continue;
                    }
                    field.setAccessible(true);
                    completions.add((CountDownLatch) field.get(worker));
                }
            }
            return completions;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to observe cache worker completion latches", ex);
        }
    }

    private static int pendingWork(CachedWorld cache) {
        try {
            Field queueField = CachedWorld.class.getDeclaredField("toPackQueue");
            Field mapField = CachedWorld.class.getDeclaredField("toPackMap");
            queueField.setAccessible(true);
            mapField.setAccessible(true);
            return ((LinkedBlockingQueue<?>) queueField.get(cache)).size()
                    + ((Map<?, ?>) mapField.get(cache)).size();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to observe closed cache work state", ex);
        }
    }

    private static void closeAndDelete(WorldData first, WorldData second, Path root) {
        try {
            if (second != null) {
                second.onClose();
            }
            if (first != null) {
                first.onClose();
            }
            if (root != null && Files.exists(root)) {
                List<Path> paths;
                try (var stream = Files.walk(root)) {
                    paths = stream.sorted(Comparator.reverseOrder()).toList();
                }
                for (Path path : paths) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException ex) {
            throw new AssertionError("Unable to clean cache lifecycle test directory", ex);
        }
    }
}
