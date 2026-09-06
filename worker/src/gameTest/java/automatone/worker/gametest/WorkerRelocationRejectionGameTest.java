package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerChunkLoading;
import automatone.worker.WorkerEntity;
import automatone.worker.WorkerRoster;
import automatone.worker.WorkerRelocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@GameTestHolder("automatone_worker_m5_relocation_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerRelocationRejectionGameTest {
    private WorkerRelocationRejectionGameTest() { }

    @GameTest(template = "provider_smoke", batch = "worker_m5_relocation_tick_dispatch", timeoutTicks = 100)
    public static void registeredServerTickAdvancesPreparationAndCancellationPreventsDeployment(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        WorkerRelocation service = WorkerRelocation.get(helper.getLevel().getServer());
        WorkerRelocation.Status initial = service.deploy(owner, request, Level.OVERWORLD, null);
        helper.assertTrue(initial.state() == WorkerRelocation.State.PENDING && initial.attempts() == 0,
                "The real service must return a pending reservation before starting terrain preparation");
        helper.runAfterDelay(1, () -> {
            try {
                WorkerRelocation.Status prepared = service.status(owner, request);
                helper.assertTrue(prepared.state() == WorkerRelocation.State.PENDING && prepared.attempts() == 1,
                        "Registered server ticks must begin preparation while leaving admission asynchronous");
                helper.assertTrue(service.cancel(owner, request).state() == WorkerRelocation.State.CANCELLED
                                && WorkerRoster.get(helper.getLevel().getServer()).list(owner, false).isEmpty(),
                        "Cancelling pending terrain preparation must prevent a live worker from being created");
                helper.succeed();
            } catch (Throwable failure) {
                helper.fail("Server tick relocation dispatch failed: " + failure);
            } finally {
                service.cancel(owner, request);
            }
        });
    }

    @GameTest(template = "provider_smoke", batch = "worker_m5_destination_rejection", timeoutTicks = 100)
    public static void rejectedDestinationKeepsOriginalWorkerInventoryAndRuntime(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        UUID owner = UUID.randomUUID();
        UUID id = worker.getUUID();
        ServerLevel source = helper.getLevel();
        ServerLevel target = source.getServer().getLevel(Level.NETHER);
        AtomicBoolean rejected = new AtomicBoolean();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel().equals(target) && event.getEntity().getUUID().equals(id)) {
                rejected.set(true);
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            worker.claim(owner);
            worker.setItem(3, new ItemStack(Items.RAW_IRON, 11));
            worker.setSelectedSlot(3);
            worker.startMining(ResourceLocation.withDefaultNamespace("iron_ore"), 64);
            worker.pauseMining();
            MiningSession.Snapshot paused = worker.miningStatus();
            Vec3 origin = worker.position();
            helper.assertTrue(target != null, "The dedicated server must provide the Nether for destination rejection");
            WorkerEntity result = worker.relocateTo(target, new Vec3(0.5, 80, 0.5));
            helper.assertTrue(rejected.get() && result == null, "The real destination join event must reject this transfer");
            helper.assertTrue(source.getEntity(id) == worker && worker.isAlive() && !worker.isRemoved()
                            && worker.position().equals(origin) && target.getEntity(id) == null,
                    "Rejected admission must leave the original entity in its original world and position");
            helper.assertTrue(worker.runtime() != null && !worker.runtime().getMineProcess().isActive()
                            && worker.miningStatus().equals(paused) && worker.selectedSlot() == 3
                            && worker.getItem(3).is(Items.RAW_IRON) && worker.getItem(3).getCount() == 11,
                    "Rejected admission must restore the runtime and retain the paused run and exact inventory");
            helper.assertTrue(WorkerRoster.get(source.getServer()).active(owner, id) == worker,
                    "The roster must continue to resolve the original worker after destination rejection");
            helper.assertTrue(WorkerChunkLoadingGameTest.tickets(source, WorkerChunkLoading.CENTER_CONTROLLER_ID, id)
                            .contains(worker.chunkPosition().toLong())
                            && WorkerChunkLoadingGameTest.tickets(target, WorkerChunkLoading.CENTER_CONTROLLER_ID, id).isEmpty()
                            && WorkerChunkLoadingGameTest.tickets(source, WorkerChunkLoading.WORKING_RING_CONTROLLER_ID, id).isEmpty(),
                    "Rejection must retain only the paused source anchor and no destination or working-ring tickets");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }
}
