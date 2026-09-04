package baritone.gametest;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.cache.IWorldData;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.IPlayerController;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.Moves;
import baritone.pathing.movement.movements.MovementDescend;
import baritone.pathing.movement.movements.MovementFall;
import baritone.utils.pathing.MutableMoveResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("automatone_gametest")
@PrefixGameTestTemplate(false)
public final class DescendFallParityGameTest {

    @GameTest(template = "provider_smoke", batch = "movement_parity", timeoutTicks = 100)
    public static void descendAndFallEntriesPreserveFrozenNativeSemantics(GameTestHelper helper) {
        IBaritoneProvider provider = BaritoneAPI.getProvider();
        provider.disposeAll();

        ServerLevel level = helper.getLevel();
        ArmorStand host = null;
        Path dataDirectory = null;
        IBaritone runtime = null;
        Throwable failure = null;
        Map<BlockPos, BlockState> originalStates = new LinkedHashMap<>();
        try {
            BlockPos relativeSource = centeredSource(helper);
            BlockPos absoluteSource = helper.absolutePos(relativeSource);
            host = new ArmorStand(level, absoluteSource.getX() + 0.5, absoluteSource.getY(), absoluteSource.getZ() + 0.5);
            host.setNoGravity(true);
            host.setYRot(0.0F);
            host.setXRot(0.0F);
            level.addFreshEntity(host);

            SimpleContainer inventory = new SimpleContainer(9);
            AtomicReference<IBaritone> ownedRuntime = new AtomicReference<>();
            FixturePlayerContext context = new FixturePlayerContext(host, inventory, new FailFastController(host), ownedRuntime);
            dataDirectory = Files.createTempDirectory(FMLPaths.GAMEDIR.get(), "movement-parity-");
            runtime = provider.createBaritone(context, dataDirectory);
            ownedRuntime.set(runtime);

            helper.assertTrue(runtime != null, "Movement parity fixture must create a native runtime");
            helper.assertTrue(context.worldData() != null, "Movement parity fixture must expose native world data");
            BetterBlockPos source = new BetterBlockPos(absoluteSource);
            helper.assertTrue(context.playerFeet().equals(source),
                    "Real entity feet must anchor the movement source");

            List<Moves> directions = List.of(
                    Moves.DESCEND_EAST,
                    Moves.DESCEND_WEST,
                    Moves.DESCEND_NORTH,
                    Moves.DESCEND_SOUTH
            );
            for (boolean deeperFall : List.of(false, true)) {
                for (Moves move : directions) {
                    seedScenario(helper, level, relativeSource, move, deeperFall, originalStates);
                    CalculationContext calculation = new CalculationContext(runtime);
                    Movement expected = frozenApply0(move, calculation, source);
                    Movement actual = move.apply0(calculation, source);
                    assertParity(helper, calculation, expected, actual, deeperFall, move);
                }
            }
        } catch (Throwable caught) {
            failure = caught;
        } finally {
            try {
                provider.disposeAll();
            } catch (Throwable cleanupFailure) {
                failure = combine(failure, cleanupFailure);
            }
            try {
                if (host != null && !host.isRemoved()) {
                    host.remove(Entity.RemovalReason.DISCARDED);
                }
            } catch (Throwable cleanupFailure) {
                failure = combine(failure, cleanupFailure);
            }
            try {
                restore(level, originalStates);
            } catch (Throwable cleanupFailure) {
                failure = combine(failure, cleanupFailure);
            }
            try {
                deleteRecursively(dataDirectory);
            } catch (Throwable cleanupFailure) {
                failure = combine(failure, cleanupFailure);
            }
        }

        if (failure != null) {
            helper.fail("Native descend/fall parity failed: " + failure);
            return;
        }
        helper.succeed();
    }

