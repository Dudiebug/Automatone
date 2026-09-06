package automatone.worker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class MiningSessionMultiTargetTest {
    private static final String IRON = "minecraft:iron_ore";
    private static final String STONE = "minecraft:stone";
    private static final String DIAMOND = "minecraft:diamond_ore";

    @Test
    public void multiTargetStartDeduplicatesAndSnapshotsDefensively() {
        MiningSession session = new MiningSession();
        List<String> suppliedTargets = new ArrayList<>(List.of(IRON, STONE, IRON));

        session.start(suppliedTargets, 5);
        suppliedTargets.set(0, DIAMOND);
        suppliedTargets.add("minecraft:gold_ore");

        MiningSession.Snapshot snapshot = session.snapshot();
        assertEquals(List.of(IRON, STONE), snapshot.targets());
        assertEquals(IRON, snapshot.target());
        assertEquals(5, snapshot.requested());
        assertEquals(0, snapshot.completed());
        assertEquals(MiningSession.State.RUNNING, snapshot.state());
        assertNotNull(snapshot.runId());
        List<String> snapshotTargets = snapshot.targets();
        assertThrows(UnsupportedOperationException.class, () -> snapshotTargets.add(DIAMOND));
        assertEquals(List.of(IRON, STONE), snapshot.targets());
    }

    @Test
    public void legacySingleTargetStartExposesACompatibleSingletonTargetList() {
        MiningSession session = new MiningSession();

        session.start(IRON, 2);

        MiningSession.Snapshot snapshot = session.snapshot();
        assertEquals(List.of(IRON), snapshot.targets());
        assertEquals(IRON, snapshot.target());
        assertEquals(2, snapshot.requested());
        assertEquals(MiningSession.State.RUNNING, snapshot.state());
    }

    @Test
    public void finiteJobsCountMatchingBreaksAcrossTargetsAndStopAtTheRequestedTotal() {
        MiningSession session = new MiningSession();
        session.start(List.of(IRON, STONE, IRON), 3);

        assertFalse(session.recordBreak("minecraft:dirt"));
        assertEquals(0, session.snapshot().completed());

        assertFalse(session.recordBreak(IRON));
        assertEquals(1, session.snapshot().completed());
        assertFalse(session.recordBreak(STONE));
        assertEquals(2, session.snapshot().completed());
        assertTrue(session.recordBreak(IRON));
        assertEquals(3, session.snapshot().completed());
        assertEquals(MiningSession.State.COMPLETED, session.snapshot().state());

        assertFalse(session.recordBreak(STONE));
        assertEquals(3, session.snapshot().completed());
    }

    @Test
    public void unlimitedJobsCountMatchingTargetsWithoutCompleting() {
        MiningSession session = new MiningSession();
        session.start(List.of(IRON, STONE), 0);

        assertFalse(session.recordBreak(IRON));
        assertFalse(session.recordBreak(STONE));
        assertFalse(session.recordBreak("minecraft:dirt"));

        assertEquals(2, session.snapshot().completed());
        assertEquals(MiningSession.State.RUNNING, session.snapshot().state());
    }

    @Test
    public void pauseAndResumePreserveProgressAndRunIdentityAndBlockBreaksWhilePaused() {
        MiningSession session = new MiningSession();
        session.start(List.of(IRON, STONE), 3);
        assertFalse(session.recordBreak(IRON));
        UUID runId = session.snapshot().runId();

        session.pause();
        MiningSession.Snapshot paused = session.snapshot();
        assertEquals(MiningSession.State.PAUSED, paused.state());
        assertEquals(runId, paused.runId());
        assertEquals(1, paused.completed());
        assertFalse(session.recordBreak(STONE));
        assertEquals(paused, session.snapshot());

        session.pause();
        assertEquals(paused, session.snapshot());

        session.resume();
        MiningSession.Snapshot resumed = session.snapshot();
        assertEquals(MiningSession.State.RUNNING, resumed.state());
        assertEquals(runId, resumed.runId());
        assertEquals(1, resumed.completed());
        assertFalse(session.recordBreak(STONE));
        assertTrue(session.recordBreak(IRON));
        assertEquals(3, session.snapshot().completed());
        assertEquals(MiningSession.State.COMPLETED, session.snapshot().state());
    }

    @Test
    public void resumeOutsidePausedStateRejectsWithoutChangingTheSnapshot() {
        MiningSession session = new MiningSession();
        assertResumeRejectedWithoutChange(session);

        session.start(IRON, 2);
        assertResumeRejectedWithoutChange(session);
        session.pause();
        session.resume();
        assertFalse(session.recordBreak(IRON));
        assertTrue(session.recordBreak(IRON));
        assertResumeRejectedWithoutChange(session);

        session.start(IRON, 0);
        session.stop();
        assertResumeRejectedWithoutChange(session);
    }

    @Test
    public void stopCancelsRunningOrPausedJobsIdempotentlyAndRetainsProgress() {
        MiningSession session = new MiningSession();
        session.start(IRON, 0);
        assertFalse(session.recordBreak(IRON));
        session.stop();

        MiningSession.Snapshot cancelled = session.snapshot();
        assertEquals(MiningSession.State.CANCELLED, cancelled.state());
        assertEquals(1, cancelled.completed());
        session.stop();
        assertEquals(cancelled, session.snapshot());
        assertFalse(session.recordBreak(IRON));
        assertEquals(cancelled, session.snapshot());

        session.start(List.of(IRON, STONE), 4);
        assertFalse(session.recordBreak(STONE));
        session.pause();
        MiningSession.Snapshot paused = session.snapshot();
        session.stop();

        MiningSession.Snapshot pausedCancelled = session.snapshot();
        assertEquals(MiningSession.State.CANCELLED, pausedCancelled.state());
        assertEquals(paused.completed(), pausedCancelled.completed());
        assertEquals(paused.runId(), pausedCancelled.runId());
        session.stop();
        assertEquals(pausedCancelled, session.snapshot());
    }

    @Test
    public void configureResetsIdleTerminalAndPausedJobsAndClearsRunIdentity() {
        MiningSession session = new MiningSession();

        session.configure(List.of(IRON, STONE, IRON), 7);
        assertConfigured(session, List.of(IRON, STONE), 7);

        session.start(IRON, 1);
        assertTrue(session.recordBreak(IRON));
        assertEquals(MiningSession.State.COMPLETED, session.snapshot().state());
        session.configure(List.of(DIAMOND), 3);
        assertConfigured(session, List.of(DIAMOND), 3);

        session.start(List.of(IRON, STONE), 4);
        assertFalse(session.recordBreak(IRON));
        session.pause();
        assertEquals(MiningSession.State.PAUSED, session.snapshot().state());
        session.configure(List.of(STONE), 2);
        assertConfigured(session, List.of(STONE), 2);

        session.start(IRON, 0);
        session.stop();
        assertEquals(MiningSession.State.CANCELLED, session.snapshot().state());
        session.configure(List.of(IRON, DIAMOND), 8);
        assertConfigured(session, List.of(IRON, DIAMOND), 8);
    }

    @Test
    public void configureWhileRunningRejectsWithoutChangingTheSnapshot() {
        MiningSession session = new MiningSession();
        session.start(List.of(IRON, STONE), 4);
        assertFalse(session.recordBreak(IRON));
        MiningSession.Snapshot before = session.snapshot();

        List<String> replacementTargets = List.of(DIAMOND);
        assertThrows(IllegalStateException.class, () -> session.configure(replacementTargets, 2));
        assertEquals(before, session.snapshot());
    }

    @Test
    public void invalidInputsAndUniqueTargetLimitLeaveTheExistingSnapshotUntouched() {
        MiningSession session = new MiningSession();
        session.configure(List.of(IRON), 5);
        MiningSession.Snapshot beforeInvalidStarts = session.snapshot();

        List<List<String>> invalidTargets = new ArrayList<>();
        invalidTargets.add(null);
        invalidTargets.add(Collections.emptyList());
        invalidTargets.add(List.of(""));
        invalidTargets.add(List.of(" "));
        invalidTargets.add(Collections.singletonList(null));
        invalidTargets.add(uniqueTargets(MiningSession.MAX_TARGET_BLOCKS + 1));
        for (List<String> targets : invalidTargets) {
            assertThrows(IllegalArgumentException.class, () -> session.start(targets, 1));
            assertEquals(beforeInvalidStarts, session.snapshot());
        }
        List<String> oneTarget = List.of(IRON);
        assertThrows(IllegalArgumentException.class, () -> session.start(oneTarget, -1));
        assertEquals(beforeInvalidStarts, session.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> session.start(oneTarget, MiningSession.MAX_REQUESTED_BLOCKS + 1));
        assertEquals(beforeInvalidStarts, session.snapshot());

        List<String> atLimitWithDuplicate = new ArrayList<>(uniqueTargets(MiningSession.MAX_TARGET_BLOCKS));
        atLimitWithDuplicate.add(atLimitWithDuplicate.get(0));
        session.start(atLimitWithDuplicate, 1);
        assertEquals(MiningSession.MAX_TARGET_BLOCKS, session.snapshot().targets().size());

        MiningSession configured = new MiningSession();
        configured.configure(List.of(IRON), 5);
        MiningSession.Snapshot beforeInvalidConfigure = configured.snapshot();
        List<String> tooManyTargets = uniqueTargets(MiningSession.MAX_TARGET_BLOCKS + 1);
        assertThrows(IllegalArgumentException.class,
                () -> configured.configure(tooManyTargets, 1));
        assertEquals(beforeInvalidConfigure, configured.snapshot());
        assertThrows(IllegalArgumentException.class, () -> configured.configure(oneTarget, -1));
        assertEquals(beforeInvalidConfigure, configured.snapshot());
    }

    @Test
    public void startResetsProgressAndAssignsAFreshRunIdentity() {
        MiningSession session = new MiningSession();
        session.start(IRON, 2);
        assertFalse(session.recordBreak(IRON));
        assertTrue(session.recordBreak(IRON));
        UUID firstRunId = session.snapshot().runId();

        session.start(List.of(STONE), 1);
        MiningSession.Snapshot restarted = session.snapshot();
        assertEquals(List.of(STONE), restarted.targets());
        assertEquals(1, restarted.requested());
        assertEquals(0, restarted.completed());
        assertEquals(MiningSession.State.RUNNING, restarted.state());
        assertNotNull(restarted.runId());
        assertNotEquals(firstRunId, restarted.runId());
    }

    @Test
    public void restorePreservesPausedRunAndConfiguredIdleJob() {
        MiningSession session = new MiningSession();
        UUID run = UUID.randomUUID();
        MiningSession.Snapshot saved = new MiningSession.Snapshot(List.of(IRON, STONE), 3, 1,
                MiningSession.State.PAUSED, "", run);
        session.restore(saved);
        assertFalse(session.recordBreak(IRON));
        assertEquals(saved, session.snapshot());
        session.resume();
        assertEquals(run, session.snapshot().runId());
        assertFalse(session.recordBreak(STONE));
        assertTrue(session.recordBreak(IRON));
        session.restore(new MiningSession.Snapshot(List.of(IRON, STONE), 64, 0,
                MiningSession.State.IDLE, "", null));
        assertEquals(List.of(IRON, STONE), session.snapshot().targets());
        assertEquals(64, session.snapshot().requested());
        assertResumeRejectedWithoutChange(session);
    }

    @Test
    public void malformedSavedRunsDoNotReplaceAnExistingJob() {
        MiningSession session = new MiningSession();
        session.start(IRON, 3);
        MiningSession.Snapshot before = session.snapshot();
        for (MiningSession.Snapshot invalid : List.of(
                new MiningSession.Snapshot(List.of(IRON), 3, 1, MiningSession.State.PAUSED, "", null),
                new MiningSession.Snapshot(List.of(IRON), 3, 3, MiningSession.State.COMPLETED, "", null),
                new MiningSession.Snapshot(List.of(IRON, IRON), 3, 1, MiningSession.State.PAUSED, "", UUID.randomUUID()),
                new MiningSession.Snapshot(List.of(), 64, 0, MiningSession.State.IDLE, "", null),
                new MiningSession.Snapshot(List.of(IRON), 3, 3, MiningSession.State.PAUSED, "", UUID.randomUUID()))) {
            assertThrows(IllegalArgumentException.class, () -> session.restore(invalid));
            assertEquals(before, session.snapshot());
        }
    }

    private static void assertResumeRejectedWithoutChange(MiningSession session) {
        MiningSession.Snapshot before = session.snapshot();
        assertThrows(IllegalStateException.class, session::resume);
        assertEquals(before, session.snapshot());
    }

    private static void assertConfigured(MiningSession session, List<String> targets, int requested) {
        MiningSession.Snapshot configured = session.snapshot();
        assertEquals(targets, configured.targets());
        assertEquals(targets.get(0), configured.target());
        assertEquals(requested, configured.requested());
        assertEquals(0, configured.completed());
        assertEquals(MiningSession.State.IDLE, configured.state());
        assertEquals("", configured.error());
        assertNull(configured.runId());
    }

    private static List<String> uniqueTargets(int count) {
        List<String> targets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            targets.add("minecraft:test_block_" + index);
        }
        return targets;
    }
}
