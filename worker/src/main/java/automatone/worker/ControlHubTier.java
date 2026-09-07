package automatone.worker;

/** Survival progression for physical worker capacity. */
public enum ControlHubTier {
    BASIC("Control Hub Mk I", 1, false),
    REINFORCED("Control Hub Mk II", 3, false),
    ADVANCED("Control Hub Mk III", 6, false),
    DRAGON("Control Hub Mk IV", 10, true);

    private final String displayName;
    private final int workerSlots;
    private final boolean remoteController;

    ControlHubTier(String displayName, int workerSlots, boolean remoteController) {
        this.displayName = displayName;
        this.workerSlots = workerSlots;
        this.remoteController = remoteController;
    }

    public String displayName() {
        return displayName;
    }

    public int workerSlots() {
        return workerSlots;
    }

    /** Mk IV replaces the anywhere-access behavior of the legacy controller item. */
    public boolean remoteController() {
        return remoteController;
    }
}