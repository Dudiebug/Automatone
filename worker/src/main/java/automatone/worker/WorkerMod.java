package automatone.worker;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(WorkerMod.MOD_ID)
public final class WorkerMod {
    public static final String MOD_ID = "automatone_worker";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerEntity>> WORKER = ENTITIES.register("worker",
            () -> EntityType.Builder.of(WorkerEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).build(MOD_ID + ":worker"));

    public WorkerMod(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(WorkerMod::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WORKER.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.STEP_HEIGHT, 0.6).build());
    }
}
