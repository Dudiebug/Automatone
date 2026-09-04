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
import net.minecraft.core.Direction;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class BetterBlockPosNullDirectionRegressionTest {

    @Test
    public void positiveDistanceRejectsNullDirection() {
        assertThrows(NullPointerException.class, () ->
                assertNotNull(new BetterBlockPos(0, 0, 0).relative((Direction) null, 1)));
    }

    @Test
    public void negativeDistanceRejectsNullDirection() {
        assertThrows(NullPointerException.class, () ->
                assertNotNull(new BetterBlockPos(0, 0, 0).relative((Direction) null, -1)));
    }
}
