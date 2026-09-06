package automatone.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Overworld-owned product records. All mutations and reservations run on the server thread. */
public final class WorkerRoster extends SavedData {
    public static final int ACTIVE_LIMIT = 10;
    private final MinecraftServer server;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Profile> profiles = new LinkedHashMap<>();
    private final Map<UUID, Reservation> reservations = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> finishedRequests = new LinkedHashSet<>();

    public record Reservation(UUID request, UUID owner, UUID worker, boolean reactivation) { }
    public record View(UUID worker, UUID owner, boolean retired, long revision, String name,
                       String dimension, BlockPos position) { }
    public record ProfileView(long revision, Map<String, String> settings) {
        public ProfileView { settings = Map.copyOf(settings); }
    }

    private static final class Profile {
        private long revision;
        private Map<String, String> settings = Map.of();
    }

    private static final class Entry {
        private final UUID owner;
        private boolean retired;
        private long revision;
        private String dimension = "";
        private BlockPos position = BlockPos.ZERO;
        private String name = "Worker";
        private Map<String, String> overrides = Map.of();
        private CompoundTag entity = new CompoundTag();

        private Entry(UUID owner) { this.owner = owner; }
    }

    public WorkerRoster(MinecraftServer server) { this.server = Objects.requireNonNull(server); }

