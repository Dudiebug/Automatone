package baritone.pathing.calc;

import baritone.api.pathing.goals.Goal;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for the coordinate-based equality contract of {@link PathNode}.
 */
public class PathNodeEqualsContractTest {

    private static final Goal ZERO_HEURISTIC_GOAL = new Goal() {
        @Override
        public boolean isInGoal(int x, int y, int z) {
            return false;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return 0;
        }
    };

    @Test
    public void equalsReturnsFalseForNull() {
        PathNode node = nodeAt(1, 2, 3);

        assertFalse(node.equals(null));
    }

    @Test
    public void equalsReturnsFalseForAnUnrelatedObject() {
        PathNode node = nodeAt(1, 2, 3);

        assertFalse(node.equals("not a path node"));
    }

    @Test
    public void equalCoordinatesRemainEqualAndHashCompatible() {
        PathNode first = nodeAt(1, 2, 3);
        PathNode second = nodeAt(1, 2, 3);

        assertTrue(first.equals(second));
        assertTrue(second.equals(first));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void differentCoordinatesRemainUnequal() {
        PathNode node = nodeAt(1, 2, 3);

        assertFalse(node.equals(nodeAt(1, 2, 4)));
    }

    private static PathNode nodeAt(int x, int y, int z) {
        return new PathNode(x, y, z, ZERO_HEURISTIC_GOAL);
    }
}
