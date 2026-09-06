package automatone.worker;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-only intent execution. Validate the whole request before changing any worker. */
final class WorkerActions {
    private record Recipient(UUID id, long revision, MiningSession.State state, UUID runId) { }
    private record Batch(UUID token, List<Recipient> recipients, List<ResourceLocation> targets,
                         int quantity, boolean start, int createdTick) { }

    private final WorkerMenu menu;
    private Batch batch;

    WorkerActions(WorkerMenu menu) { this.menu = menu; }

    CompoundTag execute(WorkerNetwork.Action action, CompoundTag data) {
        WorkerRoster roster = menu.roster();
        UUID owner = menu.owner();
        CompoundTag result = new CompoundTag();
        result.putString("Kind", "Success");
        switch (action) {
            case REFRESH -> keys(data);
            case NOTIFICATION_PAGE -> {
                keys(data, "Page");
                menu.notificationPage(integer(data, "Page", 0, 19));
            }
            case READ_NOTIFICATION -> {
                keys(data, "Run");
                roster.markRead(owner, uuid(data, "Run"));
            }
            case READ_ALL_NOTIFICATIONS -> {
                keys(data);
                roster.markAllRead(owner);
            }
            case NOTIFICATION_PREFERENCES -> {
                keys(data, "Revision", "Toasts", "Sounds");
                require(data, "Revision", Tag.TAG_LONG);
                roster.applyNotificationPreferences(owner, data.getLong("Revision"), bool(data, "Toasts"), bool(data, "Sounds"));
            }
            case OPEN_ROSTER -> {
                keys(data, "Retired", "Page");
                boolean retired = bool(data, "Retired");
                int page = integer(data, "Page", 0, 1_000_000);
                WorkerMenu.open(menu.player(), null, retired, page);
            }
            case OPEN_WORKER -> {
                keys(data, "Worker", "Revision");
                UUID id = uuid(data, "Worker");
                WorkerRoster.View view = roster.view(owner, id);
                revision(data, view.revision());
                WorkerMenu.open(menu.player(), id, view.retired(), menu.page());
            }
            case DEPLOY -> {
                keys(data, "Request", "Dimension");
                WorkerRelocation.Status status = menu.relocation().deploy(owner, uuid(data, "Request"), dimension(data), null);
                result.putUUID("Request", status.request());
            }
            case REACTIVATE -> {
                keys(data, "Revision", "Request", "Dimension");
                selected(data, true);
                WorkerRelocation.Status status = menu.relocation().deploy(owner, uuid(data, "Request"), dimension(data), menu.worker());
                result.putUUID("Request", status.request());
            }
            case RELOCATE -> {
                keys(data, "Revision", "Request", "Dimension");
                selected(data, false);
                WorkerRelocation.Status status = menu.relocation().relocate(owner, uuid(data, "Request"),
                        menu.worker(), data.getLong("Revision"), dimension(data));
                result.putUUID("Request", status.request());
            }
            case CANCEL_RELOCATION -> {
                keys(data, "Request");
                menu.relocation().cancel(owner, uuid(data, "Request"));
            }
            case CONFIGURE_JOB, START -> {
                keys(data, "Revision", "Targets", "Quantity");
                selected(data, false);
                List<ResourceLocation> targets = targets(data);
                int quantity = quantity(data);
                WorkerEntity worker = roster.active(owner, menu.worker());
                if (busy(worker)) { throw new IllegalStateException("WORKER_BUSY"); }
                if (action == WorkerNetwork.Action.START) { worker.startMining(targets, quantity); }
                else { worker.configureMining(targets, quantity); }
                roster.changed(worker);
            }
            case PAUSE, RESUME, STOP -> {
                keys(data, "Revision");
                selected(data, false);
                WorkerEntity worker = roster.active(owner, menu.worker());
                MiningSession.State state = worker.miningStatus().state();
                if (action == WorkerNetwork.Action.PAUSE && state == MiningSession.State.RUNNING) { worker.pauseMining(); }
                else if (action == WorkerNetwork.Action.RESUME && state == MiningSession.State.PAUSED) { worker.resumeMining(); }
                else if (action == WorkerNetwork.Action.STOP && busy(worker)) { worker.stopMining(); }
                else { throw new IllegalStateException("INVALID_JOB_STATE"); }
                roster.changed(worker);
            }
            case SELECT_TOOL -> {
                keys(data, "Revision", "Slot");
                selected(data, false);
                int slot = integer(data, "Slot", 0, 8);
                WorkerEntity worker = roster.active(owner, menu.worker());
                worker.setSelectedSlot(slot);
                roster.changed(worker);
            }
            case COLLECT_ALL -> {
                keys(data, "Revision");
                selected(data, menu.retired());
                result.putInt("Collected", menu.collectAll());
            }
            case INVENTORY_MANAGEMENT -> {
                keys(data, "Revision", "Enabled", "Keep", "Blocks");
                selected(data, false);
                require(data, "Blocks", Tag.TAG_LIST);
                ListTag blocks = (ListTag) data.get("Blocks");
                if (blocks.size() > 128 || (!blocks.isEmpty() && blocks.getElementType() != Tag.TAG_STRING)
                        || blocks.stream().anyMatch(value -> value.getAsString().length() > 256)) {
                    throw new IllegalArgumentException("INVALID_BLOCK");
                }
                List<ResourceLocation> ids = blocks.stream()
                        .map(value -> ResourceLocation.parse(value.getAsString())).toList();
                WorkerEntity worker = roster.active(owner, menu.worker());
                worker.applyInventoryManagement(bool(data, "Enabled"), integer(data, "Keep", 0, 4096), ids);
                roster.changed(worker);
            }
            case RENAME -> {
                keys(data, "Revision", "Name");
                selected(data, menu.retired());
                roster.rename(owner, menu.worker(), data.getLong("Revision"), string(data, "Name", 64));
            }
            case PERSONAL_SETTINGS -> {
                keys(data, "Revision", "Values");
                revision(data, roster.profile(owner).revision());
                roster.applyPersonal(owner, data.getLong("Revision"), settings(data));
            }
            case WORKER_SETTINGS -> {
                keys(data, "Revision", "Values");
                selected(data, menu.retired());
                roster.applyOverrides(owner, menu.worker(), data.getLong("Revision"), settings(data));
            }
            case RETIRE -> {
                keys(data, "Revision");
                selected(data, false);
                roster.retire(owner, menu.worker(), data.getLong("Revision"));
                WorkerMenu.open(menu.player(), menu.worker(), true, 0);
            }
            case PREVIEW_APPLY_JOB -> {
                keys(data, "Recipients", "Targets", "Quantity", "Start");
                List<ResourceLocation> targets = targets(data);
                int quantity = quantity(data);
                boolean start = bool(data, "Start");
                List<Recipient> recipients = recipients(data);
                batch = new Batch(UUID.randomUUID(), recipients, targets, quantity, start, menu.server().getTickCount());
                result.putString("Kind", "Preview");
                result.putUUID("Confirmation", batch.token());
                result.putBoolean("Start", start);
                ListTag preview = new ListTag();
                for (Recipient recipient : recipients) {
                    CompoundTag row = new CompoundTag();
                    row.putUUID("Worker", recipient.id());
                    row.putString("Name", roster.view(owner, recipient.id()).name());
                    row.putBoolean("Busy", recipient.state() == MiningSession.State.RUNNING || recipient.state() == MiningSession.State.PAUSED);
                    preview.add(row);
                }
                result.put("Recipients", preview);
            }
            case APPLY_JOB -> {
                keys(data, "Confirmation");
                return applyBatch(uuid(data, "Confirmation"));
            }
        }
        return result;
    }

