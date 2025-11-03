package engine.events.player;

import engine.events.GameEvent;

public class ClickEvent extends GameEvent {
    public enum ClickType { LEFT, MIDDLE, RIGHT }
    public final ClickType type;
    public final int worldX, worldY, worldZ;

    public ClickEvent(ClickType type, int worldX, int worldY, int worldZ) {
        this.type = type;
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
    }
}