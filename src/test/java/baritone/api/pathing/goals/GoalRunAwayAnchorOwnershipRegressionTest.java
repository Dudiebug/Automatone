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

package baritone.api.pathing.goals;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class GoalRunAwayAnchorOwnershipRegressionTest {

    @Test
    public void constructorOwnsAnchorArray() {
        BlockPos[] anchors = {new BlockPos(0, 0, 0)};
        GoalRunAway goal = new GoalRunAway(4, anchors);

        anchors[0] = new BlockPos(100, 0, 0);

        assertFalse(goal.isInGoal(1, 0, 0));
    }

    @Test
    public void constructorOwnsMutableAnchorCoordinates() {
        BlockPos.MutableBlockPos anchor = new BlockPos.MutableBlockPos(0, 0, 0);
        GoalRunAway goal = new GoalRunAway(4, anchor);

        anchor.set(100, 0, 0);

        assertFalse(goal.isInGoal(1, 0, 0));
    }
}
