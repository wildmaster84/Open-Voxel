package engine.world.block;

import engine.rendering.AnimatedTexture;
import engine.rendering.Texture;

import java.util.function.Function;

public enum BlockType {
    AIR(0, null, null, null, false, 0, 0, Block::new),
    BEDROCK(1, texture("textures/blocks/bedrock.png", false), true, 15, 0, Block::new),
    DIRT(2, texture("textures/blocks/dirt.png", false), true, 15, 0, Block::new),
    GRASS(3,texture("textures/blocks/grass_top.png", false), texture("textures/blocks/dirt.png", false), texture("textures/blocks/grass_side.png", false), true, 15, 0, Block::new),
    STONE(4, texture("textures/blocks/stone.png", false), true, 15, 0, Block::new),
    SAND(5, texture("textures/blocks/sand.png", false), true, 15, 0, Block::new),
    WATER(6, texture("textures/blocks/water_still.png", true), false, 1, 0, Block::new),
    SLAB(7, texture("textures/blocks/stone_slab_top.png", false), texture("textures/blocks/stone_slab_top.png", false), texture("textures/blocks/stone_slab_side.png", false), true, 15, 0, Slab::new),
    STAIR(8, texture("textures/blocks/stone.png", false), true, 15, 0, Stairs::new),
    GLASS(9, texture("textures/blocks/glass.png", false), false, 1, 0, Block::new);

    private final int id;

    public final Texture top, bottom, left, right, front, back;

    // --- NEW lighting fields ---
    // blocksSkyLight: true = stops the straight-down skylight column
    // lightOpacity: how much it attenuates light passing through (0..15)
    // lightEmission: block light source strength (0..15)
    private final boolean blocksSkyLight;
    private final int lightOpacity;
    private final int lightEmission;

    private final Function<BlockType, engine.world.AbstractBlock> factory;

    // all faces same texture
    BlockType(int id, Texture allFaces,
              boolean blocksSkyLight, int lightOpacity, int lightEmission,
              Function<BlockType, engine.world.AbstractBlock> factory) {
        this(id, allFaces, allFaces, allFaces, allFaces, allFaces, allFaces,
             blocksSkyLight, lightOpacity, lightEmission, factory);
    }

    // top / bottom / sides
    BlockType(int id, Texture top, Texture bottom, Texture sides,
              boolean blocksSkyLight, int lightOpacity, int lightEmission,
              Function<BlockType, engine.world.AbstractBlock> factory) {
        this(id, top, bottom, sides, sides, sides, sides,
             blocksSkyLight, lightOpacity, lightEmission, factory);
    }

    // full constructor
    BlockType(int id,
              Texture top, Texture bottom, Texture left, Texture right, Texture front, Texture back,
              boolean blocksSkyLight, int lightOpacity, int lightEmission,
              Function<BlockType, engine.world.AbstractBlock> factory) {
        this.id = id;
        this.top = top; this.bottom = bottom;
        this.left = left; this.right = right;
        this.front = front; this.back = back;
        this.blocksSkyLight = blocksSkyLight;
        this.lightOpacity = lightOpacity;
        this.lightEmission = lightEmission;
        this.factory = factory;
    }

    private static Texture texture(String path, boolean animated) {
        if (animated) return new AnimatedTexture(path, 0.2f);
        return new Texture(path);
    }

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

    // --- NEW lighting accessors ---

    /** Whether this block stops the vertical skylight column. */
    public boolean blocksSkyLight() {
        return blocksSkyLight;
    }

    /** How much this block attenuates light passing through (0..15). */
    public int getLightOpacity() {
        return lightOpacity;
    }

    /** How much block light this block emits (0..15). */
    public int getLightEmission() {
        return lightEmission;
    }

    // --- ID lookup (unchanged) ---

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
}
