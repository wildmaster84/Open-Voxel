package engine.world;

import java.util.*;

final class BlockSectionStorage {
    static final int SX = 16, SY = 16, SZ = 16, COUNT = SX * SY * SZ;
    private static final int MIN_BITS = 4;
    private static final int DIRECT_BITS = 32;

    private int bitsPerBlock;
    private boolean direct;
    private long[] data;
    private final Map<Integer,Integer> palette = new HashMap<>();
    private final ArrayList<Integer> inverse = new ArrayList<>();

    private int entriesPerLong;
    private long valueMask;

    BlockSectionStorage() {
        this.bitsPerBlock = MIN_BITS;
        this.direct = false;
        this.entriesPerLong = Math.max(1, 64 / bitsPerBlock);
        this.valueMask = (bitsPerBlock == 64) ? ~0L : ((1L << bitsPerBlock) - 1L);
        int longsNeeded = (int)Math.ceil((double)COUNT / entriesPerLong);
        this.data = new long[longsNeeded];

        palette.put(0, 0);
        inverse.add(0);
    }
    
    BlockSectionStorage(boolean skipInit) {
        this.bitsPerBlock = 0;
        this.direct = false;
        this.entriesPerLong = 0;
        this.valueMask = 0L;
        this.data = new long[0];
    }

    private static int idx(int x, int y, int z) {
    	return (y * SZ + z) * SX + x;
    }
    
