package automatone.worker;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static automatone.worker.WorkerActions.*;

/** Menu-lifetime previews; accepted deployments move into WorkerBatch's server-lifetime queue. */
final class WorkerBatchActions {
    private record Ref(UUID id, long revision, String state, UUID run) { }
    private record Preview(UUID token, int tick, String operation, List<Ref> recipients,
                           List<ResourceLocation> targets, int quantity, boolean start,
                           ResourceKey<Level> dimension, List<List<ItemStack>> kits, long supplies) { }
    private final WorkerMenu menu;
    private Preview preview;

    WorkerBatchActions(WorkerMenu menu) { this.menu = menu; }

    CompoundTag execute(WorkerNetwork.Action action, CompoundTag data) {
        if (menu.worker() != null) { throw new IllegalStateException("GLOBAL_MENU_REQUIRED"); }
        return switch (action) {
            case PREVIEW_BATCH -> previewBatch(data);
            case PREVIEW_FLEET -> previewFleet(data);
            case SUBMIT_BATCH, APPLY_FLEET -> {
                keys(data, "Confirmation");
                yield apply(uuid(data, "Confirmation"), action == WorkerNetwork.Action.SUBMIT_BATCH);
            }
            default -> throw new IllegalArgumentException("INVALID_REQUEST");
        };
    }

