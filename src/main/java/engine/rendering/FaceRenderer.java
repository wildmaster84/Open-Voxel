package engine.rendering;

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
        	case 0: {
        		return TOP.vertices;
        	}
        	case 1: {
        		return BOTTOM.vertices;
        	}
        	case 2: {
        		return LEFT.vertices;
        	}
        	case 3: {
        		return RIGHT.vertices;
        	}
        	case 4: {
        		return FRONT.vertices;
        	}
        	case 5: {
        		return BACK.vertices;
        	}
        	default: {
        		return new float[]{};
        	}
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
        	case 0: {
        		return TOP.toArray();
        	}
        	case 1: {
        		return BOTTOM.toArray();
        	}
        	case 2: {
        		return LEFT.toArray();
        	}
        	case 3: {
        		return RIGHT.toArray();
        	}
        	case 4: {
        		return FRONT.toArray();
        	}
        	case 5: {
        		return BACK.toArray();
        	}
        	default: {
        		return new int[]{};
        	}
        	}
        }
    }
}
