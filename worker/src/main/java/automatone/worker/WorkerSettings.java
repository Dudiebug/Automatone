package automatone.worker;

import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Persisted native overrides. Only settings used by the server mining host are writable. */
public final class WorkerSettings {
    private static final Set<String> SUPPORTED = Set.of(("allowBreak allowBreakAnyway allowDiagonalAscend allowDiagonalDescend "
            + "allowDownward allowJumpAtBuildLimit allowOnlyExposedOres allowOnlyExposedOresDistance "
            + "allowOvershootDiagonalDescend allowParkour allowParkourAscend allowParkourPlace allowPlace "
            + "allowPlaceInFluidsFlow allowPlaceInFluidsSource allowSprint allowVines allowWalkOnBottomSlab "
            + "allowWalkOnMagmaBlocks allowWaterBucketFall assumeExternalAutoTool "
            + "assumeSafeWalk assumeStep assumeWalkOnLava assumeWalkOnWater autoTool avoidBreakingMultiplier "
            + "avoidUpdatingFallingBlocks backtrackCostFavoringCoefficient blacklistClosestOnFailure "
            + "blockBreakAdditionalPenalty blockPlacementPenalty blocksToAvoid blocksToAvoidBreaking "
            + "blocksToDisallowBreaking considerPotionEffects costHeuristic costVerificationLookahead "
            + "exploreForBlocks extendCacheOnThreshold failureTimeoutMS forceInternalMining internalMiningAirException itemSaver "
            + "itemSaverThreshold jumpPenalty legitMine legitMineIncludeDiagonals legitMineYLevel "
            + "maxCachedWorldScanCount maxCostIncrease maxFallHeightBucket maxFallHeightNoWater "
            + "maxPathHistoryLength maxYLevelWhileMining mineDropLoiterDurationMSThanksLouca "
            + "mineGoalUpdateInterval mineMaxOreLocationsCount mineScanDroppedItems "
            + "minimumImprovementRepropagation minYLevelWhileMining movementTimeoutTicks overshootTraverse "
            + "pathHistoryCutoffAmount pathingMaxChunkBorderFetch "
            + "pathThroughCachedOnly pauseMiningForFallingBlocks planAheadFailureTimeoutMS "
            + "planAheadPrimaryTimeoutMS planningTickLookahead preferSilkTouch primaryTimeoutMS "
            + "simplifyUnloadedYCoord splicePath sprintAscends sprintInWater strictLiquidCheck "
            + "useSwordToMine walkOnWaterOnePenalty walkWhileBreaking acceptableThrowawayItems "
            + "rightClickSpeed blockBreakSpeed remainWithExistingLookDirection randomLooking randomLooking113 "
            + "avoidance mobSpawnerAvoidanceCoefficient mobAvoidanceCoefficient mobSpawnerAvoidanceRadius "
            + "mobAvoidanceRadius cutoffAtLoadBoundary pathCutoffMinimumLength pathCutoffFactor chunkCaching "
            + "pruneRegionsFromRAM chunkPackerQueueMaxSize cachedChunksExpirySeconds repackOnAnyBlockChange")
            .toLowerCase(Locale.ROOT).split(" +"));
    private WorkerSettings() { }

    public static String unavailableReason(Settings.Setting<?> setting) {
        if (setting.isJavaOnly()) {
            return "Java callback or integration setting";
        }
        if (setting.getName().equals("blockReachDistance")) {
            return "Worker interaction reach is fixed by the server host";
        }
        if (setting.getName().startsWith("pathingMap") || setting.getName().startsWith("slowPath")) {
            return "Server memory allocation and path debugging are managed by the host";
        }
        return SUPPORTED.contains(setting.getName().toLowerCase(Locale.ROOT)) ? ""
                : "Not used by the server mining worker; client, other process or shared integration setting";
    }

    public static Map<String, String> validate(Map<String, String> values) {
        if (values.size() > 256) {
            throw new IllegalArgumentException("TOO_MANY_SETTINGS");
        }
        Settings candidate = new Settings().copy();
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            String key = name.toLowerCase(Locale.ROOT);
            Settings.Setting<?> setting = candidate.byLowerName.get(key);
            if (setting == null || !unavailableReason(setting).isEmpty() || value == null || value.length() > 8192) {
                throw new IllegalArgumentException("UNAVAILABLE_SETTING: " + name);
            }
            if (setting.getValueClass() == Boolean.class && !value.equals("true") && !value.equals("false")) {
                throw new IllegalArgumentException("INVALID_BOOLEAN");
            }
            if (key.equals("acceptablethrowawayitems") && !value.isEmpty()) {
                for (String item : value.split(",", -1)) {
                    ResourceLocation id = ResourceLocation.tryParse(item.trim());
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        throw new IllegalArgumentException("INVALID_ITEM");
                    }
                }
            }
            applyValue(candidate, key, value);
            if (setting.value instanceof Number number) {
                double numeric = number.doubleValue();
                if (!Double.isFinite(numeric) || numeric < minimum(key) || numeric > maximum(key)) {
                    throw new IllegalArgumentException("SETTING_OUT_OF_RANGE: " + name);
                }
            }
            normalized.put(key, SettingsUtil.settingValueToString(setting));
        });
        return Map.copyOf(normalized);
    }

    public static double maximum(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "allowonlyexposedoresdistance" -> 8;
            case "pathcutofffactor" -> 1;
            case "mobavoidanceradius", "mobspawneravoidanceradius" -> 32;
            case "minemaxorelocationscount", "maxcachedworldscancount", "pathingmaxchunkborderfetch" -> 4096;
            default -> key.toLowerCase(Locale.ROOT).endsWith("timeoutms") ? 60_000 : 1_000_000;
        };
    }

    public static double minimum(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "cachedchunksexpiryseconds" -> -1;
            case "legitmineylevel" -> -2048;
            default -> 0;
        };
    }

    public static Settings resolve(Map<String, String> personal, Map<String, String> overrides) {
        Settings settings = new Settings().copy();
        personal.forEach((key, value) -> applyValue(settings, key, value));
        overrides.forEach((key, value) -> applyValue(settings, key, value));
        return settings;
    }

    private static void applyValue(Settings settings, String key, String value) {
        Settings.Setting<?> setting = settings.byLowerName.get(key);
        if (value.isEmpty() && setting.value instanceof java.util.List<?> list) {
            // Native list parsing otherwise treats empty input as an empty registry identifier.
            list.clear();
        } else {
            SettingsUtil.parseAndApply(settings, key, value);
        }
    }

    public static CompoundTag save(Map<String, String> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach(tag::putString);
        return tag;
    }

    public static Map<String, String> load(CompoundTag tag) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            if (!tag.contains(key, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("INVALID_SAVED_SETTINGS");
            }
            values.put(key, tag.getString(key));
        }
        return validate(values);
    }
}
