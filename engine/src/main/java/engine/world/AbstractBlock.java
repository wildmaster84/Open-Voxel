package engine.world;

import engine.physics.PhysicsEngine.AABB;
import engine.world.block.BlockState;
import engine.world.block.BlockType;

import java.util.Collections;
import java.util.List;

/**
 * A block occurrence: a packed state plus the BlockType it resolves to.
 * Cheap to create (two field writes), safe to allocate in hot loops.
 */
public class AbstractBlock {
    private int state;
    private BlockType type;

    public enum Facing { NORTH, SOUTH, EAST, WEST }

    public AbstractBlock(int packedState) {
        this.state = packedState;
        this.type  = BlockType.fromId(BlockState.typeId(packedState));
    }

    public AbstractBlock(BlockType type) {
        this(BlockState.make(type.getId()));
    }

    public int getState() { return state; }

    public void setState(int newState) {
        this.state = newState;
        this.type = BlockType.fromId(BlockState.typeId(newState));
    }

    public BlockType getType() { return type; }

    // ---- shape delegation ----

    public boolean isSlab() { return type == BlockType.SLAB; }
    public int slabKind()  { return BlockState.slabKind(state); }
    public boolean isSlabBottom() { return isSlab() && slabKind() == BlockState.SLAB_KIND_BOTTOM; }
    public boolean isSlabTop()    { return isSlab() && slabKind() == BlockState.SLAB_KIND_TOP; }
    public boolean isSlabDouble() { return isSlab() && slabKind() == BlockState.SLAB_KIND_DOUBLE; }

}
