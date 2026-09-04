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

package baritone.api.event.events;

import baritone.api.utils.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BlockChangeEventOwnershipRegressionTest {

    @Test
    public void constructorOwnsInputList() {
        List<Pair<BlockPos, BlockState>> changes = mutableChanges();
        BlockChangeEvent event = new BlockChangeEvent(new ChunkPos(0, 0), changes);

        changes.clear();

        assertEquals(1, event.getBlocks().size());
    }

    @Test
    public void accessorCannotMutateEventList() {
        BlockChangeEvent event = new BlockChangeEvent(new ChunkPos(0, 0), mutableChanges());
        List<Pair<BlockPos, BlockState>> exposed = event.getBlocks();

        try {
            exposed.clear();
        } catch (UnsupportedOperationException ignored) {
            // An immutable view is also a valid ownership boundary.
        }

        assertEquals(1, event.getBlocks().size());
    }

    private static List<Pair<BlockPos, BlockState>> mutableChanges() {
        List<Pair<BlockPos, BlockState>> changes = new ArrayList<>();
        changes.add(new Pair<>(new BlockPos(1, 2, 3), null));
        return changes;
    }
}
