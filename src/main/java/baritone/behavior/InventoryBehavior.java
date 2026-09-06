/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.Helper;
import baritone.utils.ToolSet;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.concurrent.ThreadLocalRandom;

public final class InventoryBehavior extends Behavior implements Helper {

    int ticksSinceLastInventoryMove;
    int[] lastTickRequestedMove; // not everything asks every tick, so remember the request while coming to a halt

    public InventoryBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!baritone.getSettings().allowInventory.value) {
            return;
        }
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }
        Container inventory = ctx.inventory();
        if (inventory == null || inventory.getContainerSize() < 9) {
            return;
        }
        ticksSinceLastInventoryMove++;
        if (firstValidThrowaway() >= 9) { // aka there are none on the hotbar, but there are some in main inventory
            requestSwapWithHotBar(firstValidThrowaway(), 8);
        }
        int pick = bestToolAgainst(Blocks.STONE, PickaxeItem.class);
        if (pick >= 9) {
            requestSwapWithHotBar(pick, 0);
        }
        if (lastTickRequestedMove != null) {
            logDebug("Remembering to move " + lastTickRequestedMove[0] + " " + lastTickRequestedMove[1] + " from a previous tick");
            requestSwapWithHotBar(lastTickRequestedMove[0], lastTickRequestedMove[1]);
        }
    }

    public boolean attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar) {
        OptionalInt destination = getTempHotbarSlot(disallowedHotbar);
        if (destination.isPresent()) {
            if (!requestSwapWithHotBar(inMainInvy, destination.getAsInt())) {
                return false;
            }
        }
        return true;
    }

    public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
        // we're using 0 and 8 for pickaxe and throwaway
        Container inventory = ctx.inventory();
        if (inventory == null || inventory.getContainerSize() < 9) {
            return OptionalInt.empty();
        }
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < 8; i++) {
            if (inventory.getItem(i).isEmpty() && !disallowedHotbar.test(i)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            for (int i = 1; i < 8; i++) {
                if (!disallowedHotbar.test(i)) {
                    candidates.add(i);
                }
            }
        }
        if (candidates.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private boolean requestSwapWithHotBar(int inInventory, int inHotbar) {
        Container inventory = ctx.inventory();
        if (inventory == null
                || inInventory < 0
                || inInventory >= inventory.getContainerSize()
                || inHotbar < 0
                || inHotbar >= Math.min(9, inventory.getContainerSize())) {
            lastTickRequestedMove = null;
            return false;
        }
        lastTickRequestedMove = new int[]{inInventory, inHotbar};
        if (inInventory == inHotbar) {
            lastTickRequestedMove = null;
            return true;
        }
        if (ticksSinceLastInventoryMove < baritone.getSettings().ticksBetweenInventoryMoves.value) {
            logDebug("Inventory move requested but delaying " + ticksSinceLastInventoryMove + " " + baritone.getSettings().ticksBetweenInventoryMoves.value);
            return false;
        }
        if (baritone.getSettings().inventoryMoveOnlyIfStationary.value && !baritone.getInventoryPauserProcess().stationaryForInventoryMove()) {
            logDebug("Inventory move requested but delaying until stationary");
            return false;
        }
        ctx.playerController().swapContainerSlots(inventory, inInventory, inHotbar);
        ticksSinceLastInventoryMove = 0;
        lastTickRequestedMove = null;
        return true;
    }

    private int firstValidThrowaway() { // TODO offhand idk
        Container inventory = ctx.inventory();
        if (inventory == null) {
            return -1;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (baritone.getSettings().acceptableThrowawayItems.value.contains(inventory.getItem(i).getItem())) {
                return i;
            }
        }
        return -1;
    }

    private int bestToolAgainst(Block against, Class<? extends DiggerItem> cla$$) {
        Container inventory = ctx.inventory();
        if (inventory == null) {
            return -1;
        }
        int bestInd = -1;
        double bestSpeed = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (baritone.getSettings().itemSaver.value && (stack.getDamageValue() + baritone.getSettings().itemSaverThreshold.value) >= stack.getMaxDamage() && stack.getMaxDamage() > 1) {
                continue;
            }
            if (cla$$.isInstance(stack.getItem())) {
                double speed = ToolSet.calculateSpeedVsBlock(stack, against.defaultBlockState()); // takes into account enchants
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestInd = i;
                }
            }
        }
        return bestInd;
    }

    public boolean hasGenericThrowaway() {
        return hasGenericThrowaway(baritone.getSettings());
    }

    public boolean hasGenericThrowaway(Settings settings) {
        for (Item item : settings.acceptableThrowawayItems.value) {
            if (throwaway(false, stack -> item.equals(stack.getItem()), settings.allowInventory.value)) {
                return true;
            }
        }
        return false;
    }

    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
        BlockState maybe = baritone.getBuilderProcess().placeAt(x, y, z, baritone.bsi.get0(x, y, z));
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock().equals(maybe.getBlock()))) {
            return true;
        }
        for (Item item : baritone.getSettings().acceptableThrowawayItems.value) {
            if (throwaway(select, stack -> item.equals(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
        return throwaway(select, desired, baritone.getSettings().allowInventory.value);
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired, boolean allowInventory) {
        Container inv = ctx.inventory();
        if (inv == null || inv.getContainerSize() < 9) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            // this usage of settings() is okay because it's only called once during pathing
            // (while creating the CalculationContext at the very beginning)
            // and then it's called during execution
            // since this function is never called during cost calculation, we don't need to migrate
            // acceptableThrowawayItems to the CalculationContext
            if (desired.test(item)) {
                if (select) {
                    ctx.setSelectedSlot(i);
                }
                return true;
            }
        }

        if (allowInventory) {
            for (int i = 9; i < inv.getContainerSize(); i++) {
                if (desired.test(inv.getItem(i))) {
                    if (select) {
                        requestSwapWithHotBar(i, 7);
                        ctx.setSelectedSlot(7);
                    }
                    return true;
                }
            }
        }

        return false;
    }
}
