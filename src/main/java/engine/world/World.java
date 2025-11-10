package engine.world;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import engine.world.block.BlockType;


public class World implements Serializable {
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();

    private final PerlinNoise perlin;
    private final int SEA_LEVEL = 92;

    public World(long seed) {
        this.perlin = new PerlinNoise(seed);
    }
    
    public Map<Long, Chunk> getChunks() {
    	return chunks;
    }

    public Chunk getChunk(int cx, int cz) {
        final long key = getChunkKey(cx, cz);
        return chunks.computeIfAbsent(key, k -> generateChunk(cx, cz));
    }

    public Chunk getChunkIfLoaded(int cx, int cz) {
        return chunks.get(getChunkKey(cx, cz));
    }

    public void ensureChunksAround(int centerCx, int centerCz, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                getChunk(centerCx + dx, centerCz + dz);
            }
        }
    }

    public void unloadFarChunks(int playerChunkX, int playerChunkZ, int renderRadius) {
        final int limit = renderRadius + 2;
        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> e = it.next();
            int cx = (int)(e.getKey() >> 32);
            int cz = (int)(e.getKey() & 0xffffffffL);
            if (Math.abs(cx - playerChunkX) > limit || Math.abs(cz - playerChunkZ) > limit) {
                it.remove();
            }
        }
    }

    public AbstractBlock getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.HEIGHT) return null;
        int chunkX = Math.floorDiv(x, Chunk.SIZE);
        int chunkZ = Math.floorDiv(z, Chunk.SIZE);
        int localX = Math.floorMod(x, Chunk.SIZE);
        int localZ = Math.floorMod(z, Chunk.SIZE);
        Chunk chunk = getChunk(chunkX, chunkZ);
        return chunk.getBlock(localX, y, localZ);
    }

    public void setBlock(int x, int y, int z, AbstractBlock block) {
        if (y < 0 || y >= Chunk.HEIGHT) return;
        int chunkX = Math.floorDiv(x, Chunk.SIZE);
        int chunkZ = Math.floorDiv(z, Chunk.SIZE);
        int localX = Math.floorMod(x, Chunk.SIZE);
        int localZ = Math.floorMod(z, Chunk.SIZE);
        Chunk chunk = getChunk(chunkX, chunkZ);
        chunk.setBlock(localX, y, localZ, block);
    }

    public Chunk getChunk(int x, int y, int z) {
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        return getChunk(cx, cz);
    }

    public void save(File file) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(chunks);
        }
    }

    @SuppressWarnings("unchecked")
    public void load(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Map<Long, Chunk> loaded = (Map<Long, Chunk>) in.readObject();
            chunks.clear();
            chunks.putAll(loaded);
        }
    }

    private long getChunkKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
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

                double h1 = perlin.fbm(wx * SCALE_HILLS,  wz * SCALE_HILLS,  OCTAVES, LACUNARITY, GAIN);
                double h2 = perlin.fbm(wx * SCALE_RIDGES, wz * SCALE_RIDGES, OCTAVES, LACUNARITY, GAIN);
                double combined = 0.7 * h1 + 0.3 * h2;
                double curved = Math.signum(combined) * Math.pow(Math.abs(combined), 1.2);

                int height = BASE_HEIGHT + (int) Math.round((curved * 0.5 + 0.5) * HEIGHT_VARIANCE);
                if (height < 1) height = 1;
                if (height >= Chunk.HEIGHT) height = Chunk.HEIGHT - 1;

                boolean beach = height <= SEA_LEVEL + 1 && height >= SEA_LEVEL - 2;

                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    if (y == 0) {
                        chunk.setBlock(x, y, z, new AbstractBlock(BlockType.BEDROCK));
                        continue;
                    }
                    if (y < height - SOIL_DEPTH) {
                        chunk.setBlock(x, y, z, new AbstractBlock(BlockType.STONE));
                    } else if (y < height - 1) {
                        if (beach && y >= SEA_LEVEL - 3) {
                            chunk.setBlock(x, y, z, new AbstractBlock(BlockType.STONE));
                        } else {
                        	if (y < height - 1 && y >= height - 3) {
                        		chunk.setBlock(x, y, z, new AbstractBlock(BlockType.DIRT));
                        	} else {
                        		chunk.setBlock(x, y, z, new AbstractBlock(BlockType.STONE));
                        	}
                        }
                    } else if (y == height - 1) {
                        if (beach && y <= SEA_LEVEL) {
                            chunk.setBlock(x, y, z, new AbstractBlock(BlockType.SAND));
                        } else {
                        	if (y <= SEA_LEVEL) {
                        		chunk.setBlock(x, y, z, new AbstractBlock(BlockType.DIRT));
                        	} else {
                        		chunk.setBlock(x, y, z, new AbstractBlock(BlockType.GRASS));
                        	}
                            
                        }
                    } else {
                        if (y <= SEA_LEVEL) {
                            chunk.setBlock(x, y, z, new AbstractBlock(BlockType.WATER));
                        } else {
                            chunk.setBlock(x, y, z, new AbstractBlock(BlockType.AIR));
                        }
                    }
                }
            }
        }
        return chunk;
    }
    
    public boolean isWaterAt(int x, int y, int z) {
    	AbstractBlock b = getBlock(x, y, z);
        return b != null && b.getType() == BlockType.WATER;
    }
}