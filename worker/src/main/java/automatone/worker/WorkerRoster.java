package automatone.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
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
import java.util.Optional;
import java.util.UUID;

/** Overworld-owned product records. All mutations and reservations run on the server thread. */
public final class WorkerRoster extends SavedData {
    public static final int ACTIVE_LIMIT = 10;
    public static final int NOTIFICATION_LIMIT = 100;
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
    public record NotificationPreferences(long revision, boolean toasts, boolean sounds) { }
    public record Completion(UUID run, UUID worker, String name, List<String> targets, long amount, long time, boolean read) {
        public Completion { targets = List.copyOf(targets); }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Run", run); tag.putUUID("Worker", worker); tag.putString("Name", name);
            ListTag blocks = new ListTag(); targets.forEach(target -> blocks.add(net.minecraft.nbt.StringTag.valueOf(target)));
            tag.put("Targets", blocks); tag.putLong("Amount", amount); tag.putLong("Time", time); tag.putBoolean("Read", read);
            return tag;
        }

        private static Completion load(CompoundTag tag) {
            List<String> targets = tag.getList("Targets", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
            if (targets.isEmpty() || targets.size() > MiningSession.MAX_TARGET_BLOCKS
                    || targets.stream().anyMatch(target -> target.length() > 256)
                    || tag.getString("Name").length() > 64 || tag.getLong("Amount") <= 0
                    || tag.getLong("Amount") > MiningSession.MAX_REQUESTED_BLOCKS || tag.getLong("Time") <= 0) {
                throw new IllegalArgumentException("INVALID_SAVED_NOTIFICATION");
            }
            return new Completion(tag.getUUID("Run"), tag.getUUID("Worker"), tag.getString("Name"),
                    targets, tag.getLong("Amount"), tag.getLong("Time"), tag.getBoolean("Read"));
        }
    }

    private static final class Profile {
        private long revision;
        private Map<String, String> settings = Map.of();
        private long pickupRevision;
        private List<ResourceLocation> pickupBlocks = WorkerInventoryManagement.defaultBlocks();
        private long notificationRevision;
        private boolean toasts = true;
        private boolean sounds = true;
        private final List<Completion> completions = new ArrayList<>();
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
        private SimpleContainer archiveInventory;
        private UUID notifiedRun;

        private Entry(UUID owner) { this.owner = owner; }
    }

    private WorkerRoster(MinecraftServer server) { this.server = Objects.requireNonNull(server); }

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
            profile.pickupRevision = saved.getLong("PickupRevision");
            if (saved.contains("PickupBlocks", Tag.TAG_LIST)) {
                try {
                    profile.pickupBlocks = WorkerInventoryManagement.validate(saved.getList("PickupBlocks", Tag.TAG_STRING)
                            .stream().map(Tag::getAsString).map(ResourceLocation::parse).toList());
                } catch (IllegalArgumentException | net.minecraft.ResourceLocationException invalid) {
                    profile.pickupBlocks = WorkerInventoryManagement.defaultBlocks();
                }
            }
            profile.notificationRevision = saved.getLong("NotificationRevision");
            profile.toasts = !saved.contains("ShowToasts") || saved.getBoolean("ShowToasts");
            profile.sounds = !saved.contains("PlaySounds") || saved.getBoolean("PlaySounds");
            ListTag notifications = saved.getList("Notifications", Tag.TAG_COMPOUND);
            if (notifications.size() > NOTIFICATION_LIMIT) { throw new IllegalArgumentException("TOO_MANY_SAVED_NOTIFICATIONS"); }
            for (Tag notification : notifications) {
                Completion completion = Completion.load((CompoundTag) notification);
                if (profile.completions.stream().anyMatch(previous -> previous.run().equals(completion.run()))) {
                    throw new IllegalArgumentException("DUPLICATE_SAVED_NOTIFICATION");
                }
                profile.completions.add(completion);
            }
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
            // Recover the last available contents of legacy dead workers as archives.
            if (!entry.retired && entry.entity.contains("Health", Tag.TAG_ANY_NUMERIC)
                    && entry.entity.getFloat("Health") <= 0.0F) {
                entry.retired = true;
                entry.revision++;
                CompoundTag job = entry.entity.getCompound("AutomatoneWorker").getCompound("Job");
                if (job.getString("State").equals("RUNNING")) { job.putString("State", "PAUSED"); }
                result.setDirty();
            }
            entry.notifiedRun = saved.hasUUID("NotifiedRun") ? saved.getUUID("NotifiedRun") : null;
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
            saved.putLong("PickupRevision", profile.pickupRevision);
            saved.put("PickupBlocks", WorkerInventoryManagement.strings(profile.pickupBlocks.stream().map(ResourceLocation::toString).toList()));
            saved.putLong("NotificationRevision", profile.notificationRevision);
            saved.putBoolean("ShowToasts", profile.toasts);
            saved.putBoolean("PlaySounds", profile.sounds);
            ListTag notifications = new ListTag(); profile.completions.forEach(completion -> notifications.add(completion.save()));
            saved.put("Notifications", notifications);
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
            if (entry.notifiedRun != null) { saved.putUUID("NotifiedRun", entry.notifiedRun); }
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

