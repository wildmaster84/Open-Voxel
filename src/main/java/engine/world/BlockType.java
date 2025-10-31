package engine.world;

import engine.rendering.Texture;

public enum BlockType {
    // We should do this in a more organized way...
	AIR(null, null, null, null, null, null),
    DIRT(new Texture("textures/dirt.png"), new Texture("textures/dirt.png"), new Texture("textures/dirt.png"), new Texture("textures/dirt.png"), new Texture("textures/dirt.png"), new Texture("textures/dirt.png")),
    GRASS(new Texture("textures/grass_top.png"), new Texture("textures/dirt.png"), new Texture("textures/grass_side.png"), new Texture("textures/grass_side.png"), new Texture("textures/grass_side.png"), new Texture("textures/grass_side.png")),
    STONE(new Texture("textures/stone.png"), new Texture("textures/stone.png"), new Texture("textures/stone.png"), new Texture("textures/stone.png"), new Texture("textures/stone.png"), new Texture("textures/stone.png"));

    public final Texture top, bottom, left, right, front, back;

    BlockType(Texture top, Texture bottom, Texture left, Texture right, Texture front, Texture back) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
        this.front = front;
        this.back = back;
    }

    public Texture getTextureForFace(int face) {
        switch(face) {
            case 0: return top;
            case 1: return bottom;
            case 2: return left;
            case 3: return right;
            case 4: return front;
            case 5: return back;
            default: return top;
        }
    }
}