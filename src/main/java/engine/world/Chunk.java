package engine.world;

import java.io.Serializable;

import engine.world.block.BlockType;

public class Chunk implements Serializable {
    public static final int SIZE = 16;
    public static final int HEIGHT = 512;
    private int chunkX, chunkZ;
    private final int[][][] blocks = new int[SIZE][HEIGHT][SIZE];

    

    public Chunk(int chunkX, int chunkZ) {
    	this.chunkX = chunkX;
    	this.chunkZ = chunkZ;
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < HEIGHT; y++)
                for (int z = 0; z < SIZE; z++)
                    blocks[x][y][z] = BlockType.AIR.getId();
    }

    public AbstractBlock getBlock(int x, int y, int z) {
        return new AbstractBlock(BlockType.fromId(blocks[x][y][z]));
    }

    public void setBlock(int x, int y, int z, AbstractBlock block) {
        blocks[x][y][z] = block.getType().getId();
    }
    
    public int getChunkX() {
    	return chunkX;
    }
    
    public int getChunkZ() {
    	return chunkZ;
    }
}