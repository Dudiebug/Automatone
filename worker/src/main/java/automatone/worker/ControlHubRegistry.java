package automatone.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Overworld-owned persistence for physical hubs, their storage and worker-to-hub bindings. */
public final class ControlHubRegistry extends SavedData {
    public static final int STORAGE_SLOTS = 27;
    private static final double DEPOSIT_DISTANCE = 12.0D;
    private static final double DEPOSIT_DISTANCE_SQR = DEPOSIT_DISTANCE * DEPOSIT_DISTANCE;

    public record HubRef(ResourceLocation dimension, BlockPos pos, ControlHubTier tier) { }
    public record DepositResult(int items, boolean hubFound, boolean storageFull) { }

    private record HubKey(ResourceLocation dimension, BlockPos pos) { }

    private static final class Hub {
        private final UUID owner;
        private ControlHubTier tier;
        private final NonNullList<ItemStack> storage = NonNullList.withSize(STORAGE_SLOTS, ItemStack.EMPTY);

        private Hub(UUID owner, ControlHubTier tier) {
            this.owner = owner;
            this.tier = tier;
        }
    }

    private final MinecraftServer server;
    private final Map<HubKey, Hub> hubs = new LinkedHashMap<>();
    private final Map<UUID, HubKey> workerHomes = new LinkedHashMap<>();

    private ControlHubRegistry(MinecraftServer server) {
        this.server = Objects.requireNonNull(server);
    }

