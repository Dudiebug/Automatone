package automatone.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Activates a small golem-style shell and commits a real roster worker to the nearest owned hub. */
public final class WorkerCoreItem extends Item {
    private static final double HUB_RANGE = 32.0D;

    public WorkerCoreItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Automatone Core");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        BlockPos head = context.getClickedPos();
        Optional<Shell> shell = match(level, head);
        if (shell.isEmpty()) {
            context.getPlayer().displayClientMessage(Component.literal(
                    "Automatone shell: carved pumpkin head, copper-block body, two iron-block arms, redstone-block base."), false);
            return InteractionResult.FAIL;
        }

        UUID owner = context.getPlayer().getUUID();
        ControlHubRegistry hubs = ControlHubRegistry.get(level.getServer());
        Optional<ControlHubRegistry.HubRef> nearby = hubs.nearestOwnedHub(level, owner,
                Vec3.atCenterOf(shell.orElseThrow().body()), HUB_RANGE);
        if (nearby.isEmpty()) {
            context.getPlayer().displayClientMessage(Component.literal(
                    "Build this worker within 32 blocks of one of your Control Hubs."), false);
            return InteractionResult.FAIL;
        }
        ControlHubRegistry.HubRef hub = nearby.orElseThrow();
        WorkerRoster roster = WorkerRoster.get(level.getServer());
        int active = roster.list(owner, false).size();
        if (active >= hub.tier().workerSlots()) {
            context.getPlayer().displayClientMessage(Component.literal("No free Automatone slots. "
                    + hub.tier().displayName() + " supports " + hub.tier().workerSlots() + "."), false);
            return InteractionResult.FAIL;
        }

        UUID request = UUID.randomUUID();
        Shell matched = shell.orElseThrow();
        Map<BlockPos, BlockState> consumed = matched.states();
        boolean shellConsumed = false;
        boolean deployed = false;
        try {
            roster.reserve(owner, request, null);
            consumed.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            shellConsumed = true;
            BlockPos body = matched.body();
            WorkerEntity worker = roster.deploy(owner, request, level,
                    new Vec3(body.getX() + 0.5D, body.getY(), body.getZ() + 0.5D));
            deployed = true;
            hubs.bindWorker(worker.getUUID(), hub);
            roster.changed(worker);
            if (!context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            context.getPlayer().displayClientMessage(Component.literal("Automatone Worker online and linked to "
                    + hub.tier().displayName() + "."), false);
            return InteractionResult.SUCCESS;
        } catch (RuntimeException failure) {
            if (!deployed && shellConsumed) {
                consumed.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            }
            if (!deployed) {
                try {
                    roster.cancelReservation(owner, request);
                } catch (RuntimeException ignored) {
                    // The reservation may already have been rolled back by deployment validation.
                }
            }
            context.getPlayer().displayClientMessage(Component.literal("Worker activation failed: "
                    + failure.getMessage()), false);
            return InteractionResult.FAIL;
        }
    }

    private static Optional<Shell> match(ServerLevel level, BlockPos head) {
        if (!level.getBlockState(head).is(Blocks.CARVED_PUMPKIN)
                && !level.getBlockState(head).is(Blocks.JACK_O_LANTERN)) {
            return Optional.empty();
        }
        BlockPos body = head.below();
        BlockPos base = body.below();
        if (!level.getBlockState(body).is(Blocks.COPPER_BLOCK) || !level.getBlockState(base).is(Blocks.REDSTONE_BLOCK)) {
            return Optional.empty();
        }
        BlockPos first;
        BlockPos second;
        if (level.getBlockState(body.east()).is(Blocks.IRON_BLOCK)
                && level.getBlockState(body.west()).is(Blocks.IRON_BLOCK)) {
            first = body.east();
            second = body.west();
        } else if (level.getBlockState(body.north()).is(Blocks.IRON_BLOCK)
                && level.getBlockState(body.south()).is(Blocks.IRON_BLOCK)) {
            first = body.north();
            second = body.south();
        } else {
            return Optional.empty();
        }
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos pos : new BlockPos[]{head, body, base, first, second}) {
            states.put(pos.immutable(), level.getBlockState(pos));
        }
        return Optional.of(new Shell(body.immutable(), Map.copyOf(states)));
    }

    private record Shell(BlockPos body, Map<BlockPos, BlockState> states) { }
}