    /**
     * Source-derived oracle for the four pre-cleanup DESCEND_* apply0 bodies.
     * The production implementation is intentionally not called here.
     */
    private static Movement frozenApply0(Moves move, CalculationContext context, BetterBlockPos source) {
        MutableMoveResult result = new MutableMoveResult();
        move.apply(context, source.x, source.y, source.z, result);
        if (result.y == source.y - 1) {
            return new MovementDescend(context.getBaritone(), source, new BetterBlockPos(result.x, result.y, result.z));
        }
        return new MovementFall(context.getBaritone(), source, new BetterBlockPos(result.x, result.y, result.z));
    }

    private static void assertParity(
            GameTestHelper helper,
            CalculationContext calculation,
            Movement expected,
            Movement actual,
            boolean deeperFall,
            Moves move
    ) {
        Class<?> expectedType = deeperFall ? MovementFall.class : MovementDescend.class;
        helper.assertTrue(expected.getClass() == expectedType,
                "Frozen oracle must produce " + expectedType.getSimpleName() + " for " + move);
        helper.assertTrue(actual.getClass() == expectedType,
                "Current " + move + " must produce " + expectedType.getSimpleName()
                        + " but produced " + actual.getClass().getSimpleName());
        helper.assertTrue(actual.getSrc().equals(expected.getSrc()),
                "Current " + move + " source must match the frozen source");
        helper.assertTrue(actual.getDest().equals(expected.getDest()),
                "Current " + move + " destination must match the frozen destination");
        helper.assertTrue(actual.getDirection().equals(expected.getDirection()),
                "Current " + move + " direction must match the frozen direction");

        double expectedCost = expected.getCost(calculation);
        double actualCost = actual.getCost(calculation);
        helper.assertTrue(Double.compare(actualCost, expectedCost) == 0,
                "Current " + move + " cost must match the frozen cost; expected "
                        + expectedCost + " but was " + actualCost);

        MovementStatus expectedStatus = expected.update();
        MovementStatus actualStatus = actual.update();
        helper.assertValueEqual(expectedStatus, actualStatus,
                "Current " + move + " first update status must match the frozen status");
        helper.assertValueEqual(MovementStatus.RUNNING, actualStatus,
                "Current " + move + " fixture must begin in the running state after preparation");
    }

    private static void seedScenario(
            GameTestHelper helper,
            ServerLevel level,
            BlockPos relativeSource,
            Moves move,
            boolean deeperFall,
            Map<BlockPos, BlockState> originalStates
    ) {
        setBlock(helper, level, originalStates, relativeSource.below(), Blocks.STONE.defaultBlockState());
        BlockPos relativeColumn = new BlockPos(
                relativeSource.getX() + move.xOffset,
                relativeSource.getY(),
                relativeSource.getZ() + move.zOffset
        );
        for (int y = relativeSource.getY() - 4; y <= relativeSource.getY() + 3; y++) {
            setBlock(helper, level, originalStates,
                    new BlockPos(relativeColumn.getX(), y, relativeColumn.getZ()), Blocks.AIR.defaultBlockState());
        }
        BlockPos relativeLanding = new BlockPos(
                relativeColumn.getX(),
                relativeSource.getY() - (deeperFall ? 3 : 2),
                relativeColumn.getZ()
        );
        setBlock(helper, level, originalStates, relativeLanding, Blocks.STONE.defaultBlockState());

        BlockPos absoluteLanding = helper.absolutePos(relativeLanding);
        helper.assertTrue(level.getBlockState(absoluteLanding).is(Blocks.STONE),
                "Native movement fixture landing block must be registered in the ServerLevel");
        helper.assertTrue(level.getChunkSource().getChunkNow(absoluteLanding.getX() >> 4, absoluteLanding.getZ() >> 4) != null,
                "Native movement fixture landing chunk must be loaded");
    }

