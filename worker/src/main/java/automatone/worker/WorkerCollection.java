package automatone.worker;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One menu's server-owned collection view and selection. No active contents come from saved NBT. */
final class WorkerCollection {
    static final int PAGE_SIZE = 36;
    private record Source(WorkerRoster.View view, boolean available, List<ItemStack> stacks) { }
    private record Identity(UUID id, ItemStack stack) { }
    private record Preview(UUID token, long revision, long contentsRevision, Set<UUID> selected, int tick) { }
    private static final class Variant {
        private final Identity identity;
        private final Map<UUID, Integer> amounts = new LinkedHashMap<>();
        private long count;
        private Variant(Identity identity) { this.identity = identity; }
    }

    private final WorkerMenu menu;
    private final Map<ItemStack, Identity> identities = new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
    private final Set<UUID> selected = new LinkedHashSet<>();
    private List<Source> sources = List.of();
    private List<Variant> variants = List.of();
    private int mode;
    private String dimension = "";
    private UUID worker;
    private String search = "";
    private int page;
    private int sourcePage;
    private long revision;
    private long contentsRevision;
    private Preview preview;

    WorkerCollection(WorkerMenu menu) { this.menu = menu; }

    void sourcePage(int page) { sourcePage = page; }

    void query(int mode, String dimension, UUID worker, String search, int page) {
        if (worker != null) { menu.roster().view(menu.owner(), worker); }
        if (this.mode != mode || !this.dimension.equals(dimension)) { sourcePage = 0; }
        this.mode = mode;
        this.dimension = dimension;
        this.worker = worker;
        this.search = search.toLowerCase(Locale.ROOT).strip();
        this.page = page;
        revision++;
        preview = null;
        refresh();
    }

    void select(long expected, UUID id, boolean value) {
        validate(expected);
        if (variants.stream().noneMatch(variant -> variant.identity.id().equals(id))) {
            throw new IllegalArgumentException("INVALID_VARIANT");
        }
        if (value) { selected.add(id); } else { selected.remove(id); }
        preview = null;
    }

    void selectAll(long expected, boolean clear) {
        validate(expected);
        if (clear) { selected.clear(); }
        else { filtered().forEach(variant -> selected.add(variant.identity.id())); }
        preview = null;
    }

    CompoundTag previewRetirement(long expected) {
        validate(expected);
        if (sources.stream().anyMatch(source -> !source.view.retired() && !source.available)) {
            throw new IllegalStateException("WORKER_UNAVAILABLE");
        }
        preview = new Preview(UUID.randomUUID(), revision, contentsRevision, Set.copyOf(selected), menu.server().getTickCount());
        CompoundTag result = new CompoundTag();
        result.putString("Kind", "CollectionPreview");
        result.putUUID("Confirmation", preview.token());
        ListTag workers = new ListTag();
        for (Source source : sources) {
            if (!source.view.retired()) { workers.add(sourceRow(source)); }
        }
        result.put("Workers", workers);
        result.putLong("Amount", selectedAmount());
        return result;
    }

    CompoundTag collect(long expected) {
        validate(expected);
        preview = null;
        return transfer(false);
    }

    CompoundTag collectAndRetire(UUID token) {
        Preview confirmed = preview;
        preview = null;
        if (confirmed == null || !confirmed.token().equals(token)
                || menu.server().getTickCount() - confirmed.tick() > 1200 || !confirmed.selected().equals(selected)) {
            throw new IllegalStateException("INVALID_CONFIRMATION");
        }
        validate(confirmed.revision());
        if (confirmed.contentsRevision() != contentsRevision) { throw new IllegalStateException("STALE_COLLECTION"); }
        return transfer(true);
    }

