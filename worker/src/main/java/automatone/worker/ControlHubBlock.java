package automatone.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Physical terminal, worker-cap anchor and collection point. */
public final class ControlHubBlock extends Block {
    private final ControlHubTier tier;

    public ControlHubBlock(ControlHubTier tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ControlHubTier tier() {
        return tier;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player) {
            try {
                ControlHubRegistry.get(serverLevel.getServer()).register(serverLevel, pos, player.getUUID(), tier);
                player.displayClientMessage(Component.literal(tier.displayName() + " online: "
                        + tier.workerSlots() + (tier.workerSlots() == 1 ? " worker slot." : " worker slots.")), false);
            } catch (IllegalStateException failure) {
                player.displayClientMessage(Component.literal("That Control Hub position is already owned."), false);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ControlHubRegistry hubs = ControlHubRegistry.get(serverLevel.getServer());
        if (!hubs.isOwner(serverLevel, pos, player.getUUID())) {
            player.displayClientMessage(Component.literal("This Control Hub is not yours."), false);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            int moved = hubs.withdrawAll(serverLevel, pos, player);
            player.displayClientMessage(Component.literal(moved == 0 ? "Control Hub storage is empty."
                    : "Withdrew " + moved + " items from Control Hub storage."), false);
            return InteractionResult.CONSUME;
        }
        try {
            WorkerMenu.open(player, null, false, 0);
        } catch (IllegalStateException failure) {
            player.displayClientMessage(Component.literal("Control Hub could not open: " + failure.getMessage()), false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            ControlHubRegistry.get(serverLevel.getServer()).removeAndDrop(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}