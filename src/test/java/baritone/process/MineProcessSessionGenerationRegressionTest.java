package baritone.process;

import baritone.Baritone;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MineProcessSessionGenerationRegressionTest {

    @Test(timeout = 10_000L)
    public void concurrentSessionStartsRetainEveryGeneration() throws Exception {
        MineProcess process = new MineProcess(null, emptyContext());
        BlockOptionalMetaLookup filter = new BlockOptionalMetaLookup(new BlockOptionalMeta[0]);
        int callerCount = 8;
        int startsPerCaller = 2_000;
        CountDownLatch ready = new CountDownLatch(callerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(callerCount);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int caller = 0; caller < callerCount; caller++) {
                futures.add(callers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int i = 0; i < startsPerCaller; i++) {
                        process.mine(0, filter);
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            callers.shutdownNow();
        }

        Field generation = MineProcess.class.getDeclaredField("scanGeneration");
        generation.setAccessible(true);
        assertEquals((long) callerCount * startsPerCaller, generation.getLong(process));
    }

    @Test(timeout = 10_000L)
    public void staleSuccessAndFailureCannotReplaceNewerCompletion() throws Exception {
        // Protocol-only control: the real executor and completion helper are used, but no test-only pause is
        // inserted between the generation check and mailbox write, so this does not reproduce that exact interleaving.
        MineProcess process = new MineProcess(null, emptyContext());
        BlockOptionalMetaLookup filter = new BlockOptionalMetaLookup(new BlockOptionalMeta[0]);
        Method publishScanResult = MineProcess.class.getDeclaredMethod(
                "publishScanResult", long.class, List.class, RuntimeException.class
        );
        publishScanResult.setAccessible(true);
        Method publishPendingScan = MineProcess.class.getDeclaredMethod("publishPendingScan");
        publishPendingScan.setAccessible(true);
        Field generationField = MineProcess.class.getDeclaredField("scanGeneration");
        generationField.setAccessible(true);
        Field locationsField = MineProcess.class.getDeclaredField("knownOreLocations");
        locationsField.setAccessible(true);
        Field scanInFlightField = MineProcess.class.getDeclaredField("scanInFlight");
        scanInFlightField.setAccessible(true);

        process.mine(0, filter);
        long staleSuccessGeneration = generationField.getLong(process);
        process.onLostControl();
        process.mine(0, filter);
        long activeSuccessGeneration = generationField.getLong(process);
        BlockPos activeSuccess = new BlockPos(2, 3, 4);
        scanInFlightField.setBoolean(process, true);
        invokeOnExecutor(publishScanResult, process, activeSuccessGeneration, List.of(activeSuccess), null);
        invokeOnExecutor(publishScanResult, process, staleSuccessGeneration,
                List.of(new BlockPos(8, 9, 10)), null);
        publishPendingScan.invoke(process);
        assertEquals(List.of(activeSuccess), locationsField.get(process));
        assertTrue(!scanInFlightField.getBoolean(process));

        process.mine(0, filter);
        long staleFailureGeneration = generationField.getLong(process);
        process.mine(0, filter);
        long activeFailureGeneration = generationField.getLong(process);
        BlockPos activeFailure = new BlockPos(5, 6, 7);
        scanInFlightField.setBoolean(process, true);
        invokeOnExecutor(publishScanResult, process, activeFailureGeneration, List.of(activeFailure), null);
        invokeOnExecutor(publishScanResult, process, staleFailureGeneration, List.of(),
                new IllegalStateException("stale scan failure"));
        publishPendingScan.invoke(process);
        assertEquals(List.of(activeFailure), locationsField.get(process));
    }

    private static void invokeOnExecutor(
            Method publishScanResult,
            MineProcess process,
            long generation,
            List<BlockPos> locations,
            RuntimeException failure
    ) throws Exception {
        CompletableFuture<Void> completion = CompletableFuture.runAsync(() -> {
            try {
                publishScanResult.invoke(process, generation, locations, failure);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("Unable to invoke MineProcess completion helper", ex);
            }
        }, Baritone.getExecutor());
        completion.get(5, TimeUnit.SECONDS);
    }

    private static IPlayerContext emptyContext() {
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> null
        );
    }
}
