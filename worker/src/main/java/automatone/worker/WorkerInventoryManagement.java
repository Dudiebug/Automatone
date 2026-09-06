package automatone.worker;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional consumer inventory cleanup; native Automatone still selects tools and manages the hotbar. */
final class WorkerInventoryManagement {
    private boolean enabled;
    private int keep = 64;
    private Set<ResourceLocation> discardBlocks = defaultBlocks();
    private Set<String> targets = Set.of();
    private final Set<Item> targetItems = new LinkedHashSet<>();
    private final Set<ResourceLocation> protectedItems = new LinkedHashSet<>();

    private static Set<ResourceLocation> defaultBlocks() {
        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        for (String name : List.of("dirt", "cobblestone", "cobbled_deepslate", "gravel", "sand", "red_sand",
                "netherrack", "tuff", "andesite", "diorite", "granite")) {
            blocks.add(ResourceLocation.withDefaultNamespace(name));
        }
        return blocks;
    }

    void configure(boolean enabled, int keep, List<ResourceLocation> blocks) {
        if (keep < 0 || keep > 4096 || blocks.size() > 128 || new LinkedHashSet<>(blocks).size() != blocks.size()) {
            throw new IllegalArgumentException("INVALID_INVENTORY_POLICY");
        }
        for (ResourceLocation id : blocks) {
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)
                    || BuiltInRegistries.BLOCK.get(id).asItem().equals(Items.AIR)) {
                throw new IllegalArgumentException("INVALID_BLOCK");
            }
        }
        this.enabled = enabled;
        this.keep = keep;
        this.discardBlocks = new LinkedHashSet<>(blocks);
    }

    CompoundTag settings() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Keep", keep);
        tag.put("Blocks", strings(discardBlocks.stream().map(ResourceLocation::toString).toList()));
        return tag;
    }

    CompoundTag save() {
        CompoundTag tag = settings();
        tag.put("Targets", strings(targets.stream().sorted().toList()));
        tag.put("ProtectedItems", strings(protectedItems.stream().map(ResourceLocation::toString).toList()));
        return tag;
    }

    static WorkerInventoryManagement load(CompoundTag tag) {
        WorkerInventoryManagement result = new WorkerInventoryManagement();
        if (tag.isEmpty()) { return result; }
        try {
            if (!tag.contains("Enabled", Tag.TAG_BYTE) || (tag.getByte("Enabled") != 0 && tag.getByte("Enabled") != 1)
                    || !tag.contains("Keep", Tag.TAG_INT)) {
                throw new IllegalArgumentException("INVALID_INVENTORY_POLICY");
            }
            result.configure(tag.getBoolean("Enabled"), tag.getInt("Keep"),
                    readStrings(tag, "Blocks", 128).stream().map(ResourceLocation::parse).toList());
            result.targets = Set.copyOf(readStrings(tag, "Targets", MiningSession.MAX_TARGET_BLOCKS));
            for (String item : readStrings(tag, "ProtectedItems", BuiltInRegistries.ITEM.size())) {
                ResourceLocation id = ResourceLocation.parse(item);
                if (!BuiltInRegistries.ITEM.containsKey(id)) { throw new IllegalArgumentException("INVALID_ITEM"); }
                result.protectedItems.add(id);
            }
            return result;
        } catch (IllegalArgumentException | ResourceLocationException invalid) {
            // A removed mod or malformed saved policy must disable disposal, never lose inventory.
            return new WorkerInventoryManagement();
        }
    }

    private static List<String> readStrings(CompoundTag tag, String key, int maximum) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() > maximum
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING)) {
            throw new IllegalArgumentException("INVALID_INVENTORY_POLICY");
        }
        List<String> values = new ArrayList<>();
        for (Tag value : list) {
            if (value.getAsString().length() > 256) { throw new IllegalArgumentException("INVALID_INVENTORY_POLICY"); }
            values.add(value.getAsString());
        }
        return values;
    }

    private static ListTag strings(Iterable<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    private void updateTargets(WorkerEntity worker) {
        Set<String> current = Set.copyOf(worker.miningStatus().targets());
        if (!targets.equals(current)) {
            targets = current;
            targetItems.clear();
            protectedItems.clear();
        }
        if (targetItems.isEmpty()) {
            for (String target : current) {
                targetItems.add(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target)).asItem());
            }
        }
    }

    void recordDrops(WorkerEntity worker, BlockDropsEvent event) {
        updateTargets(worker);
        if (!targets.contains(BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString())) { return; }
        // The final block's event happens after the job becomes COMPLETED. Match targets, not RUNNING.
        for (ItemEntity drop : event.getDrops()) {
            if (!drop.getItem().isEmpty()) { protectedItems.add(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem())); }
        }
    }

    int pickupLimit(WorkerEntity worker, ItemStack stack) {
        updateTargets(worker);
        if (!disposable(stack)) { return stack.getCount(); }
        int stored = 0;
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            ItemStack item = worker.getItem(slot);
            if (item.is(stack.getItem()) && disposable(item)) { stored += item.getCount(); }
        }
        return Math.min(stack.getCount(), Math.max(0, keep - stored));
    }

    private boolean disposable(ItemStack stack) {
        if (!enabled || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)
                || !stack.getComponentsPatch().isEmpty()
                || targetItems.contains(stack.getItem())
                || protectedItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) { return false; }
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        return discardBlocks.contains(block);
    }

    private Map<Item, List<Integer>> discardSlots(WorkerEntity worker) {
        updateTargets(worker);
        Map<Item, List<Integer>> groups = new LinkedHashMap<>();
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            ItemStack stack = worker.getItem(slot);
            if (disposable(stack)) { groups.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>()).add(slot); }
        }
        return groups;
    }

    boolean canMakeSpace(WorkerEntity worker) {
        if (!enabled || !(worker.level() instanceof ServerLevel level)
                || WorkerMenu.isOpenFor(level.getServer(), worker.getUUID())) { return false; }
        for (List<Integer> slots : discardSlots(worker).values()) {
            int amount = slots.stream().mapToInt(slot -> worker.getItem(slot).getCount()).sum();
            int stackSize = worker.getItem(slots.getFirst()).getMaxStackSize();
            int reserveSlots = (Math.min(keep, amount) + stackSize - 1) / stackSize;
            if (slots.size() > reserveSlots) { return true; }
        }
        return false;
    }

    void makeSpace(WorkerEntity worker, ItemStack wanted) {
        if (!canMakeSpace(worker)) { return; }
        ServerLevel level = (ServerLevel) worker.level();
        for (List<Integer> slots : discardSlots(worker).values()) {
            // Merge identical unwanted stacks first so preserving a partial reserve cannot strand a slot.
            for (int to = 0; to < slots.size(); to++) {
                ItemStack destination = worker.getItem(slots.get(to));
                if (destination.isEmpty()) { continue; }
                for (int from = to + 1; from < slots.size(); from++) {
                    ItemStack source = worker.getItem(slots.get(from));
                    if (source.isEmpty() || !ItemStack.isSameItemSameComponents(destination, source)) { continue; }
                    int moved = Math.min(source.getCount(), destination.getMaxStackSize() - destination.getCount());
                    if (moved > 0) {
                        destination.grow(moved);
                        source.shrink(moved);
                        if (source.isEmpty()) { worker.setItem(slots.get(from), ItemStack.EMPTY); }
                        worker.setChanged();
                    }
                }
            }
            if (worker.hasInventorySpace(wanted)) { return; }
            int excess = slots.stream().mapToInt(slot -> worker.getItem(slot).getCount()).sum() - keep;
            for (int index = slots.size() - 1; index >= 0; index--) {
                int slot = slots.get(index);
                ItemStack stack = worker.getItem(slot);
                if (stack.isEmpty() || stack.getCount() > excess) { continue; }
                ItemEntity drop = new ItemEntity(level, worker.getX(), worker.getY() + 0.3, worker.getZ(), stack.copy());
                drop.setPickUpDelay(40);
                if (level.addFreshEntity(drop)) {
                    excess -= stack.getCount();
                    worker.setItem(slot, ItemStack.EMPTY);
                    if (worker.hasInventorySpace(wanted)) { return; }
                }
            }
        }
    }

}
