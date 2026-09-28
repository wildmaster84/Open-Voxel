package engine.world;

import java.io.IOException;

public class Chunk {
    public static final int SIZE = 16;
    public static final int HEIGHT = 512;

    private final int chunkX, chunkZ;
    private static final int SECTION_COUNT = (HEIGHT + SIZE - 1) / SIZE;

    /** Skylight packed as 4-bit nibbles (values 0..15): 64KB instead of 128KB. */
    private final byte[] skyLight = new byte[SIZE * HEIGHT * SIZE / 2];
    /** Block-light (torches/lava) packed as 4-bit nibbles. */
    private final byte[] blockLight = new byte[SIZE * HEIGHT * SIZE / 2];

    /** Null section = all air. Allocated lazily on first non-air write.
     *  At HEIGHT=512, most columns have ~26 pure-air sections above terrain. */
    private final BlockSectionStorage[] sections = new BlockSectionStorage[SECTION_COUNT];

    private transient boolean dirty = false;

    /** Bumped on every block change. Lets the renderer drop stale in-flight meshes. */
    private volatile int blockVersion = 0;
    public int blockVersion() { return blockVersion; }
    public void bumpBlockVersion() { blockVersion++; }

    /**
     * Combined light-input version: bumped whenever anything that can change
     * this chunk's light happens ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â block edits (bumpBlockVersion callers),
     * skylight republish, or a neighbor's light spreading in. The mesher
     * skips the full-column relight when this is unchanged since last build.
     */
    private volatile int lightVersion = 0;
    public int lightVersion() { return lightVersion; }
    public void bumpLightVersion() { lightVersion++; }

    /** Mesher bookkeeping: combined light-input version at last mesh build. */
    private volatile int lastBuiltLightInputs = Integer.MIN_VALUE;
    public int lastBuiltLightInputs() { return lastBuiltLightInputs; }
    public void setLastBuiltLightInputs(int v) { lastBuiltLightInputs = v; }

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        // Sections allocated lazily - saves ~300MB at render distance 32.
    }

    public boolean isDirty() { return dirty; }

    private static void checkBounds(int x, int y, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < 0 || y >= HEIGHT) {
            throw new IndexOutOfBoundsException("x,y,z out of range: " + x + "," + y + "," + z);
        }
    }

    private BlockSectionStorage secRead(int y) {
        return sections[y >>> 4];
    }

    private BlockSectionStorage secWrite(int y) {
        int si = y >>> 4;
        BlockSectionStorage s = sections[si];
        if (s == null) {
            s = new BlockSectionStorage();
            sections[si] = s;
        }
        return s;
    }

    public AbstractBlock getBlock(int x, int y, int z) {
        checkBounds(x, y, z);
        BlockSectionStorage s = secRead(y);
        int state = (s != null) ? s.getId(x, y & 15, z) : 0;
        return new AbstractBlock(state);
    }

    public void setBlock(int x, int y, int z, AbstractBlock block) {
        checkBounds(x, y, z);
        secWrite(y).setId(x, y & 15, z, block.getState());
        dirty = true;
        blockVersion++;
        lightVersion++;
    }

    public void fill(int x, int z, int y0, int y1, AbstractBlock block) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return;
        if (y0 > y1) { int t = y0; y0 = y1; y1 = t; }
        if (y1 < 0 || y0 >= HEIGHT) return;
        if (y0 < 0) y0 = 0;
        if (y1 >= HEIGHT) y1 = HEIGHT - 1;

        final int state = block.getState();
        int s0 = y0 >>> 4, s1 = y1 >>> 4;
        for (int s = s0; s <= s1; s++) {
            BlockSectionStorage sec = secWrite(s << 4);
            int ly0 = (s == s0) ? (y0 & 15) : 0;
            int ly1 = (s == s1) ? (y1 & 15) : 15;
            sec.fillColumn(x, z, ly0, ly1, state);
        }
        dirty = true;
        blockVersion++;
        lightVersion++;
    }

    public int getState(int x, int y, int z) {
        BlockSectionStorage s = secRead(y);
        return (s != null) ? s.getId(x, y & 15, z) : 0;
    }

    public void setState(int x, int y, int z, int state) {
        secWrite(y).setId(x, y & 15, z, state);
        dirty = true;
        blockVersion++;
        lightVersion++;
    }

    private static int lightCellIdx(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    /** Packed skylight read (0..15). */
    public byte getSkyLight(int x, int y, int z) {
        int c = lightCellIdx(x, y, z);
        int bi = c >> 1;
        int b = skyLight[bi] & 0xFF;
        return (byte) (((c & 1) == 0) ? (b >>> 4) : (b & 15));
    }

    /** Packed skylight write (0..15). */
    public void setSkyLight(int x, int y, int z, int level) {
        int c = lightCellIdx(x, y, z);
        int v = Math.max(0, Math.min(15, level));
        int bi = c >> 1;
        int b = skyLight[bi] & 0xFF;
        skyLight[bi] = (byte) (((c & 1) == 0) ? ((v << 4) | (b & 15)) : ((b & 0xF0) | v));
    }

    /** Packed block-light read (0..15). */
    public byte getBlockLight(int x, int y, int z) {
        int c = lightCellIdx(x, y, z);
        int bi = c >> 1;
        int b = blockLight[bi] & 0xFF;
        return (byte) (((c & 1) == 0) ? (b >>> 4) : (b & 15));
    }

    /** Packed block-light write (0..15). */
    public void setBlockLight(int x, int y, int z, int level) {
        int c = lightCellIdx(x, y, z);
        int v = Math.max(0, Math.min(15, level));
        int bi = c >> 1;
        int b = blockLight[bi] & 0xFF;
        blockLight[bi] = (byte) (((c & 1) == 0) ? ((v << 4) | (b & 15)) : ((b & 0xF0) | v));
    }

    /** Publishes a full-res block-light working buffer into the packed array. */
    public void publishBlockLight(byte[] working) {
        boolean changed = false;
        for (int i = 0; i < blockLight.length; i++) {
            int hi = working[i * 2] & 15;
            int lo = working[i * 2 + 1] & 15;
            byte packed = (byte) ((hi << 4) | lo);
            if (blockLight[i] != packed) {
                blockLight[i] = packed;
                changed = true;
            }
        }
        if (changed) bumpLightVersion();
    }

    /**
     * Publishes a full-res skylight working buffer (131072 bytes, values 0..15)
     * into the packed array. Bumps the version only when data changed.
     * Owned by LightEngine - avoids 128KB allocation per mesh.
     */
    public void publishSkylight(byte[] working) {
        boolean changed = false;
        for (int i = 0; i < skyLight.length; i++) {
            int hi = working[i * 2] & 15;
            int lo = working[i * 2 + 1] & 15;
            byte packed = (byte) ((hi << 4) | lo);
            if (skyLight[i] != packed) {
                skyLight[i] = packed;
                changed = true;
            }
        }
        if (changed) skylightVersion++;
        if (changed) bumpLightVersion();
    }

    private volatile int skylightVersion = 0;
    public int skylightVersion() { return skylightVersion; }
    public void bumpSkylightVersion() { skylightVersion++; }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    private static byte[] AIR_SECTION_BYTES;
    private static synchronized byte[] airSectionBytes() {
        if (AIR_SECTION_BYTES == null) {
            AIR_SECTION_BYTES = new BlockSectionStorage().serialize();
        }
        return AIR_SECTION_BYTES;
    }

    public void write(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(SECTION_COUNT);
        for (int i = 0; i < SECTION_COUNT; i++) {
            byte[] secBytes = (sections[i] != null) ? sections[i].serialize() : airSectionBytes();
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
