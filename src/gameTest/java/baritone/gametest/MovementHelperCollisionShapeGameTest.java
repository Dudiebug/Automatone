package baritone.gametest;

import baritone.pathing.movement.MovementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class MovementHelperCollisionShapeGameTest {

    private static final DeferredRegister.Blocks TEST_BLOCKS = DeferredRegister.createBlocks("automatone_gametest");
    private static final DeferredBlock<ContextSensitiveBlock> CONTEXT_SENSITIVE_BLOCK = TEST_BLOCKS.registerBlock(
            "context_sensitive_collision_test", ContextSensitiveBlock::new, BlockBehaviour.Properties.of().dynamicShape());

    static void registerBlocks(net.neoforged.bus.api.IEventBus modEventBus) {
        TEST_BLOCKS.register(modEventBus);
    }

    @GameTest(template = "provider_smoke")
    public static void fullAndEmptyShapesKeepTheirExistingClassification(GameTestHelper helper) {
        helper.assertTrue(MovementHelper.isBlockNormalCube(Blocks.STONE.defaultBlockState()),
                "Full collision shape must remain classified as normal cube");
        helper.assertFalse(MovementHelper.isBlockNormalCube(Blocks.AIR.defaultBlockState()),
                "Empty collision shape must remain non-normal");
        helper.succeed();
    }

    @GameTest(template = "provider_smoke")
    public static void contextSensitiveShapeReadsTheSuppliedWorldAndPosition(GameTestHelper helper) {
        BlockPos fullRelativePosition = new BlockPos(0, 1, 0);
        BlockPos emptyRelativePosition = new BlockPos(1, 1, 0);
        BlockGetter world = helper.getLevel();
        BlockPos fullAbsolutePosition = helper.absolutePos(fullRelativePosition);
        BlockPos emptyAbsolutePosition = helper.absolutePos(emptyRelativePosition);
        helper.setBlock(fullRelativePosition, Blocks.STONE.defaultBlockState());
        helper.setBlock(emptyRelativePosition, Blocks.AIR.defaultBlockState());

        BlockState state = CONTEXT_SENSITIVE_BLOCK.get().defaultBlockState();

        helper.assertFalse(MovementHelper.isBlockNormalCube(state),
                "Context-free collision classification must remain conservative");
        helper.assertTrue(Block.isShapeFullBlock(state.getCollisionShape(world, fullAbsolutePosition)),
                "Public collision API must receive the real world and full-shape position");
        helper.assertFalse(Block.isShapeFullBlock(state.getCollisionShape(world, emptyAbsolutePosition)),
                "Public collision API must receive the real world and empty-shape position");
        helper.assertTrue(MovementHelper.isBlockNormalCube(state, world, fullAbsolutePosition),
                "Context-aware helper must classify the real full shape");
        helper.assertFalse(MovementHelper.isBlockNormalCube(state, world, emptyAbsolutePosition),
                "Context-aware helper must classify the real empty shape");
        helper.succeed();
    }

    private static final class ContextSensitiveBlock extends Block {

        private ContextSensitiveBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos position, CollisionContext context) {
            return world.getBlockState(position).is(Blocks.STONE) ? Shapes.block() : Shapes.empty();
        }
    }
}
