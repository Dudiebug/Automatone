package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerEntityController;
import baritone.api.IBaritone;
import baritone.api.utils.IPlayerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder("automatone_worker_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerBreakTargetingGameTest {

    @GameTest(template = "worker_movement", batch = "worker_m3_targeting_rejections", timeoutTicks = 100)
    public static void distinguishesActualEyeReachAndRejectsInvalidTargets(GameTestHelper helper) {
        TargetingFixture fixture = null;
        try {
            fixture = TargetingFixture.create(helper);
            BlockPos target = fixture.place(new BlockPos(6, 1, 9), Blocks.STONE.defaultBlockState());
            fixture.clear(new BlockPos(6, 2, 7));
            fixture.clear(new BlockPos(6, 2, 8));
            helper.assertTrue(fixture.controller.getBlockReachDistance() == 4.5D,
                    "The worker controller must use the fixed 4.5-block survival reach");

            fixture.worker.setYRot(90.0F);
            fixture.worker.setXRot(0.0F);
            helper.assertFalse(fixture.controller.onPlayerDamageBlock(target, Direction.NORTH),
                    "A target outside the worker's actual view ray must be rejected");
            fixture.assertIdle("Misalignment must not leave break intent behind");

            fixture.aimAt(target);
            fixture.place(new BlockPos(6, 2, 7), Blocks.STONE.defaultBlockState());
            helper.assertFalse(fixture.controller.onPlayerDamageBlock(target, Direction.NORTH),
                    "An occluded target must be rejected even when its original ray was aligned");
            fixture.assertIdle("Occlusion must not leave break intent behind");

            fixture.clear(new BlockPos(6, 2, 7));
            fixture.replace(target, Blocks.AIR.defaultBlockState());
            helper.assertFalse(fixture.controller.onPlayerDamageBlock(target, Direction.NORTH),
                    "An air target must be rejected");
            fixture.assertIdle("Invalid targets must not leave break intent behind");

            BlockPos boundaryTarget = fixture.place(new BlockPos(6, 1, 12), Blocks.STONE.defaultBlockState());
            fixture.positionEyeNorthOf(boundaryTarget, 4.49D);
            helper.assertTrue(fixture.northFaceDistance(boundaryTarget) < fixture.controller.getBlockReachDistance(),
                    "The inside reach fixture must be below the controller's exact 4.5-block limit");
            BlockHitResult insideHit = fixture.aimAt(boundaryTarget);
            helper.assertTrue(fixture.controller.onPlayerDamageBlock(boundaryTarget, insideHit.getDirection()),
                    "A block just inside the worker eye-ray reach limit must be accepted");
            fixture.assertBreaking(boundaryTarget, "The inside reach target must become the active intent");

            fixture.controller.resetBlockRemoving();
            fixture.positionEyeNorthOf(boundaryTarget, 4.51D);
            helper.assertTrue(fixture.northFaceDistance(boundaryTarget) > fixture.controller.getBlockReachDistance(),
                    "The outside reach fixture must exceed the controller's exact 4.5-block limit");
            fixture.assertRayMisses(boundaryTarget);
            helper.assertFalse(fixture.controller.onPlayerDamageBlock(boundaryTarget, Direction.NORTH),
                    "A block just outside the worker eye-ray reach limit must be rejected");
            fixture.assertIdle("An outside-reach request must not retain break intent");
        } catch (Throwable failure) {
            helper.fail("Worker targeting rejection failed: " + failure);
        } finally {
            if (fixture != null) {
                fixture.close();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_targeting_invalidation", timeoutTicks = 100)
    public static void retargetAndValidationClearOnlyStaleBreakIntent(GameTestHelper helper) {
        TargetingFixture fixture = null;
        try {
            fixture = TargetingFixture.create(helper);
            fixture.worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            fixture.worker.setItem(1, new ItemStack(Items.IRON_PICKAXE));
            fixture.worker.setSelectedSlot(0);
            BlockPos first = fixture.place(new BlockPos(6, 1, 9), Blocks.STONE.defaultBlockState());
            fixture.clear(new BlockPos(6, 2, 7));
            fixture.clear(new BlockPos(6, 2, 8));
            fixture.begin(first);
            fixture.assertBreaking(first, "A valid target must begin transient break intent");

            fixture.controller.setHittingBlock(false);
            fixture.assertBreaking(first,
                    "BlockBreakHelper's per-tick false hitting flag must not cancel valid break intent");

            BlockPos second = fixture.place(new BlockPos(7, 1, 9), Blocks.STONE.defaultBlockState());
            BlockHitResult secondHit = fixture.aimAt(second);
            helper.assertTrue(fixture.controller.clickBlock(second, secondHit.getDirection()),
                    "A newly raycast target must be accepted");
            fixture.assertBreaking(second, "Retargeting must discard the old target and retain only the new one");

            fixture.worker.setSelectedSlot(1);
            fixture.controller.validateBreakingTarget();
            fixture.assertIdle("Changing selected slots must reset state even for identical tools");

            fixture.worker.setSelectedSlot(0);
            fixture.begin(second);
            fixture.worker.setItem(0, new ItemStack(Items.STICK));
            fixture.controller.validateBreakingTarget();
            fixture.assertIdle("Changing the selected tool must reset the tool snapshot");

            fixture.worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            fixture.begin(second);
            fixture.replace(second, Blocks.DIRT.defaultBlockState());
            fixture.controller.validateBreakingTarget();
            fixture.assertIdle("Replacing the target block must clear stale break intent");
        } catch (Throwable failure) {
            helper.fail("Worker targeting invalidation failed: " + failure);
        } finally {
            if (fixture != null) {
                fixture.close();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_targeting_validation", timeoutTicks = 100)
    public static void passiveValidationClearsMovedOrMisalignedBreakIntent(GameTestHelper helper) {
        TargetingFixture fixture = null;
        try {
            fixture = TargetingFixture.create(helper);
            BlockPos target = fixture.place(new BlockPos(6, 1, 9), Blocks.STONE.defaultBlockState());
            fixture.clear(new BlockPos(6, 2, 7));
            fixture.clear(new BlockPos(6, 2, 8));
            fixture.begin(target);

            fixture.moveToLocal(6.5D, 1.0D, 2.5D);
            fixture.controller.validateBreakingTarget();
            fixture.assertIdle("Moving the worker out of reach must clear intent without a new break callback");

            fixture.moveToDefaultPosition();
            fixture.begin(target);
            fixture.worker.setYRot(90.0F);
            fixture.worker.setXRot(0.0F);
            fixture.controller.validateBreakingTarget();
            fixture.assertIdle("Looking away must clear intent without a new break callback");
        } catch (Throwable failure) {
            helper.fail("Worker targeting passive validation failed: " + failure);
        } finally {
            if (fixture != null) {
                fixture.close();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "worker_movement", batch = "worker_m3_targeting_removal", timeoutTicks = 100)
    public static void workerRemovalClearsActiveBreakIntent(GameTestHelper helper) {
        TargetingFixture fixture = null;
        try {
            fixture = TargetingFixture.create(helper);
            BlockPos target = fixture.place(new BlockPos(6, 1, 9), Blocks.STONE.defaultBlockState());
            fixture.clear(new BlockPos(6, 2, 7));
            fixture.clear(new BlockPos(6, 2, 8));
            fixture.begin(target);
            fixture.assertBreaking(target, "Removal fixture requires active break intent before removal");

            fixture.worker.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

            fixture.assertIdle("Worker removal must clear transient break intent");
            helper.assertTrue(fixture.worker.runtime() == null,
                    "Worker removal must dispose the runtime after controller cleanup");
        } catch (Throwable failure) {
            helper.fail("Worker removal targeting cleanup failed: " + failure);
        } finally {
            if (fixture != null) {
                fixture.close();
            }
        }
        helper.succeed();
    }

    private static final class TargetingFixture {
        private final GameTestHelper helper;
        private final WorkerEntity worker;
        private final WorkerEntityController controller;
        private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();

        private TargetingFixture(GameTestHelper helper, WorkerEntity worker, WorkerEntityController controller) {
            this.helper = helper;
            this.worker = worker;
            this.controller = controller;
        }

        private static TargetingFixture create(GameTestHelper helper) {
            WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
            IBaritone runtime = worker.runtime();
            if (runtime == null) {
                WorkerGameTestSupport.discardWorker(worker);
                throw new AssertionError("Targeting fixture requires a live worker runtime");
            }
            IPlayerContext context = runtime.getPlayerContext();
            WorkerEntityController controller = (WorkerEntityController) context.playerController();
            TargetingFixture fixture = new TargetingFixture(helper, worker, controller);
            fixture.moveToDefaultPosition();
            return fixture;
        }

        private BlockPos place(BlockPos localPosition, BlockState state) {
            return replace(helper.absolutePos(localPosition), state);
        }

        private BlockPos clear(BlockPos localPosition) {
            return place(localPosition, Blocks.AIR.defaultBlockState());
        }

        private BlockPos replace(BlockPos position, BlockState state) {
            originalBlocks.putIfAbsent(position, helper.getLevel().getBlockState(position));
            helper.getLevel().setBlock(position, state, 3);
            return position;
        }

        private BlockHitResult aimAt(BlockPos target) {
            lookAt(target);
            HitResult trace = worker.runtime().getPlayerContext().objectMouseOver();
            if (!(trace instanceof BlockHitResult hit)) {
                throw new AssertionError("Expected block ray for " + target + " but got " + trace);
            }
            if (!hit.getBlockPos().equals(target)) {
                throw new AssertionError("Expected ray target " + target + " but hit " + hit.getBlockPos()
                        + " from " + worker.getEyePosition(1.0F));
            }
            return hit;
        }

        private void lookAt(BlockPos target) {
            worker.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        }

        private void moveToDefaultPosition() {
            moveToLocal(6.5D, 1.0D, 6.5D);
        }

        private void moveToLocal(double x, double y, double z) {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            worker.moveTo(origin.getX() + x, origin.getY() + y, origin.getZ() + z, 0.0F, 0.0F);
        }

        private void positionEyeNorthOf(BlockPos target, double northFaceDistance) {
            double eyeX = target.getX() + 0.5D;
            double eyeY = target.getY() + 0.5D;
            double eyeZ = target.getZ() - northFaceDistance;
            worker.moveTo(eyeX, target.getY(), eyeZ, 0.0F, 0.0F);
            worker.moveTo(eyeX, worker.getY() + eyeY - worker.getEyePosition(1.0F).y, eyeZ, 0.0F, 0.0F);
            Vec3 eye = worker.getEyePosition(1.0F);
            if (Math.abs(eye.x - eyeX) > 0.0001D || Math.abs(eye.y - eyeY) > 0.0001D
                    || Math.abs(eye.z - eyeZ) > 0.0001D) {
                throw new AssertionError("Could not align the worker eye with the reach boundary: " + eye);
            }
            lookAt(target);
        }

        private double northFaceDistance(BlockPos target) {
            return target.getZ() - worker.getEyePosition(1.0F).z;
        }

        private void assertRayMisses(BlockPos target) {
            HitResult trace = worker.runtime().getPlayerContext().objectMouseOver();
            if (trace.getType() != HitResult.Type.MISS) {
                throw new AssertionError("Expected reach-limited ray to miss " + target + " but got " + trace);
            }
        }

        private void begin(BlockPos target) {
            BlockHitResult hit = aimAt(target);
            helper.assertTrue(controller.onPlayerDamageBlock(target, hit.getDirection()),
                    "A visible in-reach target must start break intent");
        }

        private void assertBreaking(BlockPos expected, String message) {
            helper.assertTrue(expected.equals(controller.breakingBlock()), message);
            helper.assertTrue(controller.breakProgress() < 1.0F,
                    "An active target must remain incomplete while M3.1 validates its identity");
            helper.assertFalse(controller.hasBrokenBlock(), "Target selection must not report block destruction");
        }

        private void assertIdle(String message) {
            helper.assertTrue(controller.breakingBlock() == null, message);
            helper.assertTrue(controller.breakProgress() == 0.0F,
                    "Reset break intent must clear accumulated progress");
            helper.assertFalse(controller.hasBrokenBlock(), "Reset break intent must not report block destruction");
        }

        private void close() {
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                helper.getLevel().setBlock(entry.getKey(), entry.getValue(), 3);
            }
            WorkerGameTestSupport.discardWorker(worker);
        }
    }
}
