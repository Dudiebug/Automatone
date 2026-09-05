package automatone.worker;

import baritone.api.utils.IPlayerController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Objects;

/** Inventory adaptation only; world interactions belong to the mining milestone. */
public final class WorkerEntityController implements IPlayerController {
    private final WorkerEntity worker;

    WorkerEntityController(WorkerEntity worker) {
        this.worker = worker;
    }

    @Override
    public void syncHeldItem() {
        // The worker reads its main hand directly from its selected container slot.
    }

    @Override
    public void swapContainerSlots(Container container, int firstSlot, int secondSlot) {
        if (container != worker) {
            throw new IllegalArgumentException("Container does not belong to this worker");
        }
        Objects.checkIndex(firstSlot, container.getContainerSize());
        Objects.checkIndex(secondSlot, container.getContainerSize());
        ItemStack first = container.getItem(firstSlot);
        ItemStack second = container.getItem(secondSlot);
        container.setItem(firstSlot, second);
        container.setItem(secondSlot, first);
    }

    @Override
    public GameType getGameType() {
        return GameType.SURVIVAL;
    }

    @Override
    public boolean hasBrokenBlock() {
        return false;
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        return false;
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        return false;
    }

    @Override
    public InteractionResult processRightClickBlock(LivingEntity player, Level world, InteractionHand hand, BlockHitResult result) {
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult processRightClick(LivingEntity player, Level world, InteractionHand hand) {
        return InteractionResult.FAIL;
    }

    @Override
    public void resetBlockRemoving() {
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
    }

    @Override
    public void resetDestroyDelay() {
    }
}
