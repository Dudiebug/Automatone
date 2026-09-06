package automatone.worker;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server worker with explicit, transient ownership of one native runtime. */
public class WorkerEntity extends Mob implements Container {
    private final SimpleContainer inventory = new SimpleContainer(9);
    private final MiningSession miningSession = new MiningSession();
    private final WorkerChunkLoading chunkLoading = new WorkerChunkLoading();
    private final WorkerContext context = new WorkerContext(this);
    private int selectedSlot;
    private IBaritone runtime;
    private UUID owner;
    private boolean pendingResume;

    public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(true);
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
        updateSwingTime();
        if (!level().isClientSide()) {
            // Native look owns entity yaw; expose the same facing through vanilla head tracking.
            setYHeadRot(getYRot());
            ((WorkerEntityController) context.playerController()).validateBreakingTarget();
            if (miningSession.snapshot().state() == MiningSession.State.RUNNING
                    && !pendingResume && runtime != null && !runtime.getMineProcess().isActive()) {
                miningSession.fail("NATIVE_STOPPED");
            }
            getNavigation().stop();
            if (onGround() && xxa == 0.0F && zza == 0.0F) {
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            }
        }
        // Preserve vanilla travel, collision, gravity and jumping.
        super.aiStep();
        if (level() instanceof ServerLevel serverLevel) {
            syncChunks();
            if (pendingResume && runtime != null && isAlive() && !isRemoved()
                    && WorkerChunkLoading.ready(serverLevel, chunkPosition())) {
                pendingResume = false;
                ResourceLocation target = ResourceLocation.parse(miningSession.snapshot().target());
                try {
                    runtime.getMineProcess().mine(BuiltInRegistries.BLOCK.get(target));
                } catch (RuntimeException failure) {
                    miningSession.fail("NATIVE_START_FAILED");
                    cancelNativeMining();
                }
            }
        }
    }

    public IBaritone runtime() {
        return runtime;
    }

    public MiningSession.Snapshot miningStatus() {
        return miningSession.snapshot();
    }

    public Optional<UUID> ownerUUID() {
        return Optional.ofNullable(owner);
    }

    public void claim(UUID claimant) {
        requireServerThread();
        Objects.requireNonNull(claimant);
        if (owner != null && !owner.equals(claimant)) {
            throw new IllegalStateException("WORKER_OWNED");
        }
        owner = claimant;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", 1);
        NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, getItem(slot));
        }
        CompoundTag storedInventory = new CompoundTag();
        ContainerHelper.saveAllItems(storedInventory, items, registryAccess());
        saved.put("Inventory", storedInventory);
        saved.putInt("SelectedSlot", selectedSlot);
        if (owner != null) {
            saved.putUUID("Owner", owner);
        }
        MiningSession.Snapshot state = miningSession.snapshot();
        CompoundTag job = new CompoundTag();
        job.putString("Target", state.target());
        job.putInt("Requested", state.requested());
        job.putLong("Completed", state.completed());
        job.putString("State", state.state().name());
        job.putString("Error", state.error());
        saved.put("Job", job);
        tag.put("AutomatoneWorker", saved);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        pendingResume = false;
        owner = null;
        miningSession.restore(new MiningSession.Snapshot("", 0, 0, MiningSession.State.IDLE, ""));
        if (!tag.contains("AutomatoneWorker")) {
            return;
        }
        CompoundTag saved = tag.getCompound("AutomatoneWorker");
        if (saved.contains("Inventory", Tag.TAG_COMPOUND)) {
            NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(saved.getCompound("Inventory"), items, registryAccess());
            for (int slot = 0; slot < items.size(); slot++) {
                setItem(slot, items.get(slot));
            }
        }
        selectedSlot = Math.clamp(saved.getInt("SelectedSlot"), 0, getContainerSize() - 1);
        if (saved.hasUUID("Owner")) {
            owner = saved.getUUID("Owner");
        }
        try {
            CompoundTag job = saved.getCompound("Job");
            if (!saved.contains("Version", Tag.TAG_INT) || saved.getInt("Version") != 1
                    || !job.contains("Target", Tag.TAG_STRING) || !job.contains("Requested", Tag.TAG_INT)
                    || !job.contains("Completed", Tag.TAG_LONG) || !job.contains("State", Tag.TAG_STRING)
                    || !job.contains("Error", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("INVALID_SAVED_JOB");
            }
            MiningSession.Snapshot state = new MiningSession.Snapshot(job.getString("Target"), job.getInt("Requested"),
                    job.getLong("Completed"), MiningSession.State.valueOf(job.getString("State")), job.getString("Error"));
            if (!state.target().isEmpty()) {
                ResourceLocation target = ResourceLocation.tryParse(state.target());
                if (target == null || BuiltInRegistries.BLOCK.getOptional(target)
                        .filter(block -> !block.defaultBlockState().isAir()).isEmpty()) {
                    throw new IllegalArgumentException("INVALID_SAVED_JOB");
                }
            }
            miningSession.restore(state);
            pendingResume = state.state() == MiningSession.State.RUNNING;
        } catch (IllegalArgumentException invalid) {
            miningSession.fail("INVALID_SAVED_JOB");
        }
    }

    void onBlockDestroyed(BlockState state) {
        if (miningSession.recordBreak(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
            cancelNativeMining();
        }
    }

    /** Server-owned product request; zero requests unlimited source blocks. */
    public void startMining(ResourceLocation target, int requested) {
        requireServerThread();
        if (runtime == null || isRemoved() || !isAlive()) {
            throw new IllegalStateException("WORKER_UNAVAILABLE");
        }
        if (runtime.getMineProcess().isActive()) {
            throw new IllegalStateException("WORKER_BUSY");
        }
        if (BuiltInRegistries.BLOCK.getOptional(Objects.requireNonNull(target))
                .filter(candidate -> !candidate.defaultBlockState().isAir()).isEmpty()) {
            throw new IllegalArgumentException("INVALID_BLOCK");
        }
        miningSession.start(target.toString(), requested);
        pendingResume = true;
        syncChunks();
    }

    private void cancelNativeMining() {
        if (runtime != null) {
            runtime.getMineProcess().cancel();
        }
        context.playerController().resetBlockRemoving();
        syncChunks();
    }

    private void syncChunks() {
        if (level() instanceof ServerLevel serverLevel && isAddedToLevel()) {
            if (isRemoved() || !isAlive()) {
                chunkLoading.release(serverLevel, getUUID());
            } else {
                boolean active = miningSession.snapshot().state() == MiningSession.State.RUNNING
                        || (runtime != null && runtime.getMineProcess().isActive());
                chunkLoading.sync(serverLevel, getUUID(), chunkPosition(), active);
            }
        }
    }

    public void stopMining() {
        requireServerThread();
        pendingResume = false;
        miningSession.stop();
        cancelNativeMining();
    }

    private void requireServerThread() {
        if (!(level() instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) {
            throw new IllegalStateException("Worker jobs require the server thread");
        }
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return inventory.canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack remainder = inventory.addItem(itemEntity.getItem());
        int collected = itemEntity.getItem().getCount() - remainder.getCount();
        if (collected > 0) {
            onItemPickup(itemEntity);
            take(itemEntity, collected);
            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }
        }
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
        pendingResume = miningSession.snapshot().state() == MiningSession.State.RUNNING;
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
        syncChunks();
    }

    @Override
    public void onRemovedFromLevel() {
        if (level() instanceof ServerLevel serverLevel && getRemovalReason() != RemovalReason.UNLOADED_TO_CHUNK) {
            chunkLoading.release(serverLevel, getUUID());
        }
        detachRuntime();
        super.onRemovedFromLevel();
    }

    @Override
    public void remove(RemovalReason reason) {
        detachRuntime();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel serverLevel) {
            chunkLoading.release(serverLevel, getUUID());
        }
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
