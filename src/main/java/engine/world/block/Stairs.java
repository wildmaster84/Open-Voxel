package engine.world.block;

import engine.physics.PhysicsEngine.AABB;
import engine.world.AbstractBlock;

import java.util.List;

public class Stairs extends AbstractBlock {
    private boolean upsideDown = stairsUpsideDown();

    public Stairs(int packedState) {
        super(packedState);
    }
    public Stairs(BlockType type) {
        super(type);
    }

    public void setUpsideDown(boolean v) { this.upsideDown = v; }
    public boolean isUpsideDown() { return upsideDown; }

    @Override
    public List<AABB> getCollisionBoxes() {
        final boolean upsideDown = this.isUpsideDown();
        final float y0a = upsideDown ? 0.5f : 0.0f;
        final float y1a = upsideDown ? 1.0f : 0.5f;
        final float y0b = upsideDown ? 0.0f : 0.5f;
        final float y1b = upsideDown ? 0.5f : 1.0f;

        float x0 = 0f, x1 = 1f, z0 = 0f, z1 = 1f;
        
        switch (stairsFacing()) {
	        case 3:  x0 = 0.5f; x1 = 1f; break;
	        case 2:  x0 = 0.0f; x1 = 0.5f; break;
	        case 0: z0 = 0.5f; z1 = 1f; break;
	        case 1: z0 = 0.0f; z1 = 0.5f; break;
	    }

        AABB A = new AABB(0f, y0a, 0f, 1f, y1a, 1f);
        AABB B = new AABB(x0, y0b, z0, x1, y1b, z1);

        return List.of(A, B);
    }


}
