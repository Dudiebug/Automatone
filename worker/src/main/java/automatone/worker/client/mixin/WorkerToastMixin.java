package automatone.worker.client.mixin;

import automatone.worker.client.WorkerCompletionToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve every other toast's audio; our payload owns its independent quiet sound preference. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class WorkerToastMixin {
    @Shadow @Final private Toast toast;

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;playSound(Lnet/minecraft/client/sounds/SoundManager;)V"),
            require = 2, expect = 2)
    protected void workerTransitionSound(Toast.Visibility visibility, SoundManager soundManager) {
        if (!(toast instanceof WorkerCompletionToast)) { visibility.playSound(soundManager); }
    }
}
