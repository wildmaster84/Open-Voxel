package engine.world;

import java.nio.ByteBuffer;
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
        // Snapshot old state to read:
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

    byte[] serialize() {
        byte version = 1;

        ByteBuffer bb = ByteBuffer
                .allocate(
                        1 + // version
                        1 + // direct
                        1 + // bitsPerBlock
                        2 + // palette size
                        inverse.size() * 4 +
                        4 + // data length
                        data.length * 8
                )
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);

        bb.put(version);
        bb.put((byte) (direct ? 1 : 0));
        bb.put((byte) bitsPerBlock);
        bb.putShort((short) inverse.size());
        for (int id : inverse) {
            bb.putInt(id);
        }
        bb.putInt(data.length);
        for (long l : data) {
            bb.putLong(l);
        }
        return bb.array();
    }

    static BlockSectionStorage deserialize(byte[] arr) {
        ByteBuffer bb = ByteBuffer.wrap(arr).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        byte version = bb.get();
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported BlockSectionStorage version: " + version);
        }

        boolean direct = bb.get() != 0;
        int bits = Byte.toUnsignedInt(bb.get());
        int palSize = Short.toUnsignedInt(bb.getShort());

        BlockSectionStorage s = new BlockSectionStorage();
        s.direct = direct;
        s.bitsPerBlock = bits;
        s.recalcPacking();

        s.palette.clear();
        s.inverse.clear();
        for (int i = 0; i < palSize; i++) {
            int id = bb.getInt();
            s.palette.put(id, i);
            s.inverse.add(id);
        }

        int len = bb.getInt();
        s.data = new long[len];
        for (int i = 0; i < len; i++) {
            s.data[i] = bb.getLong();
        }

        return s;
    }

}