    /** Current-run completion is recorded before any online presentation is attempted. */
    public Optional<Completion> recordCompletion(WorkerEntity worker) {
        requireThread();
        UUID owner = worker.ownerUUID().orElse(null);
        Entry entry = entries.get(worker.getUUID());
        MiningSession.Snapshot job = worker.miningStatus();
        if (owner == null || entry == null || !entry.owner.equals(owner) || entry.retired
                || worker.isRemoved() || !worker.isAlive() || !worker.equals(findLive(worker.getUUID()))
                || job.state() != MiningSession.State.COMPLETED || job.runId() == null
                || job.runId().equals(entry.notifiedRun)) { return Optional.empty(); }
        String name = worker.getName().getString();
        Completion completion = new Completion(job.runId(), worker.getUUID(), name.substring(0, Math.min(64, name.length())),
                job.targets(), job.completed(), System.currentTimeMillis(), false);
        Profile profile = profiles.computeIfAbsent(owner, ignored -> new Profile());
        // The marker survives history eviction, retirement, reactivation and ordinary restart.
        entry.notifiedRun = job.runId();
        profile.completions.add(completion);
        if (profile.completions.size() > NOTIFICATION_LIMIT) { profile.completions.removeFirst(); }
        capture(entry, worker);
        entry.revision++;
        setDirty();
        return Optional.of(completion);
    }

    /** Oldest first, capped at 100. The menu presents newest first in bounded pages. */
    public List<Completion> notifications(UUID owner) {
        requireThread();
        Profile profile = profiles.get(owner);
        return profile == null ? List.of() : List.copyOf(profile.completions);
    }

    public int unread(UUID owner) { return (int) notifications(owner).stream().filter(completion -> !completion.read()).count(); }

    public NotificationPreferences notificationPreferences(UUID owner) {
        requireThread();
        Profile profile = profiles.get(owner);
        return profile == null ? new NotificationPreferences(0, true, true)
                : new NotificationPreferences(profile.notificationRevision, profile.toasts, profile.sounds);
    }

    public void applyNotificationPreferences(UUID owner, long revision, boolean toasts, boolean sounds) {
        requireThread();
        checkRevision(notificationPreferences(owner).revision(), revision);
        Profile profile = profiles.computeIfAbsent(Objects.requireNonNull(owner), ignored -> new Profile());
        profile.toasts = toasts; profile.sounds = sounds; profile.notificationRevision++;
        setDirty();
    }

    public void markRead(UUID owner, UUID run) {
        requireThread();
        Profile profile = profiles.get(owner);
        if (profile != null) {
            for (int index = 0; index < profile.completions.size(); index++) {
                Completion completion = profile.completions.get(index);
                if (completion.run().equals(run)) {
                    if (!completion.read()) {
                        profile.completions.set(index, new Completion(completion.run(), completion.worker(), completion.name(),
                                completion.targets(), completion.amount(), completion.time(), true));
                        setDirty();
                    }
                    return;
                }
            }
        }
        throw new IllegalArgumentException("NOTIFICATION_NOT_FOUND");
    }

    public void markAllRead(UUID owner) {
        for (Completion completion : notifications(owner)) { if (!completion.read()) { markRead(owner, completion.run()); } }
    }

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

    public List<ResourceLocation> pickupBlocks(UUID owner) {
        requireThread();
        Profile profile = profiles.get(owner);
        return profile == null ? WorkerInventoryManagement.defaultBlocks() : profile.pickupBlocks;
    }

