package automatone.worker;

import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.cache.IWorldData;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.IPlayerController;
import baritone.api.utils.RayTraceUtils;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Stable, explicit native context for one worker. */
public final class WorkerContext implements IPlayerContext {
    private final WorkerEntity worker;
    private final WorkerEntityController controller;

    WorkerContext(WorkerEntity worker) {
        this.worker = worker;
        this.controller = new WorkerEntityController(worker);
    }

    @Override
    public Settings getSettings() {
        return worker.effectiveSettings();
    }

    @Override
    public LivingEntity player() {
        return worker;
    }

    @Override
    public Container inventory() {
        return worker.inventory();
    }

    @Override
    public int selectedSlot() {
        return worker.selectedSlot();
    }

    @Override
    public void setSelectedSlot(int slot) {
        worker.setSelectedSlot(slot);
    }

    @Override
    public IPlayerController playerController() {
        return controller;
    }

    @Override
    public Level world() {
        return worker.level();
    }

    @Override
    public IWorldData worldData() {
        IBaritone runtime = worker.runtime();
        return runtime == null ? null : runtime.getWorldProvider().getCurrentWorld();
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(worker, playerRotations(), controller.getBlockReachDistance());
    }
}
