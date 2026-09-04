package baritone.process;

import baritone.Baritone;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.IPlayerContext;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MineProcessLifecycleTest {

    @Test
    public void nativeMineProcessActivatesAndCancelsWithoutClientRuntime() {
        BlockOptionalMetaLookup filter = new BlockOptionalMetaLookup(new BlockOptionalMeta[0]);
        MineProcess process = new MineProcess(null, emptyContext());

        assertFalse(process.isActive());

        process.mine(filter);
        assertTrue(process.isActive());

        process.cancel();
        assertFalse(process.isActive());
    }

    @Test
    public void baritoneExposesTheNativeMineProcess() throws Exception {
        assertEquals(MineProcess.class, Baritone.class.getMethod("getMineProcess").getReturnType());
    }

    private static IPlayerContext emptyContext() {
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> null
        );
    }
}
