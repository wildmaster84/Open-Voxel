package engine.world.block;

import engine.physics.PhysicsEngine.AABB;
import engine.rendering.FaceRenderer;
import engine.world.AbstractBlock.Facing;

import java.util.List;

/**
 * Geometry + collision shape of a block. All block behavior that depends on
 * the physical shape lives here, so adding a new block only means picking a
 * shape (or adding one here) instead of special-casing renderers/physics.
 */
public enum BlockShape {
    CUBE {
        @Override public float[][] quads(int state) { return CUBE_QUADS; }
        @Override public List<AABB> collisionBoxes(int state) { return FULL_CUBE; }
        @Override public boolean occludes(int state) { return true; }
    },
    SLAB {
        @Override public float[][] quads(int state) {
            int kind = BlockState.slabKind(state);
            if (kind == BlockState.SLAB_KIND_DOUBLE) return CUBE_QUADS;
            return (kind == BlockState.SLAB_KIND_TOP) ? SLAB_TOP_QUADS : SLAB_BOTTOM_QUADS;
        }
        @Override public List<AABB> collisionBoxes(int state) {
            int kind = BlockState.slabKind(state);
            if (kind == BlockState.SLAB_KIND_DOUBLE) return FULL_CUBE;
            return (kind == BlockState.SLAB_KIND_TOP) ? TOP_HALF : BOTTOM_HALF;
        }
        @Override public boolean occludes(int state) {
            return BlockState.slabKind(state) == BlockState.SLAB_KIND_DOUBLE;
        }
    },
    STAIRS {
        @Override public float[][] quads(int state) {
            return stairQuads(BlockState.stairsFacing(state), BlockState.stairsUpside(state));
        }
        @Override public List<AABB> collisionBoxes(int state) {
            return stairBoxes(BlockState.stairsFacing(state), BlockState.stairsUpside(state));
        }
        @Override public boolean occludes(int state) { return false; }
    };

    /** Baked quads: each quad is 6 vertices x 5 floats (x,y,z,u,v). */
    public abstract float[][] quads(int state);
    /** Local [0..1] collision boxes. */
    public abstract List<AABB> collisionBoxes(int state);
    /** Whether this state hides the neighboring block's face against it. */
    public abstract boolean occludes(int state);

    private static final List<AABB> FULL_CUBE  = List.of(new AABB(0, 0, 0, 1, 1, 1));
    private static final List<AABB> BOTTOM_HALF = List.of(new AABB(0, 0, 0, 1, 0.5f, 1));
    private static final List<AABB> TOP_HALF    = List.of(new AABB(0, 0.5f, 0, 1, 1, 1));

    private static final float[][] CUBE_QUADS = {
        FaceRenderer.FaceVertices.get(0), FaceRenderer.FaceVertices.get(1),
        FaceRenderer.FaceVertices.get(2), FaceRenderer.FaceVertices.get(3),
        FaceRenderer.FaceVertices.get(4), FaceRenderer.FaceVertices.get(5)
    };

    private static final float[][] SLAB_BOTTOM_QUADS = toArrays(FaceRenderer.slabQuads(false));
    private static final float[][] SLAB_TOP_QUADS    = toArrays(FaceRenderer.slabQuads(true));

    // 4 facings x 2 up/down variants, precomputed.
    private static final float[][][] STAIR_QUADS = new float[8][][];
    private static final List<AABB>[] STAIR_BOXES = buildStairBoxes();

    static {
        for (int facing = 0; facing < 4; facing++) {
            STAIR_QUADS[facing * 2 + 0] = toArrays(FaceRenderer.stairQuads(quadFacing(facing), false));
            STAIR_QUADS[facing * 2 + 1] = toArrays(FaceRenderer.stairQuads(quadFacing(facing), true));
        }
    }

    private static float[][] stairQuads(int facing, boolean upsideDown) {
        return STAIR_QUADS[(facing & 3) * 2 + (upsideDown ? 1 : 0)];
    }

    private static List<AABB> stairBoxes(int facing, boolean upsideDown) {
        return STAIR_BOXES[(facing & 3) * 2 + (upsideDown ? 1 : 0)];
    }

    /** Same facing->visual mapping the renderer previously used. */
    private static Facing quadFacing(int f) {
        switch (f) {
            case BlockState.FACING_EAST:  return Facing.WEST;
            case BlockState.FACING_WEST:  return Facing.EAST;
            case BlockState.FACING_SOUTH: return Facing.NORTH;
            default:                      return Facing.SOUTH;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<AABB>[] buildStairBoxes() {
        List<AABB>[] boxes = new List[8];
        for (int facing = 0; facing < 4; facing++) {
            for (int upside = 0; upside < 2; upside++) {
                boolean up = upside == 1;
                float y0a = up ? 0.5f : 0.0f, y1a = up ? 1.0f : 0.5f;
                float y0b = up ? 0.0f : 0.5f, y1b = up ? 0.5f : 1.0f;

                float x0 = 0f, x1 = 1f, z0 = 0f, z1 = 1f;
                switch (facing) {
                    case 3:  x0 = 0.5f; x1 = 1f;   break;
                    case 2:  x0 = 0.0f; x1 = 0.5f; break;
                    case 0:  z0 = 0.5f; z1 = 1f;   break;
                    case 1:  z0 = 0.0f; z1 = 0.5f; break;
                }

                boxes[facing * 2 + upside] = List.of(
                    new AABB(0f, y0a, 0f, 1f, y1a, 1f),
                    new AABB(x0, y0b, z0, x1, y1b, z1)
                );
            }
        }
        return boxes;
    }

    private static float[][] toArrays(List<float[]> quads) {
        float[][] out = new float[quads.size()][];
        for (int i = 0; i < quads.size(); i++) out[i] = quads.get(i);
        return out;
    }
}
