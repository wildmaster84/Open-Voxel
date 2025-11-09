package engine.world;

import engine.physics.PhysicsEngine.AABB;
import engine.world.block.Block;
import engine.world.block.BlockState;
import engine.world.block.BlockType;
import engine.world.block.Slab;
import engine.world.block.Stairs;

import java.util.Collections;
import java.util.List;

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
    
    public int getId() { return type.getId();}

    public boolean isSlab() { return type == BlockType.SLAB; }
    public int slabKind()   { return BlockState.slabKind(state); }              // 0=bottom,1=top,2=double
    public boolean isSlabBottom() { return isSlab() && slabKind() == BlockState.SLAB_KIND_BOTTOM; }
    public boolean isSlabTop()    { return isSlab() && slabKind() == BlockState.SLAB_KIND_TOP; }
    public boolean isSlabDouble() { return isSlab() && slabKind() == BlockState.SLAB_KIND_DOUBLE; }

    public boolean isStairs()        { return type == BlockType.STAIR; }
    public int stairsFacing()        { return BlockState.stairsFacing(state); } // 0=E,1=W,2=S,3=N (matches BlockState docs)
    public boolean stairsUpsideDown(){ return BlockState.stairsUpside(state); }

    public List<AABB> getCollisionBoxes() {
        if (type == BlockType.AIR || type == BlockType.WATER) return Collections.emptyList();
        return List.of(new AABB(0,0,0, 1,1,1));
    }
    
    public static AbstractBlock fromState(int packedState) {
        int tid = BlockState.typeId(packedState);

        if (tid == BlockType.SLAB.getId()) {
            return new Slab(packedState);
        }
        if (tid == BlockType.STAIR.getId()) {
            return new Stairs(packedState);
        }

        // default full cube
        return new Block(packedState);
    }
}
