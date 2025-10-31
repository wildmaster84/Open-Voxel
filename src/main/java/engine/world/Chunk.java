package engine.world;

import java.io.Serializable;

public class Chunk implements Serializable {
    public static final int SIZE = 16;
    private final Block[][][] blocks = new Block[SIZE][SIZE][SIZE];

    public Chunk() {
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < SIZE; y++)
                for (int z = 0; z < SIZE; z++)
                    blocks[x][y][z] = new Block(Block.Type.AIR);
    }

    public Block getBlock(int x, int y, int z) {
        return blocks[x][y][z];
    }

    public void setBlock(int x, int y, int z, Block block) {
        blocks[x][y][z] = block;
    }
}