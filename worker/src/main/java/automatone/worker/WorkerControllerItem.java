package automatone.worker;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Mk IV remote terminal. The item stores no worker identity and is authorized from the placed hub. */
public final class WorkerControllerItem extends Item {
    public WorkerControllerItem() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            MinecraftServer server = Objects.requireNonNull(player.getServer());
            if (!player.getAbilities().instabuild
                    && !ControlHubRegistry.get(server).hasRemoteController(player.getUUID())) {
                player.displayClientMessage(Component.literal(
                        "Remote Automatone control requires your placed Control Hub Mk IV."), false);
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            WorkerMenu.open(player, null, false, 0);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}