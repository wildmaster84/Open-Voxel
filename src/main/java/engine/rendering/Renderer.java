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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class Renderer {
    private World world;
    private Camera camera;
    private ShaderProgram shader;
    private ShaderProgram skyShader;
    private ShaderProgram overlayShader;

    private int vaoId;

    private int uProjection, uView, uModel, uBlockTexture, uSunlight, uSkyProjection, uSkyView, uSkyTime, uSkySunDir, uSkyMoonDir, uSkyStars, uOverlayStrength, uOverlayColor;
    private int skyVao = 0, skyVbo = 0, overlayVao = 0, overlayVbo = 0;

    private final FloatBuffer matBuffer = MemoryUtil.memAllocFloat(16);
    private final Map<Long, ChunkMesh> meshCache = new HashMap<>();
    private final ExecutorService mesherPool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    private static final int MAX_UPDATES_PER_FRAME = 2;
    private final ConcurrentLinkedQueue<PendingMesh> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long, MeshState> meshStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, int[][]> radiusOffsetCache = new ConcurrentHashMap<>();
    private static final int MAX_BUILDS_PER_FRAME = 8;
    private static float delta = 0;

    private float timeOfDay01 = 0f;
    private static final float DAY_LENGTH_SEC = 24f * 60f; // 24 minutes full cycle

    private enum MeshState { BUILDING, READY, GPU_LOADED }

    /** NOW: holds separate opaque & translucent (water) batches */
    private final class PendingMesh {
        final long key;
        final Map<Texture, float[]> opaque;
        final Map<Texture, float[]> translucent;
        PendingMesh(long key, Map<Texture, float[]> opaque, Map<Texture, float[]> translucent) {
            this.key = key;
            this.opaque = opaque;
            this.translucent = translucent;
        }
    }

    public Renderer(World world, Camera camera) {
        this.world = world;
        this.camera = camera;
        setupGL();
        setupShaders();
        cacheUniforms();
        setupSkybox();
        setupUnderwaterOverlay();
    }

    private static final int[][] NORM = {
        { 0, 1, 0}, { 0,-1, 0}, { 0, 0, 1}, { 0, 0,-1}, {-1, 0, 0}, { 1, 0, 0}
    };
    private static final int[][] UAX = {
        { 1, 0, 0}, { 1, 0, 0}, { 1, 0, 0}, { 1, 0, 0}, { 0, 0, 1}, { 0, 0, 1}
    };
    private static final int[][] VAX = {
        { 0, 0, 1}, { 0, 0, 1}, { 0, 1, 0}, { 0, 1, 0}, { 0, 1, 0}, { 0, 1, 0}
    };
    private static final int[][] CORNER_SIGNS = {
        {-1,-1,  1,-1,  1, 1, -1, 1},
        {-1,-1,  1,-1,  1, 1, -1, 1},
        {-1,-1,  1,-1,  1, 1, -1, 1},
        {-1,-1,  1,-1,  1, 1, -1, 1},
        {-1,-1,  1,-1,  1, 1, -1, 1},
        {-1,-1,  1,-1,  1, 1, -1, 1}
    };

    private static boolean isSolid(Block b) {
        if (b == null) return false;
        BlockType t = b.getType();
        return t != null && t != BlockType.AIR && t != BlockType.WATER;
    }

    private float cornerAO(World world, int gx, int gy, int gz, int face, int cornerIndex) {
        int nx = NORM[face][0], ny = NORM[face][1], nz = NORM[face][2];
        int ux = UAX[face][0],  uy = UAX[face][1],  uz = UAX[face][2];
        int vx = VAX[face][0],  vy = VAX[face][1],  vz = VAX[face][2];

        int uSign = CORNER_SIGNS[face][cornerIndex*2 + 0];
        int vSign = CORNER_SIGNS[face][cornerIndex*2 + 1];

        int sx1x = gx + uSign*ux, sx1y = gy + uSign*uy, sx1z = gz + uSign*uz;
        int sx2x = gx + vSign*vx, sx2y = gy + vSign*vy, sx2z = gz + vSign*vz;
        int scx  = gx + uSign*ux + vSign*vx, scy  = gy + uSign*uy + vSign*vy, scz  = gz + uSign*uz + vSign*vz;

        boolean s1 = isSolid(world.getBlock(sx1x, sx1y, sx1z));
        boolean s2 = isSolid(world.getBlock(sx2x, sx2y, sx2z));
        boolean sc = isSolid(world.getBlock(scx , scy , scz ));

        int occ = (s1?1:0) + (s2?1:0) + (sc?1:0);
        if (s1 && s2)      return 0.40f;
        if (occ == 2)      return 0.60f;
        if (occ == 1)      return 0.80f;
        return 1.00f;
    }

    private static float dayAmount(float t) {
        double phase = t - 0.25; // noon at 0.25
        double c = Math.cos(2.0 * Math.PI * phase);
        return (float)((c + 1.0) * 0.5); // 0..1
    }

    private void setupGL() {
        GL11.glClearColor(0.6f, 0.6f, 0.6f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        final int STRIDE = 6 * Float.BYTES;

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);

        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);

        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);

        GL30.glBindVertexArray(0);
    }

    private void setupShaders() {
        String vertexSrc =
                "#version 330 core\n" +
                "layout(location = 0) in vec3 position;\n" +
                "layout(location = 1) in vec2 texCoord;\n" +
                "layout(location = 2) in float ao;\n" +
                "out vec2 vTexCoord;\n" +
                "out float vAO;\n" +
                "uniform mat4 projection;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 model;\n" +
                "uniform float yOffset;\n" +
                "void main() {\n" +
                "    vec4 worldPos = vec4(position, 1.0);\n"+
                "    worldPos.y += yOffset;\n" +
                "    gl_Position = projection * view * model * worldPos;\n" +
                "    vTexCoord = texCoord;\n" +
                "    vAO = ao;\n" +
                "}";

        String fragmentSrc =
                "#version 330 core\n" +
                "in vec2 vTexCoord;\n" +
                "in float vAO;\n" +
                "out vec4 FragColor;\n" +
                "uniform sampler2D blockTexture;\n" +
                "uniform float uFrameOffset;\n" +
                "uniform float uFrameScale;\n" +
                "uniform float uSunlight;\n" +
                "void main() {\n" +
                "    vec2 coord = vec2(vTexCoord.x, vTexCoord.y * uFrameScale + uFrameOffset);\n" +
                "    vec4 tex = texture(blockTexture, coord);\n" +
                "    float base = mix(0.12, 0.95, clamp(uSunlight, 0.0, 1.0));\n" +
                "    float lit = base * vAO;\n" +
                "    FragColor = vec4(tex.rgb * lit, tex.a);\n" +
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
        uSunlight     = GL20.glGetUniformLocation(program, "uSunlight");
        if (uBlockTexture >= 0) GL20.glUniform1i(uBlockTexture, 0);
        GL20.glUseProgram(0);
    }

    private void setupSkybox() {
        float[] verts = {
            1,-1,-1,  1,-1, 1,  1, 1, 1,   1,-1,-1,  1, 1, 1,  1, 1,-1,
           -1,-1,-1, -1, 1, 1, -1,-1, 1,  -1,-1,-1, -1, 1,-1, -1, 1, 1,
           -1, 1,-1,  1, 1, 1,  1, 1,-1,  -1, 1,-1, -1, 1, 1,  1, 1, 1,
           -1,-1,-1,  1,-1,-1,  1,-1, 1,  -1,-1,-1,  1,-1, 1, -1,-1, 1,
           -1,-1, 1, -1, 1, 1,  1, 1, 1,  -1,-1, 1,  1, 1, 1,  1,-1, 1,
           -1,-1,-1,  1, 1,-1, -1, 1,-1,  -1,-1,-1,  1,-1,-1,  1, 1,-1
        };

        String vsrc =
            "#version 330 core\n"
          + "layout(location=0) in vec3 aPos;\n"
          + "out vec3 vDir;\n"
          + "uniform mat4 projection;\n"
          + "uniform mat4 view;\n"
          + "void main(){\n"
          + "  vDir = aPos;\n"
          + "  mat4 viewNoTrans = mat4(mat3(view));\n"
          + "  vec4 pos = projection * viewNoTrans * vec4(aPos, 1.0);\n"
          + "  gl_Position = vec4(pos.xy, pos.w, pos.w);\n"
          + "}";

        String fsrc =
        	    "#version 330 core\n"
        	  + "in vec3 vDir;\n"
        	  + "out vec4 FragColor;\n"
        	  + "uniform float uTime;\n"
        	  + "uniform vec3  uSunDir;\n"
        	  + "uniform vec3  uMoonDir;\n"
        	  + "uniform float uStars;\n"
        	  + "float dayAmount(float t){\n"
        	  + "  float c = cos(6.2831853 * (fract(t) - 0.25));\n"
        	  + "  return clamp((c+1.0)*0.5, 0.0, 1.0);\n"
        	  + "}\n"
        	  + "float hash(vec3 p){\n"
        	  + "  p = fract(p * 0.3183099 + vec3(0.1,0.2,0.3));\n"
        	  + "  p += dot(p, p.yzx + 19.19);\n"
        	  + "  return fract(p.x * p.y * p.z * 93.533);\n"
        	  + "}\n"
        	  + "float disc(vec3 dir, vec3 dirC, float r){\n"
        	  + "  float d = acos(clamp(dot(dir, dirC), -1.0, 1.0));\n"
        	  + "  float edge = 0.003;\n"
        	  + "  return smoothstep(r, r - edge, d);\n"
        	  + "}\n"
        	  + "void main(){\n"
        	  + "  vec3 dir = normalize(vDir);\n"
        	  + "  float y = clamp(dir.y*0.5+0.5, 0.0, 1.0);\n"
        	  + "  vec3 dayTop=vec3(0.45,0.70,1.00), dayHor=vec3(0.85,0.92,1.00);\n"
        	  + "  vec3 nTop =vec3(0.02,0.04,0.10), nHor =vec3(0.06,0.08,0.16);\n"
        	  + "  float dAmt = dayAmount(uTime);\n"
        	  + "  vec3 top = mix(nTop, dayTop, dAmt);\n"
        	  + "  vec3 hor = mix(nHor, dayHor, dAmt);\n"
        	  + "  vec3 col = mix(hor, top, pow(y, 1.2));\n"
        	  + "  float sunMask = disc(dir, normalize(uSunDir), radians(2.8));\n"
        	  + "  vec3 sunCol = vec3(1.0, 0.95, 0.82);\n"
        	  + "  col += sunCol * sunMask * dAmt * 2.0;\n"
        	  + "  float moonMask = disc(dir, normalize(uMoonDir), radians(2.1));\n"
        	  + "  vec3 moonCol = vec3(0.95);\n"
        	  + "  col += moonCol * moonMask * (1.0 - dAmt) * 0.8;\n"
        	  + "  float starSeed = hash(floor(dir * 512.0));\n"
        	  + "  float starOn = step(0.9975, starSeed);\n"
        	  + "  float tw = 0.5 + 0.5 * sin(6.28318 * (starSeed * 23.17 + uTime * 120.0));\n"
        	  + "  float horizonFade = smoothstep(0.0, 0.25, y);\n"
        	  + "  float starVis = (1.0 - dAmt) * horizonFade;\n"
        	  + "  col += vec3(1.0) * starOn * tw * starVis * uStars;\n"
        	  + "  FragColor = vec4(col, 1.0);\n"
        	  + "}\n";

        skyShader = new ShaderProgram(vsrc, fsrc);
        int prog = skyShader.getProgramId();
        uSkyProjection = GL20.glGetUniformLocation(prog, "projection");
        uSkyView       = GL20.glGetUniformLocation(prog, "view");
        uSkyTime       = GL20.glGetUniformLocation(prog, "uTime");

        uSkySunDir  = GL20.glGetUniformLocation(prog, "uSunDir");
        uSkyMoonDir = GL20.glGetUniformLocation(prog, "uMoonDir");
        uSkyStars   = GL20.glGetUniformLocation(prog, "uStars");

        skyVao = GL30.glGenVertexArrays();
        skyVbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(skyVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, skyVbo);

        FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
        buf.put(verts).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void setupUnderwaterOverlay() {
        float[] verts = { -1f, -1f,   3f, -1f,   -1f, 3f };
        overlayVao = GL30.glGenVertexArrays();
        overlayVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(overlayVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, overlayVbo);
        java.nio.FloatBuffer fb = org.lwjgl.BufferUtils.createFloatBuffer(verts.length);
        fb.put(verts).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0L);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        String v =
            "#version 330 core\n" +
            "layout(location=0) in vec2 aPos;\n" +
            "void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }";

        String f =
            "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "uniform vec3 uColor;\n" +
            "uniform float uStrength;\n" +
            "void main(){ FragColor = vec4(uColor, uStrength); }";

        overlayShader = new ShaderProgram(v, f);
        int prog = overlayShader.getProgramId();
        uOverlayColor    = GL20.glGetUniformLocation(prog, "uColor");
        uOverlayStrength = GL20.glGetUniformLocation(prog, "uStrength");
    }

    public void render(float delta) {
        this.delta = delta;
        timeOfDay01 = (timeOfDay01 + (delta / DAY_LENGTH_SEC)) % 1.0f;

        int cameraChunkX = Math.floorDiv((int) camera.getPosition().x, Chunk.SIZE);
        int cameraChunkZ = Math.floorDiv((int) camera.getPosition().z, Chunk.SIZE);
        int renderRadius = camera.getRenderDistance();

        world.ensureChunksAround(cameraChunkX, cameraChunkZ, renderRadius);
        world.unloadFarChunks(cameraChunkX, cameraChunkZ, renderRadius);

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

        float sun = dayAmount(timeOfDay01);
        if (uSunlight >= 0) GL20.glUniform1f(uSunlight, sun);

        GL30.glBindVertexArray(vaoId);

        int updates = 0;
        while (updates < MAX_UPDATES_PER_FRAME) {
            world.ensureChunksAround(cameraChunkX, cameraChunkZ, renderRadius);
            PendingMesh pm = pendingUpdates.poll();
            if (pm == null) break;
            ChunkMesh oldMesh = meshCache.get(pm.key);
            ChunkMesh mesh = createChunkMesh(pm);
            meshCache.put(pm.key, mesh);
            meshStates.put(pm.key, MeshState.GPU_LOADED);
            if (oldMesh != null) oldMesh.delete();
            updates++;
        }

        int buildsThisFrame = 0;
        int[][] offsets = getOffsetsSortedByDistance(renderRadius);

        // PASS 1: draw opaque
        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0];
            int dz = offsets[i][1];
            int cx = cameraChunkX + dx;
            int cz = cameraChunkZ + dz;

            Chunk chunk = world.getChunkIfLoaded(cx, cz);
            if (chunk == null) continue;

            long key = pack(cx, cz);
            ChunkMesh mesh = meshCache.get(key);
            MeshState state = meshStates.get(key);

            if (mesh == null && state == null) {
                if (buildsThisFrame < MAX_BUILDS_PER_FRAME) {
                    meshStates.put(key, MeshState.BUILDING);
                    final int fcx = cx, fcz = cz;
                    mesherPool.submit(() -> {
                        PendingMesh built = buildChunkMesh(fcx, fcz, chunk);
                        pendingUpdates.add(built);
                        meshStates.put(built.key, MeshState.READY);
                    });
                    buildsThisFrame++;
                }
            }

            if (mesh != null) mesh.drawOpaque();
        }

        // PASS 2: draw translucent water (depth test ON, depth writes OFF, blending ON)
        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0];
            int dz = offsets[i][1];
            int cx = cameraChunkX + dx;
            int cz = cameraChunkZ + dz;

            ChunkMesh mesh = meshCache.get(pack(cx, cz));
            if (mesh != null) mesh.drawTranslucent();
        }

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        removeMesh(cameraChunkX, cameraChunkZ, renderRadius + 2);
        drawSkybox();

        if (isCameraUnderwater()) {
            float strength = 0.45f * underwaterDepthFactor();
            drawUnderwaterOverlay(strength);
        }
    }

    public void invalidateChunk(int cx, int cz) {
        long key = pack(cx, cz);
        meshStates.put(key, MeshState.BUILDING);
        Chunk chunk = world.getChunkIfLoaded(cx, cz);
        if (chunk == null) return;
        mesherPool.submit(() -> {
            PendingMesh built = buildChunkMesh(cx, cz, chunk);
            pendingUpdates.add(built);
            meshStates.put(built.key, MeshState.READY);
        });
    }

    public void invalidateBlock(int x, int y, int z) {
        int chunkX = Math.floorDiv(x, Chunk.SIZE);
        int chunkZ = Math.floorDiv(z, Chunk.SIZE);

        invalidateChunk(chunkX, chunkZ);

        int lx = Math.floorMod(x, Chunk.SIZE);
        int lz = Math.floorMod(z, Chunk.SIZE);

        boolean west  = (lx == 0);
        boolean east  = (lx == Chunk.SIZE - 1);
        boolean north = (lz == 0);
        boolean south = (lz == Chunk.SIZE - 1);

        if (west)  invalidateChunk(chunkX - 1, chunkZ);
        if (east)  invalidateChunk(chunkX + 1, chunkZ);
        if (north) invalidateChunk(chunkX,     chunkZ - 1);
        if (south) invalidateChunk(chunkX,     chunkZ + 1);

        if (west  && north) invalidateChunk(chunkX - 1, chunkZ - 1);
        if (west  && south) invalidateChunk(chunkX - 1, chunkZ + 1);
        if (east  && north) invalidateChunk(chunkX + 1, chunkZ - 1);
        if (east  && south) invalidateChunk(chunkX + 1, chunkZ + 1);
    }

    public void clearAllMeshes() {
        for (ChunkMesh m : meshCache.values()) m.delete();
        meshCache.clear();
        meshStates.clear();
        pendingUpdates.clear();
    }

    public void cleanup() {
        if (skyShader != null) skyShader.delete();
        if (skyVbo != 0) GL15.glDeleteBuffers(skyVbo);
        if (skyVao != 0) GL30.glDeleteVertexArrays(skyVao);

        if (overlayShader != null) overlayShader.delete();
        if (overlayVbo != 0) GL15.glDeleteBuffers(overlayVbo);
        if (overlayVao != 0) GL30.glDeleteVertexArrays(overlayVao);

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

    /** Builds two maps: opaque and translucent (water). */
    private PendingMesh buildChunkMesh(int cx, int cz, Chunk chunk) {
        Map<Texture, FaceBatch> opaque = new HashMap<>(32);
        Map<Texture, FaceBatch> trans  = new HashMap<>(8);

        final float baseX = cx * Chunk.SIZE;
        final float baseZ = cz * Chunk.SIZE;
        final float WATER_LIFT = -0.03f;

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
                        // OPAQUE
                        if (type != BlockType.WATER) {
                            if (!shouldRenderFace(world, cx, cz, x, y, z, FaceDirection.get(face))) continue;
                            Texture tex = type.getTextureForFace(face);
                            if (tex == null) continue;

                            FaceBatch fb = opaque.get(tex);
                            if (fb == null) {
                                fb = new FaceBatch(256 * 6 * 6);
                                opaque.put(tex, fb);
                            }
                            float[] verts = FaceVertices.get(face);

                            int nx = NORM[face][0], ny = NORM[face][1], nz = NORM[face][2];
                            int gx = (int)wx + nx;
                            int gy = (int)wy + ny;
                            int gz = (int)wz + nz;

                            float[] ao4 = new float[4];
                            ao4[0] = cornerAO(world, gx, gy, gz, face, 0);
                            ao4[1] = cornerAO(world, gx, gy, gz, face, 1);
                            ao4[2] = cornerAO(world, gx, gy, gz, face, 2);
                            ao4[3] = cornerAO(world, gx, gy, gz, face, 3);

                            fb.addFaceWithAO(wx, wy, wz, verts, ao4);
                            continue;
                        }

                        // WATER (translucent)
                        int[] dir = FaceDirection.get(face);
                        int nx = x + dir[0];
                        int ny = y + dir[1];
                        int nz = z + dir[2];

                        int globalNx = cx * Chunk.SIZE + nx;
                        int globalNy = ny;
                        int globalNz = cz * Chunk.SIZE + nz;
                        int neighborChunkX = Math.floorDiv(globalNx, Chunk.SIZE);
                        int neighborChunkZ = Math.floorDiv(globalNz, Chunk.SIZE);
                        int localNx = Math.floorMod(globalNx, Chunk.SIZE);
                        int localNy = globalNy;
                        int localNz = Math.floorMod(globalNz, Chunk.SIZE);

                        Chunk neighborChunk = world.getChunk(neighborChunkX, neighborChunkZ);
                        Block neighbor = null;
                        if (neighborChunk != null && localNx >= 0 && localNx < Chunk.SIZE && localNy >= 0 && localNy < Chunk.HEIGHT && localNz >= 0 && localNz < Chunk.SIZE) {
                            neighbor = neighborChunk.getBlock(localNx, localNy, localNz);
                        }

                        if (face == 0) {
                            boolean aboveIsAir = (neighbor == null || neighbor.getType() == BlockType.AIR);
                            if (!aboveIsAir) continue;
                            Texture tex = type.getTextureForFace(0);
                            if (tex == null) continue;
                            FaceBatch fb = trans.get(tex);
                            if (fb == null) {
                                fb = new FaceBatch(256 * 6 * 6);
                                trans.put(tex, fb);
                            }
                            float[] verts = FaceVertices.get(0);
                            float[] ao4 = {1f,1f,1f,1f};
                            fb.addFaceWithAO(wx, wy + WATER_LIFT, wz, verts, ao4);
                            continue;
                        }

                        BlockType neighborType = (neighbor == null) ? BlockType.AIR : neighbor.getType();
                        if (neighborType == BlockType.WATER) continue;
                        boolean neighborIsAir = (neighbor == null || neighborType == BlockType.AIR);
                        if (!neighborIsAir) continue;

                        Texture tex = type.getTextureForFace(face);
                        if (tex == null) continue;
                        FaceBatch fb = trans.get(tex);
                        if (fb == null) {
                            fb = new FaceBatch(256 * 6 * 6);
                            trans.put(tex, fb);
                        }
                        float[] verts = FaceVertices.get(face);
                        float[] ao4 = {1f,1f,1f,1f};
                        fb.addFaceWithAO(wx, wy + WATER_LIFT, wz, verts, ao4);
                    }
                }
            }
        }

        Map<Texture, float[]> perOpaque = new HashMap<>(opaque.size());
        for (Map.Entry<Texture, FaceBatch> e : opaque.entrySet()) perOpaque.put(e.getKey(), e.getValue().toArray());

        Map<Texture, float[]> perTrans = new HashMap<>(trans.size());
        for (Map.Entry<Texture, FaceBatch> e : trans.entrySet()) perTrans.put(e.getKey(), e.getValue().toArray());

        return new PendingMesh(pack(cx, cz), perOpaque, perTrans);
    }

    private ChunkMesh createChunkMesh(PendingMesh pm) {
        ChunkMesh mesh = new ChunkMesh();

        for (Map.Entry<Texture, float[]> e : pm.opaque.entrySet()) {
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

            mesh.addOpaque(tex, vbo, verts.length / 6);
        }

        for (Map.Entry<Texture, float[]> e : pm.translucent.entrySet()) {
            Texture tex = e.getKey();
            float[] verts = e.getValue();
            if (verts.length == 0) continue;

            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
            buf.put(e.getValue()).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(buf);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            mesh.addTranslucent(tex, vbo, verts.length / 6);
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
        return neighbor == null || neighbor.getType() == BlockType.AIR || neighbor.getType() == BlockType.WATER;
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static final class FaceBatch {
        private float[] data;
        private int size;

        FaceBatch(int initialFloats) {
            data = new float[Math.max(initialFloats, 6 * 6)];
            size = 0;
        }

        void ensure(int moreFloats) {
            int need = size + moreFloats;
            if (need > data.length) {
                int newCap = Math.max(need, data.length + (data.length >> 1));
                data = java.util.Arrays.copyOf(data, newCap);
            }
        }

        void addFaceWithAO(float wx, float wy, float wz, float[] faceVerts, float[] ao4) {
            int[] map = {0,1,2, 0,2,3};
            ensure(6 * 6);
            for (int triV = 0, src = 0; triV < 6; triV++, src += 5) {
                int corner = map[triV];
                data[size++] = faceVerts[src]     + wx;
                data[size++] = faceVerts[src + 1] + wy;
                data[size++] = faceVerts[src + 2] + wz;
                data[size++] = faceVerts[src + 3];
                data[size++] = faceVerts[src + 4];
                data[size++] = ao4[corner];
            }
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private void drawSkybox() {
        Matrix4f proj = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);

        skyShader.use();

        matBuffer.clear(); proj.get(matBuffer);
        GL20.glUniformMatrix4fv(uSkyProjection, false, matBuffer);

        matBuffer.clear(); view.get(matBuffer);
        GL20.glUniformMatrix4fv(uSkyView, false, matBuffer);

        GL20.glUniform1f(uSkyTime, timeOfDay01);

        double ang = 2.0 * Math.PI * (timeOfDay01 - 0);
        float sy = (float) Math.sin(ang);
        float sz = (float) Math.cos(ang);

        GL20.glUniform3f(uSkySunDir,  0f, sy, sz);
        GL20.glUniform3f(uSkyMoonDir, 0f, -sy, -sz);

        GL20.glUniform1f(uSkyStars, 5.0f);

        GL30.glBindVertexArray(skyVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);

        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glDepthMask(true);
    }

    private static final class ChunkMesh {
        private static final int STRIDE = 6 * Float.BYTES; // POS+UV+AO

        private final Map<Texture, Integer> vboOpaque = new HashMap<>();
        private final Map<Texture, Integer> vboTrans  = new HashMap<>();
        private final Map<Texture, Integer> cntOpaque = new HashMap<>();
        private final Map<Texture, Integer> cntTrans  = new HashMap<>();

        void addOpaque(Texture texture, int vboId, int vertexCount) {
            vboOpaque.put(texture, vboId);
            cntOpaque.put(texture, vertexCount);
        }
        void addTranslucent(Texture texture, int vboId, int vertexCount) {
            vboTrans.put(texture, vboId);
            cntTrans.put(texture, vertexCount);
        }

        private void bindForDraw(Texture tex, int yOffLoc, int offLoc, int scaleLoc) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if (tex instanceof AnimatedTexture) {
                ((AnimatedTexture) tex).update(Renderer.delta);
                tex.bind();
                if (yOffLoc >= 0) GL20.glUniform1f(yOffLoc, -0.1f);
            } else {
                tex.bind();
                if (offLoc   >= 0) GL20.glUniform1f(offLoc,   0f);
                if (scaleLoc >= 0) GL20.glUniform1f(scaleLoc, 1f);
                if (yOffLoc  >= 0) GL20.glUniform1f(yOffLoc,  0f);
            }
        }

        private void enableAttribs() {
            GL20.glEnableVertexAttribArray(0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glEnableVertexAttribArray(2);
        }
        private void disableAttribs() {
            GL20.glDisableVertexAttribArray(2);
            GL20.glDisableVertexAttribArray(1);
            GL20.glDisableVertexAttribArray(0);
        }

        void drawOpaque() {
            enableAttribs();

            int prog = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int offLoc = -1, scaleLoc = -1, yOffLoc = -1;
            if (prog != 0) {
                offLoc   = GL20.glGetUniformLocation(prog, "uFrameOffset");
                scaleLoc = GL20.glGetUniformLocation(prog, "uFrameScale");
                yOffLoc  = GL20.glGetUniformLocation(prog, "yOffset");
            }

            for (Map.Entry<Texture, Integer> e : vboOpaque.entrySet()) {
                Texture tex = e.getKey();
                int vboId = e.getValue();
                int count = cntOpaque.get(tex);

                bindForDraw(tex, yOffLoc, offLoc, scaleLoc);

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
                GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
            }

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            disableAttribs();
        }

        void drawTranslucent() {
            // blend like before; depth test ON, writes OFF
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);

            enableAttribs();

            int prog = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int offLoc = -1, scaleLoc = -1, yOffLoc = -1;
            if (prog != 0) {
                offLoc   = GL20.glGetUniformLocation(prog, "uFrameOffset");
                scaleLoc = GL20.glGetUniformLocation(prog, "uFrameScale");
                yOffLoc  = GL20.glGetUniformLocation(prog, "yOffset");
            }

            for (Map.Entry<Texture, Integer> e : vboTrans.entrySet()) {
                Texture tex = e.getKey();
                int vboId = e.getValue();
                int count = cntTrans.get(tex);

                bindForDraw(tex, yOffLoc, offLoc, scaleLoc);

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
                GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
            }

            // restore state
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            disableAttribs();
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
        }

        void delete() {
            for (int vbo : vboOpaque.values()) GL15.glDeleteBuffers(vbo);
            for (int vbo : vboTrans.values())  GL15.glDeleteBuffers(vbo);
            vboOpaque.clear(); vboTrans.clear();
            cntOpaque.clear(); cntTrans.clear();
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

    private int[][] getOffsetsSortedByDistance(int radius) {
        int[][] cached = radiusOffsetCache.get(radius);
        if (cached != null) return cached;

        final int size = (radius * 2 + 1) * (radius * 2 + 1);
        int[][] list = new int[size][2];
        int idx = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                list[idx][0] = dx;
                list[idx][1] = dz;
                idx++;
            }
        }
        Arrays.sort(list, (a, b) -> {
            int da = a[0]*a[0] + a[1]*a[1];
            int db = b[0]*b[0] + b[1]*b[1];
            return Integer.compare(da, db);
        });
        radiusOffsetCache.put(radius, list);
        return list;
    }

    private boolean isCameraUnderwater() {
        int gx = (int)Math.floor(camera.getPosition().x);
        int gy = (int)Math.floor(camera.getPosition().y + 1);
        int gz = (int)Math.floor(camera.getPosition().z);
        Block b = world.getBlock(gx, gy, gz);
        return b != null && b.getType() == BlockType.WATER;
    }

    private float underwaterDepthFactor() {
        int gx = (int)Math.floor(camera.getPosition().x);
        int gy = (int)Math.floor(camera.getPosition().y);
        int gz = (int)Math.floor(camera.getPosition().z);

        int search = 6;
        int y = gy;
        while (y < gy + search) {
            Block b = world.getBlock(gx, y, gz);
            if (b == null || b.getType() != BlockType.WATER) break;
            y++;
        }
        int topNonWaterY = y;
        float depth = (topNonWaterY - gy);
        return Math.max(0f, Math.min(1f, depth / 3f));
    }

    private void drawUnderwaterOverlay(float strength) {
        if (strength <= 0f) return;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        overlayShader.use();
        GL20.glUniform3f(uOverlayColor, 0.12f, 0.38f, 0.65f); // tint color
        GL20.glUniform1f(uOverlayStrength, Math.min(strength, 0.8f));

        GL30.glBindVertexArray(overlayVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
