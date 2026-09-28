package game.net;

import engine.world.Chunk;
import engine.world.World;

/**
 * A World whose chunks come from the network instead of local generation.
 * Missing chunks are created empty (air) and filled when CHUNK packets
 * arrive; the renderer remeshes on each fill via NetClient.
 */
public class RemoteWorld extends World {
    public RemoteWorld() {
        super(0L); // seed irrelevant — never generates
    }

    @Override
    public Chunk getChunk(int cx, int cz) {
        final long key = chunkKey(cx, cz);
        return getChunks().computeIfAbsent(key, k -> new Chunk(cx, cz)); // empty air chunk
    }

    /** Called by NetClient when a CHUNK packet arrives. Fills the chunk in place. */
    public void applyChunkData(int cx, int cz, int[] states) {
        Chunk chunk = getChunk(cx, cz);
        int p = 0;
        for (int x = 0; x < Chunk.SIZE; x++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int y = 0; y < Chunk.HEIGHT; y++)
                    chunk.setState(x, y, z, states[p++]);
    }

    /** Called on BLOCK_UPDATE. */
    public void applyBlockUpdate(int x, int y, int z, int state) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        int cx = Math.floorDiv(x, Chunk.SIZE), cz = Math.floorDiv(z, Chunk.SIZE);
        Chunk chunk = getChunkIfLoaded(cx, cz);
        if (chunk != null) {
            chunk.setBlock(Math.floorMod(x, Chunk.SIZE), y, Math.floorMod(z, Chunk.SIZE),
                    new engine.world.AbstractBlock(state));
        }
    }

    /** Remote worlds never tick generation/unload. */
    @Override
    public void tick(engine.rendering.Camera camera, float dt) {}

    /** Nothing to save locally — the server owns persistence. */
    @Override
    public void save() {}

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }
}
