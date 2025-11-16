package engine.world;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import engine.VoxelEngine;
import engine.rendering.Camera;
import engine.world.block.BlockType;
import engine.world.saving.SaveManager;


public class World {
	private static final AbstractBlock BEDROCK_BLOCK = new AbstractBlock(BlockType.BEDROCK);
	private static final AbstractBlock STONE_BLOCK   = new AbstractBlock(BlockType.STONE);
	private static final AbstractBlock DIRT_BLOCK    = new AbstractBlock(BlockType.DIRT);
	private static final AbstractBlock SAND_BLOCK    = new AbstractBlock(BlockType.SAND);
	private static final AbstractBlock WATER_BLOCK   = new AbstractBlock(BlockType.WATER);
	private static final AbstractBlock AIR_BLOCK     = new AbstractBlock(BlockType.AIR);
	
	private final ExecutorService chunkPool = Executors.newFixedThreadPool(1);
	
	
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private SaveManager saveManager;

    private final PerlinNoise perlin;
    private final int SEA_LEVEL = 92;

    public World(long seed) {
        this.perlin = new PerlinNoise(seed);
        saveManager = new SaveManager();
    }
    
    

    
    
    public Map<Long, Chunk> getChunks() {
    	return chunks;
    }

    public Chunk getChunk(int cx, int cz) {
        final long key = getChunkKey(cx, cz);
        return chunks.computeIfAbsent(key, k -> {
            Chunk loaded = saveManager.loadChunk(cx, cz);
			return (loaded != null) ? loaded : generateChunk(cx, cz);
        });
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
            	if (e.getValue().isDirty()) {
        			try {
                    	saveManager.saveChunk(e.getValue());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
        		}
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

    public void save() {
    	for (Chunk c : chunks.values()) {
    		if (c.isDirty()) {
    			try {
                	saveManager.saveChunk(c);
                } catch (IOException e) {
                    e.printStackTrace();
                }
    		}
            
        }
    }


    private long getChunkKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    private Chunk generateChunk(int cx, int cz) {
        Chunk chunk = new Chunk(cx, cz);

        // --- Noise / shape params (same as before) ---
        final double SCALE_HILLS   = 1.0 / 60.0;
        final double SCALE_RIDGES  = 1.0 / 76.0;
        final int    OCTAVES       = 5;
        final double LACUNARITY    = 2.0;
        final double GAIN          = 0.45;
        final int    BASE_HEIGHT   = 64;
        final int    HEIGHT_VAR    = 64;
        final int    SOIL_DEPTH    = 64;

        // Bedrock base (y=0) for the whole chunk
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                chunk.setBlock(x, 0, z, BEDROCK_BLOCK);
            }
        }

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                final int wx = cx * Chunk.SIZE + x;
                final int wz = cz * Chunk.SIZE + z;

                double h1 = perlin.fbm(wx * SCALE_HILLS,  wz * SCALE_HILLS,  OCTAVES, LACUNARITY, GAIN);
                double h2 = perlin.fbm(wx * SCALE_RIDGES, wz * SCALE_RIDGES, OCTAVES, LACUNARITY, GAIN);
                double combined = 0.7 * h1 + 0.3 * h2;
                double curved = Math.signum(combined) * Math.pow(Math.abs(combined), 1.2);

                int height = BASE_HEIGHT + (int) Math.round((curved * 0.5 + 0.5) * HEIGHT_VAR);
                if (height < 1) height = 1;
                if (height >= Chunk.HEIGHT) height = Chunk.HEIGHT - 1;

                final boolean beach = height <= SEA_LEVEL + 1 && height >= SEA_LEVEL - 2;

                int deepTop = (height - SOIL_DEPTH) - 1;
                if (deepTop >= 1) {
                    chunk.fill(x, z, 1, deepTop, STONE_BLOCK);
                }

                int bodyStart = Math.max(1, height - SOIL_DEPTH);
                int bodyEnd   = height - 2;

                if (bodyStart <= bodyEnd) {
                    if (beach) {
                        chunk.fill(x, z, bodyStart, bodyEnd, STONE_BLOCK);

                        int dirtA = Math.max(bodyStart, height - 3);
                        int dirtB = Math.min(bodyEnd, (SEA_LEVEL - 3) - 1); // up to SEA_LEVEL-4
                        if (dirtA <= dirtB) {
                            chunk.fill(x, z, dirtA, dirtB, DIRT_BLOCK);
                        }
                    } else {
                        // Normal hill: stone up to (height-3), then dirt to (height-2)
                        int dirtStart = Math.max(bodyStart, height - 3);
                        if (bodyStart <= dirtStart - 1) {
                            chunk.fill(x, z, bodyStart, dirtStart - 1, STONE_BLOCK);
                        }
                        if (dirtStart <= bodyEnd) {
                            chunk.fill(x, z, dirtStart, bodyEnd, DIRT_BLOCK);
                        }
                    }
                }

                // Top block at y = height - 1
                int topY = height - 1;
                int topId;
                if (beach && topY <= SEA_LEVEL) {
                    topId = BlockType.SAND.getId();
                } else if (topY <= SEA_LEVEL) {
                    topId = BlockType.DIRT.getId();
                } else {
                	//BlockState.asSlab(BlockState.make(BlockType.SLAB.getId()), BlockState.SLAB_KIND_BOTTOM);
                	//BlockState.asStairs(BlockState.make(BlockType.STAIR.getId()), BlockState.FACING_NORTH, false);
                    topId = BlockType.GRASS.getId();
                }
                chunk.setState(x, topY, z, topId);

                // Above top: water up to sea level, then air to ceiling
                if (topY + 1 <= SEA_LEVEL) {
                    chunk.fill(x, z, topY + 1, SEA_LEVEL, new AbstractBlock(BlockType.WATER));
                }
            }
        }

        try {
			saveManager.saveChunk(chunk);
		} catch (IOException e) {
			System.out.println("Failed to save chunk! " + e.getLocalizedMessage());
		}
        return chunk;
    }





	public void tick(Camera camera, float tICK_DT) {
		chunkPool.submit(() -> {
			int cameraChunkX = Math.floorDiv((int) camera.getPosition().x, Chunk.SIZE);
	        int cameraChunkZ = Math.floorDiv((int) camera.getPosition().z, Chunk.SIZE);
	        int renderRadius = camera.getRenderDistance();
	        
			unloadFarChunks(cameraChunkX, cameraChunkZ, renderRadius);
	        ensureChunksAround(cameraChunkX, cameraChunkZ, renderRadius);
		});
		
	}
}