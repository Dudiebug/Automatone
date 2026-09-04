package baritone.gametest;

import baritone.BaritoneProvider;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritoneProvider;
import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Mod("automatone_gametest")
@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public class DedicatedServerSmokeGameTest {
    public DedicatedServerSmokeGameTest(IEventBus modEventBus) {
        MovementHelperCollisionShapeGameTest.registerBlocks(modEventBus);
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void providerInitializesOnDedicatedServer(GameTestHelper helper) {
        helper.assertTrue(FMLEnvironment.dist == Dist.DEDICATED_SERVER,
                "Smoke test must run on the physical dedicated-server distribution");
        ServerLevel level = helper.getLevel();
        long startTime = level.getGameTime();
        LogUtils.getLogger().info("M1.5 smoke: physical dedicated server and real ServerLevel; initializing BaritoneAPI");

        IBaritoneProvider provider = BaritoneAPI.getProvider();
        helper.assertTrue(provider instanceof BaritoneProvider, "Expected the real BaritoneProvider");
        Settings settings = BaritoneAPI.getSettings();
        settings.logger.value.accept(Component.literal("M1.5 default logger callback"));
        settings.toaster.value.accept(Component.literal("M1.5 default toaster title"),
                Component.literal("M1.5 default toaster message"));
        settings.notifier.value.accept("M1.5 default notifier callback", false);
        LogUtils.getLogger().info("M1.5 smoke: default settings callback messages emitted on the dedicated server; verify markers in latest.log");
        runSettingsRegression(helper);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(level.getGameTime() >= startTime + 20, "ServerLevel must advance at least 20 ticks");
            helper.assertTrue(BaritoneAPI.getProvider() == provider, "Provider identity must remain stable");
            LogUtils.getLogger().info("M1.5 smoke: provider initialized and 20 world ticks observed; runtime lifecycle remains unverified");
            helper.succeed();
        });
    }

    private static void runSettingsRegression(GameTestHelper helper) {
        Path originalGameDirectory = FMLPaths.GAMEDIR.get();
        Path isolatedGameDirectory = null;
        try {
            isolatedGameDirectory = Files.createTempDirectory(originalGameDirectory, "m1-5-settings-");
            FMLPaths.loadAbsolutePaths(isolatedGameDirectory);

            Settings defaultSettings = newSettings();
            defaultSettings.logger.value.accept(Component.literal("M1.5 isolated default logger"));
            defaultSettings.toaster.value.accept(Component.literal("M1.5 isolated default toast"),
                    Component.literal("M1.5 isolated default toast message"));
            defaultSettings.notifier.value.accept("M1.5 isolated default notification", true);
            helper.assertTrue(defaultSettings.logger.value != null
                            && defaultSettings.toaster.value != null
                            && defaultSettings.notifier.value != null,
                    "Server-safe default callbacks must be present and invocable");

            Settings callbackSettings = newSettings();
            List<String> observed = new ArrayList<>();
            callbackSettings.logger.value = message -> observed.add("log:" + message.getString());
            callbackSettings.toaster.value = (title, message) -> observed.add("toast:" + title.getString() + ":" + message.getString());
            callbackSettings.notifier.value = (message, error) -> observed.add("notify:" + message + ":" + error);
            callbackSettings.logger.value.accept(Component.literal("log"));
            callbackSettings.toaster.value.accept(Component.literal("title"), Component.literal("toast"));
            callbackSettings.notifier.value.accept("notification", true);
            helper.assertTrue(observed.equals(List.of("log:log", "toast:title:toast", "notify:notification:true")),
                    "Callback types must remain replaceable and observable");

            Path settingsFile = isolatedGameDirectory.resolve("baritone").resolve("isolated.txt");
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile,
                    "chatDebug true\ninvalid\nticksBetweenInventoryMoves nope\n",
                    StandardCharsets.UTF_8);
            Settings readSettings = newSettings();
            List<String> readDiagnostics = new ArrayList<>();
            readSettings.logger.value = message -> readDiagnostics.add(message.getString());
            SettingsUtil.readAndApply(readSettings, "isolated.txt");
            helper.assertTrue(readSettings.chatDebug.value,
                    "SettingsUtil must apply valid values from an isolated settings file");
            helper.assertTrue(readDiagnostics.stream().anyMatch(message -> message.contains("Invalid syntax")),
                    "SettingsUtil must report malformed setting syntax through the supplied logger");
            helper.assertTrue(readDiagnostics.stream().anyMatch(message -> message.contains("Unable to parse line")),
                    "SettingsUtil must report parse failures through the supplied logger");

            Settings missingSettings = newSettings();
            List<String> missingDiagnostics = new ArrayList<>();
            missingSettings.logger.value = message -> missingDiagnostics.add(message.getString());
            SettingsUtil.readAndApply(missingSettings, "missing.txt");
            helper.assertTrue(!missingDiagnostics.isEmpty() && missingDiagnostics.get(0).contains("not found"),
                    "SettingsUtil must report a missing settings file through the supplied logger");

            Files.deleteIfExists(settingsFile);
            Files.deleteIfExists(settingsFile.getParent());
            helper.assertTrue(Files.notExists(settingsFile.getParent()),
                    "Save regression must start with a fresh isolated settings directory");
            Settings saveSettings = newSettings();
            saveSettings.ticksBetweenInventoryMoves.value = 7;
            SettingsUtil.save(saveSettings);
            Path savedSettings = isolatedGameDirectory.resolve("baritone").resolve(SettingsUtil.SETTINGS_DEFAULT_NAME);
            helper.assertTrue(Files.exists(savedSettings), "SettingsUtil must create the isolated settings file");
            helper.assertTrue(Files.readString(savedSettings, StandardCharsets.UTF_8)
                            .contains("ticksBetweenInventoryMoves 7"),
                    "SettingsUtil must save modified values");
            saveSettings.ticksBetweenInventoryMoves.reset();
            SettingsUtil.readAndApply(saveSettings, SettingsUtil.SETTINGS_DEFAULT_NAME);
            helper.assertTrue(Integer.valueOf(7).equals(saveSettings.ticksBetweenInventoryMoves.value),
                    "SettingsUtil must round-trip saved values");

            deleteRecursively(savedSettings.getParent());
            Files.writeString(savedSettings.getParent(), "not a directory", StandardCharsets.UTF_8);
            Settings failedSaveSettings = newSettings();
            List<String> saveDiagnostics = new ArrayList<>();
            failedSaveSettings.logger.value = message -> saveDiagnostics.add(message.getString());
            SettingsUtil.save(failedSaveSettings);
            helper.assertTrue(saveDiagnostics.stream().anyMatch(message -> message.contains("saving Baritone settings")),
                    "SettingsUtil must report save failures through the supplied logger");
        } catch (Exception ex) {
            throw new AssertionError("Settings bootstrap regression failed", ex);
        } finally {
            FMLPaths.loadAbsolutePaths(originalGameDirectory);
            if (isolatedGameDirectory != null) {
                try {
                    deleteRecursively(isolatedGameDirectory);
                } catch (IOException ex) {
                    throw new AssertionError("Unable to clean isolated settings directory", ex);
                }
            }
        }
    }

    private static Settings newSettings() throws ReflectiveOperationException {
        Constructor<Settings> constructor = Settings.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void deleteRecursively(Path root) throws IOException {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
