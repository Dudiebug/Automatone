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

package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {
    // base ticks between block breaks caused by tick logic
    private static final int BASE_BREAK_DELAY = 1;

    private final IPlayerContext ctx;
    private boolean wasHitting;
    private int breakDelayTimer = 0;

    BlockBreakHelper(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    public void stopBreakingBlock() {
        // The player controller will never be null, but the player can be
        if (ctx.player() != null) {
            ctx.playerController().setHittingBlock(false);
            ctx.playerController().resetBlockRemoving();
        }
        wasHitting = false;
        breakDelayTimer = 0;
    }

    public void tick(boolean isLeftClick) {
        HitResult trace = isLeftClick ? ctx.objectMouseOver() : null;
        if (!(trace instanceof BlockHitResult blockTrace) || trace.getType() != HitResult.Type.BLOCK) {
            stopBreakingBlock();
            return;
        }
        if (breakDelayTimer > 0) {
            breakDelayTimer--;
            return;
        }

        ctx.playerController().setHittingBlock(wasHitting);
        if (ctx.playerController().hasBrokenBlock()) {
            ctx.playerController().syncHeldItem();
            ctx.playerController().clickBlock(blockTrace.getBlockPos(), blockTrace.getDirection());
            ctx.player().swing(InteractionHand.MAIN_HAND);
        } else {
            if (ctx.playerController().onPlayerDamageBlock(blockTrace.getBlockPos(), blockTrace.getDirection())) {
                ctx.player().swing(InteractionHand.MAIN_HAND);
            }
            if (ctx.playerController().hasBrokenBlock()) { // block broken this tick
                // break delay timer only applies for multi-tick block breaks like vanilla
                breakDelayTimer = BaritoneAPI.getSettings().blockBreakSpeed.value - BASE_BREAK_DELAY;
                // must reset controller's destroy delay to prevent the client from delaying itself unnecessarily
                ctx.playerController().resetDestroyDelay();
            }
        }
        // if true, we're breaking a block. if false, we broke the block this tick
        wasHitting = !ctx.playerController().hasBrokenBlock();
        // This per-tick flag clear is distinct from an explicit interaction abort.
        ctx.playerController().setHittingBlock(false);
    }
}
