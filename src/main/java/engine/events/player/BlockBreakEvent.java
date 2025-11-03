package engine.events.player;

import engine.events.GameEvent;
import engine.world.Block;

public class BlockBreakEvent extends GameEvent {
    public final Block block;

    public BlockBreakEvent(Block block) {
        this.block = block;
    }
}