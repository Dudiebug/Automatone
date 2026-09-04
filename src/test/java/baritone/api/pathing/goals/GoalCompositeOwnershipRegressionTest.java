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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoalCompositeOwnershipRegressionTest {

    @Test
    public void constructorOwnsInputArray() {
        PointGoal retained = new PointGoal(1, 2, 3);
        Goal[] input = {retained};
        GoalComposite composite = new GoalComposite(input);

        input[0] = new PointGoal(9, 9, 9);

        assertTrue(composite.isInGoal(1, 2, 3));
        assertFalse(composite.isInGoal(9, 9, 9));
    }

    @Test
    public void accessorDoesNotExposeOwnedArray() {
        PointGoal retained = new PointGoal(1, 2, 3);
        GoalComposite composite = new GoalComposite(retained);

        Goal[] output = composite.goals();
        output[0] = new PointGoal(9, 9, 9);

        assertTrue(composite.isInGoal(1, 2, 3));
        assertFalse(composite.isInGoal(9, 9, 9));
    }

    private static final class PointGoal implements Goal {

        private final int x;
        private final int y;
        private final int z;

        private PointGoal(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return this.x == x && this.y == y && this.z == z;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return 0;
        }
    }
}
