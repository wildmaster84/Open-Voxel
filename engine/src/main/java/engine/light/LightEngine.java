package engine.light;

import engine.world.Chunk;
import engine.world.World;
import engine.world.block.BlockState;
import engine.world.block.BlockType;

public final class LightEngine {
    public static final int MAX_LIGHT = 15;
    private static final int LIGHT_CELLS = Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE;

    /** Per-thread working buffer to avoid 128KB allocation per mesh. */
    private static final ThreadLocal<byte[]> WORKING = ThreadLocal.withInitial(() -> new byte[LIGHT_CELLS]);
    /** Per-thread block-light working buffer. */
    private static final ThreadLocal<byte[]> BLOCK_WORKING = ThreadLocal.withInitial(() -> new byte[LIGHT_CELLS]);

    private static int idx(int x, int y, int z) {
        return (y * Chunk.SIZE + z) * Chunk.SIZE + x;
    }

    private static int pack(int x, int y, int z) { return (y << 8) | (z << 4) | x; }

    private static final class IntStack {
        int[] data = new int[4096];
        int top = 0;
        void push(int v) {
            if (top == data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[top++] = v;
        }
        boolean isEmpty() { return top == 0; }
    }

    public void rebuildSkylightForChunk(World world, int cx, int cz, Chunk chunk) {
        if (chunk == null) return;

        byte[] data = WORKING.get();
        java.util.Arrays.fill(data, (byte) 0);

        IntStack queue = new IntStack();
        int head = 0;
        for (int lx = 0; lx < Chunk.SIZE; lx++) {
            for (int lz = 0; lz < Chunk.SIZE; lz++) {
                int level = MAX_LIGHT;
                for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
                    BlockType t = BlockType.fromId(BlockState.typeId(chunk.getState(lx, y, lz)));
                    int opacity = t.lightOpacity();
                    if (opacity >= MAX_LIGHT) break; // fully opaque: column ends
                    level = Math.max(0, level - opacity);
                    if (level <= 0) break;
                    data[idx(lx, y, lz)] = (byte) level;
                    if (level == MAX_LIGHT) queue.push(pack(lx, y, lz));
                }
            }
        }

        // Pass 1b: inbound light from loaded neighbors' border cells.
        seedFromNeighbors(world, cx, cz, chunk, data, queue);

        // Pass 2: flood-fill bleed (BFS), allocation-free packed-int queue.
        java.util.Set<Long> touchedNeighbors = new java.util.HashSet<>(4);
        while (head < queue.top) {
            int cell = queue.data[head++];
            int x = cell & 15, z = (cell >> 4) & 15, y = cell >> 8;
            int here = data[idx(x, y, z)] & 0xFF;
            if (here <= 1) continue;
            int nextLevel = here - 1;
            tryPropagate(world, cx, cz, chunk, x + 1, y, z, nextLevel, data, queue, touchedNeighbors);
            tryPropagate(world, cx, cz, chunk, x - 1, y, z, nextLevel, data, queue, touchedNeighbors);
            tryPropagate(world, cx, cz, chunk, x, y + 1, z, nextLevel, data, queue, touchedNeighbors);
            tryPropagate(world, cx, cz, chunk, x, y - 1, z, nextLevel, data, queue, touchedNeighbors);
            tryPropagate(world, cx, cz, chunk, x, y, z + 1, nextLevel, data, queue, touchedNeighbors);
            tryPropagate(world, cx, cz, chunk, x, y, z - 1, nextLevel, data, queue, touchedNeighbors);
        }

        chunk.publishSkylight(data);
        rebuildBlockLight(world, cx, cz, chunk);

        // Bump versions of neighbors we wrote into — the renderer's version
        // guard remeshes them, their rebuild spreads light further, converging.
        for (long k : touchedNeighbors) {
            Chunk nb = world.getChunkIfLoaded((int) (k >> 32), (int) (k & 0xffffffffL));
            if (nb != null) nb.bumpSkylightVersion();
        }
    }

