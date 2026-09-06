package automatone.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/** Bounded asynchronous terrain preparation; native Automatone still owns every mining path. */
public final class WorkerRelocation {
    public static final int MAX_CONCURRENT = 4;
    public static final TicketType<UUID> PREPARATION = TicketType.create("automatone_worker_relocation", UUID::compareTo, 220);
    private static final Map<MinecraftServer, WorkerRelocation> SERVICES = new HashMap<>();
    private final MinecraftServer server;
    private final WorkerRoster roster;
    private final RandomSource random = RandomSource.create();
    private final BiFunction<ServerLevel, RandomSource, BlockPos> sampler;
    private final Limits limits;
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();

    public record Limits(int attempts, int totalTicks, int preparationTicks) {
        public Limits {
            if (attempts < 1 || attempts > 32 || totalTicks < 1 || totalTicks > 600
                    || preparationTicks < 1 || preparationTicks > 200) {
                throw new IllegalArgumentException("INVALID_RELOCATION_LIMITS");
            }
        }
    }

    public enum State { PENDING, SUCCEEDED, FAILED, CANCELLED }
    public record Status(UUID request, UUID worker, State state, String error, int attempts,
                         ResourceLocation dimension, BlockPos destination) { }

    private static final class Job {
        private final UUID request;
        private final UUID owner;
        private final UUID worker;
        private final boolean relocation;
        private final boolean reactivation;
        private final ServerLevel target;
        private final int started;
        private State state = State.PENDING;
        private String error = "";
        private int attempts;
        private int preparingSince;
        private ChunkPos ticket;
        private BlockPos column;
        private BlockPos destination;

        private Job(UUID request, UUID owner, UUID worker, boolean relocation, boolean reactivation,
                    ServerLevel target, int started) {
            this.request = request;
            this.owner = owner;
            this.worker = worker;
            this.relocation = relocation;
            this.reactivation = reactivation;
            this.target = target;
            this.started = started;
        }

        private Status status() {
            return new Status(request, worker, state, error, attempts, target.dimension().location(), destination);
        }
    }

    private WorkerRelocation(MinecraftServer server) {
        this(server, WorkerRoster.get(server), WorkerRelocation::sampleColumn, new Limits(32, 600, 200));
    }

    // Deterministic columns and shorter deadlines exercise the same terrain/ticket state machine in GameTests.
    private WorkerRelocation(MinecraftServer server, WorkerRoster roster,
                     BiFunction<ServerLevel, RandomSource, BlockPos> sampler, Limits limits) {
        this.server = Objects.requireNonNull(server);
        this.roster = Objects.requireNonNull(roster);
        this.sampler = Objects.requireNonNull(sampler);
        this.limits = Objects.requireNonNull(limits);
    }

