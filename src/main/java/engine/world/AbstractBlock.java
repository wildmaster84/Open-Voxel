package engine.world;

import engine.physics.PhysicsEngine.AABB;
import engine.world.block.BlockType;
import engine.world.block.Slab;
import engine.world.block.Stairs;

import java.util.Collections;
import java.util.List;

/** Base block: concrete full cube by default. */
public class AbstractBlock {
    protected final BlockType type;
    
    public enum Facing { NORTH, SOUTH, EAST, WEST }

    private Facing facing = Facing.NORTH;

    // Optional generic metadata if you still need it elsewhere.
    protected byte metadata = 0;
    protected short id = 0;

    public AbstractBlock(BlockType type) {
        this.type = type;
    }
    
    public void setFacing(Facing facing) { this.facing = facing; }
    public Facing getFacing() { return facing; }

    // -------- Core ----------
    public BlockType getType() { return type; }

    /** For your requested API: returns "the class instance" (this). */
    public AbstractBlock getState() { return this; }

    // -------- Kind checks (polymorphic) ----------
    public boolean isSlab()   { return this instanceof Slab; }
    public boolean isStairs() { return this instanceof Stairs; }

    public short getId() { return id; }
    public void setId(short id) { this.id = id; }
    public byte  getMetadata() { return metadata; }
    public void  setMetadata(byte m) { this.metadata = m; }


    // -------- Collision (base = full cube; air/water = empty) ----------
    public List<AABB> getCollisionBoxes() {
        if (type == BlockType.AIR || type == BlockType.WATER) return Collections.emptyList();
        return List.of(new AABB(0,0,0, 1,1,1));
    }
}
