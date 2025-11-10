package engine.world.block;

import engine.rendering.AnimatedTexture;
import engine.rendering.Texture;

import java.util.function.Function;

public enum BlockType {
    AIR(0,null,null,null,Block::new),
    BEDROCK(1,texture("textures/blocks/bedrock.png", false),Block::new),
    DIRT(2,texture("textures/blocks/dirt.png", false),Block::new),
    GRASS(3,texture("textures/blocks/grass_top.png", false),texture("textures/blocks/dirt.png", false),texture("textures/blocks/grass_side.png", false),Block::new),
    STONE(4,texture("textures/blocks/stone.png", false), Block::new),
    SAND(5,texture("textures/blocks/sand.png", false),Block::new),
    WATER(6,texture("textures/blocks/water_still.png", true),Block::new),
    SLAB(7,texture("textures/blocks/stone_slab_top.png", false),texture("textures/blocks/stone_slab_top.png", false),texture("textures/blocks/stone_slab_side.png", false),Slab::new),
    STAIR(8,texture("textures/blocks/stone.png", false),Stairs::new);

    private final int id;

    public final Texture top, bottom, left, right, front, back;
    private final Function<BlockType, engine.world.AbstractBlock> factory;

    BlockType(int id, Texture allFaces, Function<BlockType, engine.world.AbstractBlock> factory) {
        this(id, allFaces, allFaces, allFaces, allFaces, allFaces, allFaces, factory);
    }

    BlockType(int id, Texture top, Texture bottom, Texture sides, Function<BlockType, engine.world.AbstractBlock> factory) {
        this(id, top, bottom, sides, sides, sides, sides, factory);
    }

    BlockType(int id, Texture top, Texture bottom, Texture left, Texture right, Texture front, Texture back,
              Function<BlockType, engine.world.AbstractBlock> factory) {
        this.id = id;
        this.top = top; this.bottom = bottom;
        this.left = left; this.right = right;
        this.front = front; this.back = back;
        this.factory = factory;
    }

    private static Texture texture(String path, boolean animated) {
        if (animated) return new AnimatedTexture(path, 0.2f);  // 200ms per frame
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
