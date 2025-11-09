package engine.world;

import engine.rendering.AnimatedTexture;
import engine.rendering.Texture;
import engine.world.block.Block;
import engine.world.block.Slab;
import engine.world.block.Stairs;

import java.util.function.Function;

public enum BlockType {
    AIR(null, null, null, null, null, null, Block::new),
    BEDROCK(texture("textures/bedrock.png", false), Block::new),
    DIRT(texture("textures/dirt.png", false), Block::new),
    GRASS(texture("textures/grass_top.png", false), texture("textures/dirt.png", false), texture("textures/grass_side.png", false), Block::new),
    STONE(texture("textures/stone.png", false), Block::new),
    SAND(texture("textures/sand.png", false), Block::new),
    WATER(texture("textures/water_still.png", true), Block::new),
    SLAB(texture("textures/stone_slab_top.png", false), texture("textures/stone_slab_top.png", false), texture("textures/stone_slab_side.png", false), Slab::new),
    STAIR(texture("textures/stone.png", false), Stairs::new);

    public final Texture top, bottom, left, right, front, back;
    private final Function<BlockType, engine.world.AbstractBlock> factory;

    BlockType(Texture top, Texture bottom, Texture left, Texture right, Texture front, Texture back, Function<BlockType, engine.world.AbstractBlock> factory) {
        this.top = top; this.bottom = bottom;
        this.left = left; this.right = right;
        this.front = front; this.back = back;
        this.factory = factory;
    }
    
    BlockType(Texture top, Texture bottom, Texture sides, Function<BlockType, engine.world.AbstractBlock> factory) {
        this(top, bottom, sides, sides, sides, sides, factory);
    }

    BlockType(Texture allFaces, Function<BlockType, engine.world.AbstractBlock> factory) {
        this(allFaces, allFaces, allFaces, allFaces, allFaces, allFaces, factory);
    }

    private static Texture texture(String path, boolean animated) {
    	if (animated) return new AnimatedTexture(path, 5f);
        return new Texture(path);
    }

    public Texture getTextureForFace(int face) {
        switch(face) {
            case 0: return top;
            case 1: return bottom;
            case 2: return left;
            case 3: return right;
            case 4: return front;
            case 5: return back;
            default: return null;
        }
    }
}
