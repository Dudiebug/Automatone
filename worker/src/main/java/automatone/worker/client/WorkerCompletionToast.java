package automatone.worker.client;

import automatone.worker.WorkerMod;
import automatone.worker.WorkerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Marker for a native toast whose transition sounds are replaced by one optional quiet chime. */
@EventBusSubscriber(modid = WorkerMod.MOD_ID, value = Dist.CLIENT)
public final class WorkerCompletionToast extends SystemToast {
    private WorkerCompletionToast(Component title, Component message) {
        super(new SystemToastId(5000L), title, message);
    }

    @SubscribeEvent
    public static void notice(WorkerNetwork.NoticeReceived event) {
        Minecraft minecraft = Minecraft.getInstance();
        WorkerNetwork.Notice notice = event.notice();
        if (notice.toast()) {
            Component message = notice.summary()
                    ? Component.translatable("notification.automatone_worker.unread", notice.unread())
                    : Component.translatable("notification.automatone_worker.completed",
                            minecraft.font.plainSubstrByWidth(notice.workerName(), 100), notice.amount());
            minecraft.getToasts().addToast(new WorkerCompletionToast(
                    Component.translatable(notice.summary() ? "notification.automatone_worker.inbox" : "notification.automatone_worker.title"), message));
        }
        if (notice.sound()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.20F));
        }
    }
}
