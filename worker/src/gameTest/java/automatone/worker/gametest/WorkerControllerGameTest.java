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

import java.util.ArrayList;
import java.util.List;

/** Loads the shipped recipe through the dedicated server's real recipe manager. */
@GameTestHolder("automatone_worker_m5_controller_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerControllerGameTest {
    private WorkerControllerGameTest() {
    }

    @GameTest(template = "provider_smoke", timeoutTicks = 100)
    public static void controllerCraftsFromApprovedIngredientsWithoutBindingOrDurability(GameTestHelper helper) {
        List<ItemStack> ingredients = List.of(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GLASS_PANE), new ItemStack(Items.IRON_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY);
        CraftingInput input = CraftingInput.of(3, 3, ingredients);
        var manager = helper.getLevel().getRecipeManager();
        var recipe = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        helper.assertTrue(recipe.id().equals(ResourceLocation.fromNamespaceAndPath(WorkerMod.MOD_ID, "controller")),
                "The shipped controller recipe must match four iron, copper, glass pane and redstone");
        ItemStack result = recipe.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(WorkerMod.CONTROLLER.get()) && result.getCount() == 1,
                "Crafting produces exactly one controller");
        helper.assertTrue(result.getMaxStackSize() == 1 && !result.isDamageableItem(),
                "The reusable controller is non-stackable and has no durability");
        helper.assertTrue(ItemStack.isSameItemSameComponents(result, WorkerMod.CONTROLLER.get().getDefaultInstance()),
                "The crafted controller has only default components, with no binding or energy state");
        for (int missing : new int[]{0, 1, 2, 3, 4, 5, 7}) {
            List<ItemStack> incomplete = new ArrayList<>(ingredients);
            incomplete.set(missing, ItemStack.EMPTY);
            helper.assertTrue(!recipe.value().matches(CraftingInput.of(3, 3, incomplete), helper.getLevel()),
                    "Every one of the seven specified ingredients is required");
        }
        helper.succeed();
    }
}
