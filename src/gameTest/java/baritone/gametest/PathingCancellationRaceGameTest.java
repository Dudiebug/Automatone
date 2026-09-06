package baritone.gametest;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.IPlayerController;
import baritone.behavior.PathingBehavior;
import baritone.pathing.calc.AStarPathFinder;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.CalculationContext;
import baritone.utils.pathing.Favoring;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class PathingCancellationRaceGameTest {

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void cancelledCalculationCannotClearReplacement(GameTestHelper helper) {
        Path root = null;
        Baritone baritone = null;
        ArmorStand host = null;
        Thread completionThread = null;
        AtomicReference<Throwable> completionFailure = new AtomicReference<>();
        try {
            ServerLevel level = helper.getLevel();
            BlockPos hostPosition = helper.absolutePos(new BlockPos(0, 1, 0));
            host = new ArmorStand(level, hostPosition.getX() + 0.5D, hostPosition.getY(), hostPosition.getZ() + 0.5D);
            host.setNoGravity(true);
            level.addFreshEntity(host);

            root = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "pathing-cancel-race-");
            AtomicReference<Baritone> runtime = new AtomicReference<>();
            baritone = new Baritone(playerContext(host, level, runtime), root.resolve("runtime"));
            runtime.set(baritone);
            baritone.getWorldProvider().getCurrentWorld();

            PathingBehavior behavior = baritone.getPathingBehavior();
            BetterBlockPos start = new BetterBlockPos(hostPosition);
            Goal goal = new GoalBlock(start);
            CalculationContext context = new CalculationContext(baritone, true);
            AbstractNodeCostSearch stale = new AStarPathFinder(
                    start,
                    start.x,
                    start.y,
                    start.z,
                    goal,
                    new Favoring(null, context),
                    context
            );
            AbstractNodeCostSearch replacement = new AStarPathFinder(
                    start,
                    start.x,
                    start.y,
                    start.z,
                    goal,
                    new Favoring(null, context),
                    context
            );
            setField(behavior, "expectedSegmentStart", start);
            setField(behavior, "inProgress", stale);

            Method completion = asyncCompletionMethod();
            completionThread = new Thread(() -> invokeAsyncCompletion(
                    completion,
                    behavior,
                    start,
                    goal,
                    stale,
                    context,
                    completionFailure
            ), "pathing-cancellation-race");

            Object planLock = getField(behavior, "pathPlanLock");
            synchronized (planLock) {
                completionThread.start();
                awaitCalculationFinished(stale);
                behavior.secretInternalSegmentCancel();
                setField(behavior, "inProgress", replacement);
            }

            join(completionThread);
            completionThread = null;
            helper.assertTrue(completionFailure.get() == null,
                    "The production completion path must complete without an exception: " + completionFailure.get());
            helper.assertTrue(replacement.equals(getField(behavior, "inProgress")),
                    "A cancelled calculation completing late must not clear a replacement calculation");
            helper.assertTrue(behavior.getCurrent() == null && behavior.getNext() == null,
                    "A cancelled calculation completing late must not publish a stale path");
        } catch (IOException | ReflectiveOperationException ex) {
            throw new AssertionError("Unable to exercise native path calculation cancellation publication", ex);
        } finally {
            if (completionThread != null) {
                join(completionThread);
            }
            if (baritone != null) {
                baritone.dispose();
            }
            if (host != null && !host.isRemoved()) {
                host.remove(Entity.RemovalReason.DISCARDED);
            }
            deleteRecursively(root);
        }
        helper.succeed();
    }

    private static Method asyncCompletionMethod() throws NoSuchMethodException {
        return Arrays.stream(PathingBehavior.class.getDeclaredMethods())
                .filter(method -> method.isSynthetic()
                        && method.getName().startsWith("lambda$findPathInNewThread$")
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{
                                boolean.class,
                                BlockPos.class,
                                Goal.class,
                                AbstractNodeCostSearch.class,
                                long.class,
                                long.class,
                                CalculationContext.class
                        }))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("PathingBehavior async completion closure"));
    }

    private static void invokeAsyncCompletion(
            Method completion,
            PathingBehavior behavior,
            BetterBlockPos start,
            Goal goal,
            AbstractNodeCostSearch stale,
            CalculationContext context,
            AtomicReference<Throwable> completionFailure
    ) {
        try {
            completion.setAccessible(true);
            completion.invoke(behavior, false, start, goal, stale, 1_000L, 1_000L, context);
        } catch (InvocationTargetException ex) {
            completionFailure.set(ex.getCause());
        } catch (ReflectiveOperationException ex) {
            completionFailure.set(ex);
        }
    }

    private static IPlayerContext playerContext(
            ArmorStand host,
            ServerLevel level,
            AtomicReference<Baritone> runtime
    ) {
        SimpleContainer inventory = new SimpleContainer(9);
        IPlayerController controller = (IPlayerController) java.lang.reflect.Proxy.newProxyInstance(
                IPlayerController.class.getClassLoader(),
                new Class<?>[]{IPlayerController.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        return new IPlayerContext() {
            @Override
            public ArmorStand player() {
                return host;
            }

            @Override
            public Container inventory() {
                return inventory;
            }

            @Override
            public int selectedSlot() {
                return 0;
            }

            @Override
            public void setSelectedSlot(int slot) {
            }

            @Override
            public IPlayerController playerController() {
                return controller;
            }

            @Override
            public Level world() {
                return level;
            }

            @Override
            @Nullable
            public baritone.api.cache.IWorldData worldData() {
                Baritone current = runtime.get();
                return current == null ? null : current.getWorldProvider().getCurrentWorld();
            }

            @Override
            public HitResult objectMouseOver() {
                return null;
            }
        };
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

    private static void awaitCalculationFinished(AbstractNodeCostSearch calculation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!calculation.isFinished()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("The real native calculation did not finish while publication was blocked");
            }
            Thread.onSpinWait();
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for path calculation completion", ex);
        }
        if (thread.isAlive()) {
            throw new AssertionError("Path calculation completion thread did not finish");
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
            throw new AssertionError("Unable to remove isolated pathing cancellation fixture directory", ex);
        }
    }
}
