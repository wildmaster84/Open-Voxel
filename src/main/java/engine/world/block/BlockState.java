package engine.world.block;

public final class BlockState {
    private BlockState() {}

    public static final int TYPE_BITS  = 12;
    public static final int TYPE_MASK  = (1 << TYPE_BITS) - 1;

    public static int typeId(int state) { return state & TYPE_MASK; }
    public static int make(int typeId)  { return typeId & TYPE_MASK; }

    private static final int P0_SHIFT = TYPE_BITS;
    private static final int P1_SHIFT = TYPE_BITS + 1; // May use for corner stairs
    private static final int P2_SHIFT = TYPE_BITS + 2;

    public static final int SLAB_KIND_BOTTOM = 0;
    public static final int SLAB_KIND_TOP    = 1;
    public static final int SLAB_KIND_DOUBLE = 2;

    public static int slabKind(int state) { return (state >> P0_SHIFT) & 0b11; }
    public static int asSlab(int baseState, int kind) {
        int s = (baseState & TYPE_MASK);
        return s | ((kind & 0b11) << P0_SHIFT);
    }

    public static final int FACING_EAST  = 2;
    public static final int FACING_WEST  = 3;
    public static final int FACING_SOUTH = 1;
    public static final int FACING_NORTH = 0;

    public static int stairsFacing(int state)  { return (state >> P0_SHIFT) & 0b11; }
    public static boolean stairsUpside(int state) { return ((state >> P2_SHIFT) & 0b1) != 0; }

    public static int asStairs(int baseState, int facing, boolean upsideDown) {
        int s = (baseState & TYPE_MASK);
        s |= (facing & 0b11) << P0_SHIFT;
        s |= (upsideDown ? 1 : 0) << P2_SHIFT;
        return s;
    }
}