    public static ControlHubRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new ControlHubRegistry(server),
                        (tag, registries) -> load(server, tag)), "automatone_control_hubs");
    }

    private static ControlHubRegistry load(MinecraftServer server, CompoundTag tag) {
        if (tag.getInt("Version") != 1) {
            throw new IllegalArgumentException("UNSUPPORTED_CONTROL_HUB_VERSION");
        }
        ControlHubRegistry result = new ControlHubRegistry(server);
        for (Tag value : tag.getList("Hubs", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) value;
            ResourceLocation dimension = ResourceLocation.tryParse(saved.getString("Dimension"));
            ControlHubTier tier;
            try {
                tier = ControlHubTier.valueOf(saved.getString("Tier"));
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            if (dimension == null || !saved.hasUUID("Owner")) {
                continue;
            }
            HubKey key = new HubKey(dimension, BlockPos.of(saved.getLong("Position")));
            Hub hub = new Hub(saved.getUUID("Owner"), tier);
            if (saved.contains("Inventory", Tag.TAG_COMPOUND)) {
                ContainerHelper.loadAllItems(saved.getCompound("Inventory"), hub.storage, server.registryAccess());
            }
            result.hubs.put(key, hub);
        }
        for (Tag value : tag.getList("WorkerHomes", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) value;
            ResourceLocation dimension = ResourceLocation.tryParse(saved.getString("Dimension"));
            if (dimension != null && saved.hasUUID("Worker")) {
                HubKey key = new HubKey(dimension, BlockPos.of(saved.getLong("Position")));
                if (result.hubs.containsKey(key)) {
                    result.workerHomes.put(saved.getUUID("Worker"), key);
                }
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", 1);
        ListTag savedHubs = new ListTag();
        hubs.forEach((key, hub) -> {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("Owner", hub.owner);
            saved.putString("Tier", hub.tier.name());
            saved.putString("Dimension", key.dimension().toString());
            saved.putLong("Position", key.pos().asLong());
            CompoundTag inventory = new CompoundTag();
            ContainerHelper.saveAllItems(inventory, hub.storage, server.registryAccess());
            saved.put("Inventory", inventory);
            savedHubs.add(saved);
        });
        tag.put("Hubs", savedHubs);

        ListTag savedHomes = new ListTag();
        workerHomes.forEach((worker, key) -> {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("Worker", worker);
            saved.putString("Dimension", key.dimension().toString());
            saved.putLong("Position", key.pos().asLong());
            savedHomes.add(saved);
        });
        tag.put("WorkerHomes", savedHomes);
        return tag;
    }

    public void register(ServerLevel level, BlockPos pos, UUID owner, ControlHubTier tier) {
        requireThread();
        HubKey key = key(level, pos);
        Hub previous = hubs.get(key);
        if (previous != null && !previous.owner.equals(owner)) {
            throw new IllegalStateException("CONTROL_HUB_OWNED");
        }
        if (previous == null) {
            hubs.put(key, new Hub(Objects.requireNonNull(owner), Objects.requireNonNull(tier)));
        } else {
            previous.tier = Objects.requireNonNull(tier);
        }
        setDirty();
    }

    public boolean isOwner(ServerLevel level, BlockPos pos, UUID owner) {
        requireThread();
        Hub hub = hubs.get(key(level, pos));
        return hub != null && hub.owner.equals(owner);
    }

    public Optional<HubRef> nearestOwnedHub(ServerLevel level, UUID owner, Vec3 position, double maxDistance) {
        requireThread();
        double maximum = maxDistance * maxDistance;
        return hubs.entrySet().stream()
                .filter(entry -> entry.getValue().owner.equals(owner)
                        && entry.getKey().dimension().equals(level.dimension().location()))
                .filter(entry -> position.distanceToSqr(Vec3.atCenterOf(entry.getKey().pos())) <= maximum)
                .min(Comparator.comparingDouble(entry -> position.distanceToSqr(Vec3.atCenterOf(entry.getKey().pos()))))
                .map(entry -> new HubRef(entry.getKey().dimension(), entry.getKey().pos(), entry.getValue().tier));
    }

    public void bindWorker(UUID worker, HubRef hub) {
        requireThread();
        HubKey key = new HubKey(hub.dimension(), hub.pos().immutable());
        if (!hubs.containsKey(key)) {
            throw new IllegalStateException("CONTROL_HUB_UNAVAILABLE");
        }
        workerHomes.put(Objects.requireNonNull(worker), key);
        setDirty();
    }

    public DepositResult deposit(WorkerEntity worker) {
        requireThread();
        UUID owner = worker.ownerUUID().orElse(null);
        if (owner == null || !(worker.level() instanceof ServerLevel level)) {
            return new DepositResult(0, false, false);
        }
        HubKey home = workerHomes.get(worker.getUUID());
        Hub hub = home == null ? null : hubs.get(home);
        boolean usableHome = hub != null && hub.owner.equals(owner)
                && home.dimension().equals(level.dimension().location())
                && worker.position().distanceToSqr(Vec3.atCenterOf(home.pos())) <= DEPOSIT_DISTANCE_SQR;
        if (!usableHome) {
            Optional<HubRef> nearest = nearestOwnedHub(level, owner, worker.position(), DEPOSIT_DISTANCE);
            if (nearest.isEmpty()) {
                return new DepositResult(0, false, false);
            }
            HubRef replacement = nearest.orElseThrow();
            home = new HubKey(replacement.dimension(), replacement.pos());
            hub = hubs.get(home);
            workerHomes.put(worker.getUUID(), home);
            setDirty();
        }

        int moved = 0;
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            ItemStack source = worker.getItem(slot);
            if (source.isEmpty()) {
                continue;
            }
            int before = source.getCount();
            ItemStack remainder = insert(hub.storage, source);
            int transferred = before - remainder.getCount();
            if (transferred > 0) {
                worker.setItem(slot, remainder);
                moved += transferred;
            }
        }
        if (moved > 0) {
            worker.setChanged();
            setDirty();
        }
        boolean itemsRemain = false;
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            if (!worker.getItem(slot).isEmpty()) {
                itemsRemain = true;
                break;
            }
        }
        return new DepositResult(moved, true, itemsRemain);
    }

    public int withdrawAll(ServerLevel level, BlockPos pos, Player player) {
        requireThread();
        Hub hub = hubs.get(key(level, pos));
        if (hub == null || !hub.owner.equals(player.getUUID())) {
            throw new IllegalStateException("NOT_CONTROL_HUB_OWNER");
        }
        int moved = 0;
        for (int slot = 0; slot < hub.storage.size(); slot++) {
            ItemStack stored = hub.storage.get(slot);
            if (stored.isEmpty()) {
                continue;
            }
            moved += stored.getCount();
            ItemStack transfer = stored.copy();
            player.getInventory().add(transfer);
            if (!transfer.isEmpty()) {
                player.drop(transfer, false);
            }
            hub.storage.set(slot, ItemStack.EMPTY);
        }
        if (moved > 0) {
            setDirty();
        }
        return moved;
    }

    public void removeAndDrop(ServerLevel level, BlockPos pos) {
        requireThread();
        HubKey key = key(level, pos);
        Hub removed = hubs.remove(key);
        if (removed == null) {
            return;
        }
        workerHomes.entrySet().removeIf(entry -> entry.getValue().equals(key));
        for (ItemStack stack : removed.storage) {
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.75D,
                        pos.getZ() + 0.5D, stack.copy());
                level.addFreshEntity(item);
            }
        }
        setDirty();
    }

    private static ItemStack insert(NonNullList<ItemStack> storage, ItemStack source) {
        ItemStack remainder = source.copy();
        for (ItemStack target : storage) {
            if (remainder.isEmpty()) {
                break;
            }
            if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, remainder)) {
                continue;
            }
            int room = target.getMaxStackSize() - target.getCount();
            if (room > 0) {
                int transfer = Math.min(room, remainder.getCount());
                target.grow(transfer);
                remainder.shrink(transfer);
            }
        }
        for (int slot = 0; slot < storage.size() && !remainder.isEmpty(); slot++) {
            if (!storage.get(slot).isEmpty()) {
                continue;
            }
            int transfer = Math.min(remainder.getMaxStackSize(), remainder.getCount());
            storage.set(slot, remainder.copyWithCount(transfer));
            remainder.shrink(transfer);
        }
        return remainder;
    }

    private static HubKey key(ServerLevel level, BlockPos pos) {
        return new HubKey(level.dimension().location(), pos.immutable());
    }

    private void requireThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Control hub access requires the server thread");
        }
    }
}