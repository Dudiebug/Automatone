package baritone.process;

import baritone.Baritone;
import baritone.api.process.IMineProcess.TerminationReason;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MineProcessTerminationTest {

    @Test
    public void cancellationResetAndDirectNullFilterCancellationExposeStableReasons() {
        MineProcess process = new MineProcess(null, emptyContext());
        BlockOptionalMetaLookup filter = emptyFilter();

        assertEquals(Optional.empty(), process.terminationReason());
        process.mine(filter);
        assertTrue(process.isActive());
        assertEquals(Optional.empty(), process.terminationReason());

        process.cancel();
        assertFalse(process.isActive());
        assertEquals(Optional.of(TerminationReason.CANCELLED), process.terminationReason());

        process.cancel();
        process.onLostControl();
        process.mine(0, (BlockOptionalMetaLookup) null);
        assertEquals(Optional.of(TerminationReason.CANCELLED), process.terminationReason());

        process.mine(filter);
        assertTrue(process.isActive());
        assertEquals(Optional.empty(), process.terminationReason());

        process.mine(0, (BlockOptionalMetaLookup) null);
        assertFalse(process.isActive());
        assertEquals(Optional.of(TerminationReason.CANCELLED), process.terminationReason());
    }

    @Test(timeout = 10_000L)
    public void asyncFailurePublishesInternalFailureAndStaleFailureCannotOverwriteNewRun() throws Exception {
        MineProcess process = new MineProcess(null, emptyContext());
        BlockOptionalMetaLookup filter = emptyFilter();
        Method publishScanResult = MineProcess.class.getDeclaredMethod(
                "publishScanResult", long.class, List.class, RuntimeException.class
        );
        publishScanResult.setAccessible(true);
        Method publishPendingScan = MineProcess.class.getDeclaredMethod("publishPendingScan");
        publishPendingScan.setAccessible(true);
        Field generationField = MineProcess.class.getDeclaredField("scanGeneration");
        generationField.setAccessible(true);

        process.mine(filter);
        long staleGeneration = generationField.getLong(process);
        process.mine(filter);
        long activeGeneration = generationField.getLong(process);

        invokeOnExecutor(publishScanResult, process, staleGeneration,
                new RuntimeException("stale scan failure"));
        publishPendingScan.invoke(process);
        assertTrue(process.isActive());
        assertEquals(Optional.empty(), process.terminationReason());

        invokeOnExecutor(publishScanResult, process, activeGeneration,
                new RuntimeException("active scan failure"));
        publishPendingScan.invoke(process);
        assertFalse(process.isActive());
        assertEquals(Optional.of(TerminationReason.INTERNAL_FAILURE), process.terminationReason());

        process.cancel();
        process.onLostControl();
        process.mine(0, (BlockOptionalMetaLookup) null);
        assertEquals(Optional.of(TerminationReason.INTERNAL_FAILURE), process.terminationReason());
        process.mine(filter);
        invokeOnExecutor(publishScanResult, process, activeGeneration, new RuntimeException("late failure"));
        publishPendingScan.invoke(process);
        assertTrue(process.isActive());
        assertEquals(Optional.empty(), process.terminationReason());
    }

    private static void invokeOnExecutor(
            Method publishScanResult,
            MineProcess process,
            long generation,
            RuntimeException failure
    ) throws Exception {
        CompletableFuture<Void> completion = CompletableFuture.runAsync(() -> {
            try {
                publishScanResult.invoke(process, generation, List.of(), failure);
            } catch (ReflectiveOperationException ex) {
                throw new LinkageError("Unable to invoke MineProcess completion helper", ex);
            }
        }, Baritone.getExecutor());
        completion.get();
    }

    private static BlockOptionalMetaLookup emptyFilter() {
        return new BlockOptionalMetaLookup(new BlockOptionalMeta[0]);
    }

    private static IPlayerContext emptyContext() {
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> null
        );
    }
}