    private static void checkLocal(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= SX || y >= SY || z >= SZ) {
            throw new IndexOutOfBoundsException("Local out of range x=" + x + " y=" + y + " z=" + z);
        }
    }

    int getId(int x, int y, int z) {
        checkLocal(x, y, z);
        int index = readIndex(idx(x, y, z));
        return direct ? index : inverse.get(index);
    }

    void setId(int x, int y, int z, int id) {
        checkLocal(x, y, z);
        if (id < 0) throw new IllegalArgumentException("Negative block ID: " + id);

        int index;
        if (!direct) {
            Integer got = palette.get(id);
            if (got == null) {
                got = palette.size();
                palette.put(id, got);
                inverse.add(id);

                int needBits = Math.max(MIN_BITS, ceilLog2(inverse.size()));
                if (needBits > bitsPerBlock) {
                    if (needBits > 24) {
                        switchToDirect();
                    } else {
                        resizeBits(needBits);
                    }
                }
            }
            index = got;
        } else {
            if ((id & ~((1 << DIRECT_BITS) - 1)) != 0) {
                throw new IllegalArgumentException("ID too large for direct mode: " + id);
            }
            index = id;
        }

        writeIndex(idx(x, y, z), index);
    }

    void fill(int id) {
        if (!direct) {
            Integer got = palette.get(id);
            if (got == null) {
                palette.clear();
                inverse.clear();
                palette.put(id, 0);
                inverse.add(id);
            } else if (got != 0) {
                int id0 = inverse.get(0);
                inverse.set(0, id);
                inverse.set(got, id0);
                palette.put(id, 0);
                palette.put(id0, got);
            }
            Arrays.fill(data, 0L);
        } else {
            int entry = id;
            for (int i = 0; i < COUNT; i++) writeIndex(i, entry);
        }
    }
    
    void fillColumn(int x, int z, int y0, int y1, int id) {
        if (y0 < 0) y0 = 0;
        if (y1 >= SY) y1 = SY - 1;
        if (y0 > y1) return;

        int linear = idx(x, y0, z);
        final int step = SX * SZ;

        for (int y = y0; y <= y1; y++) {
            setIdUncheckedIndex(linear, id);
            linear += step;
        }
    }
    
    void setIdUncheckedIndex(int linearIndex, int id) {
        int paletteIndex;

        if (!direct) {
            Integer got = palette.get(id);
            if (got == null) {
                int next = palette.size();
                palette.put(id, next);
                inverse.add(id);

                int needBits = Math.max(MIN_BITS, ceilLog2(inverse.size()));
                if (needBits > bitsPerBlock) {
                    if (needBits > 24) {
                        switchToDirect();
                        // after switch, direct mode uses raw IDs
                        paletteIndex = id;
                        writeIndex(linearIndex, paletteIndex);
                        return;
                    } else {
                        resizeBits(needBits);
                    }
                }
                paletteIndex = next;
            } else {
                paletteIndex = got;
            }
        } else {
            paletteIndex = id;
        }

        writeIndex(linearIndex, paletteIndex);
    }


    private void recalcPacking() {
        this.entriesPerLong = Math.max(1, 64 / bitsPerBlock);
        this.valueMask = (bitsPerBlock == 64) ? ~0L : ((1L << bitsPerBlock) - 1L);
    }

    private void resizeBits(int newBits) {
        int oldBits = this.bitsPerBlock;
        if (newBits == oldBits) return;

        long[] oldData = this.data;
        int oldEntriesPerLong = this.entriesPerLong;

        this.bitsPerBlock = newBits;
        recalcPacking();
        int longsNeeded = (int)Math.ceil((double)COUNT / entriesPerLong);
        this.data = new long[longsNeeded];

        for (int i = 0; i < COUNT; i++) {
            int oldWord = i / oldEntriesPerLong;
            int oldOffset = (i % oldEntriesPerLong) * oldBits;
            long v = (oldData[oldWord] >>> oldOffset) & ((oldBits == 64) ? ~0L : ((1L << oldBits) - 1L));
            writeIndex(i, (int)v);
        }
    }

    private void switchToDirect() {
        this.direct = true;
        this.bitsPerBlock = DIRECT_BITS;
        recalcPacking();

        int oldBitsPerBlock = Math.max(MIN_BITS, ceilLog2(inverse.size()));
        int snapBits = oldBitsPerBlock;
        int snapEntriesPerLong = Math.max(1, 64 / snapBits);
        long snapMask = (snapBits == 64) ? ~0L : ((1L << snapBits) - 1L);
        long[] snap = this.data;

        this.bitsPerBlock = DIRECT_BITS;
        recalcPacking();
        this.data = new long[(int)Math.ceil((double)COUNT / entriesPerLong)];

        for (int i = 0; i < COUNT; i++) {
            int w = i / snapEntriesPerLong;
            int o = (i % snapEntriesPerLong) * snapBits;
            long paletteIndex = (snap[w] >>> o) & snapMask;
            int realId = inverse.get((int)paletteIndex);
            writeIndex(i, realId);
        }

        palette.clear();
        inverse.clear();
    }

    private int readIndex(int i) {
        int word = i / entriesPerLong;
        int offset = (i % entriesPerLong) * bitsPerBlock;
        long v = (data[word] >>> offset) & valueMask;
        return (int) v;
    }

    private void writeIndex(int i, int value) {
        int word = i / entriesPerLong;
        int offset = (i % entriesPerLong) * bitsPerBlock;
        long mask = ~(valueMask << offset);
        data[word] = (data[word] & mask) | ((long)value & valueMask) << offset;
    }

    private static int ceilLog2(int n) {
        if (n <= 1) return 1;
        return 32 - Integer.numberOfLeadingZeros(n - 1);
    }
    
    private static void writeShortLE(byte[] arr, int pos, int v) {
        arr[pos    ] = (byte) (v       & 0xFF);
        arr[pos + 1] = (byte) ((v >>> 8) & 0xFF);
    }

    private static void writeIntLE(byte[] arr, int pos, int v) {
        arr[pos    ] = (byte) (v        & 0xFF);
        arr[pos + 1] = (byte) ((v >>>  8) & 0xFF);
        arr[pos + 2] = (byte) ((v >>> 16) & 0xFF);
        arr[pos + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    private static void writeLongLE(byte[] arr, int pos, long v) {
        arr[pos    ] = (byte) (v        & 0xFFL);
        arr[pos + 1] = (byte) ((v >>>  8) & 0xFFL);
        arr[pos + 2] = (byte) ((v >>> 16) & 0xFFL);
        arr[pos + 3] = (byte) ((v >>> 24) & 0xFFL);
        arr[pos + 4] = (byte) ((v >>> 32) & 0xFFL);
        arr[pos + 5] = (byte) ((v >>> 40) & 0xFFL);
        arr[pos + 6] = (byte) ((v >>> 48) & 0xFFL);
        arr[pos + 7] = (byte) ((v >>> 56) & 0xFFL);
    }


    byte[] serialize() {
        byte version = 1;

        int palSize = inverse.size();
        int len = data.length;

        int capacity =
                1 + // version
                1 + // direct
                1 + // bitsPerBlock
                2 + // palette size
                palSize * 4 +
                4 + // data length
                len * 8;

        byte[] out = new byte[capacity];
        int pos = 0;

        out[pos++] = version;
        out[pos++] = (byte) (direct ? 1 : 0);
        out[pos++] = (byte) bitsPerBlock;

        writeShortLE(out, pos, palSize);
        pos += 2;

        for (int i = 0; i < palSize; i++) {
            int id = inverse.get(i);
            writeIntLE(out, pos, id);
            pos += 4;
        }

        writeIntLE(out, pos, len);
        pos += 4;

        for (int i = 0; i < len; i++) {
            writeLongLE(out, pos, data[i]);
            pos += 8;
        }

        return out;
    }

    private static int readShortLE(byte[] arr, int pos) {
        return (arr[pos] & 0xFF) | ((arr[pos + 1] & 0xFF) << 8);
    }

    private static int readIntLE(byte[] arr, int pos) {
        return (arr[pos] & 0xFF)
             | ((arr[pos + 1] & 0xFF) << 8)
             | ((arr[pos + 2] & 0xFF) << 16)
             | ((arr[pos + 3] & 0xFF) << 24);
    }

    private static long readLongLE(byte[] arr, int pos) {
        return ((long)arr[pos]       & 0xFFL)
             | (((long)arr[pos + 1] & 0xFFL) << 8)
             | (((long)arr[pos + 2] & 0xFFL) << 16)
             | (((long)arr[pos + 3] & 0xFFL) << 24)
             | (((long)arr[pos + 4] & 0xFFL) << 32)
             | (((long)arr[pos + 5] & 0xFFL) << 40)
             | (((long)arr[pos + 6] & 0xFFL) << 48)
             | (((long)arr[pos + 7] & 0xFFL) << 56);
    }

    static BlockSectionStorage deserialize(byte[] arr) {
        int pos = 0;

        byte version = arr[pos++];
        if (version != 1)
            throw new IllegalArgumentException("Unsupported BlockSectionStorage version: " + version);

        boolean direct = arr[pos++] != 0;
        int bits = arr[pos++] & 0xFF;

        int palSize = readShortLE(arr, pos);
        pos += 2;

        BlockSectionStorage s = new BlockSectionStorage(true);
        s.direct = direct;
        s.bitsPerBlock = bits;
        s.recalcPacking();

        s.palette.clear();
        s.inverse.clear();
        s.inverse.ensureCapacity(palSize);

        for (int i = 0; i < palSize; i++) {
            int id = readIntLE(arr, pos);
            pos += 4;
            s.palette.put(id, i);
            s.inverse.add(id);
        }

        int dataLen = readIntLE(arr, pos);
        pos += 4;

        s.data = new long[dataLen];

        // Read longs
        for (int i = 0; i < dataLen; i++) {
            s.data[i] = readLongLE(arr, pos);
            pos += 8;
        }

        return s;
    }

}
