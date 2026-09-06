package automatone.worker;

import org.junit.Test;

import static org.junit.Assert.*;

public final class MiningSessionTest {
    @Test
    public void onlyMatchingDestructionsAdvanceAndFiniteWorkStopsAtTheRequestedCount() {
        MiningSession session = new MiningSession();
        session.start("minecraft:iron_ore", 3);
        assertFalse(session.recordBreak("minecraft:stone"));
        assertEquals(0, session.snapshot().completed());
        assertFalse(session.recordBreak("minecraft:iron_ore"));
        assertFalse(session.recordBreak("minecraft:iron_ore"));
        assertTrue(session.recordBreak("minecraft:iron_ore"));
        assertEquals(MiningSession.State.COMPLETED, session.snapshot().state());
        assertFalse(session.recordBreak("minecraft:iron_ore"));
        assertEquals(3, session.snapshot().completed());
    }

    @Test
    public void unlimitedAndRepeatedStopRetainProgressWithoutRestarting() {
        MiningSession session = new MiningSession();
        session.stop();
        assertEquals(MiningSession.State.IDLE, session.snapshot().state());
        session.start("minecraft:iron_ore", 0);
        for (int count = 0; count < 5; count++) {
            assertFalse(session.recordBreak("minecraft:iron_ore"));
        }
        assertEquals(MiningSession.State.RUNNING, session.snapshot().state());
        session.stop();
        session.stop();
        assertEquals(MiningSession.State.CANCELLED, session.snapshot().state());
        assertEquals(5, session.snapshot().completed());
        assertFalse(session.recordBreak("minecraft:iron_ore"));
    }

    @Test
    public void invalidAndBusyStartsLeaveTheExistingStateAlone() {
        MiningSession session = new MiningSession();
        assertThrows(IllegalArgumentException.class, () -> session.start("minecraft:iron_ore", -1));
        assertThrows(IllegalArgumentException.class,
                () -> session.start("minecraft:iron_ore", MiningSession.MAX_REQUESTED_BLOCKS + 1));
        assertThrows(IllegalArgumentException.class, () -> session.start("", 1));
        assertEquals(MiningSession.State.IDLE, session.snapshot().state());
        session.start("minecraft:iron_ore", 3);
        session.recordBreak("minecraft:iron_ore");
        MiningSession.Snapshot before = session.snapshot();
        assertThrows(IllegalStateException.class, () -> session.start("minecraft:diamond_ore", 1));
        assertEquals(before, session.snapshot());
    }
}