    public static WorkerRelocation get(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Relocation requires the server thread");
        }
        return SERVICES.computeIfAbsent(server, WorkerRelocation::new);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        WorkerRelocation service = SERVICES.get(event.getServer());
        if (service != null) {
            service.tick();
        }
    }

    public static void stop(ServerStoppingEvent event) {
        WorkerRelocation service = SERVICES.remove(event.getServer());
        if (service != null) {
            for (Job job : service.jobs.values()) {
                if (job.state == State.PENDING) {
                    service.finish(job, State.CANCELLED, "SERVER_STOPPING");
                }
            }
        }
    }

    public Status deploy(UUID owner, UUID request, ResourceKey<Level> dimension, UUID archivedWorker) {
        requireThread();
        ServerLevel target = target(dimension);
        Job previous = previous(owner, request, archivedWorker, target, false);
        if (previous != null) {
            return previous.status();
        }
        requireCapacity();
        WorkerRoster.Reservation reservation = roster.reserve(owner, request, archivedWorker);
        Job job = new Job(request, owner, reservation.worker(), false, archivedWorker != null, target, server.getTickCount());
        remember(job);
        return job.status();
    }

    public Status relocate(UUID owner, UUID request, UUID worker, long revision, ResourceKey<Level> dimension) {
        requireThread();
        ServerLevel target = target(dimension);
        Job previous = previous(owner, request, worker, target, true);
        if (previous != null) {
            return previous.status();
        }
        WorkerEntity active = roster.active(owner, worker);
        if (roster.view(owner, worker).revision() != revision) {
            throw new IllegalStateException("STALE_REVISION");
        }
        if (pending(worker)) {
            throw new IllegalStateException("WORKER_PENDING");
        }
        requireCapacity();
        active.pauseMining();
        roster.changed(active);
        Job job = new Job(request, owner, worker, true, false, target, server.getTickCount());
        remember(job);
        return job.status();
    }

    public Status status(UUID owner, UUID request) {
        return owned(owner, request).status();
    }

    public List<Status> pendingRequests(UUID owner) {
        requireThread();
        return jobs.values().stream().filter(job -> job.owner.equals(owner) && job.state == State.PENDING)
                .map(Job::status).toList();
    }

    /** Latest bounded request history lets menus show completion, cancellation and failure after reopening. */
    public List<Status> requests(UUID owner) {
        requireThread();
        return jobs.values().stream().filter(job -> job.owner.equals(owner)).map(Job::status).toList();
    }

    public boolean pending(UUID worker) {
        requireThread();
        return jobs.values().stream().anyMatch(job -> job.worker.equals(worker) && job.state == State.PENDING);
    }

    public Status cancel(UUID owner, UUID request) {
        Job job = owned(owner, request);
        if (job.state == State.PENDING) {
            finish(job, State.CANCELLED, "");
        }
        return job.status();
    }

    public void tick() {
        requireThread();
        for (Job job : List.copyOf(jobs.values())) {
            if (job.state != State.PENDING) {
                continue;
            }
            try {
                advance(job);
            } catch (RuntimeException failure) {
                finish(job, State.FAILED, "RELOCATION_FAILED");
            }
        }
    }

    private void advance(Job job) {
        if (server.getTickCount() - job.started >= limits.totalTicks()) {
            finish(job, State.FAILED, "DESTINATION_TIMEOUT");
            return;
        }
        if (job.relocation) {
            WorkerEntity worker = roster.active(job.owner, job.worker);
            if (worker.miningStatus().state() == MiningSession.State.RUNNING) {
                worker.pauseMining();
            }
        }
        if (job.ticket == null) {
            if (job.attempts >= limits.attempts()) {
                finish(job, State.FAILED, "NO_SAFE_DESTINATION");
                return;
            }
            job.attempts++;
            job.column = sampler.apply(job.target, random);
            if (!insideBorder(job.target, job.column)) {
                return;
            }
            job.ticket = new ChunkPos(job.column);
            job.preparingSince = server.getTickCount();
            // Region tickets schedule vanilla chunk generation without joining a future on the tick thread.
            // Distance one prepares the center and adjacent full chunks for collision/hazard checks.
            job.target.getChunkSource().addRegionTicket(PREPARATION, job.ticket, 1, job.request);
            return;
        }
        if (!prepared(job.target, job.ticket)) {
            if (server.getTickCount() - job.preparingSince >= limits.preparationTicks()) {
                release(job);
            }
            return;
        }
        BlockPos destination = findSafeFloor(job.target, job.column);
        if (destination == null) {
            release(job);
            return;
        }
        Vec3 position = Vec3.atBottomCenterOf(destination);
        WorkerEntity result;
        if (job.relocation) {
            WorkerEntity worker = roster.active(job.owner, job.worker);
            result = worker.relocateTo(job.target, position);
            if (result == null) {
                finish(job, State.FAILED, "TRANSFER_REJECTED");
                return;
            }
        } else {
            result = roster.deploy(job.owner, job.request, job.target, position);
        }
        result.relocated();
        roster.changed(result);
        job.destination = destination;
        finish(job, State.SUCCEEDED, "");
    }

    public static BlockPos sampleColumn(ServerLevel level, RandomSource random) {
        WorldBorder border = level.getWorldBorder();
        double minX = Math.max(-29_999_983, border.getMinX() + 1);
        double maxX = Math.min(29_999_983, border.getMaxX() - 1);
        double minZ = Math.max(-29_999_983, border.getMinZ() + 1);
        double maxZ = Math.min(29_999_983, border.getMaxZ() - 1);
        if (maxX <= minX || maxZ <= minZ) {
            throw new IllegalStateException("BORDER_TOO_SMALL");
        }
        return BlockPos.containing(minX + random.nextDouble() * (maxX - minX), 0,
                minZ + random.nextDouble() * (maxZ - minZ));
    }

    public static boolean insideBorder(ServerLevel level, BlockPos feet) {
        double x = feet.getX() + 0.5;
        double z = feet.getZ() + 0.5;
        WorldBorder border = level.getWorldBorder();
        return x - 0.3 >= border.getMinX() && x + 0.3 < border.getMaxX()
                && z - 0.3 >= border.getMinZ() && z + 0.3 < border.getMaxZ();
    }

    public static boolean prepared(ServerLevel level, ChunkPos center) {
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int z = center.z - 1; z <= center.z + 1; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        // Full terrain can be available before vanilla exposes its entity section.
        // Wait for saved entities as well before installing a worker with a persistent UUID.
        return level.areEntitiesLoaded(center.toLong());
    }

    public static BlockPos findSafeFloor(ServerLevel level, BlockPos column) {
        int ceiling = level.dimension().equals(Level.NETHER) ? Math.min(126, level.getMaxBuildHeight() - 2)
                : level.getMaxBuildHeight() - 2;
        for (int y = ceiling; y > level.getMinBuildHeight(); y--) {
            BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
            if (isSafe(level, feet)) {
                return feet;
            }
        }
        return null;
    }

    public static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 2 > level.getMaxBuildHeight()
                || !insideBorder(level, feet) || !prepared(level, new ChunkPos(feet))) {
            return false;
        }
        BlockState floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP) || hazardous(floor)) {
            return false;
        }
        AABB body = new AABB(feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2,
                feet.getX() + 0.8, feet.getY() + 1.8, feet.getZ() + 0.8);
        if (!level.noCollision(body)) {
            return false;
        }
        for (BlockPos nearby : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            if (hazardous(level.getBlockState(nearby))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hazardous(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY);
    }

    private ServerLevel target(ResourceKey<Level> dimension) {
        if (!Level.OVERWORLD.equals(dimension) && !Level.NETHER.equals(dimension)) {
            throw new IllegalArgumentException("INVALID_DIMENSION");
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            throw new IllegalStateException("DIMENSION_UNAVAILABLE");
        }
        return level;
    }

    private Job previous(UUID owner, UUID request, UUID worker, ServerLevel target, boolean relocation) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(request);
        Job previous = jobs.get(request);
        if (previous != null && (!previous.owner.equals(owner) || previous.relocation != relocation
                || !previous.target.equals(target) || previous.reactivation != (!relocation && worker != null)
                || (worker != null && !previous.worker.equals(worker)))) {
            throw new IllegalStateException("REQUEST_CONFLICT");
        }
        return previous;
    }

    private Job owned(UUID owner, UUID request) {
        requireThread();
        Job job = jobs.get(request);
        if (job == null || !job.owner.equals(owner)) {
            throw new IllegalStateException("NOT_OWNER");
        }
        return job;
    }

    private void requireCapacity() {
        if (jobs.values().stream().filter(job -> job.state == State.PENDING).count() >= MAX_CONCURRENT) {
            throw new IllegalStateException("DESTINATION_SEARCH_BUSY");
        }
    }

    private void remember(Job job) {
        if (jobs.size() >= 256) {
            UUID oldest = jobs.values().stream().filter(candidate -> candidate.state != State.PENDING)
                    .findFirst().orElseThrow().request;
            jobs.remove(oldest);
        }
        jobs.put(job.request, job);
    }

    private void finish(Job job, State state, String error) {
        release(job);
        if (!job.relocation && state != State.SUCCEEDED) {
            roster.cancelReservation(job.owner, job.request);
        }
        job.state = state;
        job.error = error;
    }

    private static void release(Job job) {
        if (job.ticket != null) {
            job.target.getChunkSource().removeRegionTicket(PREPARATION, job.ticket, 1, job.request);
            job.ticket = null;
        }
    }

    private void requireThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Relocation requires the server thread");
        }
    }
}
