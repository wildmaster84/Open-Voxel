package engine.world.saving;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import engine.world.Chunk;

public class SaveManager {
    private final File worldDir  = new File("./world");
    private final File worldFile = new File(worldDir, "world.dat");

    private static final String WORLD_MAGIC = "WVLD";
    private static final int    WORLD_VER   = 1;

    private static final int HEADER_SIZE = 4 + 4 + 8;

    private final Object ioLock = new Object();

    private final Map<Long, ChunkIndexEntry> index = new HashMap<>();
    private boolean indexLoaded = false;

    private void ensureWorldFile() throws IOException {
        if (!worldDir.exists()) worldDir.mkdirs();
        if (!worldFile.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(worldFile, "rw")) {
                raf.seek(0);
                raf.writeBytes(WORLD_MAGIC);
                raf.writeInt(WORLD_VER);
                raf.writeLong(0L);
            }
        }
    }

    private void verifyHeader(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] magic = new byte[4];
        raf.readFully(magic);
        String m = new String(magic, java.nio.charset.StandardCharsets.US_ASCII);
        if (!WORLD_MAGIC.equals(m)) {
            throw new IOException("Bad world magic: " + m);
        }
        int ver = raf.readInt();
        if (ver != WORLD_VER) {
            throw new IOException("Unsupported world version: " + ver);
        }
        raf.readLong();
    }

    private void buildIndexIfNeeded(RandomAccessFile raf) throws IOException {
        if (indexLoaded) return;

        index.clear();
        verifyHeader(raf);

        long fileLen = raf.length();
        long pos = HEADER_SIZE;
        raf.seek(pos);

        while (pos + 12 <= fileLen) {
            raf.seek(pos);
            int cx, cz, len;
            try {
                cx = raf.readInt();
                cz = raf.readInt();
                len = raf.readInt();
            } catch (EOFException eof) {
                break;
            }

            if (len <= 0) {
                break;
            }

            long blobOffset = raf.getFilePointer();
            long nextPos = blobOffset + len;

            if (nextPos > fileLen) {
                break;
            }

            ChunkIndexEntry e = new ChunkIndexEntry(cx, cz, blobOffset, len);
            index.put(e.key(), e);

            pos = nextPos;
        }

        indexLoaded = true;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }

    public void saveChunk(Chunk chunk) throws IOException {
        synchronized (ioLock) {
            ensureWorldFile();

            try (RandomAccessFile raf = new RandomAccessFile(worldFile, "rw")) {
                buildIndexIfNeeded(raf);

                int cx = chunk.getChunkX();
                int cz = chunk.getChunkZ();
                long key = chunkKey(cx, cz);

                byte[] blob;
                {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (DataOutputStream dos = new DataOutputStream(
                             new java.util.zip.GZIPOutputStream(baos))) {

                        dos.writeInt(cx);
                        dos.writeInt(cz);
                        chunk.write(dos);
                    }
                    blob = baos.toByteArray();
                }
                int newLen = blob.length;

                ChunkIndexEntry entry = index.get(key);

                if (entry != null && newLen <= entry.length) {
                    raf.seek(entry.offset);
                    raf.write(blob);
                    entry.length = newLen;
                } else {
                    long recordStart = raf.length();
                    raf.seek(recordStart);

                    raf.writeInt(cx);
                    raf.writeInt(cz);
                    raf.writeInt(newLen);

                    long blobOffset = raf.getFilePointer();
                    raf.write(blob);

                    ChunkIndexEntry newEntry = new ChunkIndexEntry(cx, cz, blobOffset, newLen);
                    index.put(key, newEntry);
                }
            }
        }
    }

    public Chunk loadChunk(int cx, int cz) {
        synchronized (ioLock) {
            try {
                ensureWorldFile();
                try (RandomAccessFile raf = new RandomAccessFile(worldFile, "r")) {
                    buildIndexIfNeeded(raf);

                    long key = chunkKey(cx, cz);
                    ChunkIndexEntry entry = index.get(key);
                    if (entry == null) {
                        return null;
                    }

                    raf.seek(entry.offset);

                    byte[] blob = new byte[entry.length];
                    raf.readFully(blob);

                    java.io.InputStream base = new ByteArrayInputStream(blob);

                    boolean isGzip = blob.length >= 2 &&
                                     (blob[0] & 0xFF) == 0x1f &&
                                     (blob[1] & 0xFF) == 0x8b;

                    try (DataInputStream dis = new DataInputStream(
                             isGzip ? new java.util.zip.GZIPInputStream(base) : base)) {

                        int fileCx = dis.readInt();
                        int fileCz = dis.readInt();
                        if (fileCx != cx || fileCz != cz) {
                            throw new IOException("Chunk coords mismatch in blob: " +
                                                  fileCx + "," + fileCz +
                                                  " expected " + cx + "," + cz);
                        }
                        Chunk chunk = new Chunk(cx, cz);
                        chunk.read(dis);
                        return chunk;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
