package engine.rendering;

import engine.world.AbstractBlock.Facing;

public class FaceRenderer {
	public enum Faces {
	    TOP,
	    BOTTOM,
	    NORTH,
	    EAST,
	    WEST,
	    SOUTH
	}

	public enum FaceVertices {
        TOP(new float[]{0,1,0,0,0,1,1,0,1,0,1,1,1,1,1,0,1,0,0,0,1,1,1,1,1,0,1,1,0,1}),
        BOTTOM(new float[]{0,0,0,0,0,1,0,0,1,0,1,0,1,1,1,0,0,0,0,0,1,0,1,1,1,0,0,1,0,1}),
        LEFT(new float[]{0,0,0,0,0,0,0,1,1,0,0,1,1,1,1,0,0,0,0,0,0,1,1,1,1,0,1,0,0,1}),
        RIGHT(new float[]{1,0,0,0,0,1,0,1,1,0,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1}),
        FRONT(new float[]{0,0,0,0,0,1,0,0,1,0,1,1,0,1,1,0,0,0,0,0,1,1,0,1,1,0,1,0,0,1}),
        BACK(new float[]{0,0,1,0,0,1,0,1,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,1,0,1});
    	
    	private final float[] vertices;

        FaceVertices(float[] vertices) {
            this.vertices = vertices;
        }

        public static float[] get(int face) {
        	switch(face) {
        	case 0: return TOP.vertices;
        	case 1: return BOTTOM.vertices;
        	case 2: return LEFT.vertices;
        	case 3: return RIGHT.vertices;
        	case 4: return FRONT.vertices;
        	case 5: return BACK.vertices;
        	default: return new float[]{};
        	}
        }
    };

    public enum FaceDirection {
        TOP(0, 1, 0),
        BOTTOM(0, -1, 0),
        LEFT(-1, 0, 0),
        RIGHT(1, 0, 0),
        FRONT(0, 0, -1),
        BACK(0, 0, 1);

        public final int dx, dy, dz;

        FaceDirection(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int[] toArray() {
            return new int[] { dx, dy, dz };
        }
        public static int[] get(int face) {
        	switch(face) {
        	case 0: return TOP.toArray();
        	case 1: return BOTTOM.toArray();
        	case 2: return LEFT.toArray();
        	case 3: return RIGHT.toArray();
        	case 4: return FRONT.toArray();
        	case 5: return BACK.toArray();
        	default: return new int[]{};
        	}
        }
    }

    // Write one quad directly into a 30-float array (two triangles), given 4 corners + UVs.
    private static float[] writeQuad(
            float x0,float y0,float z0, float u0,float v0,
            float x1,float y1,float z1, float u1,float v1,
            float x2,float y2,float z2, float u2,float v2,
            float x3,float y3,float z3, float u3,float v3
    ) {
        float[] o = new float[30];
        int i=0;
        // tri 0: 0,1,2
        o[i++]=x0; o[i++]=y0; o[i++]=z0; o[i++]=u0; o[i++]=v0;
        o[i++]=x1; o[i++]=y1; o[i++]=z1; o[i++]=u1; o[i++]=v1;
        o[i++]=x2; o[i++]=y2; o[i++]=z2; o[i++]=u2; o[i++]=v2;
        // tri 1: 0,2,3
        o[i++]=x0; o[i++]=y0; o[i++]=z0; o[i++]=u0; o[i++]=v0;
        o[i++]=x2; o[i++]=y2; o[i++]=z2; o[i++]=u2; o[i++]=v2;
        o[i++]=x3; o[i++]=y3; o[i++]=z3; o[i++]=u3; o[i++]=v3;
        return o;
    }

