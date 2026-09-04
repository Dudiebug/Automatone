/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone;

import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.cache.IWorldScanner;
import baritone.api.command.ICommandSystem;
import baritone.api.schematic.ISchematicSystem;
import baritone.api.utils.IPlayerContext;
import baritone.cache.FasterWorldScanner;
import baritone.command.CommandSystem;
import baritone.utils.schematic.SchematicSystem;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class BaritoneProvider implements IBaritoneProvider {

    private final Map<IPlayerContext, IBaritone> runtimes = new IdentityHashMap<>();
    private final BiFunction<IPlayerContext, Path, IBaritone> runtimeFactory;

    public BaritoneProvider() {
        this(Baritone::new);
    }

    BaritoneProvider(BiFunction<IPlayerContext, Path, IBaritone> runtimeFactory) {
        this.runtimeFactory = runtimeFactory;
    }

    @Override
    public synchronized IBaritone getPrimaryBaritone() {
        return this.runtimes.values().stream().findFirst().orElse(null);
    }

    @Override
    public synchronized List<IBaritone> getAllBaritones() {
        return List.copyOf(this.runtimes.values());
    }

    @Override
    public synchronized IBaritone createBaritone(IPlayerContext context, Path dataDirectory) {
        return this.runtimes.computeIfAbsent(context, key -> this.runtimeFactory.apply(key, dataDirectory));
    }

    @Override
    public synchronized boolean destroyBaritone(IBaritone baritone) {
        final IPlayerContext context = baritone.getPlayerContext();
        if (this.runtimes.get(context) != baritone) {
            return false;
        }
        this.runtimes.remove(context);
        baritone.dispose();
        return true;
    }

    @Override
    public void tick() {
        for (IBaritone runtime : this.getAllBaritones()) {
            if (!runtime.isHostAvailable()) {
                this.destroyBaritone(runtime);
            } else {
                runtime.tick();
            }
        }
    }

    @Override
    public void disposeAll() {
        for (IBaritone runtime : this.getAllBaritones()) {
            this.destroyBaritone(runtime);
        }
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return FasterWorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
