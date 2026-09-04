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

import baritone.api.cache.IWorldScanner;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum FasterWorldScanner implements IWorldScanner {
    INSTANCE;

    @Override
    public List<BlockPos> scanChunkRadius(IPlayerContext ctx, BlockOptionalMetaLookup filter, int max, int yLevelThreshold, int maxSearchRadius) {
        assert ctx.world() != null;
        if (maxSearchRadius < 0) {
            throw new IllegalArgumentException("chunkRange must be >= 0");
        }
        return scanChunksInternal(ctx, filter, getChunkRange(ctx.playerFeet().x >> 4, ctx.playerFeet().z >> 4, maxSearchRadius), max);
    }

    @Override
    public List<BlockPos> scanChunk(IPlayerContext ctx, BlockOptionalMetaLookup filter, ChunkPos pos, int max, int yLevelThreshold) {
        Stream<BlockPos> stream = scanChunkInternal(ctx, filter, pos);
        if (max >= 0) {
            stream = stream.limit(max);
        }
        return stream.collect(Collectors.toList());
    }

    @Override
    public int repack(IPlayerContext ctx) {
        return this.repack(ctx, 40);
    }

    @Override
    public int repack(IPlayerContext ctx, int range) {
        return WorldScanner.INSTANCE.repack(ctx, range);
    }

    // ordered in a way that the closest blocks are generally first
    public static List<ChunkPos> getChunkRange(int centerX, int centerZ, int chunkRadius) {
        List<ChunkPos> chunks = new ArrayList<>();
        // spiral out
        chunks.add(new ChunkPos(centerX, centerZ));
        for (int i = 1; i < chunkRadius; i++) {
            for (int j = 0; j <= i; j++) {
                chunks.add(new ChunkPos(centerX - j, centerZ - i));
                if (j != 0) {
                    chunks.add(new ChunkPos(centerX + j, centerZ - i));
                    chunks.add(new ChunkPos(centerX - j, centerZ + i));
                }
                chunks.add(new ChunkPos(centerX + j, centerZ + i));
                if (j != i) {
                    chunks.add(new ChunkPos(centerX - i, centerZ - j));
                    chunks.add(new ChunkPos(centerX + i, centerZ - j));
                    if (j != 0) {
                        chunks.add(new ChunkPos(centerX - i, centerZ + j));
                        chunks.add(new ChunkPos(centerX + i, centerZ + j));
                    }
                }
            }
        }
        return chunks;
    }

    private List<BlockPos> scanChunksInternal(IPlayerContext ctx, BlockOptionalMetaLookup lookup, List<ChunkPos> chunkPositions, int maxBlocks) {
        assert ctx.world() != null;
        try {
            // p -> scanChunkInternal(ctx, lookup, p)
            Stream<BlockPos> posStream = chunkPositions.stream().flatMap(p -> scanChunkInternal(ctx, lookup, p));
            if (maxBlocks >= 0) {
                // WARNING: this can be expensive if maxBlocks is large...
                // see limit's javadoc
                posStream = posStream.limit(maxBlocks);
            }
            return posStream.collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private Stream<BlockPos> scanChunkInternal(IPlayerContext ctx, BlockOptionalMetaLookup lookup, ChunkPos pos) {
        ChunkSource chunkProvider = ctx.world().getChunkSource();
        // if chunk is not loaded, return empty stream
        if (!chunkProvider.hasChunk(pos.x, pos.z)) {
            return Stream.empty();
        }

        long chunkX = (long) pos.x << 4;
        long chunkZ = (long) pos.z << 4;

        int playerSectionY = (ctx.playerFeet().y - ctx.world().getMinBuildHeight()) >> 4;

        LevelChunk chunk = chunkProvider.getChunk(pos.x, pos.z, false);
        if (chunk == null || chunk.isEmpty()) {
            return Stream.empty();
        }
        return collectChunkSections(lookup, chunk, chunkX, chunkZ, playerSectionY).stream();
    }


    private List<BlockPos> collectChunkSections(BlockOptionalMetaLookup lookup, LevelChunk chunk, long chunkX, long chunkZ, int playerSection) {
        // iterate over sections relative to player
        List<BlockPos> blocks = new ArrayList<>();
        int chunkY = chunk.getMinBuildHeight();
        LevelChunkSection[] sections = chunk.getSections();
        int l = sections.length;
        int i = playerSection - 1;
        int j = playerSection;
        for (; i >= 0 || j < l; ++j, --i) {
            if (j < l) {
                visitSection(lookup, sections[j], blocks, chunkX, chunkY + j * 16, chunkZ);
            }
            if (i >= 0) {
                visitSection(lookup, sections[i], blocks, chunkX, chunkY + i * 16, chunkZ);
            }
        }
        return blocks;
    }

    private void visitSection(BlockOptionalMetaLookup lookup, LevelChunkSection section, List<BlockPos> blocks, long chunkX, int sectionY, long chunkZ) {
        if (section == null || section.hasOnlyAir()) {
            return;
        }

        PalettedContainer<BlockState> states = section.getStates();
        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    if (lookup.has(states.get(x, y, z))) {
                        blocks.add(new BlockPos(
                                (int) chunkX + x,
                                sectionY + y,
                                (int) chunkZ + z
                        ));
                    }
                }
            }
        }
    }
}
