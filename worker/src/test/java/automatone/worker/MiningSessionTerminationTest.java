package automatone.worker;

import baritone.api.process.IMineProcess.TerminationReason;
import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MiningSessionTerminationTest {
    @Test
    public void nativeFailuresRetainRunConfigurationAndProgress() {
        Map<TerminationReason, String> errors = Map.of(
                TerminationReason.CANCELLED, "INTERRUPTED", TerminationReason.COMPLETED, "INTERNAL_FAILURE",
                TerminationReason.NO_TARGETS, "NO_TARGETS", TerminationReason.PATH_FAILED, "PATH_FAILED",
                TerminationReason.BREAK_DISABLED, "BREAK_DISABLED", TerminationReason.INTERNAL_FAILURE, "INTERNAL_FAILURE");
        for (Map.Entry<TerminationReason, String> expected : errors.entrySet()) {
            MiningSession session = new MiningSession();
            session.start("minecraft:iron_ore", 3);
            session.recordBreak("minecraft:iron_ore");
            MiningSession.Snapshot before = session.snapshot();
            session.nativeStopped(expected.getKey());
            MiningSession.Snapshot after = session.snapshot();
            assertEquals(MiningSession.State.FAILED, after.state());
            assertEquals(before.targets(), after.targets());
            assertEquals(before.requested(), after.requested());
            assertEquals(before.completed(), after.completed());
            assertEquals(before.runId(), after.runId());
            assertEquals(expected.getValue(), after.error());
        }
    }

    @Test
    public void nativeCleanupCannotOverwritePauseStopOrExactCompletion() {
        MiningSession session = new MiningSession();
        assertCleanupPreserves(session);
        session.start("minecraft:iron_ore", 1);
        session.pause();
        assertCleanupPreserves(session);
        session.stop();
        assertEquals(MiningSession.State.CANCELLED, session.snapshot().state());
        assertCleanupPreserves(session);
        session.start("minecraft:iron_ore", 1);
        session.recordBreak("minecraft:iron_ore");
        assertEquals(MiningSession.State.COMPLETED, session.snapshot().state());
        assertCleanupPreserves(session);
        session.fail("INVALID_SAVED_JOB");
        assertCleanupPreserves(session);
    }

    private static void assertCleanupPreserves(MiningSession session) {
        MiningSession.Snapshot before = session.snapshot();
        for (TerminationReason reason : TerminationReason.values()) {
            session.nativeStopped(reason);
            assertEquals(before, session.snapshot());
        }
    }
}
