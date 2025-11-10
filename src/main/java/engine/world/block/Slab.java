package engine.world.block;

import engine.physics.PhysicsEngine.AABB;
import engine.world.AbstractBlock;

import java.util.List;

public class Slab extends AbstractBlock {
    public enum SlabType { BOTTOM, TOP, DOUBLE }

    private SlabType type = SlabType.BOTTOM;

    public Slab(BlockType type) {
        super(type);
    }

    public Slab setSlabType(SlabType type) { this.type = type; return this; }
    public SlabType getSlabType() { return type; }

    @Override public boolean isSlab() { return true; }

    @Override
    public List<AABB> getCollisionBoxes() {
        switch (type) {
            case TOP:    return List.of(new AABB(0, 0.5f, 0, 1, 1.0f, 1));
            case DOUBLE: return List.of(new AABB(0, 0.0f, 0, 1, 1.0f, 1));
            default:     return List.of(new AABB(0, 0.0f, 0, 1, 0.5f, 1)); // bottom
        }
    }
}
