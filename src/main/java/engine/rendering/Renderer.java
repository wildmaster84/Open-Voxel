package engine.rendering;

import engine.world.World;
import engine.world.Chunk;
import engine.rendering.FaceRenderer.FaceDirection;
import engine.rendering.FaceRenderer.FaceVertices;
import engine.world.Block;
import engine.world.BlockType;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class Renderer {
    private World world;
    private Camera camera;
    private ShaderProgram shader;

    private int vaoId;

    private int uProjection;
    private int uView;
    private int uModel;
    private int uBlockTexture;

    private final FloatBuffer matBuffer = MemoryUtil.memAllocFloat(16);
    private final Map<Long, ChunkMesh> meshCache = new HashMap<>();
    
    private final ExecutorService mesherPool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    private static final int MAX_UPDATES_PER_FRAME = 2;

    private final ConcurrentLinkedQueue<PendingMesh> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long, MeshState> meshStates = new ConcurrentHashMap<>();
    
    private enum MeshState { BUILDING, READY, GPU_LOADED }

    private static final class PendingMesh {
        final long key;
        final Map<Texture, float[]> perTextureVerts;
        PendingMesh(long key, Map<Texture, float[]> perTextureVerts) {
            this.key = key;
            this.perTextureVerts = perTextureVerts;
        }
    }

    public Renderer(World world, Camera camera) {
        this.world = world;
        this.camera = camera;
        setupGL();
        setupShaders();
        cacheUniforms();
    }
    
    private void setupGL() {
        GL11.glClearColor(0.6f, 0.6f, 0.6f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);

        GL30.glBindVertexArray(0);
    }

    private void setupShaders() {
        String vertexSrc =
                "#version 330 core\n" +
                "layout(location = 0) in vec3 position;\n" +
                "layout(location = 1) in vec2 texCoord;\n" +
                "out vec2 vTexCoord;\n" +
                "uniform mat4 projection;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 model;\n" +
                "void main() {\n" +
                "    gl_Position = projection * view * model * vec4(position, 1.0);\n" +
                "    vTexCoord = texCoord;\n" +
                "}";

        String fragmentSrc =
                "#version 330 core\n" +
                "in vec2 vTexCoord;\n" +
                "out vec4 FragColor;\n" +
                "uniform sampler2D blockTexture;\n" +
                "void main() {\n" +
                "    FragColor = texture(blockTexture, vTexCoord);\n" +
                "}";

        shader = new ShaderProgram(vertexSrc, fragmentSrc);
    }

    private void cacheUniforms() {
        shader.use();
        int program = shader.getProgramId();
        uProjection   = GL20.glGetUniformLocation(program, "projection");
        uView         = GL20.glGetUniformLocation(program, "view");
        uModel        = GL20.glGetUniformLocation(program, "model");
        uBlockTexture = GL20.glGetUniformLocation(program, "blockTexture");
        if (uBlockTexture >= 0) GL20.glUniform1i(uBlockTexture, 0);
        GL20.glUseProgram(0);
    }

    public void render() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        shader.use();

        Matrix4f projection = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        matBuffer.clear(); projection.get(matBuffer);
        GL20.glUniformMatrix4fv(uProjection, false, matBuffer);

        matBuffer.clear(); view.get(matBuffer);
        GL20.glUniformMatrix4fv(uView, false, matBuffer);

        matBuffer.clear(); new Matrix4f().identity().get(matBuffer);
        GL20.glUniformMatrix4fv(uModel, false, matBuffer);

        GL30.glBindVertexArray(vaoId);

        int updates = 0;
        while (updates < MAX_UPDATES_PER_FRAME) {
            PendingMesh pm = pendingUpdates.poll();
            if (pm == null) break;
            ChunkMesh mesh = createChunkMesh(pm);
            meshCache.put(pm.key, mesh);
            meshStates.put(pm.key, MeshState.GPU_LOADED);
            updates++;
        }

        int cameraChunkX = Math.floorDiv((int) camera.getPosition().x, Chunk.SIZE);
        int cameraChunkZ = Math.floorDiv((int) camera.getPosition().z, Chunk.SIZE);
        int renderRadius = camera.getRenderDistance();

        for (int cx = cameraChunkX - renderRadius; cx <= cameraChunkX + renderRadius; cx++) {
            for (int cz = cameraChunkZ - renderRadius; cz <= cameraChunkZ + renderRadius; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk == null) continue;

                long key = pack(cx, cz);
                ChunkMesh mesh = meshCache.get(key);
                MeshState state = meshStates.get(key);

                if (mesh == null && state == null) {
                    meshStates.put(key, MeshState.BUILDING);
                    final int fcx = cx, fcz = cz;
                    mesherPool.submit(() -> {
                        PendingMesh built = buildChunkMesh(fcx, fcz, chunk);
                        pendingUpdates.add(built);
                        meshStates.put(built.key, MeshState.READY);
                    });
                }

                if (mesh != null) {
                    mesh.draw();
                }
            }
        }

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        removeMesh(cameraChunkX, cameraChunkZ, renderRadius + 2);
    }

    public void invalidateChunk(int cx, int cz) {
        long key = pack(cx, cz);
        ChunkMesh mesh = meshCache.remove(key);
        if (mesh != null) mesh.delete();
        meshStates.remove(key);
        pendingUpdates.removeIf(pm -> pm.key == key);
    }

    public void clearAllMeshes() {
        for (ChunkMesh m : meshCache.values()) m.delete();
        meshCache.clear();
        meshStates.clear();
        pendingUpdates.clear();
    }

    public void cleanup() {
        shader.delete();
        try {
            for (BlockType block : BlockType.values()) {
                block.back.cleanup();
                block.bottom.cleanup();
                block.front.cleanup();
                block.left.cleanup();
                block.right.cleanup();
                block.top.cleanup();
            }
        } catch (NullPointerException ignored) {}

        clearAllMeshes();
        MemoryUtil.memFree(matBuffer);

        GL30.glDeleteVertexArrays(vaoId);

        mesherPool.shutdownNow();
    }


    private PendingMesh buildChunkMesh(int cx, int cz, Chunk chunk) {
        Map<Texture, FaceBatch> batches = new HashMap<>(32);
        final float baseX = cx * Chunk.SIZE;
        final float baseZ = cz * Chunk.SIZE;

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block == null) continue;
                    BlockType type = block.getType();
                    if (type == null || type == BlockType.AIR) continue;

                    float wx = baseX + x;
                    float wy = y;
                    float wz = baseZ + z;

                    for (int face = 0; face < 6; face++) {
                        if (!shouldRenderFace(world, cx, cz, x, y, z, FaceDirection.get(face))) continue;

                        Texture tex = type.getTextureForFace(face);
                        if (tex == null) continue;

                        FaceBatch fb = batches.get(tex);
                        if (fb == null) {
                            fb = new FaceBatch(256 * 6 * 5);
                            batches.put(tex, fb);
                        }
                        float[] verts = FaceVertices.get(face);
                        fb.addFace(wx, wy, wz, verts);
                    }
                }
            }
        }

        Map<Texture, float[]> perTex = new HashMap<>(batches.size());
        for (Map.Entry<Texture, FaceBatch> e : batches.entrySet()) {
            perTex.put(e.getKey(), e.getValue().toArray());
        }
        return new PendingMesh(pack(cx, cz), perTex);
    }

    private ChunkMesh createChunkMesh(PendingMesh pm) {
        ChunkMesh mesh = new ChunkMesh();
        for (Map.Entry<Texture, float[]> e : pm.perTextureVerts.entrySet()) {
            Texture tex = e.getKey();
            float[] verts = e.getValue();
            if (verts.length == 0) continue;

            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
            buf.put(verts).flip();

            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(buf);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            mesh.addBatch(tex, vbo, verts.length / 5);
        }
        return mesh;
    }

    private boolean shouldRenderFace(World world, int chunkX, int chunkZ, int x, int y, int z, int[] dir) {
        int nx = x + dir[0];
        int ny = y + dir[1];
        int nz = z + dir[2];

        int globalX = chunkX * Chunk.SIZE + nx;
        int globalY = ny;
        int globalZ = chunkZ * Chunk.SIZE + nz;

        int neighborChunkX = Math.floorDiv(globalX, Chunk.SIZE);
        int neighborChunkZ = Math.floorDiv(globalZ, Chunk.SIZE);
        int localX = Math.floorMod(globalX, Chunk.SIZE);
        int localY = globalY;
        int localZ = Math.floorMod(globalZ, Chunk.SIZE);

        Chunk neighborChunk = world.getChunk(neighborChunkX, neighborChunkZ);

        if (neighborChunk == null || localX < 0 || localX >= Chunk.SIZE || localY < 0 || localY >= Chunk.HEIGHT || localZ < 0 || localZ >= Chunk.SIZE)
            return false;
        Block neighbor = neighborChunk.getBlock(localX, localY, localZ);
        return neighbor == null || neighbor.getType() == BlockType.AIR;
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }


    private static final class FaceBatch {
        private float[] data;
        private int size;

        FaceBatch(int initialFloats) {
            data = new float[Math.max(initialFloats, 6 * 5)];
            size = 0;
        }

        void ensure(int moreFloats) {
            int need = size + moreFloats;
            if (need > data.length) {
                int newCap = Math.max(need, data.length + (data.length >> 1));
                float[] nd = new float[newCap];
                System.arraycopy(data, 0, nd, 0, size);
                data = nd;
            }
        }

        void addFace(float wx, float wy, float wz, float[] faceVerts) {
            ensure(faceVerts.length);
            for (int i = 0; i < faceVerts.length; i += 5) {
                data[size++] = faceVerts[i]     + wx;
                data[size++] = faceVerts[i + 1] + wy;
                data[size++] = faceVerts[i + 2] + wz;
                data[size++] = faceVerts[i + 3];
                data[size++] = faceVerts[i + 4];
            }
        }

        float[] toArray() {
            float[] out = new float[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }
    }

    private static final class ChunkMesh {
        private static final int STRIDE = 5 * Float.BYTES;

        private final Map<Texture, Integer> vbos = new HashMap<>();
        private final Map<Texture, Integer> counts = new HashMap<>();

        void addBatch(Texture texture, int vboId, int vertexCount) {
            vbos.put(texture, vboId);
            counts.put(texture, vertexCount);
        }

        void draw() {
            GL20.glEnableVertexAttribArray(0);
            GL20.glEnableVertexAttribArray(1);

            for (Map.Entry<Texture, Integer> e : vbos.entrySet()) {
                Texture tex = e.getKey();
                int vboId = e.getValue();
                int count = counts.get(tex);

                tex.bind();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);

                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
            }

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL20.glDisableVertexAttribArray(0);
            GL20.glDisableVertexAttribArray(1);
        }

        void delete() {
            for (int vbo : vbos.values()) GL15.glDeleteBuffers(vbo);
            vbos.clear();
            counts.clear();
        }
    }

    private void removeMesh(int centerCx, int centerCz, int radius) {
        int r2 = radius;
        meshCache.entrySet().removeIf(e -> {
            long key = e.getKey();
            int cx = (int) (key >> 32);
            int cz = (int) (key & 0xFFFFFFFFL);
            boolean far = Math.abs(cx - centerCx) > r2 || Math.abs(cz - centerCz) > r2;
            if (far) {
                e.getValue().delete();
                meshStates.remove(key);
                pendingUpdates.removeIf(pm -> pm.key == key);
            }
            return far;
        });
    }
}
