package automatone.worker.gametest;

import automatone.worker.WorkerMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Proves survival progression enters through the physical hub instead of the legacy controller recipe. */
@GameTestHolder("automatone_worker_m5_controller_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerControllerGameTest {
    private WorkerControllerGameTest() {
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void basicControlHubReplacesDirectControllerCraft(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<ItemStack> oldControllerIngredients = List.of(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GLASS_PANE), new ItemStack(Items.IRON_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY);
        CraftingInput oldInput = CraftingInput.of(3, 3, oldControllerIngredients);
        var oldMatch = manager.getRecipeFor(RecipeType.CRAFTING, oldInput, helper.getLevel());
        helper.assertTrue(oldMatch.isEmpty() || !oldMatch.orElseThrow().value()
                        .assemble(oldInput, helper.getLevel().registryAccess()).is(WorkerMod.CONTROLLER.get()),
                "The legacy direct controller recipe must not bypass Control Hub progression");

        List<ItemStack> hubIngredients = List.of(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.BARREL), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(Items.IRON_INGOT));
        CraftingInput hubInput = CraftingInput.of(3, 3, hubIngredients);
        var hubRecipe = manager.getRecipeFor(RecipeType.CRAFTING, hubInput, helper.getLevel()).orElseThrow();
        helper.assertTrue(hubRecipe.id().equals(ResourceLocation.fromNamespaceAndPath(WorkerMod.MOD_ID, "control_hub_t1")),
                "The survival entry recipe must craft Control Hub Mk I");
        ItemStack result = hubRecipe.value().assemble(hubInput, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(WorkerMod.CONTROL_HUB_T1_ITEM.get()) && result.getCount() == 1,
                "Control Hub Mk I recipe must produce exactly one physical hub");
        helper.succeed();
    }
}