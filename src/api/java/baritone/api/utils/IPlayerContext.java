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

package baritone.api.utils;

import baritone.api.cache.IWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Brady
 * @since 11/12/2018
 */
public interface IPlayerContext {

    LivingEntity player();

    /**
     * The host's server-side inventory/container. Non-player hosts may not have one.
     */
    @Nullable
    Container inventory();

    /** The selected hotbar slot maintained by the host adapter. */
    int selectedSlot();

    /** Updates the selected hotbar slot maintained by the host adapter. */
    void setSelectedSlot(int slot);

    IPlayerController playerController();

    Level world();

    default Iterable<Entity> entities() {
        Level level = world();
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getAllEntities();
        }
        return java.util.List.of();
    }

    default Stream<Entity> entitiesStream() {
        return StreamSupport.stream(entities().spliterator(), false);
    }


    IWorldData worldData();

    HitResult objectMouseOver();

    default BetterBlockPos playerFeet() {
        // TODO find a better way to deal with soul sand!!!!!
        BetterBlockPos feet = new BetterBlockPos(player().position().x, player().position().y + 0.1251, player().position().z);

        Level level = world();
        if (level != null && level.getBlockState(feet).getBlock() instanceof SlabBlock) {
            return feet.above();
        }

        return feet;
    }

    default Vec3 playerFeetAsVec() {
        return new Vec3(player().position().x, player().position().y, player().position().z);
    }

    default Vec3 playerHead() {
        return player().getEyePosition(1.0F);
    }

    default Vec3 playerMotion() {
        return player().getDeltaMovement();
    }

    default BetterBlockPos viewerPos() {
        return playerFeet();
    }

    default Rotation playerRotations() {
        return new Rotation(player().getYRot(), player().getXRot());
    }

    /**
     * Returns the player's eye height, taking into account whether or not the player is sneaking.
     *
     * @param ifSneaking Whether or not the player is sneaking
     * @return The player's eye height
     * @deprecated Use entity.getEyeHeight(Pose.CROUCHING) instead
     */
    @Deprecated
    static double eyeHeight(boolean ifSneaking) {
        return ifSneaking ? 1.27 : 1.62;
    }

    /**
     * Returns the block that the crosshair is currently placed over. Updated once per tick.
     *
     * @return The position of the highlighted block
     */
    default Optional<BlockPos> getSelectedBlock() {
        HitResult result = objectMouseOver();
        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            return Optional.of(((BlockHitResult) result).getBlockPos());
        }
        return Optional.empty();
    }

    default boolean isLookingAt(BlockPos pos) {
        return getSelectedBlock().equals(Optional.of(pos));
    }
}
