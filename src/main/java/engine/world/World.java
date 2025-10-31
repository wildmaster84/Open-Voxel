package engine.world;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class World implements Serializable {
    private Map<Long, Chunk> chunks = new HashMap<>();
    public static final int WORLD_SIZE = 768;
    public static final int NUM_CHUNKS = WORLD_SIZE / Chunk.SIZE;
    
    private final PerlinNoise perlin; // choose your seed
    private final int SEA_LEVEL = 10;  // tweak to taste

    public World(long seed) {
    	perlin = new PerlinNoise(seed);
    	for (int cx = -NUM_CHUNKS / 2; cx < NUM_CHUNKS / 2; cx++) {
    	    for (int cz = -NUM_CHUNKS / 2; cz < NUM_CHUNKS / 2; cz++) {
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
        Chunk chunk = new Chunk(cx, cz);

        final double SCALE_HILLS = 1.0 / 60.0;
        final double SCALE_RIDGES = 1.0 / 76.0;
        final int OCTAVES = 5;
        final double LACUNARITY = 2.0;
        final double GAIN = 0.45;
        final int BASE_HEIGHT = 64;
        final int HEIGHT_VARIANCE = 64;
        final int SOIL_DEPTH = 64;

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = cx * Chunk.SIZE + x;
                int wz = cz * Chunk.SIZE + z;

                double h1 = perlin.fbm(wx * SCALE_HILLS,  wz * SCALE_HILLS,  OCTAVES, LACUNARITY, GAIN);   // [-1,1]
                double h2 = perlin.fbm(wx * SCALE_RIDGES, wz * SCALE_RIDGES, OCTAVES, LACUNARITY, GAIN);   // [-1,1]
                double combined = 0.7 * h1 + 0.3 * h2;       // still roughly [-1,1]
                double curved = Math.signum(combined) * Math.pow(Math.abs(combined), 1.2); // gentle bias

                int height = BASE_HEIGHT + (int) Math.round((curved * 0.5 + 0.5) * HEIGHT_VARIANCE);

                if (height < 1) height = 1;
                if (height >= Chunk.HEIGHT) height = Chunk.HEIGHT - 1;

                boolean beach = height <= SEA_LEVEL + 1 && height >= SEA_LEVEL - 2;

                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    if (y == 0) {
                    	// Should be bedrock
                        chunk.setBlock(x, y, z, new Block(BlockType.STONE));
                        continue;
                    }

                    if (y < height - SOIL_DEPTH) {
                        chunk.setBlock(x, y, z, new Block(BlockType.STONE));
                    } else if (y < height - 1) {
                        // for beach near water
                        if (beach && y >= SEA_LEVEL - 3) {
                            chunk.setBlock(x, y, z, new Block(BlockType.STONE));
                        } else {
                            chunk.setBlock(x, y, z, new Block(BlockType.DIRT));
                        }
                    } else if (y == height - 1) {
                        if (beach && y <= SEA_LEVEL) {
                            chunk.setBlock(x, y, z, new Block(BlockType.DIRT));
                        } else {
                            chunk.setBlock(x, y, z, new Block(BlockType.GRASS));
                        }
                    } else {
                        // above ground
                        if (y <= SEA_LEVEL) {
                            // replace with water
                            chunk.setBlock(x, y, z, new Block(BlockType.STONE));
                        } else {
                            chunk.setBlock(x, y, z, new Block(BlockType.AIR));
                        }
                    }
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
                it.remove();
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