package baritone.gametest;

import baritone.api.utils.BlockOptionalMeta;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class ApiBlockOptionalMetaOwnershipGameTest {

    @GameTest(template = "provider_smoke")
    public static void accessorCannotMutateOwnedStateSet(GameTestHelper helper) {
        BlockOptionalMeta meta = new BlockOptionalMeta(Blocks.STONE);
        Set<BlockState> exposed = meta.getAllBlockStates();
        int originalSize = exposed.size();

        try {
            exposed.clear();
        } catch (UnsupportedOperationException ignored) {
            // An immutable view is also a valid ownership boundary.
        }

        helper.assertValueEqual(meta.getAllBlockStates().size(), originalSize,
                "BlockOptionalMeta state ownership must survive accessor mutation");
        helper.succeed();
    }
}