    public CompoundTag pickupRules(UUID owner) {
        requireThread();
        CompoundTag result = new CompoundTag();
        Profile profile = profiles.get(owner);
        result.putLong("Revision", profile == null ? 0 : profile.pickupRevision);
        result.put("Blocks", WorkerInventoryManagement.strings(pickupBlocks(owner).stream().map(ResourceLocation::toString).toList()));
        return result;
    }

    public void applyPersonalPickupRules(UUID owner, long revision, List<ResourceLocation> blocks) {
        requireThread();
        Profile current = profiles.get(owner);
        checkRevision(current == null ? 0 : current.pickupRevision, revision);
        List<ResourceLocation> validated = WorkerInventoryManagement.validate(blocks);
        Profile profile = profiles.computeIfAbsent(owner, ignored -> new Profile());
        profile.pickupBlocks = validated;
        profile.pickupRevision++;
        entries.forEach((id, entry) -> {
            if (entry.owner.equals(owner) && !entry.retired) {
                WorkerEntity live = findLive(id);
                if (live != null && live.isAlive() && !live.isRemoved()) {
                    live.inheritInventoryManagement(validated);
                    capture(entry, live);
                }
                entry.revision++;
            }
        });
        setDirty();
    }

    public void applyWorkerPickupRules(UUID owner, UUID id, long revision, boolean override, List<ResourceLocation> blocks) {
        Entry entry = owned(owner, id);
        checkRevision(entry.revision, revision);
        List<ResourceLocation> validated = WorkerInventoryManagement.validate(blocks);
        WorkerEntity worker = active(owner, id);
        if (override) { worker.applyInventoryManagement(validated); }
        else { worker.resetInventoryManagement(pickupBlocks(owner)); }
        changed(worker);
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

    public int freeSlots(UUID owner) {
        requireThread();
        long active = entries.values().stream().filter(entry -> entry.owner.equals(owner) && !entry.retired).count();
        long pending = reservations.values().stream().filter(reservation -> reservation.owner().equals(owner)).count();
        return (int) Math.max(0, ACTIVE_LIMIT - active - pending);
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
            worker.prepareReactivation();
        }
        worker.setUUID(reservation.worker());
        worker.claim(owner);
        worker.setCustomName(Component.literal(entry.name));
        worker.moveTo(destination.x, destination.y, destination.z, 0, 0);
        worker.applySettings(WorkerSettings.resolve(profile(owner).settings(), entry.overrides));
        worker.inheritInventoryManagement(pickupBlocks(owner));
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
        entry.archiveInventory = null;
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
        archive(entry, worker);
        worker.discard();
    }

    /** Called after death cancellation, before vanilla can drop or damage equipment. */
    boolean archiveDeath(WorkerEntity worker) {
        requireThread();
        if (worker.ownerUUID().isEmpty()) { return false; }
        UUID owner = worker.ownerUUID().orElseThrow();
        WorkerEntity live = findLive(worker.getUUID());
        if (live != null && !live.equals(worker)) { return true; }
        Entry entry = entries.computeIfAbsent(worker.getUUID(), ignored -> new Entry(owner));
        if (!entry.owner.equals(owner)) { return false; }
        if (!entry.retired) { archive(entry, worker); }
        return true;
    }

    private void archive(Entry entry, WorkerEntity worker) {
        worker.pauseMining();
        capture(entry, worker);
        entry.retired = true;
        entry.archiveInventory = null;
        entry.revision++;
        worker.clearArchivedContents();
        setDirty();
    }

    public List<ItemStack> archivedInventory(UUID owner, UUID id) {
        Entry entry = archived(owner, id);
        return List.copyOf(readInventory(entry));
    }

    /** Main hand aliases the 36-slot inventory; the other equipment slots are separate saved contents. */
    List<ItemStack> archivedEquipment(UUID owner, UUID id) {
        CompoundTag entity = archived(owner, id).entity;
        List<ItemStack> items = new ArrayList<>();
        items.add(ItemStack.parseOptional(server.registryAccess(), entity.getList("HandItems", Tag.TAG_COMPOUND).getCompound(1)));
        ListTag armor = entity.getList("ArmorItems", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < 4; slot++) { items.add(ItemStack.parseOptional(server.registryAccess(), armor.getCompound(slot))); }
        items.add(ItemStack.parseOptional(server.registryAccess(), entity.getCompound("body_armor_item")));
        return items;
    }

