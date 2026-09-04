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

package baritone.api;

import baritone.api.cache.IWorldScanner;
import baritone.api.command.ICommand;
import baritone.api.command.ICommandSystem;
import baritone.api.schematic.ISchematicSystem;
import baritone.api.utils.IPlayerContext;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Provides the present {@link IBaritone} instances, as well as non-baritone instance related APIs.
 *
 * @author leijurv
 */
public interface IBaritoneProvider {

    /**
     * Legacy view of the first explicitly registered runtime.
     *
     * @return The first runtime, or {@code null} when none are registered.
     */
    IBaritone getPrimaryBaritone();

    /**
     * Returns a snapshot of all explicitly owned runtimes.
     *
     * @return All active {@link IBaritone} instances.
     * @see #getBaritoneForPlayer(Entity)
     */
    List<IBaritone> getAllBaritones();

    /**
     * Provides the {@link IBaritone} instance for a given host entity.
     *
     * @param player The player
     * @return The {@link IBaritone} instance.
     */
    default IBaritone getBaritoneForPlayer(Entity player) {
        for (IBaritone baritone : this.getAllBaritones()) {
            if (Objects.equals(player, baritone.getPlayerContext().player())) {
                return baritone;
            }
        }
        return null;
    }

    default IBaritone getBaritoneForContext(IPlayerContext context) {
        for (IBaritone baritone : this.getAllBaritones()) {
            if (baritone.getPlayerContext() == context) {
                return baritone;
            }
        }
        return null;
    }

    /**
     * Creates and registers one runtime for an explicitly supplied host context.
     *
     * @param context The host context
     * @param dataDirectory The runtime-owned cache/settings directory
     * @return The {@link IBaritone} instance
     */
    IBaritone createBaritone(IPlayerContext context, Path dataDirectory);

    /**
     * Destroys and removes the specified {@link IBaritone} instance.
     *
     * @param baritone The baritone instance to remove
     * @return Whether the baritone instance was removed
     */
    boolean destroyBaritone(IBaritone baritone);

    /** Ticks every currently owned runtime once. */
    void tick();

    /** Disposes every runtime owned by this provider. */
    void disposeAll();

    /**
     * Returns the {@link IWorldScanner} instance. This is not a type returned by
     * {@link IBaritone} implementation, because it is not linked with {@link IBaritone}.
     *
     * @return The {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ICommandSystem} instance. This is not bound to a specific {@link IBaritone}
     * instance because {@link ICommandSystem} itself controls global behavior for {@link ICommand}s.
     *
     * @return The {@link ICommandSystem} instance.
     */
    ICommandSystem getCommandSystem();

    /**
     * @return The {@link ISchematicSystem} instance.
     */
    ISchematicSystem getSchematicSystem();
}