    private CompoundTag transfer(boolean retire) {
        long requested = selectedAmount();
        long moved = 0;
        int retired = 0;
        // All sources/revisions/contents were checked above on the same server thread.
        for (Source source : sources) {
            if (!source.available) { continue; }
            WorkerRoster.View view = source.view;
            Container inventory = view.retired() ? menu.roster().archivedContainer(menu.owner(), view.worker())
                    : menu.roster().active(menu.owner(), view.worker()).inventory();
            List<Integer> eligible = eligible(source.stacks, view.retired());
            boolean changed = false;
            for (int slot = 0; slot < source.stacks.size(); slot++) {
                ItemStack stack = source.stacks.get(slot);
                int amount = eligible.get(slot);
                if (amount == 0 || !selected.contains(identity(stack).id())) { continue; }
                ItemStack transfer = stack.copyWithCount(amount);
                int accepted = menu.insertCollectionStack(transfer);
                if (accepted == 0) { continue; }
                if (slot < WorkerEntity.INVENTORY_SIZE) { inventory.removeItem(slot, accepted); }
                else { menu.roster().removeArchivedEquipment(menu.owner(), view.worker(), slot - WorkerEntity.INVENTORY_SIZE, accepted); }
                changed = true;
                moved += accepted;
            }
            if (changed && !view.retired()) { menu.roster().changed(menu.roster().active(menu.owner(), view.worker())); }
        }
        if (retire) {
            for (Source source : sources) {
                if (!source.view.retired()) {
                    UUID id = source.view.worker();
                    menu.roster().retire(menu.owner(), id, menu.roster().view(menu.owner(), id).revision());
                    retired++;
                }
            }
        }
        refresh();
        CompoundTag result = new CompoundTag();
        result.putString("Kind", "CollectionResult");
        result.putLong("Collected", moved);
        result.putLong("Remaining", requested - moved);
        result.putInt("Retired", retired);
        return result;
    }

    private void validate(long expected) {
        refresh();
        if (expected != revision) { throw new IllegalStateException("STALE_COLLECTION"); }
    }

    private void refresh() {
        List<WorkerRoster.View> views = new ArrayList<>();
        if (mode != 1) { views.addAll(menu.roster().list(menu.owner(), false)); }
        if (mode != 0) { views.addAll(menu.roster().list(menu.owner(), true)); }
        List<Source> next = new ArrayList<>();
        for (WorkerRoster.View view : views) {
            if ((!dimension.isEmpty() && !dimension.equals(view.dimension()))
                    || (worker != null && !worker.equals(view.worker()))) { continue; }
            List<ItemStack> stacks = new ArrayList<>();
            boolean available = !menu.pending(view.worker());
            if (available) {
                try {
                    if (view.retired()) {
                        menu.roster().archivedInventory(menu.owner(), view.worker()).forEach(stack -> stacks.add(stack.copy()));
                        stacks.addAll(menu.roster().archivedEquipment(menu.owner(), view.worker()));
                    } else {
                        WorkerEntity live = menu.roster().active(menu.owner(), view.worker());
                        for (int slot = 0; slot < live.getContainerSize(); slot++) { stacks.add(live.getItem(slot).copy()); }
                        for (EquipmentSlot slot : EquipmentSlot.values()) {
                            if (slot != EquipmentSlot.MAINHAND) { stacks.add(live.getItemBySlot(slot).copy()); }
                        }
                    }
                } catch (IllegalStateException unavailable) {
                    available = false;
                    stacks.clear();
                }
            }
            next.add(new Source(view, available, List.copyOf(stacks)));
        }
        if (sameSources(next)) { return; }
        // Mining changes counts between a snapshot and a click. Variant selection and ordinary
        // transfer use the current live amounts; only retirement freezes exact source contents.
        contentsRevision++;
        sources = next;
        Map<UUID, Variant> grouped = new LinkedHashMap<>();
        for (Source source : sources) {
            List<Integer> amounts = eligible(source.stacks, source.view.retired());
            for (int slot = 0; slot < amounts.size(); slot++) {
                int amount = amounts.get(slot);
                if (amount == 0) { continue; }
                Identity id = identity(source.stacks.get(slot));
                Variant variant = grouped.computeIfAbsent(id.id(), ignored -> new Variant(id));
                variant.count += amount;
                variant.amounts.merge(source.view.worker(), amount, Integer::sum);
            }
        }
        variants = List.copyOf(grouped.values());
    }

    private boolean sameSources(List<Source> next) {
        if (sources.size() != next.size()) { return false; }
        for (int index = 0; index < next.size(); index++) {
            Source before = sources.get(index);
            Source after = next.get(index);
            // Position changes during mining do not invalidate an unchanged inventory view.
            if (!before.view.worker().equals(after.view.worker()) || before.view.revision() != after.view.revision()
                    || before.view.retired() != after.view.retired() || !before.view.dimension().equals(after.view.dimension())
                    || !before.view.name().equals(after.view.name()) || before.available != after.available
                    || !ItemStack.listMatches(before.stacks, after.stacks)) { return false; }
        }
        return true;
    }

