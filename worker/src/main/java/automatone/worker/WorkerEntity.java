package automatone.worker;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

/** Minimal inventory host; native runtime attachment is a separate lifecycle. */
public class WorkerEntity extends Mob implements Container {
    private final SimpleContainer inventory = new SimpleContainer(9);
    private int selectedSlot;

    public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public Container inventory() {
        return this;
    }

    @Override
    public int getContainerSize() {
        return inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return inventory.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        inventory.setChanged();
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
    }

    @Override
    public boolean stillValid(Player player) {
        return inventory.stillValid(player);
    }

    // The Container contract serves the native host, not hopper storage automation.
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return false;
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        Objects.checkIndex(slot, inventory.getContainerSize());
        selectedSlot = slot;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? inventory.getItem(selectedSlot) : super.getItemBySlot(slot);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            inventory.setItem(selectedSlot, stack);
        } else {
            super.setItemSlot(slot, stack);
        }
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return List.of(getMainHandItem(), getOffhandItem());
    }
}