    private CompoundTag applyBatch(UUID confirmation) {
        if (batch == null || !batch.token().equals(confirmation) || menu.server().getTickCount() - batch.createdTick() > 1200) {
            throw new IllegalStateException("INVALID_CONFIRMATION");
        }
        // Validate every recipient again before replacing even the first job.
        for (Recipient recipient : batch.recipients()) {
            WorkerEntity worker = available(recipient.id(), recipient.revision());
            if (worker.miningStatus().state() != recipient.state() || !Objects.equals(worker.miningStatus().runId(), recipient.runId())) {
                throw new IllegalStateException("STALE_PREVIEW");
            }
        }
        Batch approved = batch;
        batch = null;
        ListTag outcomes = new ListTag();
        for (Recipient recipient : approved.recipients()) {
            CompoundTag outcome = new CompoundTag();
            outcome.putUUID("Worker", recipient.id());
            WorkerEntity worker = menu.roster().active(menu.owner(), recipient.id());
            try {
                worker.pauseMining();
                if (approved.start()) { worker.startMining(approved.targets(), approved.quantity()); }
                else { worker.configureMining(approved.targets(), approved.quantity()); }
                outcome.putString("Error", "");
            } catch (RuntimeException failure) {
                outcome.putString("Error", WorkerMenu.errorCode(failure));
            } finally {
                menu.roster().changed(worker);
            }
            outcomes.add(outcome);
        }
        CompoundTag result = new CompoundTag();
        result.putString("Kind", "BatchResult");
        result.put("Recipients", outcomes);
        return result;
    }

    private List<Recipient> recipients(CompoundTag data) {
        if (!data.contains("Recipients", Tag.TAG_LIST)) { throw new IllegalArgumentException("INVALID_RECIPIENTS"); }
        ListTag list = data.getList("Recipients", Tag.TAG_COMPOUND);
        if (list.isEmpty() || list.size() > WorkerRoster.ACTIVE_LIMIT) { throw new IllegalArgumentException("INVALID_RECIPIENTS"); }
        List<Recipient> result = new ArrayList<>();
        for (Tag item : list) {
            CompoundTag ref = (CompoundTag) item;
            keys(ref, "Worker", "Revision");
            UUID id = uuid(ref, "Worker");
            require(ref, "Revision", Tag.TAG_LONG);
            if (result.stream().anyMatch(previous -> previous.id().equals(id))) { throw new IllegalArgumentException("DUPLICATE_RECIPIENT"); }
            WorkerEntity worker = available(id, ref.getLong("Revision"));
            result.add(new Recipient(id, ref.getLong("Revision"), worker.miningStatus().state(), worker.miningStatus().runId()));
        }
        return List.copyOf(result);
    }

