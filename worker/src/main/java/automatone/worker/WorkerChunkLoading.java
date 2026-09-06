package automatone.worker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owns the worker's persistent center ticket and transient eight-chunk working ring. */
public final class WorkerChunkLoading {
    public static final ResourceLocation CENTER_CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath(
            WorkerMod.MOD_ID, "worker_center");
    public static final ResourceLocation WORKING_RING_CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath(
            WorkerMod.MOD_ID, "worker_working_ring");

    private static final Map<ServerLevel, Map<UUID, Long>> RESTORED_CENTERS = new IdentityHashMap<>();
    private static final TicketController CENTER_CONTROLLER = new TicketController(
            CENTER_CONTROLLER_ID, WorkerChunkLoading::restoreCenters);
    private static final TicketController WORKING_RING_CONTROLLER = new TicketController(
            WORKING_RING_CONTROLLER_ID, WorkerChunkLoading::discardRestoredRings);

    private ChunkPos center;
    private Set<Long> workingRing = Set.of();

    public WorkerChunkLoading() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CENTER_CONTROLLER);
        event.register(WORKING_RING_CONTROLLER);
    }

    public static void tick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<ServerLevel, Map<UUID, Long>>> levels = RESTORED_CENTERS.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ServerLevel, Map<UUID, Long>> entry = levels.next();
            ServerLevel level = entry.getKey();
            if (level.getServer() != event.getServer()) {
                continue;
            }
            entry.getValue().entrySet().removeIf(restored -> validateRestoredCenter(level, restored));
            if (entry.getValue().isEmpty()) {
                levels.remove();
            }
        }
    }

    public static void clear(ServerStoppedEvent event) {
        RESTORED_CENTERS.clear();
    }

    public static boolean ready(ServerLevel level, ChunkPos center) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(center);
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int z = center.z - 1; z <= center.z + 1; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                BlockPos position = new BlockPos(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ());
                if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(position)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Applies one worker's desired tickets, always adding replacements before removing old tickets. */
    public void sync(ServerLevel level, UUID owner, ChunkPos nextCenter, boolean mining) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(nextCenter);

        if (nextCenter.equals(center) && mining == !workingRing.isEmpty()) {
            return;
        }

        Long restoredCenter = removeRestoredCenter(level, owner);
        Set<Long> nextRing = mining ? workingRing(nextCenter) : Set.of();
        long nextCenterLong = nextCenter.toLong();

        if (center == null || !center.equals(nextCenter)) {
            CENTER_CONTROLLER.forceChunk(level, owner, nextCenter.x, nextCenter.z, true, true);
        }
        for (long chunk : nextRing) {
            if (!workingRing.contains(chunk)) {
                force(WORKING_RING_CONTROLLER, level, owner, chunk, true);
            }
        }

        for (long chunk : workingRing) {
            if (!nextRing.contains(chunk)) {
                force(WORKING_RING_CONTROLLER, level, owner, chunk, false);
            }
        }
        if (center != null && !center.equals(nextCenter)) {
            CENTER_CONTROLLER.forceChunk(level, owner, center.x, center.z, false, true);
        } else if (center == null && restoredCenter != null && restoredCenter != nextCenterLong) {
            force(CENTER_CONTROLLER, level, owner, restoredCenter, false);
        }

        center = nextCenter;
        workingRing = nextRing;
    }

    /** Releases all tickets owned by this worker manager from the supplied level. */
    public void release(ServerLevel level, UUID owner) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(owner);

        Long restoredCenter = removeRestoredCenter(level, owner);
        for (long chunk : workingRing) {
            force(WORKING_RING_CONTROLLER, level, owner, chunk, false);
        }
        if (center != null) {
            CENTER_CONTROLLER.forceChunk(level, owner, center.x, center.z, false, true);
        }
        if (restoredCenter != null && (center == null || restoredCenter != center.toLong())) {
            force(CENTER_CONTROLLER, level, owner, restoredCenter, false);
        }
        center = null;
        workingRing = Set.of();
    }

    private static Set<Long> workingRing(ChunkPos center) {
        Set<Long> chunks = new HashSet<>(8);
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int z = center.z - 1; z <= center.z + 1; z++) {
                if (x != center.x || z != center.z) {
                    chunks.add(ChunkPos.asLong(x, z));
                }
            }
        }
        return Set.copyOf(chunks);
    }

    private static void force(TicketController controller, ServerLevel level, UUID owner, long chunk, boolean add) {
        ChunkPos position = new ChunkPos(chunk);
        controller.forceChunk(level, owner, position.x, position.z, add, true);
    }

    private static void restoreCenters(ServerLevel level, TicketHelper tickets) {
        Set.copyOf(tickets.getBlockTickets().keySet()).forEach(tickets::removeAllTickets);
        Map<UUID, Long> restored = RESTORED_CENTERS.computeIfAbsent(level, ignored -> new HashMap<>());
        Map.copyOf(tickets.getEntityTickets()).forEach((owner, owned) -> {
            for (long chunk : owned.nonTicking().toLongArray()) {
                tickets.removeTicket(owner, chunk, false);
            }
            long[] ticking = owned.ticking().toLongArray();
            if (ticking.length == 0) {
                return;
            }
            Arrays.sort(ticking);
            for (int index = 1; index < ticking.length; index++) {
                tickets.removeTicket(owner, ticking[index], true);
            }
            restored.put(owner, ticking[0]);
        });
        if (restored.isEmpty()) {
            RESTORED_CENTERS.remove(level);
        }
    }

    private static void discardRestoredRings(ServerLevel level, TicketHelper tickets) {
        Set.copyOf(tickets.getBlockTickets().keySet()).forEach(tickets::removeAllTickets);
        Set.copyOf(tickets.getEntityTickets().keySet()).forEach(tickets::removeAllTickets);
    }

    private static boolean validateRestoredCenter(ServerLevel level, Map.Entry<UUID, Long> restored) {
        if (!level.areEntitiesLoaded(restored.getValue())) {
            return false;
        }
        if (!(level.getEntity(restored.getKey()) instanceof WorkerEntity worker)
                || worker.isRemoved() || !worker.isAlive()) {
            force(CENTER_CONTROLLER, level, restored.getKey(), restored.getValue(), false);
        }
        return true;
    }

    private static Long removeRestoredCenter(ServerLevel level, UUID owner) {
        Map<UUID, Long> restored = RESTORED_CENTERS.get(level);
        if (restored == null) {
            return null;
        }
        Long center = restored.remove(owner);
        if (restored.isEmpty()) {
            RESTORED_CENTERS.remove(level);
        }
        return center;
    }
}
