/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.cache;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.cache.IWorldProvider;
import baritone.api.utils.IPlayerContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Runtime-owned world data. No component key or global cache owns this instance. */
public final class WorldProvider implements IWorldProvider {

    private final IPlayerContext context;
    private final Supplier<Settings> settings;
    private final Path dataRoot;
    private Level loadedLevel;
    private WorldData currentWorld;

    public WorldProvider(Baritone baritone, Path dataRoot) {
        this.context = baritone.getPlayerContext();
        this.settings = baritone::getSettings;
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
    }

    @Override
    public synchronized WorldData getCurrentWorld() {
        final Level level = this.context.world();
        if (level != this.loadedLevel) {
            this.closeWorld();
            if (level != null) {
                this.initWorld(level);
            }
        }
        return this.currentWorld;
    }

    public synchronized void initWorld(Level level) {
        if (level == this.loadedLevel && this.currentWorld != null) {
            return;
        }
        this.closeWorld();
        final Path directory = this.worldDataDirectory(level);
        try {
            Files.createDirectories(directory);
            Files.writeString(
                    this.dataRoot.resolve("readme.txt"),
                    "https://github.com/Dudiebug/Automatone\n",
                    StandardCharsets.US_ASCII
            );
        } catch (IOException ignored) {
        }
        this.loadedLevel = level;
        this.currentWorld = new WorldData(directory, level.dimensionType(), settings);
    }

    public synchronized void closeWorld() {
        final WorldData world = this.currentWorld;
        this.currentWorld = null;
        this.loadedLevel = null;
        if (world != null) {
            world.onClose();
        }
    }

    public void dispose() {
        this.closeWorld();
    }

    private Path worldDataDirectory(Level level) {
        final ResourceLocation dimension = level.dimension().location();
        return this.dataRoot
                .resolve(dimension.getNamespace())
                .resolve(dimension.getPath() + "_" + level.dimensionType().logicalHeight());
    }
}
