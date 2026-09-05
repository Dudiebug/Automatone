package automatone.worker.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WorkerGameTestMod.MOD_ID)
public final class WorkerGameTestMod {
    public static final String MOD_ID = "automatone_worker_gametest";

    public WorkerGameTestMod(IEventBus modEventBus) {
        // GameTest discovery is the only responsibility of this test mod.
    }
}