    private CompoundTag previewBatch(CompoundTag data) {
        preview = null;
        keys(data, "Recipients", "Targets", "Quantity", "Start", "NewCount", "Dimension", "ToolSlots", "Materials", "SupplyRevision");
        List<Ref> refs = references(data);
        List<ResourceLocation> targets = targets(data);
        int quantity = quantity(data);
        boolean start = bool(data, "Start");
        int count = integer(data, "NewCount", 0, WorkerRoster.ACTIVE_LIMIT);
        if (refs.size() + count == 0) { throw new IllegalArgumentException("INVALID_RECIPIENTS"); }
        ResourceKey<Level> dimension = dimension(data);
        if (menu.server().getLevel(dimension) == null) { throw new IllegalStateException("DIMENSION_UNAVAILABLE"); }
        require(data, "SupplyRevision", Tag.TAG_LONG);
        long supplyRevision = data.getLong("SupplyRevision");
        List<ItemStack> inventory = menu.supplies(supplyRevision);
        require(data, "ToolSlots", Tag.TAG_LIST);
        ListTag slots = (ListTag) data.get("ToolSlots");
        if (slots.size() > count || (!slots.isEmpty() && slots.getElementType() != Tag.TAG_INT)) {
            throw new IllegalArgumentException("INVALID_TOOLS");
        }
        List<ItemStack> tools = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Tag value : slots) {
            int slot = ((net.minecraft.nbt.IntTag) value).getAsInt();
            if (slot < 0 || slot >= 36 || !seen.add(slot) || !inventory.get(slot).has(DataComponents.TOOL)) {
                throw new IllegalArgumentException("INVALID_TOOLS");
            }
            tools.add(inventory.get(slot).copyWithCount(1));
        }
        require(data, "Materials", Tag.TAG_LIST);
        ListTag materialRefs = (ListTag) data.get("Materials");
        if (materialRefs.size() > 8 || (!materialRefs.isEmpty() && materialRefs.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("INVALID_MATERIALS");
        }
        List<ItemStack> materials = new ArrayList<>();
        for (Tag tag : materialRefs) {
            CompoundTag ref = (CompoundTag) tag;
            keys(ref, "Slot", "Count");
            ItemStack stack = inventory.get(integer(ref, "Slot", 0, 35));
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)
                    || materials.stream().anyMatch(other -> ItemStack.isSameItemSameComponents(other, stack))) {
                throw new IllegalArgumentException("INVALID_MATERIALS");
            }
            materials.add(stack.copyWithCount(integer(ref, "Count", 1, 2048)));
        }
        int requiredSlots = 1 + materials.stream().mapToInt(stack -> (stack.getCount() + stack.getMaxStackSize() - 1) / stack.getMaxStackSize()).sum();
        if (requiredSlots > WorkerEntity.INVENTORY_SIZE) { throw new IllegalArgumentException("KIT_CAPACITY"); }
        List<List<ItemStack>> kits = new ArrayList<>();
        for (ItemStack tool : tools) {
            List<ItemStack> kit = new ArrayList<>();
            kit.add(tool);
            kit.addAll(materials);
            kits.add(List.copyOf(kit));
        }
        List<ItemStack> required = new ArrayList<>(tools);
        materials.forEach(stack -> { if (count > 0) { required.add(stack.copyWithCount(stack.getCount() * count)); } });
        boolean enough = tools.size() == count && WorkerBatch.fitsSupplies(inventory, required);
        boolean capacity = count <= menu.roster().freeSlots(menu.owner());
        Preview proposed = new Preview(UUID.randomUUID(), menu.server().getTickCount(), "BATCH", refs,
                targets, quantity, start, dimension, kits, supplyRevision);
        CompoundTag result = describe(proposed, "DeploymentPreview");
        result.putInt("NewCount", count);
        result.putBoolean("CanSubmit", enough && capacity);
        result.putString("Error", !capacity ? "ACTIVE_LIMIT" : !enough ? "KIT_SHORTAGE" : "");
        result.putInt("ToolsRequired", count);
        result.putInt("ToolsSelected", tools.size());
        ListTag supplyRows = new ListTag();
        for (ItemStack need : required) {
            CompoundTag row = new CompoundTag();
            row.put("Stack", need.copyWithCount(1).save(menu.server().registryAccess()));
            row.putInt("Required", need.getCount());
            row.putInt("Available", inventory.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, need)).mapToInt(ItemStack::getCount).sum());
            supplyRows.add(row);
        }
        result.put("Supplies", supplyRows);
        if (enough && capacity) { preview = proposed; }
        return result;
    }

    private CompoundTag previewFleet(CompoundTag data) {
        preview = null;
        keys(data, "Recipients", "Operation");
        String operation = string(data, "Operation", 16);
        if (!Set.of("START", "PAUSE", "RESUME", "STOP", "RETIRE").contains(operation)) {
            throw new IllegalArgumentException("INVALID_OPERATION");
        }
        List<Ref> refs = references(data);
        if (refs.isEmpty()) { throw new IllegalArgumentException("INVALID_RECIPIENTS"); }
        preview = new Preview(UUID.randomUUID(), menu.server().getTickCount(), operation, refs,
                List.of(), 0, false, Level.OVERWORLD, List.of(), 0);
        return describe(preview, "FleetPreview");
    }

    private CompoundTag describe(Preview proposed, String kind) {
        CompoundTag result = new CompoundTag();
        result.putString("Kind", kind);
        result.putUUID("Confirmation", proposed.token);
        result.putString("Operation", proposed.operation);
        result.putBoolean("Start", proposed.start);
        ListTag rows = new ListTag();
        boolean confirm = proposed.operation.equals("RETIRE");
        for (Ref ref : proposed.recipients) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Worker", ref.id);
            row.putString("Name", menu.roster().view(menu.owner(), ref.id).name());
            boolean busy = ref.state.equals("RUNNING") || ref.state.equals("PAUSED");
            row.putBoolean("Busy", busy);
            String error = eligibility(ref, proposed.operation);
            row.putString("Error", error);
            row.putBoolean("Eligible", error.isEmpty());
            if ((proposed.operation.equals("START") || proposed.operation.equals("BATCH")) && busy && error.isEmpty()) { confirm = true; }
            rows.add(row);
        }
        result.put("Recipients", rows);
        result.putBoolean("ConfirmationRequired", confirm);
        return result;
    }

    private List<Ref> references(CompoundTag data) {
        require(data, "Recipients", Tag.TAG_LIST);
        ListTag refs = (ListTag) data.get("Recipients");
        if (refs.size() > WorkerRoster.ACTIVE_LIMIT || (!refs.isEmpty() && refs.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("INVALID_RECIPIENTS");
        }
        List<Ref> result = new ArrayList<>();
        for (Tag tag : refs) {
            CompoundTag row = (CompoundTag) tag;
            keys(row, "Worker", "Revision");
            UUID id = uuid(row, "Worker");
            WorkerRoster.View view = menu.roster().view(menu.owner(), id);
            revision(row, view.revision());
            if (view.retired() || result.stream().anyMatch(ref -> ref.id.equals(id))) {
                throw new IllegalArgumentException("INVALID_RECIPIENTS");
            }
            CompoundTag job = menu.roster().jobData(menu.owner(), id);
            result.add(new Ref(id, view.revision(), job.getString("State"), job.hasUUID("RunId") ? job.getUUID("RunId") : null));
        }
        return List.copyOf(result);
    }

    private String eligibility(Ref ref, String operation) {
        try {
            if (menu.relocation().pending(ref.id)) { return "WORKER_PENDING"; }
            menu.roster().active(menu.owner(), ref.id);
            if ((operation.equals("PAUSE") && !ref.state.equals("RUNNING"))
                    || (operation.equals("RESUME") && !ref.state.equals("PAUSED"))
                    || (operation.equals("STOP") && !ref.state.equals("PAUSED") && !ref.state.equals("RUNNING"))) {
                return "INVALID_JOB_STATE";
            }
            if (operation.equals("START") && menu.roster().jobData(menu.owner(), ref.id).getList("Targets", Tag.TAG_STRING).isEmpty()) {
                return "NO_JOB";
            }
            return "";
        } catch (RuntimeException failure) { return WorkerMenu.errorCode(failure); }
    }

    private CompoundTag apply(UUID token, boolean deployment) {
        Preview approved = preview;
        preview = null;
        if (approved == null || !approved.token.equals(token) || deployment != approved.operation.equals("BATCH")
                || menu.server().getTickCount() - approved.tick > 1200) { throw new IllegalStateException("INVALID_CONFIRMATION"); }
        for (Ref ref : approved.recipients) {
            WorkerRoster.View view = menu.roster().view(menu.owner(), ref.id);
            CompoundTag job = menu.roster().jobData(menu.owner(), ref.id);
            UUID run = job.hasUUID("RunId") ? job.getUUID("RunId") : null;
            if (view.retired() || view.revision() != ref.revision || !job.getString("State").equals(ref.state)
                    || !Objects.equals(run, ref.run)) { throw new IllegalStateException("STALE_PREVIEW"); }
        }
        if (deployment) {
            menu.supplies(approved.supplies);
            WorkerBatch.get(menu.server()).submit(menu.owner(), token, approved.kits, approved.dimension,
                    approved.targets, approved.quantity, approved.start);
        }
        ListTag outcomes = new ListTag();
        for (Ref ref : approved.recipients) {
            CompoundTag outcome = new CompoundTag();
            outcome.putUUID("Worker", ref.id);
            outcome.putString("Name", menu.roster().view(menu.owner(), ref.id).name());
            String error = eligibility(ref, approved.operation);
            if (error.isEmpty()) {
                try {
                    WorkerEntity worker = menu.roster().active(menu.owner(), ref.id);
                    switch (approved.operation) {
                        case "BATCH" -> {
                            worker.pauseMining();
                            if (approved.start) { worker.startMining(approved.targets, approved.quantity); }
                            else { worker.configureMining(approved.targets, approved.quantity); }
                        }
                        case "START" -> {
                            CompoundTag job = menu.roster().jobData(menu.owner(), ref.id);
                            worker.pauseMining();
                            worker.startMining(targets(job), job.getInt("Requested"));
                        }
                        case "PAUSE" -> worker.pauseMining();
                        case "RESUME" -> worker.resumeMining();
                        case "STOP" -> worker.stopMining();
                        case "RETIRE" -> menu.roster().retire(menu.owner(), ref.id, ref.revision);
                        default -> throw new IllegalStateException("INVALID_OPERATION");
                    }
                    if (!approved.operation.equals("RETIRE")) { menu.roster().changed(worker); }
                } catch (RuntimeException failure) { error = WorkerMenu.errorCode(failure); }
            }
            outcome.putString("Error", error);
            outcome.putString("State", error.isEmpty() ? "SUCCEEDED" : "FAILED");
            outcomes.add(outcome);
        }
        CompoundTag result = new CompoundTag();
        result.putString("Kind", deployment ? "DeploymentResult" : "FleetResult");
        result.putUUID("Batch", token);
        result.put("Recipients", outcomes);
        result.putInt("Queued", approved.kits.size());
        return result;
    }
}
