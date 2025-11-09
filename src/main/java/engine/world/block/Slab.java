package engine.world.block;

import java.util.Collections;
import java.util.List;

import engine.physics.PhysicsEngine.AABB;
import engine.world.AbstractBlock;

public class Slab extends AbstractBlock {
    public enum SlabType { BOTTOM, TOP, DOUBLE }

    private SlabType type = SlabType.BOTTOM;

    public Slab(int packedState) {
        super(packedState);
    }
    
    public Slab(BlockType type) {
        super(type);
    }

    public Slab setSlabType(SlabType type) { this.type = type; return this; }
    public SlabType getSlabType() { return type; }
    
    @Override
    public List<AABB> getCollisionBoxes() {
    	if (isSlabDouble()) return List.of(new AABB(0,0,0, 1,1,1));
        if (isSlabTop())    return List.of(new AABB(0,0.5f,0, 1,1,1));
        return List.of(new AABB(0,0,0, 1,0.5f,1));
    }

}
