package automatone.worker.mixin;

import automatone.worker.WorkerMenu;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla resynchronizes stale clicks after applying them; controlled worker slots reject them first. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WorkerPacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;suppressRemoteUpdates()V"), cancellable = true)
    protected void rejectStaleWorkerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (player.containerMenu instanceof WorkerMenu menu && !menu.acceptsClick(player, packet.getStateId())) {
            menu.broadcastFullState();
            callback.cancel();
        }
    }
}
