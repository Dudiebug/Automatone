package automatone.worker;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.util.List;
import java.util.Objects;

/** Server worker with explicit, transient ownership of one native runtime. */
public class WorkerEntity extends Mob implements Container {
    private final SimpleContainer inventory = new SimpleContainer(9);
    private final WorkerContext context = new WorkerContext(this);
    private int selectedSlot;
    private IBaritone runtime;

    public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        // Native server input owns this worker; vanilla controls must not replace it.
        moveControl = new MoveControl(this) {
            @Override
            public void tick() {
            }
        };
        lookControl = new LookControl(this) {
            @Override
            public void tick() {
            }
        };
        jumpControl = new JumpControl(this) {
            @Override
            public void tick() {
            }
        };
    }

    @Override
    public float getSpeed() {
        // Mob.setSpeed also changes forward input. Travel needs only the attribute.
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide()) {
            ((WorkerEntityController) context.playerController()).validateBreakingTarget();
            getNavigation().stop();
            if (onGround() && xxa == 0.0F && zza == 0.0F) {
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            }
        }
        // Preserve vanilla travel, collision, gravity and jumping.
        super.aiStep();
    }

    public IBaritone runtime() {
        return runtime;
    }

    /** Repeated attachment of a loaded worker preserves its context and runtime. */
    public void attachRuntime() {
        if (runtime == null && isAddedToLevel() && !isRemoved() && level() instanceof ServerLevel serverLevel) {
            runtime = BaritoneAPI.getProvider().createBaritone(context,
                    serverLevel.getServer().getWorldPath(LevelResource.ROOT)
                            .resolve("automatone").resolve(getUUID().toString()));
        }
    }

    public void detachRuntime() {
        context.playerController().resetBlockRemoving();
        if (runtime != null) {
            BaritoneAPI.getProvider().destroyBaritone(runtime);
            runtime = null;
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        attachRuntime();
    }

    @Override
    public void onRemovedFromLevel() {
        detachRuntime();
        super.onRemovedFromLevel();
    }

    @Override
    public void remove(RemovalReason reason) {
        detachRuntime();
        super.remove(reason);
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
