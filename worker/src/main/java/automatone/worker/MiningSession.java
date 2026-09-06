package automatone.worker;

/** Product progress counts destroyed source blocks, independently of native item quantities. */
public final class MiningSession {
    public static final int MAX_REQUESTED_BLOCKS = 1_000_000;

    public enum State {
        IDLE, RUNNING, COMPLETED, CANCELLED, FAILED
    }

    public record Snapshot(String target, int requested, long completed, State state, String error) {
        public boolean unlimited() {
            return requested == 0;
        }
    }

    private Snapshot current = new Snapshot("", 0, 0, State.IDLE, "");

    public Snapshot snapshot() {
        return current;
    }

    void restore(Snapshot saved) {
        boolean idle = saved.state() == State.IDLE;
        boolean invalid = saved.requested() < 0 || saved.requested() > MAX_REQUESTED_BLOCKS
                || saved.completed() < 0 || (saved.requested() > 0 && saved.completed() > saved.requested())
                || (idle && (!saved.target().isEmpty() || saved.requested() != 0 || saved.completed() != 0))
                || (!idle && saved.state() != State.FAILED && saved.target().isBlank())
                || (saved.state() == State.RUNNING && saved.requested() > 0 && saved.completed() == saved.requested())
                || (saved.state() == State.COMPLETED && (saved.requested() == 0 || saved.completed() != saved.requested()));
        if (invalid) {
            throw new IllegalArgumentException("INVALID_SAVED_JOB");
        }
        current = saved;
    }

    void start(String target, int requested) {
        if (current.state() == State.RUNNING) {
            throw new IllegalStateException("WORKER_BUSY");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("INVALID_BLOCK");
        }
        if (requested < 0 || requested > MAX_REQUESTED_BLOCKS) {
            throw new IllegalArgumentException("INVALID_QUANTITY");
        }
        current = new Snapshot(target, requested, 0, State.RUNNING, "");
    }

    boolean recordBreak(String block) {
        if (current.state() != State.RUNNING || !current.target().equals(block)) {
            return false;
        }
        long completed = current.completed() == Long.MAX_VALUE ? Long.MAX_VALUE : current.completed() + 1;
        State state = !current.unlimited() && completed == current.requested() ? State.COMPLETED : State.RUNNING;
        current = new Snapshot(current.target(), current.requested(), completed, state, "");
        return state == State.COMPLETED;
    }

    void stop() {
        if (current.state() == State.RUNNING) {
            current = new Snapshot(current.target(), current.requested(), current.completed(), State.CANCELLED, "");
        }
    }

    void fail(String error) {
        current = new Snapshot(current.target(), current.requested(), current.completed(), State.FAILED, error);
    }
}
