/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.cache;

import baritone.Baritone;
import baritone.api.cache.ICachedWorld;
import baritone.api.utils.Helper;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Brady
 * @since 8/4/2018
 */
public final class CachedWorld implements ICachedWorld, Helper {

    /**
     * The maximum number of regions in any direction from (0,0)
     */
    private static final int REGION_MAX = 30_000_000 / 512 + 1;

    /**
     * A map of all of the cached regions.
     */
    private Long2ObjectMap<CachedRegion> cachedRegions = new Long2ObjectOpenHashMap<>();

    /**
     * The directory that the cached region files are saved to
     */
    private final String directory;

    /**
     * Queue of positions to pack. Refers to the toPackMap, in that every element of this queue will be a
     * key in that map.
     */
    private final LinkedBlockingQueue<ChunkPos> toPackQueue = new LinkedBlockingQueue<>();

    /**
     * All chunk positions pending packing. This map will be updated in-place if a new update to the chunk occurs
     * while waiting in the queue for the packer thread to get to it.
     */
    private final Map<ChunkPos, LevelChunk> toPackMap = CacheBuilder.newBuilder().softValues().<ChunkPos, LevelChunk>build().asMap();

    private final DimensionType dimension;

    private final Object lifecycleLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch closeComplete = new CountDownLatch(1);
    private final WorkerTask packerTask;
    private final WorkerTask periodicSaveTask;

