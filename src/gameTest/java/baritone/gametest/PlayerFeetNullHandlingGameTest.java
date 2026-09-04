package baritone.gametest;

import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class PlayerFeetNullHandlingGameTest {

    @GameTest(template = "provider_smoke", batch = "playerfeet_null_20260904")
    public static void nullWorldKeepsBaseFeet(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            BetterBlockPos actual = fixture.context(() -> null).playerFeet();
            helper.assertTrue(actual.equals(fixture.baseFeet),
                    "Null world must keep the +0.1251-adjusted base feet");
        });
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "playerfeet_null_20260904")
    public static void ordinaryBlockKeepsBaseFeet(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.setFeetBlock(Blocks.STONE.defaultBlockState());
            BetterBlockPos actual = fixture.context(() -> fixture.level).playerFeet();
            helper.assertTrue(actual.equals(fixture.baseFeet),
                    "An ordinary registered block must keep the adjusted base feet");
        });
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "playerfeet_null_20260904")
    public static void slabAdvancesBaseFeet(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            fixture.setFeetBlock(Blocks.OAK_SLAB.defaultBlockState());
            BetterBlockPos actual = fixture.context(() -> fixture.level).playerFeet();
            helper.assertTrue(actual.equals(fixture.baseFeet.above()),
                    "A registered slab must advance feet by exactly one block");
        });
        helper.succeed();
    }

    @GameTest(template = "provider_smoke", batch = "playerfeet_null_20260904")
    public static void worldGetterNpePropagates(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            NullPointerException sentinel = new NullPointerException("world getter sentinel");
            NullPointerException observed = null;
            try {
                fixture.context(() -> {
                    throw sentinel;
                }).playerFeet();
            } catch (NullPointerException exception) {
                observed = exception;
            }
            helper.assertTrue(observed == sentinel,
                    "An NPE from world() must propagate as the same exception");
        });
        helper.succeed();
    }

    private static void withFixture(GameTestHelper helper, Consumer<Fixture> assertion) {
        Fixture fixture = Fixture.create(helper);
        try {
            assertion.accept(fixture);
        } finally {
            fixture.close();
        }
    }

    private static final class Fixture {
        private final ServerLevel level;
        private final ArmorStand host;
        private final BetterBlockPos baseFeet;
        private final BlockState originalFeetState;

        private Fixture(ServerLevel level, ArmorStand host, BetterBlockPos baseFeet, BlockState originalFeetState) {
            this.level = level;
            this.host = host;
            this.baseFeet = baseFeet;
            this.originalFeetState = originalFeetState;
        }

        private static Fixture create(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
            ArmorStand host = new ArmorStand(level, origin.getX() + 0.5D, origin.getY() + 0.95D, origin.getZ() + 0.5D);
            host.setNoGravity(true);
            level.addFreshEntity(host);
            BetterBlockPos baseFeet = new BetterBlockPos(
                    host.position().x,
                    host.position().y + 0.1251D,
                    host.position().z
            );
            return new Fixture(level, host, baseFeet, level.getBlockState(baseFeet));
        }

        private IPlayerContext context(Supplier<Level> world) {
            return new FixturePlayerContext(host, world);
        }

        private void setFeetBlock(BlockState state) {
            level.setBlock(baseFeet, state, 3);
        }

        private void close() {
            level.setBlock(baseFeet, originalFeetState, 3);
            if (!host.isRemoved()) {
                host.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    private static final class FixturePlayerContext implements IPlayerContext {
        private final LivingEntity host;
        private final Supplier<Level> worldSupplier;

        private FixturePlayerContext(LivingEntity host, Supplier<Level> worldSupplier) {
            this.host = Objects.requireNonNull(host, "host");
            this.worldSupplier = Objects.requireNonNull(worldSupplier, "worldSupplier");
        }

        @Override
        public LivingEntity player() {
            return host;
        }

        @Override
        @Nullable
        public Container inventory() {
            return null;
        }

        @Override
        public int selectedSlot() {
            return 0;
        }

        @Override
        public void setSelectedSlot(int slot) {
        }

        @Override
        public baritone.api.utils.IPlayerController playerController() {
            return null;
        }

        @Override
        public Level world() {
            return worldSupplier.get();
        }

        @Override
        @Nullable
        public IWorldData worldData() {
            return null;
        }

        @Override
        public HitResult objectMouseOver() {
            return null;
        }
    }

    private PlayerFeetNullHandlingGameTest() {
    }
}
