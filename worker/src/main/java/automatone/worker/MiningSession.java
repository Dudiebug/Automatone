package automatone.worker;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Product progress counts destroyed source blocks, independently of native item quantities. */
public final class MiningSession {
    public static final int MAX_REQUESTED_BLOCKS = 1_000_000;
    public static final int MAX_TARGET_BLOCKS = 128;

    public enum State {
        IDLE, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED
    }

    public record Snapshot(List<String> targets, int requested, long completed, State state, String error, UUID runId) {
        public Snapshot {
            targets = List.copyOf(targets);
            Objects.requireNonNull(state);
            Objects.requireNonNull(error);
        }

        /** Compatibility for single-target callers and version-one saves. */
        public Snapshot(String target, int requested, long completed, State state, String error) {
            this(target.isEmpty() ? List.of() : List.of(target), requested, completed, state, error,
                    target.isEmpty() || state == State.IDLE ? null : UUID.randomUUID());
        }

        public String target() {
            return targets.isEmpty() ? "" : targets.getFirst();
        }

        public boolean unlimited() {
            return requested == 0;
        }
    }

    private Snapshot current = new Snapshot(List.of(), 0, 0, State.IDLE, "", null);

    public Snapshot snapshot() {
        return current;
    }

    void restore(Snapshot saved) {
        boolean active = saved.state() == State.RUNNING || saved.state() == State.PAUSED;
        boolean invalid = saved.requested() < 0 || saved.requested() > MAX_REQUESTED_BLOCKS
                || saved.completed() < 0 || (saved.requested() > 0 && saved.completed() > saved.requested())
                || saved.targets().size() > MAX_TARGET_BLOCKS
                || saved.targets().stream().anyMatch(String::isBlank)
                || saved.targets().stream().distinct().count() != saved.targets().size()
                || (saved.state() == State.IDLE && (saved.completed() != 0 || saved.runId() != null))
                || (saved.targets().isEmpty() && saved.state() == State.IDLE && saved.requested() != 0)
                || (saved.state() != State.IDLE && saved.state() != State.FAILED && saved.targets().isEmpty())
                || (saved.state() != State.IDLE && saved.state() != State.FAILED && saved.runId() == null)
                || (active && saved.requested() > 0 && saved.completed() == saved.requested())
                || (saved.state() == State.COMPLETED && (saved.requested() == 0 || saved.completed() != saved.requested()));
        if (invalid) {
            throw new IllegalArgumentException("INVALID_SAVED_JOB");
        }
        current = saved;
    }

    void start(String target, int requested) {
        if (target == null) {
            throw new IllegalArgumentException("INVALID_BLOCK");
        }
        start(List.of(target), requested);
    }

    void start(List<String> targets, int requested) {
        ensureNotRunning();
        List<String> checked = validate(targets, requested);
        current = new Snapshot(checked, requested, 0, State.RUNNING, "", UUID.randomUUID());
    }

    void configure(List<String> targets, int requested) {
        ensureNotRunning();
        List<String> checked = validate(targets, requested);
        current = new Snapshot(checked, requested, 0, State.IDLE, "", null);
    }

    private void ensureNotRunning() {
        if (current.state() == State.RUNNING) {
            throw new IllegalStateException("WORKER_BUSY");
        }
    }

    private static List<String> validate(List<String> targets, int requested) {
        if (targets == null || targets.isEmpty() || targets.stream().anyMatch(target -> target == null || target.isBlank())) {
            throw new IllegalArgumentException("INVALID_BLOCK");
        }
        List<String> unique = targets.stream().distinct().toList();
        if (unique.size() > MAX_TARGET_BLOCKS) {
            throw new IllegalArgumentException("TOO_MANY_TARGETS");
        }
        if (requested < 0 || requested > MAX_REQUESTED_BLOCKS) {
            throw new IllegalArgumentException("INVALID_QUANTITY");
        }
        return unique;
    }

    boolean recordBreak(String block) {
        if (current.state() != State.RUNNING || !current.targets().contains(block)) {
            return false;
        }
        long completed = current.completed() == Long.MAX_VALUE ? Long.MAX_VALUE : current.completed() + 1;
        State state = !current.unlimited() && completed == current.requested() ? State.COMPLETED : State.RUNNING;
        current = new Snapshot(current.targets(), current.requested(), completed, state, "", current.runId());
        return state == State.COMPLETED;
    }

    void pause() {
        if (current.state() == State.RUNNING) {
            setState(State.PAUSED, "");
        }
    }

    void resume() {
        if (current.state() != State.PAUSED) {
            throw new IllegalStateException("WORKER_NOT_PAUSED");
        }
        setState(State.RUNNING, "");
    }

    void stop() {
        if (current.state() == State.RUNNING || current.state() == State.PAUSED) {
            setState(State.CANCELLED, "");
        }
    }

    void fail(String error) {
        setState(State.FAILED, error);
    }

    private void setState(State state, String error) {
        current = new Snapshot(current.targets(), current.requested(), current.completed(), state, error, current.runId());
    }
}