    /**
     * Block-light pass: BFS from emissive blocks, same attenuation rules as
     * skylight (opacity per block), cross-chunk in both directions.
     */
    private void rebuildBlockLight(World world, int cx, int cz, Chunk chunk) {
        byte[] data = BLOCK_WORKING.get();
        java.util.Arrays.fill(data, (byte) 0);

        IntStack queue = new IntStack();
        int head = 0;

        // Seeds: emissive blocks (and their cells) in this chunk.
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    BlockType t = BlockType.fromId(BlockState.typeId(chunk.getState(x, y, z)));
                    int emission = t.lightEmission();
                    if (emission <= 0) continue;
                    data[idx(x, y, z)] = (byte) emission;
                    queue.push(pack(x, y, z));
                }
            }
        }

        // Inbound: neighbors' border block-light.
        int[][] NB = {{0,-1},{0,1},{-1,0},{1,0}};
        for (int i = 0; i < 4; i++) {
            Chunk nb = world.getChunkIfLoaded(cx + NB[i][0], cz + NB[i][1]);
            if (nb == null) continue;
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int j = 0; j < Chunk.SIZE; j++) {
                    int lx, lz, nlx, nlz;
                    if (NB[i][1] != 0) {
                        lx = j; lz = (NB[i][1] < 0) ? 0 : Chunk.SIZE - 1;
                        nlx = j; nlz = (NB[i][1] < 0) ? Chunk.SIZE - 1 : 0;
                    } else {
                        lz = j; lx = (NB[i][0] < 0) ? 0 : Chunk.SIZE - 1;
                        nlz = j; nlx = (NB[i][0] < 0) ? Chunk.SIZE - 1 : 0;
                    }
                    int nLevel = nb.getBlockLight(nlx, y, nlz) & 0xFF;
                    if (nLevel <= 0) continue;
                    BlockType t = BlockType.fromId(BlockState.typeId(chunk.getState(lx, y, lz)));
                    int opacity = t.lightOpacity();
                    if (opacity >= MAX_LIGHT) continue;
                    int level = nLevel - 1 - opacity;
                    if (level <= 0) continue;
                    int index = idx(lx, y, lz);
                    if (level > (data[index] & 0xFF)) {
                        data[index] = (byte) level;
                        queue.push(pack(lx, y, lz));
                    }
                }
            }
        }

        // Flood fill.
        java.util.Set<Long> touched = new java.util.HashSet<>(4);
        while (head < queue.top) {
            int cell = queue.data[head++];
            int x = cell & 15, z = (cell >> 4) & 15, y = cell >> 8;
            int here = data[idx(x, y, z)] & 0xFF;
            if (here <= 1) continue;
            int nextLevel = here - 1;
            propagateBlockLight(world, cx, cz, chunk, x + 1, y, z, nextLevel, data, queue, touched);
            propagateBlockLight(world, cx, cz, chunk, x - 1, y, z, nextLevel, data, queue, touched);
            propagateBlockLight(world, cx, cz, chunk, x, y + 1, z, nextLevel, data, queue, touched);
            propagateBlockLight(world, cx, cz, chunk, x, y - 1, z, nextLevel, data, queue, touched);
            propagateBlockLight(world, cx, cz, chunk, x, y, z + 1, nextLevel, data, queue, touched);
            propagateBlockLight(world, cx, cz, chunk, x, y, z - 1, nextLevel, data, queue, touched);
        }

        chunk.publishBlockLight(data);

        // Outbound: bump neighbors we wrote into; their rebuilds converge.
        for (long k : touched) {
            Chunk nb = world.getChunkIfLoaded((int) (k >> 32), (int) (k & 0xffffffffL));
            if (nb != null) nb.bumpSkylightVersion();
        }
    }

    private void propagateBlockLight(World world, int cx, int cz, Chunk chunk, int lx, int y, int lz,
                                     int newLevel, byte[] data, IntStack stack, java.util.Set<Long> touched) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) {
            int ncx = cx + (lx < 0 ? -1 : (lx >= Chunk.SIZE ? 1 : 0));
            int ncz = cz + (lz < 0 ? -1 : (lz >= Chunk.SIZE ? 1 : 0));
            Chunk nb = world.getChunkIfLoaded(ncx, ncz);
            if (nb == null) return;
        int nlx = lx & 15;
        int nlz = lz & 15;
            BlockType t = BlockType.fromId(BlockState.typeId(nb.getState(nlx, y, nlz)));
            int opacity = t.lightOpacity();
            if (opacity >= MAX_LIGHT) return;
            int level = newLevel - 1 - opacity;
            if (level <= 0) return;
            if (level > (nb.getBlockLight(nlx, y, nlz) & 0xFF)) {
                nb.setBlockLight(nlx, y, nlz, level);
                touched.add(((long) ncx << 32) | (ncz & 0xffffffffL));
            }
            return;
        }
        BlockType t = BlockType.fromId(BlockState.typeId(chunk.getState(lx, y, lz)));
        int opacity = t.lightOpacity();
        if (opacity >= MAX_LIGHT) return;
        newLevel -= opacity;
        if (newLevel <= 0) return;
        int index = idx(lx, y, lz);
        int old = data[index] & 0xFF;
        if (newLevel <= old) return;
        data[index] = (byte) newLevel;
        stack.push(pack(lx, y, lz));
    }

    /** Seeds border cells from loaded neighbors' published skylight. */
    private void seedFromNeighbors(World world, int cx, int cz, Chunk chunk, byte[] data, IntStack queue) {
        int[][] NB = {{0,-1},{0,1},{-1,0},{1,0}};
        for (int i = 0; i < 4; i++) {
            Chunk nb = world.getChunkIfLoaded(cx + NB[i][0], cz + NB[i][1]);
            if (nb == null) continue;
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int j = 0; j < Chunk.SIZE; j++) {
                    int lx, lz, nlx, nlz;
                    if (NB[i][1] != 0) {
                        lx = j; lz = (NB[i][1] < 0) ? 0 : Chunk.SIZE - 1;
                        nlx = j; nlz = (NB[i][1] < 0) ? Chunk.SIZE - 1 : 0;
                    } else {
                        lz = j; lx = (NB[i][0] < 0) ? 0 : Chunk.SIZE - 1;
                        nlz = j; nlx = (NB[i][0] < 0) ? Chunk.SIZE - 1 : 0;
                    }
                    int nLevel = nb.getSkyLight(nlx, y, nlz) & 0xFF;
                    if (nLevel <= 1) continue;
                    BlockType t = BlockType.fromId(BlockState.typeId(chunk.getState(lx, y, lz)));
                    int opacity = t.lightOpacity();
                    if (opacity >= MAX_LIGHT) continue;
                    int level = nLevel - 1 - opacity;
 if (level <= 0) continue;
                    int index = idx(lx, y, lz);
                    if (level > (data[index] & 0xFF)) {
                        data[index] = (byte) level;
                        queue.push(pack(lx, y, lz));
                    }
                }
            }
        }
    }

    private void tryPropagate(World world, int cx, int cz, Chunk chunk, int lx, int y, int lz,
                              int newLevel, byte[] data, IntStack stack, java.util.Set<Long> touched) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) {
            // Outbound: spread into a loaded neighbor chunk (increase-only).
            int ncx = cx + (lx < 0 ? -1 : (lx >= Chunk.SIZE ? 1 : 0));
            int ncz = cz + (lz < 0 ? -1 : (lz >= Chunk.SIZE ? 1 : 0));
            Chunk nb = world.getChunkIfLoaded(ncx, ncz);
            if (nb == null) return;
        int nlx = lx & 15;
        int nlz = lz & 15;
            BlockType t = BlockType.fromId(BlockState.typeId(nb.getState(nlx, y, nlz)));
            int opacity = t.lightOpacity();
            if (opacity >= MAX_LIGHT) return;
            int level = newLevel - 1 - opacity;
            if (level <= 0) return;
            if (level > (nb.getSkyLight(nlx, y, nlz) & 0xFF)) {
                nb.setSkyLight(nlx, y, nlz, level);
                touched.add(((long) ncx << 32) | (ncz & 0xffffffffL));
            }
            return;
        }
        BlockType t = BlockType.fromId(BlockState.typeId(chunk.getState(lx, y, lz)));
        int opacity = t.lightOpacity();
        if (opacity >= MAX_LIGHT) return;
        newLevel -= opacity;
        if (newLevel <= 0) return;
        int index = idx(lx, y, lz);
        int old = data[index] & 0xFF;
        if (newLevel <= old) return;
        data[index] = (byte) newLevel;
        stack.push(pack(lx, y, lz));
    }

    public int getSkyLight(World world, int gx, int gy, int gz) {
        if (gy < 0 || gy >= Chunk.HEIGHT) return 0;
        Chunk chunk = world.getChunkIfLoaded(gx >> 4, gz >> 4);
        if (chunk == null) return 0;
        return chunk.getSkyLight(gx & 15, gy, gz & 15) & 0xFF;
    }

    public float sampleSkyLight01(World world, int gx, int gy, int gz, float dayFactor01) {
        int level = getSkyLight(world, gx, gy, gz);
        if (level <= 0) return 0.0f;
        float base = (level / (float) MAX_LIGHT);
        return base * (0.2f + 0.8f * dayFactor01);
    }

    /** 0..1 block-light brightness for a face-neighbor cell. */
    public float sampleBlockLight01(World world, int gx, int gy, int gz) {
        if (gy < 0 || gy >= Chunk.HEIGHT) return 0.0f;
        Chunk chunk = world.getChunkIfLoaded(gx >> 4, gz >> 4);
        if (chunk == null) return 0.0f;
        int level = chunk.getBlockLight(gx & 15, gy, gz & 15) & 0xFF;
        if (level <= 0) return 0.0f;
        return level / (float) MAX_LIGHT;
    }
}
