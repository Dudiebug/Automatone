package automatone.worker.gametest;

import automatone.worker.MiningSession;
import automatone.worker.WorkerChunkLoading;
import automatone.worker.WorkerEntity;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Two-process dedicated-server evidence for M4.5. Run write then read against the same game directory.
 */
@EventBusSubscriber(modid = WorkerGameTestMod.MOD_ID)
public final class WorkerRestartProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PHASE_PROPERTY = "automatone.worker.restartPhase";
    private static final String MANIFEST = "m45-restart.properties";
    private static final ResourceLocation TARGET = ResourceLocation.withDefaultNamespace("iron_ore");
    private static final UUID OWNER = UUID.fromString("c0a80101-0000-4000-8000-000000000045");
    private static final UUID ORPHAN = UUID.fromString("c0a80102-0000-4000-8000-000000000045");
    private static final int MAX_TICKS = 1_400;
    private static final int SETTLE_TICKS = 40;
    private static final int FINITE_REQUESTED = 6;
    private static final BlockPos FINITE_ORIGIN = new BlockPos(512, 96, 512);

    private static Probe probe;

    private WorkerRestartProbe() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String phase = System.getProperty(PHASE_PROPERTY, "").trim();
        if (phase.isEmpty()) {
            return;
        }
        if (!phase.equals("write") && !phase.equals("read")) {
            fail(event.getServer(), "unknown restart phase " + phase, null);
            return;
        }
        try {
            probe = phase.equals("write") ? Probe.write(event.getServer()) : Probe.read(event.getServer());
            LOGGER.info("M45_RESTART_BEGIN phase={}", phase);
        } catch (Throwable failure) {
            fail(event.getServer(), "phase=" + phase + " startup", failure);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (probe == null || probe.server != event.getServer()) {
            return;
        }
        try {
            probe.tick();
        } catch (Throwable failure) {
            fail(event.getServer(), "phase=" + probe.phase + " tick=" + probe.ticks, failure);
        }
    }

    private static void fail(MinecraftServer server, String detail, Throwable failure) {
        if (failure == null) {
            LOGGER.error("M45_RESTART_FAIL {}", detail);
        } else {
            LOGGER.error("M45_RESTART_FAIL {}", detail, failure);
        }
        probe = null;
        server.halt(false);
    }

    private static final class Probe {
        private final MinecraftServer server;
        private final String phase;
        private final ServerLevel level;
        private final Properties manifest;
        private int ticks;
        private int settleTicks;

        private Probe(MinecraftServer server, String phase, Properties manifest) {
            this.server = server;
            this.phase = phase;
            level = server.overworld();
            this.manifest = manifest;
        }

        static Probe write(MinecraftServer server) {
            Probe result = new Probe(server, "write", new Properties());
            result.createWriteScenario();
            return result;
        }

        static Probe read(MinecraftServer server) {
            return new Probe(server, "read", loadManifest(server));
        }

        void tick() {
            ticks++;
            if (ticks > MAX_TICKS) {
                throw new AssertionError("restart probe exceeded " + MAX_TICKS + " ticks");
            }
            if (phase.equals("write")) {
                writeTick();
            } else {
                readTick();
            }
        }

        private void createWriteScenario() {
            require(server.getPlayerCount() == 0, "restart probe must run without connected players");
            WorkerEntity finite = createMiner(FINITE_ORIGIN, true, false);
            WorkerEntity unlimited = createMiner(new BlockPos(512, 96, 576), false, true);
            WorkerEntity completed = createMiner(new BlockPos(512, 96, 640), false, false);
            WorkerEntity cancelled = createMiner(new BlockPos(512, 96, 704), false, false);
            WorkerEntity failed = createMiner(new BlockPos(512, 96, 768), false, false);

            finite.startMining(TARGET, FINITE_REQUESTED);
            unlimited.startMining(TARGET, 0);
            completed.startMining(TARGET, 1);
            cancelled.startMining(TARGET, 0);
            cancelled.stopMining();
            restoreTerminal(failed, MiningSession.State.FAILED, "PROBE_FAILED");

            require(new TicketController(WorkerChunkLoading.CENTER_CONTROLLER_ID).forceChunk(level, ORPHAN, 80, 80, true, true),
                    "restart probe could not seed its orphan center ticket");
            manifest.setProperty("finite.uuid", finite.getUUID().toString());
            manifest.setProperty("unlimited.uuid", unlimited.getUUID().toString());
            manifest.setProperty("completed.uuid", completed.getUUID().toString());
            manifest.setProperty("cancelled.uuid", cancelled.getUUID().toString());
            manifest.setProperty("failed.uuid", failed.getUUID().toString());
            manifest.setProperty("finite.owner", OWNER.toString());
            manifest.setProperty("finite.selected", Integer.toString(finite.selectedSlot()));
            manifest.setProperty("orphan.uuid", ORPHAN.toString());
            manifest.setProperty("orphan.x", "80");
            manifest.setProperty("orphan.z", "80");
        }

        private WorkerEntity createMiner(BlockPos origin, boolean owned, boolean denseTargets) {
            level.getChunk(origin);
            WorkerNativeMineProcessGameTest.MiningChamber chamber = new WorkerNativeMineProcessGameTest.MiningChamber(level, origin);
            chamber.build();
            for (int x = denseTargets ? -3 : 0; x <= (denseTargets ? 1 : 0); x++) {
                for (int z = -3; z <= 3; z++) {
                    chamber.replace(chamber.target().offset(x, 0, z), Blocks.IRON_ORE.defaultBlockState());
                }
            }
            WorkerEntity worker = WorkerNativeMineProcessGameTest.spawnWorker(level, chamber.workerPosition());
            worker.setItem(1, new ItemStack(Items.DIAMOND, 7));
            worker.setItem(3, new ItemStack(Items.IRON_PICKAXE));
            worker.setSelectedSlot(3);
            if (owned) {
                worker.claim(OWNER);
            }
            return worker;
        }

        private void writeTick() {
            WorkerEntity finite = worker("finite.uuid");
            WorkerEntity unlimited = worker("unlimited.uuid");
            WorkerEntity completed = worker("completed.uuid");
            MiningSession.Snapshot finiteStatus = finite.miningStatus();
            MiningSession.Snapshot unlimitedStatus = unlimited.miningStatus();
            if (finiteStatus.state() != MiningSession.State.RUNNING || unlimitedStatus.state() != MiningSession.State.RUNNING) {
                throw new AssertionError("running worker stopped before restart save: finite=" + finiteStatus
                        + ", unlimited=" + unlimitedStatus);
            }
            if (finiteStatus.completed() == 0 || unlimitedStatus.completed() == 0
                    || completed.miningStatus().state() != MiningSession.State.COMPLETED) {
                return;
            }
            require(finiteStatus.completed() < FINITE_REQUESTED,
                    "finite worker completed before its partial-progress save: " + finiteStatus);
            require(unlimited.runtime().getMineProcess().isActive(),
                    "unlimited worker stopped before its restart save");
            require(destroyedFiniteTargets() == finiteStatus.completed(),
                    "saved finite progress must equal actual source destruction");
            manifest.setProperty("finite.saved", Long.toString(finiteStatus.completed()));
            manifest.setProperty("finite.destroyed.saved", Long.toString(destroyedFiniteTargets()));
            manifest.setProperty("unlimited.saved", Long.toString(unlimitedStatus.completed()));
            storeManifest(server, manifest);
            server.saveEverything(true, true, true);
            LOGGER.info("M45_RESTART_WRITE_PASS finiteProgress={} unlimitedProgress={}", finiteStatus.completed(),
                    unlimitedStatus.completed());
            probe = null;
            server.halt(false);
        }

        private void readTick() {
            require(server.getPlayerCount() == 0, "restart probe must remain playerless after reload");
            WorkerEntity finite = findWorker("finite.uuid");
            WorkerEntity unlimited = findWorker("unlimited.uuid");
            WorkerEntity completed = findWorker("completed.uuid");
            WorkerEntity cancelled = findWorker("cancelled.uuid");
            WorkerEntity failed = findWorker("failed.uuid");
            if (finite == null || unlimited == null || completed == null || cancelled == null || failed == null) {
                return;
            }
            verifyPersistedIdentity(finite);
            verifyTerminal(completed, MiningSession.State.COMPLETED);
            verifyTerminal(cancelled, MiningSession.State.CANCELLED);
            verifyTerminal(failed, MiningSession.State.FAILED);
            long finiteSaved = value("finite.saved");
            long finiteDestroyedSaved = value("finite.destroyed.saved");
            long unlimitedSaved = value("unlimited.saved");
            MiningSession.Snapshot finiteStatus = finite.miningStatus();
            MiningSession.Snapshot unlimitedStatus = unlimited.miningStatus();
            require(finite.runtime() != null && unlimited.runtime() != null,
                    "resumed workers must reconstruct native runtimes");
            require(unlimitedStatus.state() == MiningSession.State.RUNNING,
                    "unlimited restart stopped unexpectedly: " + unlimitedStatus);
            if (finiteStatus.state() == MiningSession.State.RUNNING || unlimitedStatus.completed() <= unlimitedSaved) {
                return;
            }
            require(finiteStatus.state() == MiningSession.State.COMPLETED && finiteStatus.completed() == FINITE_REQUESTED,
                    "finite restart must stop at its exact remaining count: " + finiteStatus);
            require(unlimitedStatus.completed() > unlimitedSaved,
                    "unlimited restart must advance beyond saved progress: " + unlimitedStatus);
            require(unlimitedStatus.state() == MiningSession.State.RUNNING
                            && unlimited.runtime().getMineProcess().isActive(),
                    "unlimited restart did not retain active native mining: " + unlimitedStatus);
            require(finiteStatus.completed() > finiteSaved,
                    "finite restart must advance beyond saved progress: " + finiteStatus);
            if (settleTicks++ < SETTLE_TICKS) {
                return;
            }
            require(finite.miningStatus().completed() == FINITE_REQUESTED,
                    "finite restart continued after exact completion");
            require(destroyedFiniteTargets() == FINITE_REQUESTED,
                    "finite restart did not destroy exactly six of the seven real source targets");
            require(destroyedFiniteTargets() - finiteDestroyedSaved == FINITE_REQUESTED - finiteSaved,
                    "finite restart source destruction did not match the saved remaining quantity");
            UUID orphan = UUID.fromString(manifest.getProperty("orphan.uuid"));
            require(WorkerChunkLoadingGameTest.tickets(level, WorkerChunkLoading.CENTER_CONTROLLER_ID, orphan).isEmpty(),
                    "orphan center ticket was not cleaned after entity loading");
            LOGGER.info("M45_RESTART_READ_PASS finiteSaved={} finiteFinal={} unlimitedSaved={} unlimitedFinal={}",
                    finiteSaved, finite.miningStatus().completed(), unlimitedSaved, unlimited.miningStatus().completed());
            probe = null;
            server.halt(false);
        }

        private void verifyPersistedIdentity(WorkerEntity finite) {
            require(finite.ownerUUID().equals(Optional.of(OWNER)), "optional owner UUID was not restored");
            require(finite.selectedSlot() == Integer.parseInt(manifest.getProperty("finite.selected")),
                    "selected inventory slot was not restored");
            require(finite.getItem(1).is(Items.DIAMOND) && finite.getItem(1).getCount() == 7
                            && finite.getItem(3).is(Items.IRON_PICKAXE),
                    "nine-slot inventory was not restored");
        }

        private void verifyTerminal(WorkerEntity worker, MiningSession.State expected) {
            MiningSession.Snapshot state = worker.miningStatus();
            require(state.state() == expected,
                    "terminal " + expected + " job restarted or changed state: " + state);
            require(worker.runtime() != null && !worker.runtime().getMineProcess().isActive(),
                    "terminal " + expected + " job recreated an active native process");
            if (expected == MiningSession.State.COMPLETED) {
                require(state.requested() == 1 && state.completed() == 1,
                        "completed terminal progress was not retained: " + state);
            }
        }

        private long destroyedFiniteTargets() {
            long destroyed = 0;
            for (int offset = -3; offset <= 3; offset++) {
                if (level.getBlockState(FINITE_ORIGIN.offset(13, 1, 4 + offset)).isAir()) {
                    destroyed++;
                }
            }
            return destroyed;
        }

        private WorkerEntity worker(String idKey) {
            WorkerEntity worker = findWorker(idKey);
            if (worker == null) {
                throw new AssertionError("persisted worker is not loaded for " + idKey);
            }
            return worker;
        }

        private WorkerEntity findWorker(String idKey) {
            UUID id = UUID.fromString(manifest.getProperty(idKey));
            Entity entity = level.getEntity(id);
            return entity instanceof WorkerEntity worker ? worker : null;
        }

        private long value(String key) {
            return Long.parseLong(manifest.getProperty(key));
        }
    }

    private static void restoreTerminal(WorkerEntity worker, MiningSession.State state, String error) {
        CompoundTag tag = new CompoundTag();
        worker.addAdditionalSaveData(tag);
        CompoundTag saved = tag.getCompound("AutomatoneWorker");
        CompoundTag job = saved.getCompound("Job");
        job.putString("Target", TARGET.toString());
        job.putInt("Requested", state == MiningSession.State.COMPLETED ? 1 : 0);
        job.putLong("Completed", state == MiningSession.State.COMPLETED ? 1 : 0);
        job.putString("State", state.name());
        job.putString("Error", error);
        saved.put("Job", job);
        tag.put("AutomatoneWorker", saved);
        worker.readAdditionalSaveData(tag);
    }

    private static Properties loadManifest(MinecraftServer server) {
        Properties properties = new Properties();
        Path path = manifestPath(server);
        try (var stream = Files.newInputStream(path)) {
            properties.load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("restart manifest is unavailable: " + path, failure);
        }
        return properties;
    }

    private static void storeManifest(MinecraftServer server, Properties properties) {
        Path path = manifestPath(server);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream stream = Files.newOutputStream(path)) {
                properties.store(stream, "M4.5 real dedicated-server restart probe");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not write restart manifest: " + path, failure);
        }
    }

    private static Path manifestPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(MANIFEST);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
