package engine.world;

import java.io.Serializable;

public class Block implements Serializable {
    public enum Type { AIR, STONE, DIRT, GRASS }
    private final Type type;

    public Block(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}