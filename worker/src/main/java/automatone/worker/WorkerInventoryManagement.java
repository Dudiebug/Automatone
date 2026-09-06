package automatone.worker;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pickup filtering only; held contents are never automatically ejected or deleted. */
final class WorkerInventoryManagement {
    static final int COBBLESTONE_RESERVE = 64;
    private Set<ResourceLocation> ignoredBlocks = new LinkedHashSet<>(defaultBlocks());
    private boolean overridden;
    private Set<String> targets = Set.of();
    private final Set<Item> targetItems = new LinkedHashSet<>();
    private final Set<ResourceLocation> protectedItems = new LinkedHashSet<>();

    static List<ResourceLocation> defaultBlocks() {
        return List.of("dirt", "cobblestone", "cobbled_deepslate", "gravel", "sand", "red_sand",
                "netherrack", "tuff", "andesite", "diorite", "granite").stream()
                .map(ResourceLocation::withDefaultNamespace).toList();
    }

    static List<ResourceLocation> validate(List<ResourceLocation> blocks) {
        if (blocks.size() > 128 || new LinkedHashSet<>(blocks).size() != blocks.size()) {
            throw new IllegalArgumentException("INVALID_INVENTORY_POLICY");
        }
        for (ResourceLocation id : blocks) {
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)
                    || BuiltInRegistries.BLOCK.get(id).asItem().equals(Items.AIR)) {
                throw new IllegalArgumentException("INVALID_BLOCK");
            }
        }
        return List.copyOf(blocks);
    }

    void configure(List<ResourceLocation> blocks) {
        ignoredBlocks = new LinkedHashSet<>(validate(blocks));
        overridden = true;
    }

    void inherit(List<ResourceLocation> blocks) {
        if (!overridden) { ignoredBlocks = new LinkedHashSet<>(validate(blocks)); }
    }

    void reset(List<ResourceLocation> blocks) {
        List<ResourceLocation> validated = validate(blocks);
        overridden = false;
        inherit(validated);
    }

    CompoundTag settings() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Override", overridden);
        tag.put("Blocks", strings(ignoredBlocks.stream().map(ResourceLocation::toString).toList()));
        return tag;
    }

    CompoundTag save() {
        CompoundTag tag = settings();
        tag.putInt("Version", 2);
        tag.put("Targets", strings(targets.stream().sorted().toList()));
        tag.put("ProtectedItems", strings(protectedItems.stream().map(ResourceLocation::toString).toList()));
        return tag;
    }

    static WorkerInventoryManagement load(CompoundTag tag) {
        WorkerInventoryManagement result = new WorkerInventoryManagement();
        if (tag.isEmpty()) { return result; }
        try {
            List<ResourceLocation> blocks = readStrings(tag, "Blocks", 128).stream().map(ResourceLocation::parse).toList();
            result.configure(blocks);
            if (tag.contains("Version")) {
                if (tag.getInt("Version") != 2 || !tag.contains("Override", Tag.TAG_BYTE)
                        || (tag.getByte("Override") != 0 && tag.getByte("Override") != 1)) {
                    throw new IllegalArgumentException("INVALID_INVENTORY_POLICY");
                }
                result.overridden = tag.getBoolean("Override");
            } else {
                // Legacy Enable/Keep knobs are superseded, but customized block lists survive.
                result.overridden = !result.ignoredBlocks.equals(new LinkedHashSet<>(defaultBlocks()));
            }
            result.targets = Set.copyOf(readStrings(tag, "Targets", MiningSession.MAX_TARGET_BLOCKS));
            for (String item : readStrings(tag, "ProtectedItems", BuiltInRegistries.ITEM.size())) {
                ResourceLocation id = ResourceLocation.parse(item);
                if (!BuiltInRegistries.ITEM.containsKey(id)) { throw new IllegalArgumentException("INVALID_ITEM"); }
                result.protectedItems.add(id);
            }
            return result;
        } catch (IllegalArgumentException | ResourceLocationException invalid) {
            // A bad policy cannot mutate inventory; recover the documented pickup defaults.
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

    static ListTag strings(Iterable<String> values) {
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
            for (String target : current) { targetItems.add(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target)).asItem()); }
        }
    }

    void recordDrops(WorkerEntity worker, BlockDropsEvent event) {
        updateTargets(worker);
        if (!targets.contains(BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString())) { return; }
        // The final event arrives after COMPLETED, so match configured targets rather than job state.
        for (ItemEntity drop : event.getDrops()) {
            if (!drop.getItem().isEmpty()) { protectedItems.add(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem())); }
        }
    }

    int pickupLimit(WorkerEntity worker, ItemStack stack) {
        updateTargets(worker);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)
                || !stack.getComponentsPatch().isEmpty() || targetItems.contains(stack.getItem())
                || protectedItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) { return stack.getCount(); }
        if (stack.is(Items.COBBLESTONE)) {
            int stored = 0;
            for (int slot = 0; slot < worker.getContainerSize(); slot++) {
                ItemStack item = worker.getItem(slot);
                if (ordinaryCobblestone(item)) { stored += item.getCount(); }
            }
            return Math.min(stack.getCount(), Math.max(0, COBBLESTONE_RESERVE - stored));
        }
        return ignoredBlocks.contains(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock())) ? 0 : stack.getCount();
    }

    static boolean ordinaryCobblestone(ItemStack stack) {
        return stack.is(Items.COBBLESTONE) && stack.getComponentsPatch().isEmpty();
    }
}
