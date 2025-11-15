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

    private final Map<Long, byte[]> skylight = new HashMap<>();

    private static long packChunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static int idx(int x, int y, int z) {
        return (y * Chunk.SIZE + z) * Chunk.SIZE + x;
    }

    private static boolean isOpaque(BlockType t) {
        if (t == BlockType.AIR)   return false;
        if (t == BlockType.WATER) return false;
        if (t == BlockType.GLASS) return false; 
        return true;
    }
    
    public byte[] getSkylightArray(int cx, int cz) {
        return skylight.get(packChunk(cx, cz));
    }

    public void rebuildSkylightForChunk(World world, int cx, int cz, Chunk chunk) {
        if (chunk == null) {
            skylight.remove(packChunk(cx, cz));
            return;
        }

        byte[] data = new byte[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        for (int lx = 0; lx < Chunk.SIZE; lx++) {
            for (int lz = 0; lz < Chunk.SIZE; lz++) {
                boolean blocked = false;

                for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
                    int state = chunk.getState(lx, y, lz);
                    BlockType type = BlockType.fromId(BlockState.typeId(state));

                    if (!blocked && !isOpaque(type)) {
                        int index = idx(lx, y, lz);
                        data[index] = (byte) MAX_LIGHT;
                        queue.add(new int[]{lx, y, lz});
                    } else {
                        if (isOpaque(type)) blocked = true;
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            int x = cell[0], y = cell[1], z = cell[2];
            int here = data[idx(x, y, z)] & 0xFF;

            if (here <= 1) continue;

            int nextLevel = here - 1;

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

        if (isOpaque(type)) return;
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

    public float sampleSkyLight01(int gx, int gy, int gz, float dayFactor01) {
        int level = getSkyLight(gx, gy, gz);
        if (level <= 0) return 0.0f;

        float base = (level / (float) MAX_LIGHT);
        return base * (0.2f + 0.8f * dayFactor01);
    }
}