    public static WorkerRoster get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new WorkerRoster(server),
                        (tag, registries) -> load(server, tag)), "automatone_workers");
    }

    public static WorkerRoster load(MinecraftServer server, CompoundTag tag) {
        if (tag.getInt("Version") != 1) {
            throw new IllegalArgumentException("UNSUPPORTED_ROSTER_VERSION");
        }
        WorkerRoster result = new WorkerRoster(server);
        for (Tag item : tag.getList("Profiles", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) item;
            Profile profile = new Profile();
            profile.revision = saved.getLong("Revision");
            profile.settings = WorkerSettings.load(saved.getCompound("Settings"));
            if (result.profiles.put(saved.getUUID("Owner"), profile) != null) {
                throw new IllegalArgumentException("DUPLICATE_PROFILE");
            }
        }
        for (Tag item : tag.getList("Workers", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) item;
            Entry entry = new Entry(saved.getUUID("Owner"));
            entry.retired = saved.getBoolean("Retired");
            entry.revision = saved.getLong("Revision");
            entry.dimension = saved.getString("Dimension");
            entry.position = BlockPos.of(saved.getLong("Position"));
            entry.name = saved.getString("Name");
            entry.overrides = WorkerSettings.load(saved.getCompound("Overrides"));
            entry.entity = saved.getCompound("Entity").copy();
            if (result.entries.put(saved.getUUID("Worker"), entry) != null) {
                throw new IllegalArgumentException("DUPLICATE_WORKER");
            }
        }
        // Pending destination searches do not survive a restart and consume no saved slots.
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        requireThread();
        tag.putInt("Version", 1);
        ListTag savedProfiles = new ListTag();
        profiles.forEach((owner, profile) -> {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("Owner", owner);
            saved.putLong("Revision", profile.revision);
            saved.put("Settings", WorkerSettings.save(profile.settings));
            savedProfiles.add(saved);
        });
        tag.put("Profiles", savedProfiles);
        ListTag savedEntries = new ListTag();
        entries.forEach((id, entry) -> {
            WorkerEntity worker = findLive(id);
            if (!entry.retired && worker != null) {
                capture(entry, worker);
            }
            CompoundTag saved = new CompoundTag();
            saved.putUUID("Owner", entry.owner);
            saved.putUUID("Worker", id);
            saved.putBoolean("Retired", entry.retired);
            saved.putLong("Revision", entry.revision);
            saved.putString("Dimension", entry.dimension);
            saved.putLong("Position", entry.position.asLong());
            saved.putString("Name", entry.name);
            saved.put("Overrides", WorkerSettings.save(entry.overrides));
            saved.put("Entity", entry.entity.copy());
            savedEntries.add(saved);
        });
        tag.put("Workers", savedEntries);
        return tag;
    }

    public List<View> list(UUID owner, boolean retired) {
        requireThread();
        List<View> result = new ArrayList<>();
        entries.forEach((id, entry) -> {
            if (entry.owner.equals(owner) && entry.retired == retired) {
                WorkerEntity live = findLive(id);
                if (live != null && !retired) {
                    entry.position = live.blockPosition();
                    entry.dimension = live.level().dimension().location().toString();
                }
                result.add(view(id, entry));
            }
        });
        return List.copyOf(result);
    }

    public View view(UUID owner, UUID worker) { return view(worker, owned(owner, worker)); }

    private static View view(UUID id, Entry entry) {
        return new View(id, entry.owner, entry.retired, entry.revision, entry.name, entry.dimension, entry.position);
    }

    public WorkerEntity active(UUID owner, UUID id) {
        Entry entry = owned(owner, id);
        WorkerEntity worker = findLive(id);
        if (entry.retired || worker == null || !worker.isAlive() || worker.isRemoved()
                || !worker.ownerUUID().filter(owner::equals).isPresent()) {
            throw new IllegalStateException("WORKER_UNAVAILABLE");
        }
        return worker;
    }

    public ProfileView profile(UUID owner) {
        requireThread();
        Profile profile = profiles.get(owner);
        return profile == null ? new ProfileView(0, Map.of()) : new ProfileView(profile.revision, profile.settings);
    }

    public Map<String, String> overrides(UUID owner, UUID worker) { return owned(owner, worker).overrides; }

    public void applyPersonal(UUID owner, long revision, Map<String, String> values) {
        requireThread();
        Objects.requireNonNull(owner);
        Profile current = profiles.get(owner);
        checkRevision(current == null ? 0 : current.revision, revision);
        Map<String, String> validated = WorkerSettings.validate(values);
        Profile profile = profiles.computeIfAbsent(owner, ignored -> new Profile());
        profile.settings = validated;
        profile.revision++;
        entries.forEach((id, entry) -> {
            if (entry.owner.equals(owner) && !entry.retired) {
                WorkerEntity live = findLive(id);
                if (live != null && live.isAlive() && !live.isRemoved()) {
                    live.applySettings(WorkerSettings.resolve(validated, entry.overrides));
                }
                entry.revision++;
            }
        });
        setDirty();
    }

    public void applyOverrides(UUID owner, UUID worker, long revision, Map<String, String> values) {
        Entry entry = owned(owner, worker);
        checkRevision(entry.revision, revision);
        Map<String, String> validated = WorkerSettings.validate(values);
        if (!entry.retired) {
            active(owner, worker).applySettings(WorkerSettings.resolve(profile(owner).settings(), validated));
        }
        entry.overrides = validated;
        entry.revision++;
        setDirty();
    }

    public void rename(UUID owner, UUID worker, long revision, String name) {
        Entry entry = owned(owner, worker);
        checkRevision(entry.revision, revision);
        if (name == null || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("INVALID_NAME");
        }
        if (!entry.retired) {
            active(owner, worker).setCustomName(Component.literal(name));
        }
        entry.name = name;
        entry.revision++;
        setDirty();
    }

    public Reservation reserve(UUID owner, UUID request, UUID retiredWorker) {
        requireThread();
        Objects.requireNonNull(owner);
        Objects.requireNonNull(request);
        if (finishedRequests.contains(request)) {
            throw new IllegalStateException("REQUEST_FINISHED");
        }
        Reservation previous = reservations.get(request);
        if (previous != null) {
            if (!previous.owner().equals(owner) || previous.reactivation() != (retiredWorker != null)
                    || (retiredWorker != null && !previous.worker().equals(retiredWorker))) {
                throw new IllegalStateException("RESERVATION_CONFLICT");
            }
            return previous;
        }
        if (retiredWorker != null && !owned(owner, retiredWorker).retired) {
            throw new IllegalStateException("WORKER_NOT_RETIRED");
        }
        if (retiredWorker != null && reservations.values().stream().anyMatch(r -> r.worker().equals(retiredWorker))) {
            throw new IllegalStateException("WORKER_PENDING");
        }
        long active = entries.values().stream().filter(e -> e.owner.equals(owner) && !e.retired).count();
        long pending = reservations.values().stream().filter(r -> r.owner().equals(owner)).count();
        if (active + pending >= ACTIVE_LIMIT) {
            throw new IllegalStateException("ACTIVE_LIMIT");
        }
        Reservation reservation = new Reservation(request, owner, retiredWorker == null ? UUID.randomUUID() : retiredWorker,
                retiredWorker != null);
        reservations.put(request, reservation);
        return reservation;
    }

    public void cancelReservation(UUID owner, UUID request) {
        requireThread();
        Reservation reservation = reservations.get(request);
        if (reservation != null && !reservation.owner().equals(owner)) {
            throw new IllegalStateException("NOT_OWNER");
        }
        if (reservations.remove(request) != null) {
            finishRequest(request);
        }
    }

    /** Destination safety is established by the relocation service before this server-thread commit. */
    public WorkerEntity deploy(UUID owner, UUID request, ServerLevel level, Vec3 destination) {
        requireThread();
        Reservation reservation = reservations.get(request);
        if (reservation == null || !reservation.owner().equals(owner) || !level.getServer().equals(server)) {
            throw new IllegalStateException("INVALID_RESERVATION");
        }
        Entry entry = reservation.reactivation() ? owned(owner, reservation.worker()) : new Entry(owner);
        if (reservation.reactivation() && !entry.retired) {
            throw new IllegalStateException("WORKER_NOT_RETIRED");
        }
        WorkerEntity worker = Objects.requireNonNull(WorkerMod.WORKER.get().create(level));
        if (reservation.reactivation()) {
            worker.load(entry.entity.copy());
            worker.pauseMining();
        }
        worker.setUUID(reservation.worker());
        worker.claim(owner);
        worker.setCustomName(Component.literal(entry.name));
        worker.moveTo(destination.x, destination.y, destination.z, 0, 0);
        worker.applySettings(WorkerSettings.resolve(profile(owner).settings(), entry.overrides));
        entry.retired = false;
        entries.put(reservation.worker(), entry);
        if (!level.addFreshEntity(worker)) {
            if (reservation.reactivation()) {
                entry.retired = true;
            } else {
                entries.remove(reservation.worker());
            }
            throw new IllegalStateException("SPAWN_FAILED");
        }
        reservations.remove(request);
        finishRequest(request);
        entry.revision++;
        capture(entry, worker);
        setDirty();
        return worker;
    }

    public void retire(UUID owner, UUID id, long revision) {
        Entry entry = owned(owner, id);
        checkRevision(entry.revision, revision);
        WorkerEntity worker = active(owner, id);
        worker.pauseMining();
        capture(entry, worker);
        entry.retired = true;
        entry.revision++;
        worker.clearContent();
        worker.discard();
        setDirty();
    }

    public List<ItemStack> archivedInventory(UUID owner, UUID id) {
        Entry entry = archived(owner, id);
        return List.copyOf(readInventory(entry));
    }

    public ItemStack withdraw(UUID owner, UUID id, long revision, int slot, int amount) {
        Entry entry = archived(owner, id);
        checkRevision(entry.revision, revision);
        Objects.checkIndex(slot, 9);
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("INVALID_AMOUNT");
        }
        if (reservations.values().stream().anyMatch(r -> r.worker().equals(id))) {
            throw new IllegalStateException("WORKER_PENDING");
        }
        NonNullList<ItemStack> inventory = readInventory(entry);
        ItemStack removed = inventory.get(slot).split(amount);
        CompoundTag saved = new CompoundTag();
        ContainerHelper.saveAllItems(saved, inventory, server.registryAccess());
        entry.entity.getCompound("AutomatoneWorker").put("Inventory", saved);
        entry.revision++;
        setDirty();
        return removed;
    }

    private NonNullList<ItemStack> readInventory(Entry entry) {
        NonNullList<ItemStack> inventory = NonNullList.withSize(9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(entry.entity.getCompound("AutomatoneWorker").getCompound("Inventory"),
                inventory, server.registryAccess());
        return inventory;
    }

    private Entry archived(UUID owner, UUID id) {
        Entry entry = owned(owner, id);
        if (!entry.retired) {
            throw new IllegalStateException("WORKER_NOT_RETIRED");
        }
        return entry;
    }

    /** Adopt legacy entities; archive overflow rather than losing identity or inventory at the active cap. */
    public void attached(WorkerEntity worker) {
        requireThread();
        if (worker.ownerUUID().isEmpty()) {
            return;
        }
        UUID owner = worker.ownerUUID().orElseThrow();
        Entry entry = entries.get(worker.getUUID());
        boolean overflow = false;
        if (entry == null) {
            long active = entries.values().stream().filter(candidate -> candidate.owner.equals(owner) && !candidate.retired).count();
            long pending = reservations.values().stream().filter(reservation -> reservation.owner().equals(owner)).count();
            overflow = active + pending >= ACTIVE_LIMIT;
            entry = new Entry(owner);
            entries.put(worker.getUUID(), entry);
        }
        if (!entry.owner.equals(owner) || entry.retired) {
            worker.discard();
            return;
        }
        worker.applySettings(WorkerSettings.resolve(profile(owner).settings(), entry.overrides));
        capture(entry, worker);
        if (overflow) {
            retire(owner, worker.getUUID(), entry.revision);
        }
        setDirty();
    }

    public void removed(WorkerEntity worker, Entity.RemovalReason reason) {
        requireThread();
        Entry entry = entries.get(worker.getUUID());
        if (entry != null && !entry.retired) {
            if (reason != null && reason.shouldDestroy()) {
                entries.remove(worker.getUUID());
            } else {
                capture(entry, worker);
            }
            setDirty();
        }
    }

    private Entry owned(UUID owner, UUID id) {
        requireThread();
        Entry entry = entries.get(id);
        if (entry == null || !entry.owner.equals(owner)) {
            throw new IllegalStateException("NOT_OWNER");
        }
        return entry;
    }

    void changed(WorkerEntity worker) {
        Entry entry = owned(worker.ownerUUID().orElseThrow(), worker.getUUID());
        capture(entry, worker);
        entry.revision++;
        setDirty();
    }

    private WorkerEntity findLive(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof WorkerEntity worker) {
                return worker;
            }
        }
        return null;
    }

    private static void capture(Entry entry, WorkerEntity worker) {
        entry.entity = worker.saveWithoutId(new CompoundTag());
        entry.position = worker.blockPosition();
        entry.dimension = worker.level().dimension().location().toString();
        entry.name = worker.getName().getString();
    }

    private static void checkRevision(long actual, long expected) {
        if (actual != expected) {
            throw new IllegalStateException("STALE_REVISION");
        }
    }

    private void finishRequest(UUID request) {
        finishedRequests.add(request);
        if (finishedRequests.size() > 1024) {
            finishedRequests.removeFirst();
        }
    }

    private void requireThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Roster requires the server thread");
        }
    }
}
