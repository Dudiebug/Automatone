package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** Shared server-side spawn path for registered worker GameTests. */
public final class WorkerGameTestSupport {

    private static final BlockPos SPAWN_POSITION = new BlockPos(0, 1, 0);

    private WorkerGameTestSupport() {
    }

    public static WorkerEntity spawnWorker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorkerEntity worker = WorkerMod.WORKER.get().create(level);
        if (worker == null) {
            throw new AssertionError("Registered worker entity type did not create an entity");
        }

        BlockPos position = helper.absolutePos(SPAWN_POSITION);
        worker.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        worker.setNoGravity(true);
        if (!level.addFreshEntity(worker)) {
            throw new AssertionError("Registered worker entity was rejected by the ServerLevel");
        }
        return worker;
    }

    public static void discardWorker(WorkerEntity worker) {
        if (worker != null && !worker.isRemoved()) {
            worker.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }
}
