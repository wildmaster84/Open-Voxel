package engine.world;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
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

    private final PerlinNoise perlin = new PerlinNoise(7483901); // Add this to your World class

    private Chunk generateChunk(int cx, int cz) {
        Chunk chunk = new Chunk();
        int maxHeight = Chunk.HEIGHT - 1;
        double scale = 0.03;   // Lower = smoother, less repetition
        double amp = 8.0;     // Controls hill height
        double base = 64.0;    // Base terrain height

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = cx * Chunk.SIZE + x;
                int wz = cz * Chunk.SIZE + z;
                double noiseVal = perlin.noise(wx * scale, wz * scale, 0.0); // [-1,1]
                int height = (int)(base + noiseVal * amp);
                height = Math.min(height, maxHeight);

                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    if (y < height - 3) chunk.setBlock(x, y, z, new Block(BlockType.STONE));
                    else if (y < height - 1) chunk.setBlock(x, y, z, new Block(BlockType.DIRT));
                    else if (y == height - 1) chunk.setBlock(x, y, z, new Block(BlockType.GRASS));
                    else chunk.setBlock(x, y, z, new Block(BlockType.AIR));
                }
            }
        }
        return chunk;
    }
    
    public void unloadFarChunks(int playerChunkX, int playerChunkZ, int renderRadius) {
        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> entry = it.next();
            int cx = (int)(entry.getKey() >> 32);
            int cz = (int)(entry.getKey() & 0xffffffffL);
            if (Math.abs(cx - playerChunkX) > renderRadius + 2 || Math.abs(cz - playerChunkZ) > renderRadius + 2) {
                it.remove(); // Unload chunk
            }
        }
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