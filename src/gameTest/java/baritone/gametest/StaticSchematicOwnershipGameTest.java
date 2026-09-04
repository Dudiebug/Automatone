package baritone.gametest;

import baritone.utils.schematic.StaticSchematic;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class StaticSchematicOwnershipGameTest {

    @GameTest(template = "provider_smoke", batch = "schematic_ownership_20260904")
    public static void callerArraysAndColumnsCannotMutateStaticSchematic(GameTestHelper helper) {
        BlockState stableOuter = Blocks.STONE.defaultBlockState();
        BlockState stableMiddle = Blocks.DIRT.defaultBlockState();
        BlockState stableColumn = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState stablePublishedColumn = Blocks.EMERALD_BLOCK.defaultBlockState();
        BlockState replacement = Blocks.DIAMOND_BLOCK.defaultBlockState();
        BlockState[][][] callerStates = {
                {
                        {stableOuter, stableOuter, stableOuter},
                        {stableOuter, stableOuter, stableOuter}
                },
                {
                        {stableMiddle, stableMiddle, stableMiddle},
                        {stablePublishedColumn, stablePublishedColumn, stableColumn}
                }
        };
        StaticSchematic schematic = new StaticSchematic(callerStates);

        helper.assertTrue(schematic.widthX() == 2, "StaticSchematic width must preserve caller dimensions");
        helper.assertTrue(schematic.heightY() == 3, "StaticSchematic height must preserve caller dimensions");
        helper.assertTrue(schematic.lengthZ() == 2, "StaticSchematic length must preserve caller dimensions");

        callerStates[0] = new BlockState[][]{
                {replacement, replacement, replacement},
                {replacement, replacement, replacement}
        };
        callerStates[1][0] = new BlockState[]{replacement, replacement, replacement};
        callerStates[1][1][2] = replacement;
        BlockState[] publishedColumn = schematic.getColumn(1, 1);
        publishedColumn[0] = replacement;

        assertStableState(helper, schematic, 0, 0, 0, stableOuter, "outer caller array");
        assertStableState(helper, schematic, 1, 1, 0, stableMiddle, "middle caller array");
        assertStableState(helper, schematic, 1, 2, 1, stableColumn, "column caller array");
        assertStableState(helper, schematic, 1, 0, 1, stablePublishedColumn, "returned getColumn array");
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "schematic_ownership_20260904")
    public static void emptyDimensionsRemainZero(GameTestHelper helper) {
        assertEmptyDimensions(helper, new BlockState[0][2][3], "empty X dimension");
        assertEmptyDimensions(helper, new BlockState[2][0][3], "empty Z dimension");
        assertEmptyDimensions(helper, new BlockState[2][2][0], "empty Y dimension");
        helper.succeed();
    }

    private static void assertStableState(
            GameTestHelper helper,
            StaticSchematic schematic,
            int x,
            int y,
            int z,
            BlockState expected,
            String mutationSource
    ) {
        helper.assertTrue(schematic.getDirect(x, y, z) == expected,
                mutationSource + " must not change getDirect state identity");
        helper.assertTrue(schematic.desiredState(x, y, z, Blocks.AIR.defaultBlockState(), List.of()) == expected,
                mutationSource + " must not change desiredState identity");
    }

    private static void assertEmptyDimensions(GameTestHelper helper, BlockState[][][] states, String description) {
        StaticSchematic schematic = new StaticSchematic(states);
        helper.assertTrue(schematic.widthX() == 0 && schematic.heightY() == 0 && schematic.lengthZ() == 0,
                description + " must report zero schematic dimensions");
    }

    private StaticSchematicOwnershipGameTest() {
    }
}
