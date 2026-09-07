package automatone.worker;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-owned physical handoff/drop-off interactions for real WorkerEntity instances. */
final class WorkerPhysicalInteractions {
    private WorkerPhysicalInteractions() { }

    static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getTarget() instanceof WorkerEntity worker)) {
            return;
        }
        Player player = event.getEntity();
        if (!worker.ownerUUID().filter(player.getUUID()::equals).isPresent()) {
            player.displayClientMessage(Component.literal("That Automatone Worker belongs to another player."), false);
            finish(event, InteractionResult.FAIL);
            return;
        }

        ItemStack held = event.getItemStack();
        if (held.is(ItemTags.PICKAXES)) {
            if (WorkerActions.busy(worker)) {
                player.displayClientMessage(Component.literal("Stop or finish the worker before replacing its tool."), false);
                finish(event, InteractionResult.FAIL);
                return;
            }
            ItemStack previous = worker.getItem(worker.selectedSlot()).copy();
            ItemStack equipped = held.copyWithCount(1);
            worker.setItem(worker.selectedSlot(), equipped);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (!previous.isEmpty()) {
                ItemStack returned = previous.copy();
                player.getInventory().add(returned);
                if (!returned.isEmpty()) {
                    player.drop(returned, false);
                }
            }
            worker.setChanged();
            WorkerRoster.get(player.getServer()).changed(worker);
            player.displayClientMessage(Component.literal("Gave " + equipped.getHoverName().getString()
                    + " to " + worker.getName().getString() + ". Tool durability will be consumed normally."), false);
            finish(event, InteractionResult.SUCCESS);
            return;
        }

        if (held.isEmpty()) {
            ControlHubRegistry hubs = ControlHubRegistry.get(player.getServer());
            ControlHubRegistry.DepositResult deposited = hubs.deposit(worker);
            if (!deposited.hubFound()) {
                player.displayClientMessage(Component.literal(
                        "Bring this worker within 12 blocks of one of your Control Hubs to drop off its inventory."), false);
            } else if (deposited.items() == 0) {
                player.displayClientMessage(Component.literal("This worker has nothing to drop off."), false);
            } else {
                WorkerRoster.get(player.getServer()).changed(worker);
                player.displayClientMessage(Component.literal("Worker dropped off " + deposited.items() + " items"
                        + (deposited.storageFull() ? "; the Control Hub filled before everything fit." : ".")), false);
            }
            finish(event, InteractionResult.SUCCESS);
        }
    }

    private static void finish(PlayerInteractEvent.EntityInteract event, InteractionResult result) {
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}