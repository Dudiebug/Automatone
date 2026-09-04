package baritone.command;

import baritone.api.IBaritone;
import baritone.api.command.ICommand;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.event.listener.IEventBus;
import baritone.api.utils.IPlayerContext;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import baritone.command.defaults.FollowCommand;
import baritone.command.defaults.SelCommand;
import baritone.command.defaults.WaypointsCommand;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CommandQualityCleanup20260902RegressionTest {

    @Test
    public void unknownFollowListProducesNoCompletions() throws Exception {
        IBaritone baritone = (IBaritone) Proxy.newProxyInstance(
                IBaritone.class.getClassLoader(),
                new Class<?>[]{IBaritone.class},
                (proxy, method, arguments) -> null
        );
        FollowCommand command = new FollowCommand(baritone);
        ArgConsumer arguments = new ArgConsumer(
                null,
                CommandArguments.from("unknown entity")
        );

        assertFalse(command.tabComplete("follow", arguments).findAny().isPresent());
    }

    @Test
    public void selAndWaypointsKeepAliasesCaseInsensitive() throws Exception {
        IBaritone baritone = commandBaritone();
        SelCommand sel = new SelCommand(baritone);
        WaypointsCommand waypoints = new WaypointsCommand(baritone);

        assertEquals(
                completions(sel, "SHIFT a"),
                completions(sel, "SH a")
        );
        assertEquals(Arrays.asList("a", "all"), completions(sel, "SH a"));
        assertEquals(
                completions(waypoints, "LIST h"),
                completions(waypoints, "lIsT h")
        );
        assertEquals(Arrays.asList("home"), completions(waypoints, "lIsT h"));
        assertTrue(completions(waypoints, "g").containsAll(Arrays.asList("get", "goal", "goto")));
    }

    @Test
    public void argConsumerAccessorsRemainLive() throws Exception {
        ArgConsumer arguments = new ArgConsumer(null, CommandArguments.from("one two"));
        List<ICommandArgument> argsView = arguments.getArgs();
        Deque<ICommandArgument> consumedView = arguments.getConsumed();
        ICommandArgument pending = argsView.get(0);
        ICommandArgument consumed = arguments.get();

        assertSame(arguments.getArgs(), arguments.getArgs());
        assertSame(argsView, arguments.getArgs());
        assertSame(consumedView, arguments.getConsumed());
        assertSame(pending, consumed);
        assertFalse(argsView.contains(consumed));
        assertTrue(arguments.getConsumed().contains(consumed));
        assertTrue(consumedView.contains(consumed));
    }

    private static IArgConsumer arguments(String input) {
        return new ArgConsumer(null, CommandArguments.from(input));
    }

    private static List<String> completions(ICommand command, String input) throws Exception {
        try (Stream<String> stream = command.tabComplete(command.getNames().get(0), arguments(input))) {
            return stream.toList();
        }
    }

    private static IBaritone commandBaritone() {
        IEventBus eventBus = proxy(IEventBus.class, (proxy, method, arguments) -> null);
        IPlayerContext playerContext = proxy(IPlayerContext.class, (proxy, method, arguments) ->
                defaultValue(method.getReturnType()));
        return proxy(IBaritone.class, (proxy, method, arguments) -> {
            switch (method.getName()) {
                case "getGameEventHandler":
                    return eventBus;
                case "getPlayerContext":
                    return playerContext;
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
