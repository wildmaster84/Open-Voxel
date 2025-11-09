package engine.events.player;

import engine.events.GameEvent;
import engine.world.AbstractBlock;

public class BlockBreakEvent extends GameEvent {
    public final AbstractBlock block;

    public BlockBreakEvent(AbstractBlock block) {
        this.block = block;
    }
}