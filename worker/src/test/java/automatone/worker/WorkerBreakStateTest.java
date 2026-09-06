package automatone.worker;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WorkerBreakStateTest {
    private static final BlockPos FIRST_TARGET = new BlockPos(1, 64, 1);
    private static final BlockPos SECOND_TARGET = new BlockPos(2, 64, 1);

    @Test
    public void startCopiesTargetAndResetReturnsToIdle() {
        WorkerBreakState state = new WorkerBreakState();
        BlockPos.MutableBlockPos suppliedTarget = new BlockPos.MutableBlockPos(1, 64, 1);

        assertNull(state.target());
        assertEquals(0.0F, state.progress(), 0.0F);
        assertFalse(state.complete());
        assertFalse(state.matches(FIRST_TARGET));

        state.start(suppliedTarget);
        suppliedTarget.move(10, 0, 0);

        assertEquals(FIRST_TARGET, state.target());
        assertNotSame(state.target(), state.target());
        assertEquals(0.0F, state.progress(), 0.0F);
        assertFalse(state.complete());
        assertTrue(state.matches(FIRST_TARGET));
        assertFalse(state.matches(SECOND_TARGET));

        state.reset();

        assertNull(state.target());
        assertEquals(0.0F, state.progress(), 0.0F);
        assertFalse(state.complete());
        assertFalse(state.matches(FIRST_TARGET));
    }

    @Test
    public void advanceRunsOncePerTickAndCompletesOnlyOnce() {
        WorkerBreakState state = new WorkerBreakState();
        state.start(FIRST_TARGET);

        assertFalse(state.advance(10L, 0.4F));
        assertEquals(0.4F, state.progress(), 0.0F);
        assertFalse(state.advance(10L, 0.4F));
        assertEquals(0.4F, state.progress(), 0.0F);
        assertFalse(state.advance(9L, 0.4F));
        assertEquals(0.4F, state.progress(), 0.0F);

        assertTrue(state.advance(11L, 0.7F));
        assertEquals(1.0F, state.progress(), 0.0F);
        assertTrue(state.complete());
        assertFalse(state.matches(FIRST_TARGET));
        assertFalse(state.advance(12L, 0.1F));
        assertEquals(1.0F, state.progress(), 0.0F);
        assertEquals(11L, state.lastTick());
    }

    @Test
    public void invalidAdvancesAndRetargetsDoNotBypassTheTickFence() {
        WorkerBreakState state = new WorkerBreakState();

        assertFalse(state.advance(20L, 0.5F));

        state.start(FIRST_TARGET);
        assertFalse(state.advance(20L, 0.0F));
        assertFalse(state.advance(20L, -0.5F));
        assertFalse(state.advance(20L, Float.NaN));
        assertFalse(state.advance(20L, Float.POSITIVE_INFINITY));
        assertEquals(0.0F, state.progress(), 0.0F);

        assertFalse(state.advance(20L, 0.5F));
        assertEquals(0.5F, state.progress(), 0.0F);
        state.start(SECOND_TARGET);
        assertFalse(state.advance(20L, 0.5F));
        assertEquals(0.0F, state.progress(), 0.0F);
        assertFalse(state.advance(21L, 0.5F));
        assertEquals(0.5F, state.progress(), 0.0F);

        state.reset();
        state.start(FIRST_TARGET);
        assertFalse(state.advance(21L, 0.5F));
        assertEquals(0.0F, state.progress(), 0.0F);
        assertFalse(state.advance(22L, 0.5F));
        assertEquals(0.5F, state.progress(), 0.0F);
    }

}
