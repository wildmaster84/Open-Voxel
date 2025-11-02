package engine.world;

import java.io.Serializable;

public class Chunk implements Serializable {
    public static final int SIZE = 16;
    public static final int HEIGHT = 512;
    private int chunkX, chunkZ;
    private final Block[][][] blocks = new Block[SIZE][HEIGHT][SIZE];

    

    public Chunk(int chunkX, int chunkZ) {
    	this.chunkX = chunkX;
    	this.chunkZ = chunkZ;
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < HEIGHT; y++)
                for (int z = 0; z < SIZE; z++)
                    blocks[x][y][z] = new Block(BlockType.AIR);
    }

    public Block getBlock(int x, int y, int z) {
        return blocks[x][y][z];
    }

    public void setBlock(int x, int y, int z, Block block) {
        blocks[x][y][z] = block;
    }
}