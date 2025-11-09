package engine.world.block;

import engine.world.AbstractBlock;

public class Block extends AbstractBlock {
	public Block(int packedState) {
        super(packedState);
    }
    public Block(BlockType type) {
        super(type);
    }
}
