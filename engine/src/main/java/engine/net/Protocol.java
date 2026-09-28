package engine.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Open-Voxel wire protocol v1. TCP, big-endian frames:
 *   [u32 payloadLength][u8 packetId][payload...]
 *
 * C→S  HELLO        0x01  u32 protocol, utf username, utf uuid, utf token, utf serverId
 * S→C  HELLO_ACK    0x02  u8 ok, utf message, f32 spawnX/Y/Z
 * S→C  CHUNK        0x03  i32 cx, i32 cz, u32 uncompLen, u32 compLen, zlib bytes
 *                         (state ints in index order (x*16+z)*256+y, deflated)
 * C→S  SET_BLOCK    0x04  i32 x, i32 y, i32 z, i32 state   (break = state 0)
 * S→C  BLOCK_UPDATE 0x05  i32 x, i32 y, i32 z, i32 state   (authoritative echo/broadcast)
 * C→S  PLAYER_POS   0x06  f32 x, f32 y, f32 z              (drives chunk streaming)
 * S→C  KICK         0x07  utf reason
 */
public final class Protocol {
    public static final int VERSION = 1;

    public static final int HELLO = 0x01;
    public static final int HELLO_ACK = 0x02;
    public static final int CHUNK = 0x03;
    public static final int SET_BLOCK = 0x04;
    public static final int BLOCK_UPDATE = 0x05;
    public static final int PLAYER_POS = 0x06;
    public static final int KICK = 0x07;
    public static final int PING = 0x08;
    public static final int PONG = 0x09;

    public static final int MAX_FRAME = 8 * 1024 * 1024; // 8MB sanity cap

    private Protocol() {}

    /** Writes one frame. payload[0] must be the packet id. */
    public static synchronized void writeFrame(DataOutputStream out, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    /** Reads one frame (blocking). Returns payload including the id byte. */
    public static byte[] readFrame(DataInputStream in) throws IOException {
        int len;
        try {
            len = in.readInt();
        } catch (EOFException e) {
            return null;
        }
        if (len <= 0 || len > MAX_FRAME) throw new IOException("bad frame length " + len);
        byte[] payload = new byte[len];
        in.readFully(payload);
        return payload;
    }

    /** Helper: growable little buffer for building payloads (big-endian). */
    public static final class PayloadWriter {
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(buf);

        public PayloadWriter id(int packetId) { u8(packetId); return this; }
        public PayloadWriter u8(int v) { try { out.writeByte(v); } catch (IOException e) { throw new RuntimeException(e); } return this; }
        public PayloadWriter i32(int v) { try { out.writeInt(v); } catch (IOException e) { throw new RuntimeException(e); } return this; }
        public PayloadWriter u32(long v) { return i32((int) (v & 0xffffffffL)); }
        public PayloadWriter f32(float v) { try { out.writeFloat(v); } catch (IOException e) { throw new RuntimeException(e); } return this; }
        public PayloadWriter utf(String s) { try { out.writeUTF(s == null ? "" : s); } catch (IOException e) { throw new RuntimeException(e); } return this; }
        public PayloadWriter bytes(byte[] b) { try { out.write(b); } catch (IOException e) { throw new RuntimeException(e); } return this; }
        public byte[] toArray() { return buf.toByteArray(); }
    }

    /** Reader over a received payload (position 0 = packet id, already consumed by caller). */
    public static final class PayloadReader {
        private final DataInputStream in;
        public PayloadReader(byte[] payload, int offset) {
            in = new DataInputStream(new java.io.ByteArrayInputStream(payload, offset, payload.length - offset));
        }
        public int u8() throws IOException { return in.readByte() & 0xff; }
        public int i32() throws IOException { return in.readInt(); }
        public long u32() throws IOException { return in.readInt() & 0xffffffffL; }
        public float f32() throws IOException { return in.readFloat(); }
        public String utf() throws IOException { return in.readUTF(); }
        public void bytes(byte[] dst) throws IOException { in.readFully(dst); }
    }
}
