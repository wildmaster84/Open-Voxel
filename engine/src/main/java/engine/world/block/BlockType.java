package engine.world.block;

import engine.rendering.AnimatedTexture;
import engine.rendering.Texture;

/**
 * Property-driven block registry. Adding a new block is ONE enum constant:
 *   MY_BLOCK(nextId, texture("textures/blocks/my_block.png"), BlockShape.CUBE)
 * — rendering, lighting, physics, meshing and picking all derive from the
 * properties below.
 */
public enum BlockType {
    AIR    (0, null, BlockShape.CUBE) {
        @Override public boolean occludesLight()   { return false; }
    },
    BEDROCK(1, texture("textures/blocks/bedrock.png"), BlockShape.CUBE),
    DIRT   (2, texture("textures/blocks/dirt.png"), BlockShape.CUBE),
    GRASS  (3, texture("textures/blocks/grass_top.png"), texture("textures/blocks/dirt.png"), texture("textures/blocks/grass_side.png"), BlockShape.CUBE),
    STONE  (4, texture("textures/blocks/stone.png"), BlockShape.CUBE),
    SAND   (5, texture("textures/blocks/sand.png"), BlockShape.CUBE),
    WATER  (6, texture("textures/blocks/water_still.png", true), BlockShape.CUBE) {
        @Override public boolean isLiquid()        { return true; }
        @Override public RenderLayer renderLayer() { return RenderLayer.TRANSLUCENT; }
        @Override public int lightOpacity()       { return 2; }
    },
    SLAB   (7, texture("textures/blocks/stone_slab_top.png"), texture("textures/blocks/stone_slab_top.png"), texture("textures/blocks/stone_slab_side.png"), BlockShape.SLAB) {
        @Override public boolean occludesLight()   { return false; }
    },
    STAIR  (8, texture("textures/blocks/stone.png"), BlockShape.STAIRS) {
        @Override public boolean occludesLight()   { return false; }
    },
    GLASS  (9, texture("textures/blocks/glass.png"), BlockShape.CUBE) {
        @Override public RenderLayer renderLayer() { return RenderLayer.TRANSLUCENT; }
        @Override public boolean occludesLight()   { return false; }
        // TEMP: emissive placeholder to test block light (acts as glowstone).
        @Override public int lightEmission()      { return 14; }
    };

    private final int id;

    public final Texture top, bottom, left, right, front, back;
    private final BlockShape shape;

    BlockType(int id, Texture allFaces, BlockShape shape) {
        this(id, allFaces, allFaces, allFaces, shape);
    }

    BlockType(int id, Texture top, Texture bottom, Texture sides, BlockShape shape) {
        this(id, top, bottom, sides, sides, sides, sides, shape);
    }

    BlockType(int id, Texture top, Texture bottom, Texture left, Texture right, Texture front, Texture back,
              BlockShape shape) {
        this.id = id;
        this.top = top; this.bottom = bottom;
        this.left = left; this.right = right;
        this.front = front; this.back = back;
        this.shape = shape;
    }

    // ---- properties (override in enum constants when needed) ----

    /** Blocks skylight propagation. */
    public boolean occludesLight() { return true; }
    /** How much skylight is lost passing through this block (0 = transparent). */
    public int lightOpacity() { return occludesLight() ? 15 : 0; }
    /** Light this block emits (0 = none). */
    public int lightEmission() { return 0; }
    /** Player collides with it. */
    public boolean isSolid()       { return this != AIR && !isLiquid(); }
    /** Water-like: swimming physics, no collision, lowered top surface. */
    public boolean isLiquid()      { return false; }
    /** Which render pass draws it. */
    public RenderLayer renderLayer() { return RenderLayer.OPAQUE; }

    public BlockShape getShape() { return shape; }

    // ---- helpers ----

    public Texture getTextureForFace(int face) {
        switch (face) {
            case 0: return top;
            case 1: return bottom;
            case 2: return left;
            case 3: return right;
            case 4: return front;
            case 5: return back;
            default: return null;
        }
    }

    private static Texture texture(String path) {
        return new Texture(path);
    }

    /**
     * Recreates any face textures whose GL ids were deleted by renderer
     * teardown. Enum constants hold Texture instances for the JVM's lifetime,
     * so they must survive world exit/re-enter cycles. No-op when live.
     */
    public static void reloadTextures() {
        for (BlockType b : values()) {
            if (b.back != null) b.back.reload();
            if (b.bottom != null) b.bottom.reload();
            if (b.front != null) b.front.reload();
            if (b.left != null) b.left.reload();
            if (b.right != null) b.right.reload();
            if (b.top != null) b.top.reload();
        }
    }

    private static Texture texture(String path, boolean animated) {
        if (animated) return new AnimatedTexture(path, 0.2f);
        return new Texture(path);
    }

    private static final BlockType[] ID_LOOKUP;
    static {
        BlockType[] vals = values();
        int maxId = 0;
        for (BlockType bt : vals) maxId = Math.max(maxId, bt.id);
        BlockType[] tmp = new BlockType[maxId + 1];
        for (BlockType bt : vals) tmp[bt.id] = bt;
        ID_LOOKUP = tmp;
    }

    public static BlockType fromId(int id) {
        if (id < 0 || id >= ID_LOOKUP.length) return AIR; // safe fallback
        BlockType bt = ID_LOOKUP[id];
        return bt != null ? bt : AIR;
    }

    public int getId() {
        return id;
    }

    /** Render pass for chunk meshes. */
    public enum RenderLayer { OPAQUE, TRANSLUCENT }
}