    private static void setBlock(
            GameTestHelper helper,
            ServerLevel level,
            Map<BlockPos, BlockState> originalStates,
            BlockPos relativePosition,
            BlockState state
    ) {
        BlockPos absolutePosition = helper.absolutePos(relativePosition);
        originalStates.putIfAbsent(absolutePosition, level.getBlockState(absolutePosition));
        helper.setBlock(relativePosition, state);
    }

    private static BlockPos centeredSource(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int centerX = Math.floorDiv(origin.getX(), 16) * 16 + 8;
        int centerZ = Math.floorDiv(origin.getZ(), 16) * 16 + 8;
        return new BlockPos(centerX - origin.getX(), 8, centerZ - origin.getZ());
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originalStates) {
        for (Map.Entry<BlockPos, BlockState> original : originalStates.entrySet()) {
            level.setBlock(original.getKey(), original.getValue(), 3);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Throwable combine(Throwable current, Throwable next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static final class FixturePlayerContext implements IPlayerContext {
        private final LivingEntity host;
        private final Container inventory;
        private final IPlayerController controller;
        private final AtomicReference<IBaritone> ownedRuntime;
        private int selectedSlot;

        private FixturePlayerContext(
                LivingEntity host,
                Container inventory,
                IPlayerController controller,
                AtomicReference<IBaritone> ownedRuntime
        ) {
            this.host = Objects.requireNonNull(host, "host");
            this.inventory = Objects.requireNonNull(inventory, "inventory");
            this.controller = Objects.requireNonNull(controller, "controller");
            this.ownedRuntime = Objects.requireNonNull(ownedRuntime, "ownedRuntime");
        }

        @Override
        public LivingEntity player() {
            return host;
        }

        @Override
        public Container inventory() {
            return inventory;
        }

        @Override
        public int selectedSlot() {
            return selectedSlot;
        }

        @Override
        public void setSelectedSlot(int slot) {
            if (slot < 0 || slot >= Math.min(9, inventory.getContainerSize())) {
                throw new IllegalArgumentException("Selected slot outside fixture hotbar: " + slot);
            }
            selectedSlot = slot;
            host.setItemInHand(InteractionHand.MAIN_HAND, inventory.getItem(slot));
        }

        @Override
        public IPlayerController playerController() {
            return controller;
        }

        @Override
        public Level world() {
            return host.level();
        }

        @Override
        public IWorldData worldData() {
            IBaritone runtime = ownedRuntime.get();
            return runtime == null ? null : runtime.getWorldProvider().getCurrentWorld();
        }

        @Override
        public HitResult objectMouseOver() {
            return null;
        }
    }

    private static final class FailFastController implements IPlayerController {
        private final LivingEntity host;

        private FailFastController(LivingEntity host) {
            this.host = Objects.requireNonNull(host, "host");
        }

        @Override
        public void syncHeldItem() {
        }

        @Override
        public boolean hasBrokenBlock() {
            return false;
        }

        @Override
        public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
            throw unexpected("damage a block");
        }

        @Override
        public void resetBlockRemoving() {
        }

        @Override
        public void swapContainerSlots(Container container, int firstSlot, int secondSlot) {
            throw unexpected("swap container slots");
        }

        @Override
        public GameType getGameType() {
            return GameType.SURVIVAL;
        }

        @Override
        public InteractionResult processRightClickBlock(
                LivingEntity player,
                Level world,
                InteractionHand hand,
                BlockHitResult result
        ) {
            throw unexpected("right-click a block");
        }

        @Override
        public InteractionResult processRightClick(LivingEntity player, Level world, InteractionHand hand) {
            throw unexpected("right-click an item");
        }

        @Override
        public boolean clickBlock(BlockPos loc, Direction face) {
            throw unexpected("click a block");
        }

        @Override
        public void setHittingBlock(boolean hittingBlock) {
        }

        @Override
        public void resetDestroyDelay() {
        }

        private AssertionError unexpected(String action) {
            return new AssertionError("Movement parity host unexpectedly tried to " + action + ": " + host.getUUID());
        }
    }
}