    void removeArchivedEquipment(UUID owner, UUID id, int slot, int amount) {
        Entry entry = archived(owner, id);
        Objects.checkIndex(slot, 6);
        ItemStack stack = archivedEquipment(owner, id).get(slot).copy();
        if (amount < 1 || amount > stack.getCount()) { throw new IllegalArgumentException("INVALID_AMOUNT"); }
        stack.shrink(amount);
        Tag saved = stack.saveOptional(server.registryAccess());
        if (slot == 5) {
            entry.entity.put("body_armor_item", saved);
        } else {
            String key = slot == 0 ? "HandItems" : "ArmorItems";
            ListTag list = entry.entity.getList(key, Tag.TAG_COMPOUND);
            int index = slot == 0 ? 1 : slot - 1;
            while (list.size() <= index) { list.add(new CompoundTag()); }
            list.set(index, saved);
            entry.entity.put(key, list);
        }
        entry.revision++;
        setDirty();
    }

    public ItemStack withdraw(UUID owner, UUID id, long revision, int slot, int amount) {
        Entry entry = archived(owner, id);
        checkRevision(entry.revision, revision);
        Objects.checkIndex(slot, WorkerEntity.INVENTORY_SIZE);
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("INVALID_AMOUNT");
        }
        if (reservations.values().stream().anyMatch(r -> r.worker().equals(id))) {
            throw new IllegalStateException("WORKER_PENDING");
        }
        return archivedContainer(owner, id).removeItem(slot, amount);
    }

    /** Native slots share one authoritative archive inventory; menus enforce withdrawal-only access. */
    Container archivedContainer(UUID owner, UUID id) {
        Entry entry = archived(owner, id);
        if (entry.archiveInventory == null) {
            entry.archiveInventory = new SimpleContainer(readInventory(entry).toArray(ItemStack[]::new));
            entry.archiveInventory.addListener(container -> {
                requireThread();
                if (!entry.retired) {
                    throw new IllegalStateException("WORKER_NOT_RETIRED");
                }
                NonNullList<ItemStack> items = NonNullList.withSize(WorkerEntity.INVENTORY_SIZE, ItemStack.EMPTY);
                for (int slot = 0; slot < items.size(); slot++) {
                    items.set(slot, container.getItem(slot));
                }
                CompoundTag saved = new CompoundTag();
                ContainerHelper.saveAllItems(saved, items, server.registryAccess());
                if (saved.equals(entry.entity.getCompound("AutomatoneWorker").getCompound("Inventory"))) {
                    return;
                }
                entry.entity.getCompound("AutomatoneWorker").put("Inventory", saved);
                entry.revision++;
                setDirty();
            });
        }
        return entry.archiveInventory;
    }

    /** Product-only saved job data for roster cards, including unloaded and retired workers. */
    CompoundTag jobData(UUID owner, UUID id) {
        Entry entry = owned(owner, id);
        WorkerEntity live = entry.retired ? null : findLive(id);
        if (live != null) {
            return live.saveWithoutId(new CompoundTag()).getCompound("AutomatoneWorker").getCompound("Job").copy();
        }
        return entry.entity.getCompound("AutomatoneWorker").getCompound("Job").copy();
    }

    private NonNullList<ItemStack> readInventory(Entry entry) {
        NonNullList<ItemStack> inventory = NonNullList.withSize(WorkerEntity.INVENTORY_SIZE, ItemStack.EMPTY);
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
        if (worker.getHealth() <= 0.0F) {
            removed(worker, Entity.RemovalReason.KILLED);
            worker.discard();
            return;
        }
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
        worker.inheritInventoryManagement(pickupBlocks(owner));
        capture(entry, worker);
        if (overflow) {
            retire(owner, worker.getUUID(), entry.revision);
        }
        setDirty();
    }

    public void removed(WorkerEntity worker, Entity.RemovalReason reason) {
        requireThread();
        if (worker.getHealth() <= 0.0F || reason == Entity.RemovalReason.KILLED) {
            archiveDeath(worker);
            return;
        }
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
