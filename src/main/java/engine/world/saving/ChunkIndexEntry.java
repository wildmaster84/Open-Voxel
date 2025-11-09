package engine.world.saving;

public class ChunkIndexEntry {
    public final int cx;
	public final int cz;
    public long offset;
    public int length;

    public ChunkIndexEntry(int cx, int cz, long offset, int length) {
        this.cx = cx;
        this.cz = cz;
        this.offset = offset;
        this.length = length;
    }

    public long key() {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }
}