    static List<Integer> eligible(List<ItemStack> stacks, boolean retired) {
        List<Integer> amounts = new ArrayList<>();
        int reserve = WorkerInventoryManagement.COBBLESTONE_RESERVE;
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            int amount = stack.getCount();
            if (!retired) {
                if (slot >= WorkerEntity.INVENTORY_SIZE || activeEquipment(stack)) { amount = 0; }
                else if (WorkerInventoryManagement.ordinaryCobblestone(stack)) {
                    int protectedCount = Math.min(reserve, amount);
                    reserve -= protectedCount;
                    amount -= protectedCount;
                }
            }
            amounts.add(amount);
        }
        return amounts;
    }

    private static boolean activeEquipment(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) || stack.has(DataComponents.TOOL) || Equipable.get(stack) != null;
    }

    private Identity identity(ItemStack stack) {
        Identity identity = identities.get(stack);
        if (identity == null) {
            ItemStack key = stack.copyWithCount(1);
            identity = new Identity(UUID.randomUUID(), key);
            identities.put(key, identity);
        }
        return identity;
    }

    private List<Variant> filtered() {
        return variants.stream().filter(variant -> search.isEmpty()
                || variant.identity.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(search)
                || BuiltInRegistries.ITEM.getKey(variant.identity.stack().getItem()).toString().contains(search)).toList();
    }

    private long selectedAmount() {
        return variants.stream().filter(variant -> selected.contains(variant.identity.id())).mapToLong(variant -> variant.count).sum();
    }

    CompoundTag snapshot() {
        refresh();
        CompoundTag data = new CompoundTag();
        data.putLong("Revision", revision);
        data.putInt("Mode", mode);
        data.putString("Dimension", dimension);
        ListTag dimensions = new ListTag();
        menu.server().getAllLevels().forEach(level -> dimensions.add(StringTag.valueOf(level.dimension().location().toString())));
        data.put("Dimensions", dimensions);
        data.putString("Worker", worker == null ? "" : worker.toString());
        data.putString("Search", search);
        List<Variant> visible = filtered();
        page = Math.min(page, Math.max(0, (visible.size() - 1) / PAGE_SIZE));
        data.putInt("Page", page);
        data.putInt("Count", visible.size());
        data.putInt("Selected", (int) variants.stream().filter(variant -> selected.contains(variant.identity.id())).count());
        data.putLong("SelectedAmount", selectedAmount());
        ListTag items = new ListTag();
        int first = page * PAGE_SIZE;
        for (Variant variant : visible.subList(first, Math.min(visible.size(), first + PAGE_SIZE))) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Variant", variant.identity.id());
            row.put("Stack", variant.identity.stack().save(menu.server().registryAccess()));
            row.putLong("Count", variant.count);
            row.putBoolean("Selected", selected.contains(variant.identity.id()));
            ListTag amounts = new ListTag();
            variant.amounts.entrySet().stream().limit(PAGE_SIZE).forEach(entry -> {
                CompoundTag source = new CompoundTag();
                source.putUUID("Worker", entry.getKey());
                source.putString("Name", menu.roster().view(menu.owner(), entry.getKey()).name());
                source.putInt("Count", entry.getValue());
                amounts.add(source);
            });
            row.put("Sources", amounts);
            row.putInt("SourceCount", variant.amounts.size());
            items.add(row);
        }
        data.put("Items", items);
        ListTag workers = new ListTag();
        // The item page is bounded; source rows are separately paged for large archive collections.
        for (Source source : sources.subList(0, Math.min(sources.size(), PAGE_SIZE))) { workers.add(sourceRow(source)); }
        data.put("Sources", workers);
        data.putInt("SourceCount", sources.size());
        data.putInt("Unavailable", (int) sources.stream().filter(source -> !source.available).count());
        List<WorkerRoster.View> options = new ArrayList<>();
        if (mode != 1) { options.addAll(menu.roster().list(menu.owner(), false)); }
        if (mode != 0) { options.addAll(menu.roster().list(menu.owner(), true)); }
        options.removeIf(view -> !dimension.isEmpty() && !dimension.equals(view.dimension()));
        sourcePage = Math.min(sourcePage, Math.max(0, (options.size() - 1) / PAGE_SIZE));
        data.putInt("SourcePage", sourcePage);
        data.putInt("WorkerOptionCount", options.size());
        ListTag workerOptions = new ListTag();
        for (WorkerRoster.View view : options.subList(sourcePage * PAGE_SIZE, Math.min(options.size(), (sourcePage + 1) * PAGE_SIZE))) {
            workerOptions.add(sourceRow(new Source(view, true, List.of())));
        }
        data.put("WorkerOptions", workerOptions);
        return data;
    }

    private static CompoundTag sourceRow(Source source) {
        CompoundTag row = new CompoundTag();
        row.putUUID("Worker", source.view.worker());
        row.putString("Name", source.view.name());
        row.putString("Dimension", source.view.dimension());
        row.putBoolean("Retired", source.view.retired());
        row.putBoolean("Available", source.available);
        return row;
    }
}