    public static java.util.List<float[]> slabQuads(boolean topHalf) {
        float y0 = topHalf ? 0.5f : 0.0f;
        float y1 = y0 + 0.5f;

        java.util.ArrayList<float[]> quads = new java.util.ArrayList<>(6);

        // Top (uv = xz) — use full 0..1 in Z
        quads.add(writeQuad(
            0f, y1, 1f,  0f, 1f,
            1f, y1, 1f,  1f, 1f,
            1f, y1, 0f,  1f, 0f,
            0f, y1, 0f,  0f, 0f
        ));

        // Bottom (uv = xz)
        quads.add(writeQuad(
            0f, y0, 0f,  0f, 0f,
            1f, y0, 0f,  1f, 0f,
            1f, y0, 1f,  1f, 1f,
            0f, y0, 1f,  0f, 1f
        ));

        // -X side (uv = zy)
        quads.add(writeQuad(
            0f, y0, 0f,  0f, y0,
            0f, y0, 1f,  1f, y0,
            0f, y1, 1f,  1f, y1,
            0f, y1, 0f,  0f, y1
        ));
        // +X side (uv = zy)
        quads.add(writeQuad(
            1f, y0, 1f,  1f, y0,
            1f, y0, 0f,  0f, y0,
            1f, y1, 0f,  0f, y1,
            1f, y1, 1f,  1f, y1
        ));
        // -Z side (uv = xy)
        quads.add(writeQuad(
            1f, y0, 0f,  1f, y0,
            0f, y0, 0f,  0f, y0,
            0f, y1, 0f,  0f, y1,
            1f, y1, 0f,  1f, y1
        ));
        // +Z side (uv = xy)
        quads.add(writeQuad(
            0f, y0, 1f,  0f, y0,
            1f, y0, 1f,  1f, y0,
            1f, y1, 1f,  1f, y1,
            0f, y1, 1f,  0f, y1
        ));

        return quads;
    }

    
    public static java.util.List<float[]> stairQuads(Facing facing, boolean upsideDown) {
        final float y0a = upsideDown ? 0.5f : 0.0f; // lower part start
        final float y1a = upsideDown ? 1.0f : 0.5f; // lower part end
        final float y0b = upsideDown ? 0.0f : 0.5f; // upper part start (lower when upsideDown)
        final float y1b = upsideDown ? 0.5f : 1.0f; // upper part end

        java.util.ArrayList<float[]> quads = new java.util.ArrayList<>(13);

        // ---------- Piece A: full XZ, height 0.5 ----------
        // Top of A (uv = xz)
        quads.add(writeQuad(
            0f,y1a,1f, 0f,1f,
            1f,y1a,1f, 1f,1f,
            1f,y1a,0f, 1f,0f,
            0f,y1a,0f, 0f,0f
        ));
        // Bottom of A (uv = xz)
        quads.add(writeQuad(
            0f,y0a,0f, 0f,0f,
            1f,y0a,0f, 1f,0f,
            1f,y0a,1f, 1f,1f,
            0f,y0a,1f, 0f,1f
        ));
        // -X side of A (uv = zy)
        quads.add(writeQuad(
            0f,y0a,0f, 0f,y0a,
            0f,y0a,1f, 1f,y0a,
            0f,y1a,1f, 1f,y1a,
            0f,y1a,0f, 0f,y1a
        ));
        // +X side of A (uv = zy)
        quads.add(writeQuad(
            1f,y0a,1f, 1f,y0a,
            1f,y0a,0f, 0f,y0a,
            1f,y1a,0f, 0f,y1a,
            1f,y1a,1f, 1f,y1a
        ));
        // -Z side of A (uv = xy)
        quads.add(writeQuad(
            1f,y0a,0f, 1f,y0a,
            0f,y0a,0f, 0f,y0a,
            0f,y1a,0f, 0f,y1a,
            1f,y1a,0f, 1f,y1a
        ));
        // +Z side of A (uv = xy)
        quads.add(writeQuad(
            0f,y0a,1f, 0f,y0a,
            1f,y0a,1f, 1f,y0a,
            1f,y1a,1f, 1f,y1a,
            0f,y1a,1f, 0f,y1a
        ));

        // ---------- Piece B: back half ----------
        float x0=0f, x1=1f, z0=0f, z1=1f;
        switch (facing) {
            case SOUTH: /* +Z */ z0 = 0.5f; z1 = 1f; break;
            case NORTH: /* -Z */ z0 = 0.0f; z1 = 0.5f; break;
            case EAST: /* +X */ x0 = 0.5f; x1 = 1f; break;
            case WEST: /* -X */ x0 = 0.0f; x1 = 0.5f; break;
        }

        // Top of B (uv = xz)
        quads.add(writeQuad(
            x0,y1b,z1, x0,z1,
            x1,y1b,z1, x1,z1,
            x1,y1b,z0, x1,z0,
            x0,y1b,z0, x0,z0
        ));

        // Bottom of B: 
        // - Skip for normal (not upsideDown) to avoid coplanar z-fight at y = 0.5 with A's top.
        // - Emit for upsideDown so the very bottom (y = 0.0) is visible.
        if (upsideDown) {
            quads.add(writeQuad(
                x0,y0b,z0, x0,z0,
                x1,y0b,z0, x1,z0,
                x1,y0b,z1, x1,z1,
                x0,y0b,z1, x0,z1
            ));
        }

        // Vertical sides of B (always emit; neighbor culling hides internals):
        // -X (uv = zy)
        quads.add(writeQuad(
            x0,y0b,z0, z0,y0b,
            x0,y0b,z1, z1,y0b,
            x0,y1b,z1, z1,y1b,
            x0,y1b,z0, z0,y1b
        ));
        // +X (uv = zy)
        quads.add(writeQuad(
            x1,y0b,z1, z1,y0b,
            x1,y0b,z0, z0,y0b,
            x1,y1b,z0, z0,y1b,
            x1,y1b,z1, z1,y1b
        ));
        // -Z (uv = xy)
        quads.add(writeQuad(
            x1,y0b,z0, x1,y0b,
            x0,y0b,z0, x0,y0b,
            x0,y1b,z0, x0,y1b,
            x1,y1b,z0, x1,y1b
        ));
        // +Z (uv = xy)
        quads.add(writeQuad(
            x0,y0b,z1, x0,y0b,
            x1,y0b,z1, x1,y0b,
            x1,y1b,z1, x1,y1b,
            x0,y1b,z1, x0,y1b
        ));

        return quads;
    }

}
