package baritone.pathing.sensor.fixture;

import net.minecraft.client.Minecraft;

public final class ClientDependentFixture {
    private ClientDependentFixture() {
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }
}
