package automatone.worker;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Gives each progression block a readable name without adding client-only code. */
public final class ControlHubBlockItem extends BlockItem {
    private final ControlHubTier tier;

    public ControlHubBlockItem(Block block, ControlHubTier tier) {
        super(block, new Properties().stacksTo(1));
        this.tier = tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(tier.displayName());
    }
}