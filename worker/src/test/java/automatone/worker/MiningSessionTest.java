package automatone.worker;

import org.junit.Test;

import static org.junit.Assert.*;

public final class MiningSessionTest {
    @Test
    public void restoredFiniteJobCountsOnlyTheRemainingBlocks() {
        MiningSession session = new MiningSession();
        session.restore(new MiningSession.Snapshot("minecraft:iron_ore", 3, 2, MiningSession.State.RUNNING, ""));
        assertTrue(session.recordBreak("minecraft:iron_ore"));
        assertEquals(3, session.snapshot().completed());
        assertEquals(MiningSession.State.COMPLETED, session.snapshot().state());
    }

    @Test
    public void invalidRestoredProgressCannotBecomeActiveWork() {
        MiningSession session = new MiningSession();
        for (MiningSession.Snapshot invalid : new MiningSession.Snapshot[] {
                new MiningSession.Snapshot("minecraft:iron_ore", 3, 3, MiningSession.State.RUNNING, ""),
                new MiningSession.Snapshot("minecraft:iron_ore", 3, 4, MiningSession.State.CANCELLED, ""),
                new MiningSession.Snapshot("minecraft:iron_ore", 0, 2, MiningSession.State.COMPLETED, ""),
                new MiningSession.Snapshot("minecraft:iron_ore", 3, -1, MiningSession.State.RUNNING, ""),
                new MiningSession.Snapshot("", 3, 0, MiningSession.State.RUNNING, "")}) {
            assertThrows(IllegalArgumentException.class, () -> session.restore(invalid));
        }
        assertEquals(MiningSession.State.IDLE, session.snapshot().state());
    }

    @Test
    public void restoredTerminalJobsNeverCountAndUnlimitedProgressDoesNotOverflow() {
        MiningSession session = new MiningSession();
        for (MiningSession.State state : new MiningSession.State[] {
                MiningSession.State.COMPLETED, MiningSession.State.CANCELLED, MiningSession.State.FAILED}) {
            session.restore(new MiningSession.Snapshot("minecraft:iron_ore", 3, 3, state, ""));
            assertFalse(session.recordBreak("minecraft:iron_ore"));
            assertEquals(state, session.snapshot().state());
        }
        session.restore(new MiningSession.Snapshot("minecraft:iron_ore", 0, Long.MAX_VALUE, MiningSession.State.RUNNING, ""));
        assertFalse(session.recordBreak("minecraft:iron_ore"));
        assertEquals(Long.MAX_VALUE, session.snapshot().completed());
    }

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
