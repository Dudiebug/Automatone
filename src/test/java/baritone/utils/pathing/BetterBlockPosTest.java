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

package baritone.utils.pathing;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BetterBlockPosTest {

    /**
     * Make sure BetterBlockPos behaves just like BlockPos
     */
    @Test
    public void testSimple() {
        BlockPos pos = new BlockPos(1, 2, 3);
        BetterBlockPos better = new BetterBlockPos(1, 2, 3);
        assertEquals(pos, better);
        assertEquals(pos.above(), better.above());
        assertEquals(pos.below(), better.below());
        assertEquals(pos.north(), better.north());
        assertEquals(pos.south(), better.south());
        assertEquals(pos.east(), better.east());
        assertEquals(pos.west(), better.west());
        for (Direction dir : Direction.values()) {
            assertEquals(pos.relative(dir), better.relative(dir));
            assertEquals(pos.relative(dir, 0), pos);
            assertEquals(better.relative(dir, 0), better);
            for (int i = -10; i < 10; i++) {
                assertEquals(pos.relative(dir, i), better.relative(dir, i));
            }
            assertTrue(better.relative(dir, 0) == better);
        }
        for (int i = -10; i < 10; i++) {
            assertEquals(pos.above(i), better.above(i));
            assertEquals(pos.below(i), better.below(i));
            assertEquals(pos.north(i), better.north(i));
            assertEquals(pos.south(i), better.south(i));
            assertEquals(pos.east(i), better.east(i));
            assertEquals(pos.west(i), better.west(i));
        }
        assertTrue(better.relative((Direction) null, 0) == better);
    }
}