    private WorkerEntity available(UUID id, long expected) {
        if (menu.roster().view(menu.owner(), id).revision() != expected) { throw new IllegalStateException("STALE_REVISION"); }
        if (menu.relocation().pending(id)) { throw new IllegalStateException("WORKER_PENDING"); }
        return menu.roster().active(menu.owner(), id);
    }

    private void selected(CompoundTag data, boolean retired) {
        if (menu.worker() == null || menu.retired() != retired) { throw new IllegalStateException("INVALID_SELECTION"); }
        WorkerRoster.View view = menu.roster().view(menu.owner(), menu.worker());
        revision(data, view.revision());
        if (view.retired() != retired) { throw new IllegalStateException("WORKER_UNAVAILABLE"); }
        if (menu.relocation().pending(menu.worker())) { throw new IllegalStateException("WORKER_PENDING"); }
    }

    private static boolean busy(WorkerEntity worker) {
        return worker.miningStatus().state() == MiningSession.State.RUNNING || worker.miningStatus().state() == MiningSession.State.PAUSED;
    }

    private static List<ResourceLocation> targets(CompoundTag data) {
        require(data, "Targets", Tag.TAG_LIST);
        ListTag list = data.getList("Targets", Tag.TAG_STRING);
        if (list.isEmpty() || list.size() > MiningSession.MAX_TARGET_BLOCKS) { throw new IllegalArgumentException("INVALID_BLOCK"); }
        List<ResourceLocation> targets = new ArrayList<>();
        for (Tag value : list) {
            String text = value.getAsString();
            ResourceLocation id = text.length() <= 256 ? ResourceLocation.tryParse(text) : null;
            if (id == null || targets.contains(id)) { throw new IllegalArgumentException("INVALID_BLOCK"); }
            targets.add(id);
        }
        WorkerEntity.validateTargets(targets);
        return List.copyOf(targets);
    }

    private static int quantity(CompoundTag data) { return integer(data, "Quantity", 0, MiningSession.MAX_REQUESTED_BLOCKS); }

    private static ResourceKey<Level> dimension(CompoundTag data) {
        ResourceLocation dimension = ResourceLocation.tryParse(string(data, "Dimension", 256));
        if (!Level.OVERWORLD.location().equals(dimension) && !Level.NETHER.location().equals(dimension)) {
            throw new IllegalArgumentException("INVALID_DIMENSION");
        }
        return ResourceKey.create(Registries.DIMENSION, dimension);
    }

    private static Map<String, String> settings(CompoundTag data) {
        require(data, "Values", Tag.TAG_COMPOUND);
        CompoundTag values = data.getCompound("Values");
        if (values.getAllKeys().size() > 256) { throw new IllegalArgumentException("TOO_MANY_SETTINGS"); }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : values.getAllKeys()) {
            if (key.length() > 128) { throw new IllegalArgumentException("INVALID_SETTING"); }
            result.put(key, string(values, key, 8192));
        }
        return Map.copyOf(result);
    }

    private static void revision(CompoundTag data, long expected) {
        require(data, "Revision", Tag.TAG_LONG);
        if (data.getLong("Revision") != expected) { throw new IllegalStateException("STALE_REVISION"); }
    }

    private static int integer(CompoundTag data, String key, int min, int max) {
        require(data, key, Tag.TAG_INT);
        int result = data.getInt(key);
        if (result < min || result > max) { throw new IllegalArgumentException("INVALID_" + key.toUpperCase(java.util.Locale.ROOT)); }
        return result;
    }

    private static boolean bool(CompoundTag data, String key) {
        require(data, key, Tag.TAG_BYTE);
        if (data.getByte(key) != 0 && data.getByte(key) != 1) { throw new IllegalArgumentException("INVALID_BOOLEAN"); }
        return data.getBoolean(key);
    }

    private static String string(CompoundTag data, String key, int max) {
        require(data, key, Tag.TAG_STRING);
        String result = data.getString(key);
        if (result.length() > max) { throw new IllegalArgumentException("STRING_TOO_LONG"); }
        return result;
    }

    private static UUID uuid(CompoundTag data, String key) {
        if (!data.hasUUID(key)) { throw new IllegalArgumentException("INVALID_UUID"); }
        return data.getUUID(key);
    }

    private static void require(CompoundTag data, String key, int type) {
        if (!data.contains(key, type)) { throw new IllegalArgumentException("INVALID_REQUEST"); }
    }

    private static void keys(CompoundTag data, String... keys) {
        if (!data.getAllKeys().equals(Set.of(keys))) { throw new IllegalArgumentException("INVALID_REQUEST"); }
    }
}
