package automatone.worker;

import baritone.api.utils.IPlayerController;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Objects;

/** Adapts native break intent and inventory access to one real server worker. */
public final class WorkerEntityController implements IPlayerController {
    private final WorkerEntity worker;
    private final WorkerBreakState breaking = new WorkerBreakState();
    private BlockState targetState;
    private ItemStack selectedTool = ItemStack.EMPTY;
    private int selectedSlot = -1;
    private int breakStage = -1;
    private int hitTicks;

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
    public double getBlockReachDistance() {
        return 4.5D;
    }

    @Override
    public boolean hasBrokenBlock() {
        return breaking.complete();
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        if (worker.level().isClientSide()) {
            return false;
        }
        requireServerThread();
        if (side == null || !canTarget(pos)) {
            resetBlockRemoving();
            return false;
        }
        BlockState state = worker.level().getBlockState(pos);
        if (!matchesTarget(pos, state)) {
            resetBlockRemoving();
            breaking.start(pos);
            targetState = state;
            selectedTool = worker.getMainHandItem().copy();
            selectedSlot = worker.selectedSlot();
        }
        long tick = worker.level().getGameTime();
        if (tick <= breaking.lastTick()) {
            return true;
        }
        boolean complete = breaking.advance(tick, destroyProgress(state, pos));
        if (breaking.lastTick() != tick) {
            return false;
        }
        updateBreakEffects(state, pos);
        if (complete) {
            return destroyBlock(state, pos);
        }
        return true;
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        return onPlayerDamageBlock(loc, face);
    }

    private boolean matchesTarget(BlockPos pos, BlockState state) {
        return breaking.matches(pos) && matchesSnapshot(state);
    }

    private boolean matchesSnapshot(BlockState state) {
        return selectedSlot == worker.selectedSlot()
                && Objects.equals(targetState, state)
                && ItemStack.isSameItemSameComponents(selectedTool, worker.getMainHandItem());
    }

    private float destroyProgress(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(worker.level(), pos);
        if (hardness == 0.0F) {
            return 1.0F;
        }
        ItemStack stack = worker.getMainHandItem();
        float speed = stack.getDestroySpeed(state);
        if (speed > 1.0F) {
            speed += (float) worker.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }
        if (MobEffectUtil.hasDigSpeed(worker)) {
            speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(worker) + 1) * 0.2F;
        }
        if (worker.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            speed *= switch (worker.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 0.00081F;
            };
        }
        speed *= (float) worker.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (worker.isEyeInFluid(FluidTags.WATER)) {
            speed *= (float) worker.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }
        if (!worker.onGround()) {
            speed /= 5.0F;
        }
        return speed / hardness / (canHarvest(state, stack) ? 30.0F : 100.0F);
    }

    private static boolean canHarvest(BlockState state, ItemStack tool) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    private void updateBreakEffects(BlockState state, BlockPos pos) {
        int stage = Math.min(9, (int) (breaking.progress() * 10.0F));
        if (stage != breakStage) {
            worker.level().destroyBlockProgress(worker.getId(), pos, stage);
            breakStage = stage;
        }
        if (hitTicks++ % 4 == 0) {
            SoundType sound = state.getSoundType(worker.level(), pos, worker);
            worker.level().playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F);
        }
    }

    private boolean destroyBlock(BlockState state, BlockPos pos) {
        ServerLevel level = (ServerLevel) worker.level();
        if (!state.canEntityDestroy(level, pos, worker) || !EventHooks.canEntityGrief(level, worker)
                || !EventHooks.onEntityDestroyBlock(worker, pos, state)
                || !canTarget(pos) || !matchesSnapshot(level.getBlockState(pos))) {
            resetBlockRemoving();
            return false;
        }
        ItemStack stack = worker.getMainHandItem();
        ItemStack dropTool = stack.copy();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!level.destroyBlock(pos, false, worker)) {
            resetBlockRemoving();
            return false;
        }
        worker.onBlockDestroyed(state);
        clearCracks();
        state.getBlock().destroy(level, pos, state);
        stack.getItem().mineBlock(stack, level, state, pos, worker);
        if (canHarvest(state, dropTool)) {
            Block.dropResources(state, level, pos, blockEntity, worker, dropTool);
        }
        return true;
    }

    private boolean canTarget(BlockPos pos) {
        if (pos == null || !worker.isAlive() || !worker.isAddedToLevel() || worker.isRemoved()
                || !worker.level().hasChunkAt(pos) || !worker.level().getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockState state = worker.level().getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(worker.level(), pos) < 0.0F) {
            return false;
        }
        HitResult trace = RayTraceUtils.rayTraceTowards(worker,
                new Rotation(worker.getYRot(), worker.getXRot()), getBlockReachDistance());
        return trace.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) trace).getBlockPos().equals(pos);
    }

    /** Also invalidate an abandoned interaction when no new break call arrives. */
    public void validateBreakingTarget() {
        if (worker.level().isClientSide() || breaking.target() == null || breaking.complete()) {
            return;
        }
        requireServerThread();
        BlockPos pos = breaking.target();
        if (!canTarget(pos) || !matchesTarget(pos, worker.level().getBlockState(pos))) {
            resetBlockRemoving();
        }
    }

    /** Immutable position of the active interaction, or null while idle/complete. */
    public BlockPos breakingBlock() {
        return breaking.complete() ? null : breaking.target();
    }

    public float breakProgress() {
        return breaking.progress();
    }

    public int breakStage() {
        return breakStage;
    }

    private void requireServerThread() {
        if (!(worker.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Worker block interaction requires the server thread");
        }
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
        if (worker.level().isClientSide()) {
            return;
        }
        requireServerThread();
        clearCracks();
        hitTicks = 0;
        selectedSlot = -1;
        targetState = null;
        selectedTool = ItemStack.EMPTY;
        breaking.reset();
    }

    private void clearCracks() {
        if (breakStage != -1 && breaking.target() != null) {
            worker.level().destroyBlockProgress(worker.getId(), breaking.target(), -1);
        }
        breakStage = -1;
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        // BlockBreakHelper toggles this every tick. Only explicit reset aborts progress.
    }

    @Override
    public void resetDestroyDelay() {
        // BlockBreakHelper owns the inter-block delay; the worker adds no second timer.
    }
}
