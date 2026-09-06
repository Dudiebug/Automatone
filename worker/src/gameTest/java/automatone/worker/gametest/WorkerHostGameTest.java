package automatone.worker.gametest;

import automatone.worker.WorkerEntity;
import automatone.worker.WorkerMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("automatone_worker_gametest")
@PrefixGameTestTemplate(false)
public final class WorkerHostGameTest {

    @GameTest(template = "provider_smoke", batch = "worker_host", timeoutTicks = 100)
    public static void registeredWorkerSpawnsWithMinimalHostContract(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        try {
            helper.assertTrue(worker.getType().equals(WorkerMod.WORKER.get()),
                    "Spawn helper must create the registered worker entity type");
            helper.assertTrue(Math.abs(worker.getMaxHealth() - 20.0F) < 0.001F,
                    "Worker health must be 20");
            helper.assertTrue(Math.abs(worker.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.1D) < 0.001D,
                    "Worker movement speed must be 0.1");
            helper.assertTrue(Math.abs(worker.getDimensions(Pose.STANDING).width() - 0.6F) < 0.001F,
                    "Worker standing width must be 0.6");
            helper.assertTrue(Math.abs(worker.getDimensions(Pose.STANDING).height() - 1.8F) < 0.001F,
                    "Worker standing height must be 1.8");
            helper.assertTrue(Math.abs(worker.maxUpStep() - 0.6F) < 0.001F,
                    "Worker step height must be 0.6");
            helper.assertTrue(worker.goalSelector.getAvailableGoals().isEmpty(),
                    "Worker must not register wandering, combat, or product goals");
            helper.assertTrue(worker.getTarget() == null,
                    "Worker must not acquire a combat or following target");
            helper.assertTrue(worker.canPickUpLoot(),
                    "Worker must collect nearby drops into its host inventory");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }

        helper.assertTrue(worker.isRemoved(), "Worker must despawn cleanly when discarded");
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_host", timeoutTicks = 100)
    public static void inventorySelectionAndMainHandRemainConsistent(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        try {
            Container inventory = worker.inventory();
            helper.assertTrue(inventory.getContainerSize() == WorkerEntity.INVENTORY_SIZE
                            && WorkerEntity.INVENTORY_SIZE == 36
                            && WorkerEntity.HOTBAR_SIZE == 9,
                    "Worker host inventory must expose 36 slots with a nine-slot hotbar");
            helper.assertTrue(worker.selectedSlot() == 0,
                    "Worker selected slot must start at zero");
            helper.assertFalse(inventory.canPlaceItem(0, new ItemStack(Items.COBBLESTONE)),
                    "Worker inventory must reject automated insertion");
            helper.assertFalse(inventory.canTakeItem(inventory, 0, new ItemStack(Items.COBBLESTONE)),
                    "Worker inventory must reject automated extraction");

            ItemStack slotZero = new ItemStack(Items.IRON_PICKAXE);
            inventory.setItem(0, slotZero);
            helper.assertTrue(worker.getMainHandItem() == slotZero,
                    "Selected slot zero must be the worker main hand");

            ItemStack slotEight = new ItemStack(Items.DIAMOND_PICKAXE);
            inventory.setItem(8, slotEight);
            worker.setSelectedSlot(8);
            helper.assertTrue(worker.selectedSlot() == 8 && worker.getMainHandItem() == slotEight,
                    "Selected slot eight must replace the worker main hand");
            assertInvalidSelectionDoesNotMutate(helper, worker, -1, slotEight);
            assertInvalidSelectionDoesNotMutate(helper, worker, WorkerEntity.HOTBAR_SIZE, slotEight);

            ItemStack replacement = new ItemStack(Items.GOLD_NUGGET, 2);
            inventory.setItem(8, replacement);
            helper.assertTrue(worker.getMainHandItem() == replacement,
                    "Replacing the selected inventory slot must replace the worker main hand");

            ItemStack removedNormally = inventory.removeItem(8, 1);
            helper.assertTrue(removedNormally.is(Items.GOLD_NUGGET)
                            && removedNormally.getCount() == 1,
                    "Normal selected-slot removal must return one item from the selected stack");
            helper.assertTrue(inventory.getItem(8).is(Items.GOLD_NUGGET)
                            && inventory.getItem(8).getCount() == 1,
                    "Normal selected-slot removal must leave one item in the selected inventory slot");
            helper.assertTrue(worker.getMainHandItem().is(Items.GOLD_NUGGET)
                            && worker.getMainHandItem().getCount() == 1,
                    "Normal selected-slot removal must update the worker main hand");

            ItemStack removed = inventory.removeItemNoUpdate(8);
            helper.assertTrue(removed.is(Items.GOLD_NUGGET) && removed.getCount() == 1,
                    "Removing the selected slot without update must return the remaining stack");
            helper.assertTrue(worker.getMainHandItem().isEmpty(),
                    "Removing the selected slot must clear the worker main hand");

            worker.setSelectedSlot(0);
            ItemStack setThroughHand = new ItemStack(Items.STONE_PICKAXE);
            worker.setItemSlot(EquipmentSlot.MAINHAND, setThroughHand);
            helper.assertTrue(inventory.getItem(0) == setThroughHand,
                    "Main-hand setItemSlot must write the selected inventory slot");
            helper.assertTrue(worker.getMainHandItem() == setThroughHand,
                    "Main-hand getItemBySlot must read the selected inventory slot");
        } finally {
            WorkerGameTestSupport.discardWorker(worker);
        }

        helper.assertTrue(worker.isRemoved(), "Inventory host worker must be removable after the check");
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "worker_host", timeoutTicks = 100)
    public static void pickupRespectsDelayCapacityAndHeldTool(GameTestHelper helper) {
        WorkerEntity worker = WorkerGameTestSupport.spawnWorker(helper);
        ItemEntity drop = new ItemEntity(helper.getLevel(), worker.getX(), worker.getY(), worker.getZ(),
                new ItemStack(Items.RAW_IRON, 5));
        try {
            worker.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            for (int slot = 1; slot < worker.getContainerSize(); slot++) {
                worker.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            worker.setItem(1, new ItemStack(Items.RAW_IRON, 62));
            drop.setPickUpDelay(10);
            helper.getLevel().addFreshEntity(drop);
            worker.aiStep();
            helper.assertTrue(drop.getItem().getCount() == 5 && worker.getItem(1).getCount() == 62,
                    "Pickup delay must keep the complete dropped stack in the world");

            drop.setNoPickUpDelay();
            worker.aiStep();
            helper.assertTrue(worker.getItem(1).getCount() == 64
                            && !drop.isRemoved() && drop.getItem().getCount() == 3,
                    "Only the two items that fit may leave the dropped stack");
            worker.aiStep();
            helper.assertTrue(!drop.isRemoved() && drop.getItem().getCount() == 3,
                    "A full inventory must leave the remainder on the ground");

            worker.setItem(2, ItemStack.EMPTY);
            worker.aiStep();
            helper.assertTrue(drop.isRemoved() && worker.getItem(2).is(Items.RAW_IRON)
                            && worker.getItem(2).getCount() == 3,
                    "Freeing inventory space must allow the remaining real items to be collected");
            helper.assertTrue(worker.getMainHandItem().is(Items.IRON_PICKAXE),
                    "Picking up ore must preserve the selected mining tool");
        } finally {
            drop.discard();
            WorkerGameTestSupport.discardWorker(worker);
        }
        helper.succeed();
    }

    private static void assertInvalidSelectionDoesNotMutate(
            GameTestHelper helper,
            WorkerEntity worker,
            int invalidSlot,
            ItemStack expectedHeld
    ) {
        int selectedBefore = worker.selectedSlot();
        boolean rejected = false;
        try {
            worker.setSelectedSlot(invalidSlot);
        } catch (IndexOutOfBoundsException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Invalid selected slot " + invalidSlot + " must be rejected");
        helper.assertTrue(worker.selectedSlot() == selectedBefore,
                "Invalid selected slot " + invalidSlot + " must not change selection");
        helper.assertTrue(worker.getMainHandItem() == expectedHeld,
                "Invalid selected slot " + invalidSlot + " must not change the main hand");
    }

}
