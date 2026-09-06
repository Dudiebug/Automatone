package automatone.worker;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(WorkerMod.MOD_ID)
public final class WorkerMod {
    public static final String MOD_ID = "automatone_worker";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredHolder<Item, WorkerControllerItem> CONTROLLER = ITEMS.register("controller", WorkerControllerItem::new);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<WorkerMenu>> MENU = MENUS.register("controller",
            () -> IMenuTypeExtension.create(WorkerMenu::new));
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerEntity>> WORKER = ENTITIES.register("worker",
            () -> EntityType.Builder.of(WorkerEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).build(MOD_ID + ":worker"));

    public WorkerMod(IEventBus bus) {
        ITEMS.register(bus);
        MENUS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(WorkerNetwork::register);
        bus.addListener(WorkerMod::creativeItems);
        bus.addListener(WorkerMod::registerAttributes);
        bus.addListener(WorkerChunkLoading::register);
        NeoForge.EVENT_BUS.addListener(WorkerChunkLoading::tick);
        NeoForge.EVENT_BUS.addListener(WorkerChunkLoading::clear);
        NeoForge.EVENT_BUS.addListener(WorkerRelocation::onServerTick);
        NeoForge.EVENT_BUS.addListener(WorkerBatch::onServerTick);
        NeoForge.EVENT_BUS.addListener(WorkerBatch::logout);
        NeoForge.EVENT_BUS.addListener(WorkerBatch::stop);
        NeoForge.EVENT_BUS.addListener(WorkerRelocation::stop);
        NeoForge.EVENT_BUS.addListener(WorkerNotifications::login);
        NeoForge.EVENT_BUS.addListener(WorkerNotifications::logout);
        NeoForge.EVENT_BUS.addListener(WorkerNotifications::stop);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockDropsEvent.class, WorkerEntity::onBlockDrops);
    }

    private static void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(CONTROLLER.get());
        }
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WORKER.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.MINING_EFFICIENCY)
                .add(Attributes.BLOCK_BREAK_SPEED)
                .add(Attributes.SUBMERGED_MINING_SPEED)
                .add(Attributes.STEP_HEIGHT, 0.6).build());
    }
}
