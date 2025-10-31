package engine.world;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class World implements Serializable {
    private Map<Long, Chunk> chunks = new HashMap<>();

    public World() {
        // Generate all chunks in a 3D grid for testing
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                chunks.put(getChunkKey(cx, cz), generateChunk(cx, cz));
            }
        }
    }

    private long getChunkKey(int x, int z) {
        return (((long)x) << 32) | (z & 0xffffffffL);
    }

    public Chunk getChunk(int x, int z) {
        return chunks.get(getChunkKey(x, z));
    }

    private Chunk generateChunk(int cx, int cz) {
        Chunk chunk = new Chunk();
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = cx * Chunk.SIZE + x;
                int wz = cz * Chunk.SIZE + z;
                int height = 8 + (int)(4 * Math.sin(wx * 0.13) + 4 * Math.cos(wz * 0.11));
                for (int y = 0; y < Chunk.SIZE; y++) {
                    if (y < height - 3) chunk.setBlock(x, y, z, new Block(Block.Type.STONE));
                    else if (y < height - 1) chunk.setBlock(x, y, z, new Block(Block.Type.DIRT));
                    else if (y == height - 1) chunk.setBlock(x, y, z, new Block(Block.Type.GRASS));
                    else chunk.setBlock(x, y, z, new Block(Block.Type.AIR));
                }
            }
        }
        return chunk;
    }

    // Basic "heightmap" function, replace with Perlin/Simplex for realism!
    private int getHeight(int worldX, int worldZ) {
        // Simple pseudo-random hills
        return 8 + (int)(4 * Math.sin(worldX * 0.13) + 4 * Math.cos(worldZ * 0.11));
    }

    // Save/load world
    public void save(File file) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(chunks);
        }
    }

    @SuppressWarnings("unchecked")
    public void load(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            chunks = (Map<Long, Chunk>) in.readObject();
        }
    }
}