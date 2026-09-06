package automatone.worker.client;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Vanilla player geometry and animations for the real worker, loaded only on clients. */
@EventBusSubscriber(modid = WorkerMod.MOD_ID, value = Dist.CLIENT)
public final class WorkerRenderer extends HumanoidMobRenderer<WorkerEntity, PlayerModel<WorkerEntity>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(
            WorkerMod.MOD_ID, "textures/entity/worker.png");

    public WorkerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WorkerMod.WORKER.get(), WorkerRenderer::new);
    }

    @Override
    public ResourceLocation getTextureLocation(WorkerEntity worker) {
        return SKIN;
    }

    @Override
    public void render(WorkerEntity worker, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        model.rightArmPose = armPose(worker, HumanoidArm.RIGHT);
        model.leftArmPose = armPose(worker, HumanoidArm.LEFT);
        super.render(worker, yaw, partialTick, pose, buffers, light);
    }

    private static HumanoidModel.ArmPose armPose(WorkerEntity worker, HumanoidArm arm) {
        boolean empty = arm == worker.getMainArm() ? worker.getMainHandItem().isEmpty() : worker.getOffhandItem().isEmpty();
        return empty ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }
}