    CachedWorld(Path directory, DimensionType dimension) {
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException ignored) {
            }
        }
        this.directory = directory.toString();
        this.dimension = dimension;
        System.out.println("Cached world directory: " + directory);
        this.packerTask = new WorkerTask(new PackerThread());
        this.periodicSaveTask = new WorkerTask(this::runPeriodicSave);
        Baritone.getExecutor().execute(this.packerTask);
        Baritone.getExecutor().execute(this.periodicSaveTask);
    }

    @Override
    public final void queueForPacking(LevelChunk chunk) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            if (toPackMap.put(chunk.getPos(), chunk) == null) {
                toPackQueue.add(chunk.getPos());
            }
        }
    }

    @Override
    public final boolean isCached(int blockX, int blockZ) {
        CachedRegion region = getRegion(blockX >> 9, blockZ >> 9);
        if (region == null) {
            return false;
        }
        return region.isCached(blockX & 511, blockZ & 511);
    }

    public final boolean regionLoaded(int blockX, int blockZ) {
        return getRegion(blockX >> 9, blockZ >> 9) != null;
    }

    @Override
    public final ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
        ArrayList<BlockPos> res = new ArrayList<>();
        if (closed.get()) {
            return res;
        }
        int centerRegionX = centerX >> 9;
        int centerRegionZ = centerZ >> 9;

        int searchRadius = 0;
        while (searchRadius <= maxRegionDistanceSq) {
            for (int xoff = -searchRadius; xoff <= searchRadius; xoff++) {
                for (int zoff = -searchRadius; zoff <= searchRadius; zoff++) {
                    int distance = xoff * xoff + zoff * zoff;
                    if (distance != searchRadius) {
                        continue;
                    }
                    int regionX = xoff + centerRegionX;
                    int regionZ = zoff + centerRegionZ;
                    CachedRegion region = getOrCreateRegion(regionX, regionZ);
                    if (region != null) {
                        // TODO: 100% verify if this or addAll is faster.
                        res.addAll(region.getLocationsOf(block));
                    }
                }
            }
            if (res.size() >= maximum) {
                return res;
            }
            searchRadius++;
        }
        return res;
    }

    private void updateCachedChunk(CachedChunk chunk) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            CachedRegion region = getOrCreateRegion(chunk.x >> 5, chunk.z >> 5);
            if (region != null) {
                region.updateCachedChunk(chunk.x & 31, chunk.z & 31, chunk);
            }
        }
    }

    @Override
    public final void save() {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            saveInternal();
        }
    }

    private void saveInternal() {
        if (!Baritone.settings().chunkCaching.value) {
            System.out.println("Not saving to disk; chunk caching is disabled.");
            allRegions().forEach(region -> {
                if (region != null) {
                    region.removeExpired();
                }
            }); // even if we aren't saving to disk, still delete expired old chunks from RAM
            prune();
            return;
        }
        long start = System.nanoTime() / 1000000L;
        allRegions().parallelStream().forEach(region -> {
            if (region != null) {
                region.save(this.directory);
            }
        });
        long now = System.nanoTime() / 1000000L;
        System.out.println("World save took " + (now - start) + "ms");
        prune();
    }

    /**
     * Delete regions that are too far from the player
     */
    private void prune() {
        synchronized (lifecycleLock) {
            if (!Baritone.settings().pruneRegionsFromRAM.value) {
                return;
            }
            BlockPos pruneCenter = guessPosition();
            for (CachedRegion region : allRegions()) {
                if (region == null) {
                    continue;
                }
                int distX = ((region.getX() << 9) + 256) - pruneCenter.getX();
                int distZ = ((region.getZ() << 9) + 256) - pruneCenter.getZ();
                double dist = Math.sqrt(distX * distX + distZ * distZ);
                if (dist > 1024) {
                    logDebug("Deleting cached region from ram");
                    cachedRegions.remove(getRegionID(region.getX(), region.getZ()));
                }
            }
        }
    }

    /**
     * Return the most recently modified cached chunk as an approximate prune center.
     *
     * This deliberately does not consult the provider or a live world. Pruning can
     * run while a provider is disposing a runtime, so those callbacks would invert
     * the provider/lifecycle lock order during close.
     */
    private BlockPos guessPosition() {
        CachedChunk mostRecentlyModified = null;
        for (CachedRegion region : allRegions()) {
            if (region == null) {
                continue;
            }
            CachedChunk ch = region.mostRecentlyModified();
            if (ch == null) {
                continue;
            }
            if (mostRecentlyModified == null || mostRecentlyModified.cacheTimestamp < ch.cacheTimestamp) {
                mostRecentlyModified = ch;
            }
        }
        if (mostRecentlyModified == null) {
            return new BlockPos(0, 0, 0);
        }
        return new BlockPos((mostRecentlyModified.x << 4) + 8, 0, (mostRecentlyModified.z << 4) + 8);
    }

    private List<CachedRegion> allRegions() {
        synchronized (lifecycleLock) {
            return new ArrayList<>(this.cachedRegions.values());
        }
    }

    @Override
    public final void reloadAllFromDisk() {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            long start = System.nanoTime() / 1000000L;
            allRegions().forEach(region -> {
                if (region != null) {
                    region.load(this.directory);
                }
            });
            long now = System.nanoTime() / 1000000L;
            System.out.println("World load took " + (now - start) + "ms");
        }
    }

    public final CachedRegion getRegion(int regionX, int regionZ) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return null;
            }
            return cachedRegions.get(getRegionID(regionX, regionZ));
        }
    }

    /**
     * Returns the region at the specified region coordinates. If a
     * region is not found, then a new one is created.
     *
     * @param regionX The region X coordinate
     * @param regionZ The region Z coordinate
     * @return The region located at the specified coordinates
     */
    private CachedRegion getOrCreateRegion(int regionX, int regionZ) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return null;
            }
            return cachedRegions.computeIfAbsent(getRegionID(regionX, regionZ), id -> {
                CachedRegion newRegion = new CachedRegion(regionX, regionZ, dimension);
                newRegion.load(this.directory);
                return newRegion;
            });
        }
    }

    public void tryLoadFromDisk(int regionX, int regionZ) {
        getOrCreateRegion(regionX, regionZ);
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            awaitClose();
            return;
        }
        try {
            packerTask.cancelAndAwait();
            periodicSaveTask.cancelAndAwait();
            synchronized (lifecycleLock) {
                toPackQueue.clear();
                toPackMap.clear();
                saveInternal();
            }
        } finally {
            closeComplete.countDown();
        }
    }

    private void awaitClose() {
        boolean interrupted = false;
        while (true) {
            try {
                closeComplete.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void runPeriodicSave() {
        try {
            Thread.sleep(30000);
            while (!closed.get()) {
                save();
                Thread.sleep(600000);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the region ID based on the region coordinates. 0 will be
     * returned if the specified region coordinates are out of bounds.
     *
     * @param regionX The region X coordinate
     * @param regionZ The region Z coordinate
     * @return The region ID
     */
    private long getRegionID(int regionX, int regionZ) {
        if (!isRegionInWorld(regionX, regionZ)) {
            return 0;
        }

        return (long) regionX & 0xFFFFFFFFL | ((long) regionZ & 0xFFFFFFFFL) << 32;
    }

    /**
     * Returns whether or not the specified region coordinates is within the world bounds.
     *
     * @param regionX The region X coordinate
     * @param regionZ The region Z coordinate
     * @return Whether or not the region is in world bounds
     */
    private boolean isRegionInWorld(int regionX, int regionZ) {
        return regionX <= REGION_MAX && regionX >= -REGION_MAX && regionZ <= REGION_MAX && regionZ >= -REGION_MAX;
    }

    private class PackerThread implements Runnable {

        public void run() {
            while (!closed.get()) {
                try {
                    ChunkPos pos = toPackQueue.take();
                    LevelChunk chunk;
                    synchronized (lifecycleLock) {
                        if (closed.get()) {
                            return;
                        }
                        chunk = toPackMap.remove(pos);
                    }
                    if (chunk == null) {
                        continue;
                    }
                    if (toPackQueue.size() > Baritone.settings().chunkPackerQueueMaxSize.value) {
                        continue;
                    }
                    CachedChunk cached = ChunkPacker.pack(chunk);
                    if (closed.get()) {
                        return;
                    }
                    CachedWorld.this.updateCachedChunk(cached);
                    //System.out.println("Processed chunk at " + chunk.x + "," + chunk.z);
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable th) {
                    // in the case of an exception, keep consuming from the queue so as not to leak memory
                    if (!closed.get()) {
                        th.printStackTrace();
                    }
                }
            }
        }
    }

    private final class WorkerTask extends FutureTask<Void> {

        private final CountDownLatch stopped = new CountDownLatch(1);
        private boolean started;

        private WorkerTask(Runnable task) {
            super(task, null);
        }

        @Override
        public void run() {
            synchronized (lifecycleLock) {
                if (isCancelled()) {
                    stopped.countDown();
                    return;
                }
                started = true;
            }
            try {
                super.run();
            } finally {
                stopped.countDown();
            }
        }

        private void cancelAndAwait() {
            synchronized (lifecycleLock) {
                cancel(true);
                if (!started) {
                    stopped.countDown();
                }
            }
            boolean interrupted = false;
            while (true) {
                try {
                    stopped.await();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
