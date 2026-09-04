package baritone;

import baritone.api.IBaritone;
import baritone.api.utils.IPlayerContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BaritoneProviderLifecycleTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void runtimeIsExplicitlyOwnedTickedAndDisposed() throws Exception {
        IPlayerContext context = emptyContext();
        AtomicBoolean hostAvailable = new AtomicBoolean(true);
        AtomicBoolean disposed = new AtomicBoolean();
        AtomicInteger ticks = new AtomicInteger();
        BaritoneProvider provider = new BaritoneProvider((host, path) -> testRuntime(
                host, hostAvailable, disposed, ticks
        ));

        IBaritone runtime = provider.createBaritone(context, temporaryFolder.newFolder("runtime").toPath());

        assertSame(runtime, provider.createBaritone(context, temporaryFolder.getRoot().toPath()));
        assertSame(runtime, provider.getBaritoneForContext(context));
        assertEquals(1, provider.getAllBaritones().size());

        provider.tick();
        assertEquals(1, ticks.get());
        assertFalse(runtime.isDisposed());

        hostAvailable.set(false);
        provider.tick();
        assertTrue(runtime.isDisposed());
        assertTrue(provider.getAllBaritones().isEmpty());
        assertFalse(provider.destroyBaritone(runtime));

        provider.disposeAll();
    }

    private static IBaritone testRuntime(
            IPlayerContext context,
            AtomicBoolean hostAvailable,
            AtomicBoolean disposed,
            AtomicInteger ticks
    ) {
        return (IBaritone) Proxy.newProxyInstance(
                IBaritone.class.getClassLoader(),
                new Class<?>[]{IBaritone.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayerContext" -> context;
                    case "tick" -> {
                        ticks.incrementAndGet();
                        yield null;
                    }
                    case "dispose" -> {
                        disposed.set(true);
                        yield null;
                    }
                    case "isDisposed" -> disposed.get();
                    case "isHostAvailable" -> hostAvailable.get();
                    case "toString" -> "test-runtime";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static IPlayerContext emptyContext() {
        return (IPlayerContext) Proxy.newProxyInstance(
                IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "empty-context";
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return null;
    }
}
