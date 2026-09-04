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

package baritone.pathing.goals;

import baritone.api.pathing.goals.GoalAxis;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoalAxisEqualityRegressionTest {

    @Test
    public void equalsHandlesNull() {
        assertFalse(new GoalAxis().equals(null));
    }

    @Test
    public void equalGoalAxisInstancesRetainTheirContract() {
        GoalAxis first = new GoalAxis();
        GoalAxis second = new GoalAxis();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void baseAndSubtypeRemainUnequal() {
        assertFalse(new GoalAxis().equals(new DerivedGoalAxis()));
    }

    @Test
    public void subtypeIsReflexiveAndRejectsWrongType() {
        GoalAxis first = new DerivedGoalAxis();
        GoalAxis second = new DerivedGoalAxis();

        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());
        assertFalse(first.equals(new Object()));
    }

    @Test
    public void equalsRemainsSymmetricForSubtypes() {
        GoalAxis base = new GoalAxis();
        GoalAxis subtype = new DerivedGoalAxis();

        assertEquals(base.equals(subtype), subtype.equals(base));
    }

    private static final class DerivedGoalAxis extends GoalAxis {
    }
}
