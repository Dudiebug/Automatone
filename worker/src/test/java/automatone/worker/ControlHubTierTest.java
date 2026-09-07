package automatone.worker;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ControlHubTierTest {
    @Test
    public void progressionExpandsPhysicalWorkerCapacity() {
        assertArrayEquals(new int[]{1, 3, 6, 10}, java.util.Arrays.stream(ControlHubTier.values())
                .mapToInt(ControlHubTier::workerSlots).toArray());
        assertFalse(ControlHubTier.BASIC.remoteController());
        assertFalse(ControlHubTier.REINFORCED.remoteController());
        assertFalse(ControlHubTier.ADVANCED.remoteController());
        assertTrue(ControlHubTier.DRAGON.remoteController());
    }
}
