package engine.world;

import java.io.IOException;

public class Chunk {
    public static final int SIZE = 16;
    public static final int HEIGHT = 512;

    private final int chunkX, chunkZ;
    private static final int SECTION_COUNT = (HEIGHT + SIZE - 1) / SIZE;

    private final BlockSectionStorage[] sections = new BlockSectionStorage[SECTION_COUNT];
    
    private transient boolean dirty = false;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        for (int i = 0; i < SECTION_COUNT; i++) sections[i] = new BlockSectionStorage();
    }
    
    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }

    private static void checkBounds(int x, int y, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < 0 || y >= HEIGHT) {
            throw new IndexOutOfBoundsException("x,y,z out of range: " + x + "," + y + "," + z);
        }
    }

    private BlockSectionStorage sec(int y) {
        int si = y / SIZE;
        return sections[si];
    }

    public AbstractBlock getBlock(int x, int y, int z) {
        checkBounds(x, y, z);
        int ly = y % SIZE;
        int state = sec(y).getId(x, ly, z);
        return new AbstractBlock(state);
    }

    public void setBlock(int x, int y, int z, AbstractBlock block) {
        checkBounds(x, y, z);
        int ly = y % SIZE;
        sec(y).setId(x, ly, z, block.getState());
        dirty = true;
    }

    public void fillChunk(AbstractBlock block) {
        for (BlockSectionStorage s : sections) s.fill(block.getId());
        dirty = true;
    }
    
    public void fill(int x, int z, int y0, int y1, AbstractBlock block) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return;

        if (y0 > y1) {
            int t = y0; y0 = y1; y1 = t;
        }

        if (y1 < 0 || y0 >= HEIGHT) return;
        if (y0 < 0) y0 = 0;
        if (y1 >= HEIGHT) y1 = HEIGHT - 1;

        final int state = block.getState();

        int s0 = y0 >>> 4;
        int s1 = y1 >>> 4;

        for (int s = s0; s <= s1; s++) {
            BlockSectionStorage sec = sections[s];
            int ly0 = (s == s0) ? (y0 & 15) : 0;
            int ly1 = (s == s1) ? (y1 & 15) : 15;
            sec.fillColumn(x, z, ly0, ly1, state);
        }

        dirty = true;
    }

    private BlockSectionStorage sectionFor(int y) {
        return sections[y >>> 4];
    }
    
    public int getState(int x, int y, int z) {
        return sectionFor(y).getId(x, y & 15, z);
    }
    public void setState(int x, int y, int z, int state) {
        sectionFor(y).setId(x, y & 15, z, state);
        dirty = true;
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    
    public int[] toStateArray() {
        int[] arr = new int[SIZE * SIZE * HEIGHT];
        int idx = 0;

        for (int y = 0; y < HEIGHT; y++) {
            BlockSectionStorage section = sections[y >>> 4];
            int ly = y & 15;

            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    arr[idx++] = section.getId(x, ly, z);
                }
            }
        }
        return arr;
    }
    
    public void loadFromStateArray(int[] arr) {
        int idx = 0;

        for (int y = 0; y < HEIGHT; y++) {
            BlockSectionStorage section = sections[y >>> 4];
            int ly = y & 15;

            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    section.setId(x, ly, z, arr[idx++]);
                }
            }
        }
    }
    
    public void write(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(SECTION_COUNT);
        for (int i = 0; i < SECTION_COUNT; i++) {
            byte[] secBytes = sections[i].serialize();
            out.writeInt(secBytes.length);
            out.write(secBytes);
        }
    }

    public void read(java.io.DataInput in) throws java.io.IOException {
        int count = in.readInt();
        if (count != SECTION_COUNT) {
            throw new IOException("Mismatched section count: " + count + " (expected " + SECTION_COUNT + ")");
        }
        for (int i = 0; i < SECTION_COUNT; i++) {
            int len = in.readInt();
            byte[] secBytes = new byte[len];
            in.readFully(secBytes);
            sections[i] = BlockSectionStorage.deserialize(secBytes);
        }
    }
}
