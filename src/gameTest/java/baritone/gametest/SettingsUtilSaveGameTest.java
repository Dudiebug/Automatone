package baritone.gametest;

import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class SettingsUtilSaveGameTest {

    private static final Object TEST_LOCK = new Object();

    private SettingsUtilSaveGameTest() {
    }

    @GameTest(template = "provider_smoke", batch = "settings_save", timeoutTicks = 100)
    public static void publicClassMonitorDoesNotBlockSave(GameTestHelper helper) {
        synchronized (TEST_LOCK) {
            Path isolatedGameDirectory = null;
            Path originalGameDirectory = FMLPaths.GAMEDIR.get();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Settings settings = null;
            try {
                isolatedGameDirectory = Files.createTempDirectory(originalGameDirectory, "settings-public-monitor-");
                FMLPaths.loadAbsolutePaths(isolatedGameDirectory);
                settings = newSettings();
                settings.ticksBetweenInventoryMoves.value = 7;
                Settings saveSettings = settings;
                CountDownLatch started = new CountDownLatch(1);
                Future<?> save;
                synchronized (SettingsUtil.class) {
                    save = executor.submit(() -> {
                        started.countDown();
                        SettingsUtil.save(saveSettings);
                    });
                    helper.assertTrue(await(started, 2, TimeUnit.SECONDS), "save did not start");
                    helper.assertTrue(awaitFuture(save, 2, TimeUnit.SECONDS),
                            "save waited for the public SettingsUtil.class monitor");
                }

                Path settingsFile = isolatedGameDirectory.resolve("baritone").resolve(SettingsUtil.SETTINGS_DEFAULT_NAME);
                helper.assertTrue(Files.exists(settingsFile), "save did not create the settings file");
                helper.assertTrue(Files.readString(settingsFile, StandardCharsets.UTF_8)
                        .contains("ticksBetweenInventoryMoves 7"), "save did not persist the changed setting");
                helper.succeed();
            } catch (IOException ex) {
                throw new AssertionError("Unable to create SettingsUtil save fixture", ex);
            } finally {
                shutdown(executor);
                restoreSettings(settings);
                restorePaths(originalGameDirectory);
                deleteRecursively(isolatedGameDirectory);
            }
        }
    }

    @GameTest(template = "provider_smoke", batch = "settings_save", timeoutTicks = 100)
    public static void parallelSavesAreSerializedAndComplete(GameTestHelper helper) {
        synchronized (TEST_LOCK) {
            Path isolatedGameDirectory = null;
            Path originalGameDirectory = FMLPaths.GAMEDIR.get();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Settings first = null;
            Settings second = null;
            try {
                isolatedGameDirectory = Files.createTempDirectory(originalGameDirectory, "settings-parallel-");
                FMLPaths.loadAbsolutePaths(isolatedGameDirectory);
                CountDownLatch firstEntered = new CountDownLatch(1);
                CountDownLatch releaseFirst = new CountDownLatch(1);
                CountDownLatch secondEntered = new CountDownLatch(1);
                first = newSettings();
                first.chatDebug.value = true;
                first.colorCurrentPath.value = new BlockingColor(Color.RED, firstEntered, releaseFirst);
                second = newSettings();
                second.chatDebug.value = true;
                second.colorCurrentPath.value = new BlockingColor(Color.BLUE, secondEntered, new CountDownLatch(0));
                Settings firstSettings = first;
                Settings secondSettings = second;

                Future<?> firstSave = executor.submit(() -> SettingsUtil.save(firstSettings));
                helper.assertTrue(await(firstEntered, 2, TimeUnit.SECONDS),
                        "first save did not reach serialization");
                Future<?> secondSave = executor.submit(() -> SettingsUtil.save(secondSettings));
                helper.assertFalse(await(secondEntered, 200, TimeUnit.MILLISECONDS),
                        "parallel save entered before the first save released");

                releaseFirst.countDown();
                helper.assertTrue(awaitFuture(firstSave, 2, TimeUnit.SECONDS), "first save did not complete");
                helper.assertTrue(awaitFuture(secondSave, 2, TimeUnit.SECONDS), "second save did not complete");

                Path settingsFile = isolatedGameDirectory.resolve("baritone").resolve(SettingsUtil.SETTINGS_DEFAULT_NAME);
                String actual = Files.readString(settingsFile, StandardCharsets.UTF_8);
                helper.assertTrue(actual.equals(serialized(first)) || actual.equals(serialized(second)),
                        "save produced an incomplete or interleaved format");
                helper.succeed();
            } catch (IOException ex) {
                throw new AssertionError("Unable to create SettingsUtil save fixture", ex);
            } finally {
                shutdown(executor);
                if (first != null) {
                    restoreSettings(first);
                }
                restoreSettings(second);
                restorePaths(originalGameDirectory);
                deleteRecursively(isolatedGameDirectory);
            }
        }
    }

    @GameTest(template = "provider_smoke", batch = "settings_save", timeoutTicks = 100)
    public static void saveCreatesExpectedPathAndRoundTrips(GameTestHelper helper) {
        synchronized (TEST_LOCK) {
            Path isolatedGameDirectory = null;
            Path originalGameDirectory = FMLPaths.GAMEDIR.get();
            Settings settings = null;
            Settings loaded = null;
            try {
                isolatedGameDirectory = Files.createTempDirectory(originalGameDirectory, "settings-round-trip-");
                FMLPaths.loadAbsolutePaths(isolatedGameDirectory);
                settings = newSettings();
                settings.ticksBetweenInventoryMoves.value = 11;
                SettingsUtil.save(settings);

                Path settingsFile = isolatedGameDirectory.resolve("baritone").resolve(SettingsUtil.SETTINGS_DEFAULT_NAME);
                helper.assertTrue(Files.isRegularFile(settingsFile), "save did not create the settings file");
                helper.assertTrue(Files.readString(settingsFile, StandardCharsets.UTF_8)
                        .equals("ticksBetweenInventoryMoves 11\n"), "save wrote an unexpected setting payload");

                loaded = newSettings();
                SettingsUtil.readAndApply(loaded, SettingsUtil.SETTINGS_DEFAULT_NAME);
                helper.assertTrue(Integer.valueOf(11).equals(loaded.ticksBetweenInventoryMoves.value),
                        "readAndApply did not restore the saved setting");
                helper.succeed();
            } catch (IOException ex) {
                throw new AssertionError("Unable to create SettingsUtil save fixture", ex);
            } finally {
                restoreSettings(settings);
                restoreSettings(loaded);
                restorePaths(originalGameDirectory);
                deleteRecursively(isolatedGameDirectory);
            }
        }
    }

    private static String serialized(Settings settings) {
        List<Settings.Setting> modified = SettingsUtil.modifiedSettings(settings);
        return modified.stream()
                .map(SettingsUtil::settingToString)
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static Settings newSettings() {
        try {
            Constructor<Settings> constructor = Settings.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to construct Settings fixture", ex);
        }
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for SettingsUtil save", ex);
        }
    }

    private static boolean awaitFuture(Future<?> future, long timeout, TimeUnit unit) {
        try {
            future.get(timeout, unit);
            return true;
        } catch (java.util.concurrent.TimeoutException ex) {
            return false;
        } catch (Exception ex) {
            throw new AssertionError("SettingsUtil save failed", ex);
        }
    }

    private static void restoreSettings(Settings settings) {
        if (settings != null) {
            for (Settings.Setting<?> setting : settings.allSettings) {
                setting.reset();
            }
        }
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdownNow();
        if (!await(executor, 5, TimeUnit.SECONDS)) {
            throw new AssertionError("SettingsUtil save executor did not terminate");
        }
    }

    private static boolean await(ExecutorService executor, long timeout, TimeUnit unit) {
        try {
            return executor.awaitTermination(timeout, unit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while stopping SettingsUtil save executor", ex);
        }
    }

    private static void restorePaths(Path originalGameDirectory) {
        FMLPaths.loadAbsolutePaths(originalGameDirectory);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            throw new AssertionError("Unable to clean SettingsUtil save fixture", ex);
        }
    }

    private static final class BlockingColor extends Color {

        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingColor(Color color, CountDownLatch entered, CountDownLatch release) {
            super(color.getRGB());
            this.entered = entered;
            this.release = release;
        }

        @Override
        public int getRed() {
            entered.countDown();
            if (!await(release, 2, TimeUnit.SECONDS)) {
                throw new AssertionError("color serialization was not released");
            }
            return super.getRed();
        }
    }
}
