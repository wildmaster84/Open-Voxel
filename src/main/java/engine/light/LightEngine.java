package engine.light;

import engine.world.Chunk;
import engine.world.World;
import engine.world.block.BlockState;
import engine.world.block.BlockType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class LightEngine {
    public static final int MAX_LIGHT = 15;

    // per-chunk skylight data: packed (cx,cz) -> byte[]
    private final Map<Long, byte[]> skylight = new HashMap<>();

    private static long packChunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static int idx(int x, int y, int z) {
        return (y * Chunk.SIZE + z) * Chunk.SIZE + x;
    }

    private static boolean isOpaque(BlockType t) {
        // VERY important: what blocks STOP skylight?
        if (t == BlockType.AIR)   return false;
        if (t == BlockType.WATER) return false;   // if you want water to block skylight, change to true
        if (t == BlockType.GLASS) return false;   // if you want glass to block skylight, change to true
        // slabs / stairs etc. could be semi-transparent, but let’s keep it simple for now:
        return true;
    }

    public void rebuildSkylightForChunk(World world, int cx, int cz, Chunk chunk) {
        if (chunk == null) {
            skylight.remove(packChunk(cx, cz));
            return;
        }

        byte[] data = new byte[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        // 1) seed skylight from top, per (x,z) column
        for (int lx = 0; lx < Chunk.SIZE; lx++) {
            for (int lz = 0; lz < Chunk.SIZE; lz++) {
                boolean blocked = false;

                // start at top of chunk
                for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
                    int state = chunk.getState(lx, y, lz);
                    BlockType type = BlockType.fromId(BlockState.typeId(state));

                    if (!blocked && !isOpaque(type)) {
                        // can see the sky here → full skylight
                        int index = idx(lx, y, lz);
                        data[index] = (byte) MAX_LIGHT;
                        queue.add(new int[]{lx, y, lz});
                    } else {
                        // once we hit an opaque block, no more direct sky below
                        if (isOpaque(type)) blocked = true;
                    }
                }
            }
        }

        // 2) propagate via BFS, fading by 1 per step into neighbors
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            int x = cell[0], y = cell[1], z = cell[2];
            int here = data[idx(x, y, z)] & 0xFF;

            if (here <= 1) continue; // can't spread further

            int nextLevel = here - 1;

            // 6 directions
            tryPropagate(world, cx, cz, x + 1, y, z, nextLevel, data, queue);
            tryPropagate(world, cx, cz, x - 1, y, z, nextLevel, data, queue);
            tryPropagate(world, cx, cz, x, y + 1, z, nextLevel, data, queue);
            tryPropagate(world, cx, cz, x, y - 1, z, nextLevel, data, queue);
            tryPropagate(world, cx, cz, x, y, z + 1, nextLevel, data, queue);
            tryPropagate(world, cx, cz, x, y, z - 1, nextLevel, data, queue);
        }

        skylight.put(packChunk(cx, cz), data);
    }

    private void tryPropagate(World world, int cx, int cz,
                              int lx, int y, int lz,
                              int newLevel,
                              byte[] data,
                              ArrayDeque<int[]> queue) {
        if (y < 0 || y >= Chunk.HEIGHT) return;

        // neighbor chunk if we stepped outside [0, SIZE)
        int ncx = cx;
        int ncz = cz;

        if (lx < 0) {
            ncx = cx - 1;
            lx += Chunk.SIZE;
        } else if (lx >= Chunk.SIZE) {
            ncx = cx + 1;
            lx -= Chunk.SIZE;
        }

        if (lz < 0) {
            ncz = cz - 1;
            lz += Chunk.SIZE;
        } else if (lz >= Chunk.SIZE) {
            ncz = cz + 1;
            lz -= Chunk.SIZE;
        }

        Chunk chunk = world.getChunkIfLoaded(ncx, ncz);
        if (chunk == null) return;

        int state = chunk.getState(lx, y, lz);
        BlockType type = BlockType.fromId(BlockState.typeId(state));

        if (isOpaque(type)) return; // can't propagate into solid

        // only write into THIS chunk's buffer; cross-chunk propagation
        // will be handled when you rebuild neighboring chunks.
        if (ncx != cx || ncz != cz) return;

        int index = idx(lx, y, lz);
        int old = data[index] & 0xFF;
        if (newLevel <= old) return;

        data[index] = (byte) newLevel;
        queue.add(new int[]{lx, y, lz});
    }

    public int getSkyLight(int gx, int gy, int gz) {
        if (gy < 0 || gy >= Chunk.HEIGHT) return 0;
        int cx = Math.floorDiv(gx, Chunk.SIZE);
        int cz = Math.floorDiv(gz, Chunk.SIZE);
        int lx = Math.floorMod(gx, Chunk.SIZE);
        int lz = Math.floorMod(gz, Chunk.SIZE);

        byte[] data = skylight.get(packChunk(cx, cz));
        if (data == null) return 0;
        return data[idx(lx, gy, lz)] & 0xFF;
    }

    /**
     * Map skylight 0..15 -> 0..1 for rendering.
     * You can tweak this curve to taste.
     */
    public float sampleSkyLight01(int gx, int gy, int gz, float dayFactor01) {
        int level = getSkyLight(gx, gy, gz);
        if (level <= 0) return 0.0f;

        float base = (level / (float) MAX_LIGHT); // 0..1
        // Scale with time-of-day, but only as a multiplier on already computed light:
        return base * (0.2f + 0.8f * dayFactor01); // never fully zero unless level == 0
    }

    /**
     * Get the raw skylight array for a chunk, or null if not computed.
     * Used by lighting-only update path to sample per-vertex light values.
     */
    public byte[] getSkylightArray(int cx, int cz) {
        return skylight.get(packChunk(cx, cz));
    }
}
