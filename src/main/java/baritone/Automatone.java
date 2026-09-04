package baritone;

import com.mojang.logging.LogUtils;
import baritone.api.BaritoneAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(Automatone.MOD_ID)
public final class Automatone {
    public static final String MOD_ID = "automatone";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final TagKey<Item> EMPTY_BUCKETS = TagKey.create(Registries.ITEM, id("empty_buckets"));
    public static final TagKey<Item> WATER_BUCKETS = TagKey.create(Registries.ITEM, id("water_buckets"));

    public Automatone() {
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        BaritoneAPI.getProvider().tick();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        BaritoneAPI.getProvider().disposeAll();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
