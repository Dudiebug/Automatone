package automatone.worker;

import net.minecraft.core.BlockPos;

/** Transient state of one server-side block interaction, including its tick budget. */
final class WorkerBreakState {
    private BlockPos target;
    private float progress;
    private long lastTick = Long.MIN_VALUE;

    boolean matches(BlockPos pos) {
        return target != null && !complete() && target.equals(pos);
    }

    void start(BlockPos pos) {
        target = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        progress = 0.0F;
    }

    boolean advance(long tick, float increment) {
        if (target == null || complete() || tick <= lastTick
                || !Float.isFinite(increment) || increment <= 0.0F) {
            return false;
        }
        lastTick = tick;
        progress = Math.min(1.0F, progress + increment);
        return complete();
    }

    void reset() {
        target = null;
        progress = 0.0F;
        // A retarget or abort in this tick must not buy another progress step.
    }

    BlockPos target() {
        return target == null ? null : new BlockPos(target.getX(), target.getY(), target.getZ());
    }

    float progress() {
        return progress;
    }

    boolean complete() {
        return progress >= 1.0F;
    }

    long lastTick() {
        return lastTick;
    }
}
