package engine.rendering;

import engine.VoxelEngine;
import engine.input.InputHandler;
import engine.rendering.FaceRenderer.FaceDirection;
import engine.world.Chunk;
import engine.world.World;
import engine.world.AbstractBlock;
import engine.world.block.BlockType;

import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;

public class Renderer {
    private final World world;
    private final Camera camera;

    private ShaderProgram shader;
    private ShaderProgram skyShader;
    private ShaderProgram overlayShader;

    private int vaoId;
    private int skyVao = 0, skyVbo = 0, overlayVao = 0, overlayVbo = 0;

    private int uProjection, uView, uModel, uBlockTexture, uDayFactor;
    private int uSkyProjection, uSkyView, uSkyTime, uSkySunDir, uSkyMoonDir, uSkyStars;
    private int uOverlayStrength, uOverlayColor;

    private final FloatBuffer matBuffer = MemoryUtil.memAllocFloat(16);

    private final Map<Long, ChunkMesh> meshCache = new HashMap<>();
    private final ConcurrentLinkedQueue<PendingMesh> pendingUpdates = new ConcurrentLinkedQueue<>();
    /** Block-edit remeshes — separate lane so player edits aren't starved by chunk generation. */
    private final ConcurrentLinkedQueue<PendingMesh> pendingEditUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long, Boolean> meshStates = new ConcurrentHashMap<>();
    private final Map<Long, int[]> uploadedLightVersions = new HashMap<>();

    private final ExecutorService mesherPool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    /** Dedicated single-thread lane for player block edits — never queues behind generation. */
    private final ExecutorService editPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chunk-edit-mesh");
        t.setDaemon(true);
        return t;
    });
    private final ChunkMesher mesher;

    private static final int MAX_UPLOADS_PER_FRAME = 4;
    private static final int MAX_BUILDS_PER_FRAME = 2;

    private final OutlineRenderer outline = new OutlineRenderer();

    private float timeOfDay01 = 0f;
    private static final float DAY_LENGTH_SEC = 4f * 60f;

    private static final class PendingMesh {
        final long key;
        final int cx, cz;
        final ChunkMesher.Result result;
        PendingMesh(long key, int cx, int cz, ChunkMesher.Result result) {
            this.key = key;
            this.cx = cx;
            this.cz = cz;
            this.result = result;
        }
    }

    public Renderer(World world, Camera camera) {
        this.world = world;
        this.camera = camera;
        this.mesher = new ChunkMesher(world, VoxelEngine.getLightEngine());
        // Block face textures outlive renderers (they live on the BlockType
        // enum); re-upload any that cleanup() deleted on a previous world exit.
        engine.world.block.BlockType.reloadTextures();

        setupGL();
        setupShaders();
        cacheUniforms();
        setupSkybox();
        setupUnderwaterOverlay();
        outline.init();
    }

    private static float dayAmount(float t) {
        double phase = t - 0.25;
        double c = Math.cos(2.0 * Math.PI * phase);
        return (float)((c + 1.0) * 0.5);
    }

    private void setupGL() {
        GL30.glClearColor(0.6f,0.6f,0.6f,1.0f);
        GL30.glEnable(GL30.GL_DEPTH_TEST);

        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);
        GL30.glBindVertexArray(0);
    }

    private void setupShaders() {
        String vertexSrc =
            "#version 330 core\n" +
            "layout(location = 0) in vec3 position;\n" +
            "layout(location = 1) in vec2 texCoord;\n" +
            "layout(location = 2) in float ao;\n" +
            "layout(location = 3) in float skylight;\n" +
            "layout(location = 4) in float blockLight;\n" +
            "out vec2 vTexCoord;\n" +
            "out float vAO;\n" +
            "out float vSkylight;\n" +
            "out float vBlockLight;\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 model;\n" +
            "void main() {\n" +
            "  gl_Position = projection * view * model * vec4(position, 1.0);\n" +
            "  vTexCoord = texCoord;\n" +
            "  vAO = ao;\n" +
            "  vSkylight = skylight;\n" +
            "  vBlockLight = blockLight;\n" +
            "}";
        String fragmentSrc =
            "#version 330 core\n" +
            "in vec2 vTexCoord;\n" +
            "in float vAO;\n" +
            "in float vSkylight;\n" +
            "in float vBlockLight;\n" +
            "out vec4 FragColor;\n" +
            "uniform sampler2D blockTexture;\n" +
            "uniform float uFrameOffset;\n" +
            "uniform float uFrameScale;\n" +
            "uniform float uDayFactor;\n" +
            "void main() {\n" +
            "  vec2 coord = vec2(vTexCoord.x, vTexCoord.y * uFrameScale + uFrameOffset);\n" +
            "  vec4 tex = texture(blockTexture, coord);\n" +
            "  if (tex.a <= 0.1) discard;\n" +
            "  float ao = clamp(vAO, 0.0, 1.0);\n" +
            // skylight is baked day-independent; day factor is applied here.
            "  float brightness = max(vSkylight * (0.2 + 0.8 * uDayFactor), vBlockLight) * ao;\n" +
            "  brightness = max(brightness, 0.12);\n" +
            "  FragColor = vec4(tex.rgb * brightness, tex.a);\n" +
            "}";

        shader = new ShaderProgram(vertexSrc, fragmentSrc);
    }

    private void cacheUniforms() {
        shader.use();
        int prog = shader.getProgramId();
        uProjection   = GL30.glGetUniformLocation(prog, "projection");
        uView         = GL30.glGetUniformLocation(prog, "view");
        uModel        = GL30.glGetUniformLocation(prog, "model");
        uBlockTexture = GL30.glGetUniformLocation(prog, "blockTexture");
        uDayFactor    = GL30.glGetUniformLocation(prog, "uDayFactor");
        if (uBlockTexture >= 0) GL30.glUniform1i(uBlockTexture, 0);
        GL30.glUseProgram(0);
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
            "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "out vec3 vDir;\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "void main(){\n" +
            "  vDir = aPos;\n" +
            "  mat4 viewNoTrans = mat4(mat3(view));\n" +
            "  vec4 pos = projection * viewNoTrans * vec4(aPos, 1.0);\n" +
            "  gl_Position = vec4(pos.xy, pos.w, pos.w);\n" +
            "}";
        String fsrc =
            "#version 330 core\n" +
            "in vec3 vDir;\n" +
            "out vec4 FragColor;\n" +
            "uniform float uTime;\n" +
            "uniform vec3 uSunDir;\n" +
            "uniform vec3 uMoonDir;\n" +
            "uniform float uStars;\n" +
            "float dayAmount(float t){ float c = cos(6.2831853 * (fract(t) - 0.25)); return clamp((c+1.0)*0.5, 0.0, 1.0); }\n" +
            "float hash(vec3 p){ p = fract(p * 0.3183099 + vec3(0.1,0.2,0.3)); p += dot(p, p.yzx + 19.19); return fract(p.x * p.y * p.z * 93.533); }\n" +
            "float disc(vec3 dir, vec3 dirC, float r){ float d = acos(clamp(dot(dir, dirC), -1.0, 1.0)); float edge = 0.003; return smoothstep(r, r - edge, d); }\n" +
            "void main(){ vec3 dir = normalize(vDir); float y = clamp(dir.y*0.5+0.5, 0.0, 1.0); vec3 dayTop=vec3(0.45,0.70,1.00), dayHor=vec3(0.85,0.92,1.00); vec3 nTop=vec3(0.02,0.04,0.10), nHor=vec3(0.06,0.08,0.16); float dAmt = dayAmount(uTime); vec3 top = mix(nTop, dayTop, dAmt); vec3 hor = mix(nHor, dayHor, dAmt); vec3 col = mix(hor, top, pow(y, 1.2)); float sunMask = disc(dir, normalize(uSunDir), radians(2.8)); vec3 sunCol = vec3(1.0, 0.95, 0.82); col += sunCol * sunMask * dAmt * 2.0; float moonMask = disc(dir, normalize(uMoonDir), radians(2.1)); vec3 moonCol = vec3(0.95); col += moonCol * moonMask * (1.0 - dAmt) * 0.8; float starSeed = hash(floor(dir * 512.0)); float starOn = step(0.9975, starSeed); float tw = 0.5 + 0.5 * sin(6.28318 * (starSeed * 23.17 + uTime * 120.0)); float horizonFade = smoothstep(0.0, 0.25, y); float starVis = (1.0 - dAmt) * horizonFade; col += vec3(1.0) * starOn * tw * starVis * uStars; FragColor = vec4(col, 1.0); }";

        skyShader = new ShaderProgram(vsrc, fsrc);
        int prog = skyShader.getProgramId();
        uSkyProjection = GL30.glGetUniformLocation(prog, "projection");
        uSkyView       = GL30.glGetUniformLocation(prog, "view");
        uSkyTime       = GL30.glGetUniformLocation(prog, "uTime");
        uSkySunDir  = GL30.glGetUniformLocation(prog, "uSunDir");
        uSkyMoonDir = GL30.glGetUniformLocation(prog, "uMoonDir");
        uSkyStars   = GL30.glGetUniformLocation(prog, "uStars");

        skyVao = GL30.glGenVertexArrays();
        skyVbo = GL30.glGenBuffers();
        GL30.glBindVertexArray(skyVao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, skyVbo);

        FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
        buf.put(verts).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buf, GL30.GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);

        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 3 * Float.BYTES, 0L);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void setupUnderwaterOverlay() {
        float[] verts = { -1f, -1f,   3f, -1f,   -1f, 3f };
        overlayVao = GL30.glGenVertexArrays();
        overlayVbo = GL30.glGenBuffers();
        GL30.glBindVertexArray(overlayVao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, overlayVbo);
        java.nio.FloatBuffer fb = org.lwjgl.BufferUtils.createFloatBuffer(verts.length);
        fb.put(verts).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, fb, GL30.GL_STATIC_DRAW);
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(0, 2, GL30.GL_FLOAT, false, 2 * Float.BYTES, 0L);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        String v = "#version 330 core\nlayout(location=0) in vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }";
        String f = "#version 330 core\nout vec4 FragColor;\nuniform vec3 uColor;\nuniform float uStrength;\nvoid main(){ FragColor = vec4(uColor, uStrength); }";

        overlayShader = new ShaderProgram(v, f);
        int prog = overlayShader.getProgramId();
        uOverlayColor    = GL30.glGetUniformLocation(prog, "uColor");
        uOverlayStrength = GL30.glGetUniformLocation(prog, "uStrength");
    }

    public void tick(float dt) {
        timeOfDay01 = (timeOfDay01 + (dt / DAY_LENGTH_SEC)) % 1.0f;

        // A block may share one AnimatedTexture across all 6 faces — update
        // each unique texture once.
        java.util.Set<Texture> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (BlockType type : BlockType.values()) {
            Texture[] textures = {type.back, type.bottom, type.front, type.left, type.right, type.top};
            for (Texture tex : textures) {
                if (tex instanceof AnimatedTexture && seen.add(tex)) {
                    ((AnimatedTexture) tex).update(dt);
                }
            }
        }
    }

    public void render(InputHandler input) {
        // floor (not int-cast) so -0.5 maps to chunk -1, not 0
        int cameraChunkX = (int) Math.floor(camera.getPosition().x) >> 4;
        int cameraChunkZ = (int) Math.floor(camera.getPosition().z) >> 4;
        int renderRadius = camera.getRenderDistance();

        GL30.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);

        // Skybox first: it writes no depth, so opaque terrain draws over it
        // and translucent faces blend over it. (Drawing it last made the sky
        // paint over translucent faces at the horizon — both sit at depth 1.)
        drawSkybox();

        shader.use();

        Matrix4f projection = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        matBuffer.clear(); projection.get(matBuffer);
        GL30.glUniformMatrix4fv(uProjection, false, matBuffer);

        matBuffer.clear(); view.get(matBuffer);
        GL30.glUniformMatrix4fv(uView, false, matBuffer);

        matBuffer.clear(); new Matrix4f().identity().get(matBuffer);
        GL30.glUniformMatrix4fv(uModel, false, matBuffer);

        // Frustum culling: skip chunks entirely outside the view. At high
        // render distances the draw-call count (one per texture batch per
        // chunk) is the frame-time killer, not vertex count.
        FrustumIntersection frustum = new FrustumIntersection();
        frustum.set(projection.mul(view, new Matrix4f()));

        float day = dayAmount(timeOfDay01);
        if (uDayFactor >= 0) GL30.glUniform1f(uDayFactor, day);

        GL30.glBindVertexArray(vaoId);

        // Upload finished meshes (built on worker threads last frame).
        int uploads = 0;
        while (uploads < MAX_UPLOADS_PER_FRAME) {
            // Player edits first — never wait behind generation meshes.
            PendingMesh pm = pendingEditUpdates.poll();
            if (pm == null) pm = pendingUpdates.poll();
            if (pm == null) break;

            // Drop stale meshes: a newer block edit (or a later generation
            // mesh) superseded this one while it was in flight.
            Chunk nowChunk = world.getChunkIfLoaded(pm.cx, pm.cz);
            if (nowChunk != null && pm.result.blockVersionAtBuild != nowChunk.blockVersion()) {
                continue; // stale — a newer mesh is queued or will be
            }
            ChunkMesh oldMesh = meshCache.remove(pm.key);
            if (oldMesh != null) oldMesh.delete();

            ChunkMesh mesh = new ChunkMesh();
            for (Map.Entry<Texture, float[]> e : pm.result.opaque.entrySet()) {
                mesh.addOpaque(e.getKey(), e.getValue());
            }
            for (Map.Entry<Texture, float[]> e : pm.result.translucent.entrySet()) {
                mesh.addTranslucent(e.getKey(), e.getValue());
            }
            meshCache.put(pm.key, mesh);
            uploads++;

            // Light-version guards, both directions. Converges because the
            // version only bumps when light data actually changes.
            int[][] NB = {{0,-1},{0,1},{-1,0},{1,0}};
            final int[] OPP = {1, 0, 3, 2};

            // Forward: if a neighbor's skylight changed after this mesh was
            // built, the border vertices baked stale light — remesh once.
            for (int i = 0; i < 4; i++) {
                Chunk nb = world.getChunkIfLoaded(pm.cx + NB[i][0], pm.cz + NB[i][1]);
                int seen = pm.result.neighborLightVersions[i];
                if ((nb == null && seen != -1) || (nb != null && nb.skylightVersion() != seen)) {
                    invalidateChunk(pm.cx, pm.cz);
                    break;
                }
            }

            // Reverse: neighbors that meshed while THIS chunk was missing or
            // had older light must remesh now that we've uploaded.
            uploadedLightVersions.put(pm.key, pm.result.neighborLightVersions);
            Chunk self = world.getChunkIfLoaded(pm.cx, pm.cz);
            int selfVersion = (self != null) ? self.skylightVersion() : -1;
            for (int i = 0; i < 4; i++) {
                long nKey = pack(pm.cx + NB[i][0], pm.cz + NB[i][1]);
                int[] rec = uploadedLightVersions.get(nKey);
                if (rec == null) continue;
                if (rec[OPP[i]] != selfVersion) {
                    invalidateChunk(pm.cx + NB[i][0], pm.cz + NB[i][1]);
                }
            }
        }

        int buildsThisFrame = 0;
        int[][] offsets = getOffsetsSortedByDistance(renderRadius);

        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0], dz = offsets[i][1];
            int cx = cameraChunkX + dx, cz = cameraChunkZ + dz;

            Chunk chunk = world.getChunkIfLoaded(cx, cz);
            if (chunk == null) continue;

            long key = pack(cx, cz);
            ChunkMesh mesh = meshCache.get(key);

            if (mesh == null && !meshStates.containsKey(key) && buildsThisFrame < MAX_BUILDS_PER_FRAME) {
                meshStates.put(key, Boolean.TRUE); // BUILDING
                final int fcx = cx, fcz = cz;
                mesherPool.submit(() -> {
                    ChunkMesher.Result result = mesher.mesh(fcx, fcz, chunk);
                    pendingUpdates.add(new PendingMesh(pack(fcx, fcz), fcx, fcz, result));
                });
                buildsThisFrame++;
            }

            if (mesh != null) {
                if (!frustum.testAab(cx * Chunk.SIZE, 0, cz * Chunk.SIZE,
                        (cx + 1) * Chunk.SIZE, Chunk.HEIGHT, (cz + 1) * Chunk.SIZE)) {
                    continue;
                }
                mesh.setProgram(shader.getProgramId());
                mesh.drawOpaque();
            }
        }

        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0], dz = offsets[i][1];
            int cx = cameraChunkX + dx, cz = cameraChunkZ + dz;
            ChunkMesh mesh = meshCache.get(pack(cx, cz));
            if (mesh != null) {
                if (!frustum.testAab(cx * Chunk.SIZE, 0, cz * Chunk.SIZE,
                        (cx + 1) * Chunk.SIZE, Chunk.HEIGHT, (cz + 1) * Chunk.SIZE)) {
                    continue;
                }
                mesh.setProgram(shader.getProgramId());
                mesh.drawTranslucent();
            }
        }

        GL30.glBindVertexArray(0);
        GL30.glUseProgram(0);

        removeMesh(cameraChunkX, cameraChunkZ, renderRadius + 2);
        if (isCameraUnderwater()) {
            float strength = 0.45f * underwaterDepthFactor();
            drawUnderwaterOverlay(strength);
        }

        if (input != null) {
            var h = input.getHoverHit();
            if (h != null) drawHoverOutline(h);
        }
    }

    /** @param priority true for player block edits (fast lane), false for generation. */
    public void invalidateChunk(int cx, int cz, boolean priority) {
        meshStates.remove(pack(cx, cz)); // allow rebuild
        Chunk chunk = world.getChunkIfLoaded(cx, cz);
        if (chunk == null) return;
        (priority ? editPool : mesherPool).submit(() -> {
            ChunkMesher.Result result = mesher.mesh(cx, cz, chunk);
            ConcurrentLinkedQueue<PendingMesh> q = priority ? pendingEditUpdates : pendingUpdates;
            q.add(new PendingMesh(pack(cx, cz), cx, cz, result));
        });
    }

    public void invalidateChunk(int cx, int cz) {
        invalidateChunk(cx, cz, false);
    }

    public void invalidateBlock(int x, int y, int z) {
        int chunkX = Math.floorDiv(x, Chunk.SIZE);
        int chunkZ = Math.floorDiv(z, Chunk.SIZE);

        invalidateChunk(chunkX, chunkZ, true);

        int lx = Math.floorMod(x, Chunk.SIZE);
        int lz = Math.floorMod(z, Chunk.SIZE);

        boolean north = (lz == 0);
        boolean east  = (lx == Chunk.SIZE - 1);
        boolean south = (lz == Chunk.SIZE - 1);
        boolean west  = (lx == 0);

        if (west)  invalidateChunk(chunkX - 1, chunkZ, true);
        if (east)  invalidateChunk(chunkX + 1, chunkZ, true);
        if (north) invalidateChunk(chunkX,     chunkZ - 1, true);
        if (south) invalidateChunk(chunkX,     chunkZ + 1, true);

        if (west  && north) invalidateChunk(chunkX - 1, chunkZ - 1, true);
        if (west  && south) invalidateChunk(chunkX - 1, chunkZ + 1, true);
        if (east  && north) invalidateChunk(chunkX + 1, chunkZ - 1, true);
        if (east  && south) invalidateChunk(chunkX + 1, chunkZ + 1, true);
    }

    public void clearAllMeshes() {
        for (ChunkMesh m : meshCache.values()) m.delete();
        meshCache.clear();
        meshStates.clear();
        uploadedLightVersions.clear();
        pendingUpdates.clear();
        pendingEditUpdates.clear();
    }

    public void cleanup() {
        // Was never called before — leaked the outline shader/VBO and the
        // UI font texture + glyph buffer across world re-enters.
        outline.dispose();
        engine.ui.GLUIRenderer.cleanup();

        if (skyShader != null) skyShader.delete();
        if (skyVbo != 0) GL30.glDeleteBuffers(skyVbo);
        if (skyVao != 0) GL30.glDeleteVertexArrays(skyVao);

        if (overlayShader != null) overlayShader.delete();
        if (overlayVbo != 0) GL30.glDeleteBuffers(overlayVbo);
        if (overlayVao != 0) GL30.glDeleteVertexArrays(overlayVao);

        shader.delete();

        try {
            for (BlockType block : BlockType.values()) {
                if (block.back != null) block.back.cleanup();
                if (block.bottom != null) block.bottom.cleanup();
                if (block.front != null) block.front.cleanup();
                if (block.left != null) block.left.cleanup();
                if (block.right != null) block.right.cleanup();
                if (block.top != null) block.top.cleanup();
            }
        } catch (NullPointerException ignored) {}

        clearAllMeshes();
        MemoryUtil.memFree(matBuffer);
        GL30.glDeleteVertexArrays(vaoId);
        mesherPool.shutdownNow();
        editPool.shutdownNow();
    }

    private void drawSkybox() {
        Matrix4f proj = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        GL30.glEnable(GL30.GL_DEPTH_TEST);
        GL30.glDepthMask(false);
        GL30.glDepthFunc(GL30.GL_LEQUAL);

        skyShader.use();

        matBuffer.clear(); proj.get(matBuffer);
        GL30.glUniformMatrix4fv(uSkyProjection, false, matBuffer);

        matBuffer.clear(); view.get(matBuffer);
        GL30.glUniformMatrix4fv(uSkyView, false, matBuffer);

        GL30.glUniform1f(uSkyTime, timeOfDay01);

        double ang = 2.0 * Math.PI * (timeOfDay01 - 0);
        float sy = (float) Math.sin(ang);
        float sz = (float) Math.cos(ang);

        GL30.glUniform3f(uSkySunDir,  0f, sy, sz);
        GL30.glUniform3f(uSkyMoonDir, 0f, -sy, -sz);

        GL30.glUniform1f(uSkyStars, 5.0f);

        GL30.glBindVertexArray(skyVao);
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, 36);
        GL30.glBindVertexArray(0);

        GL30.glUseProgram(0);

        GL30.glDepthFunc(GL30.GL_LESS);
        GL30.glDepthMask(true);
    }

    private void drawHoverOutline(InputHandler.Hit h) {
        float x0 = h.x, x1 = h.x + 1f, y0 = h.y, y1 = h.y + 1f, z0 = h.z, z1 = h.z + 1f;

        AbstractBlock block = world.getBlock(h.x, h.y, h.z);
        if (block != null && block.isSlab()) {
            if (block.isSlabTop()) { y0 = h.y + 0.5f; y1 = h.y + 1.0f; }
            else if (block.isSlabBottom()) { y0 = h.y; y1 = h.y + 0.5f; }
        }

        final float EPS = 0.002f;
        float[][] corners = null;

        if (h.nx != 0) {
            float px = (h.nx < 0) ? x0 : x1; px += h.nx * EPS;
            corners = new float[][] { {px,y0,z0}, {px,y0,z1}, {px,y1,z1}, {px,y1,z0} };
        } else if (h.ny != 0) {
            float py = (h.ny < 0) ? y0 : y1; py += h.ny * EPS;
            corners = new float[][] { {x0,py,z0}, {x1,py,z0}, {x1,py,z1}, {x0,py,z1} };
        } else if (h.nz != 0) {
            float pz = (h.nz < 0) ? z0 : z1; pz += h.nz * EPS;
            corners = new float[][] { {x0,y0,pz}, {x1,y0,pz}, {x1,y1,pz}, {x0,y1,pz} };
        }

        if (corners != null) outline.draw(corners, camera.getProjectionMatrix(), camera.getViewMatrix());
    }

    private void removeMesh(int centerCx, int centerCz, int radius) {
        meshCache.entrySet().removeIf(e -> {
            long key = e.getKey();
            int cx = (int) (key >> 32);
            int cz = (int) (key & 0xFFFFFFFFL);
            boolean far = Math.abs(cx - centerCx) > radius || Math.abs(cz - centerCz) > radius;
            if (far) {
                e.getValue().delete();
                meshStates.remove(key);
                uploadedLightVersions.remove(key);
            }
            return far;
        });
    }

    private final Map<Integer, int[][]> radiusOffsetCache = new HashMap<>();

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
        Arrays.sort(list, Comparator.comparingInt(a -> a[0]*a[0] + a[1]*a[1]));
        radiusOffsetCache.put(radius, list);
        return list;
    }

    private boolean isCameraUnderwater() {
        int gx = (int)Math.floor(camera.getPosition().x);
        int gy = (int)Math.floor(camera.getPosition().y + 1);
        int gz = (int)Math.floor(camera.getPosition().z);
        AbstractBlock b = world.getBlock(gx, gy, gz);
        return b != null && b.getType().isLiquid();
    }

    private float underwaterDepthFactor() {
        int gx = (int)Math.floor(camera.getPosition().x);
        int gy = (int)Math.floor(camera.getPosition().y);
        int gz = (int)Math.floor(camera.getPosition().z);

        int search = 6;
        int y = gy;
        while (y < gy + search) {
            AbstractBlock b = world.getBlock(gx, y, gz);
            if (b == null || !b.getType().isLiquid()) break;
            y++;
        }
        int topNonWaterY = y;
        float depth = (topNonWaterY - gy);
        return Math.max(0f, Math.min(1f, depth / 3f));
    }

    private void drawUnderwaterOverlay(float strength) {
        if (strength <= 0f) return;

        GL30.glDisable(GL30.GL_DEPTH_TEST);
        GL30.glEnable(GL30.GL_BLEND);
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);

        overlayShader.use();
        GL30.glUniform3f(uOverlayColor, 0.12f, 0.38f, 0.65f);
        GL30.glUniform1f(uOverlayStrength, Math.min(strength, 0.8f));

        GL30.glBindVertexArray(overlayVao);
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);
        GL30.glUseProgram(0);

        GL30.glDisable(GL30.GL_BLEND);
        GL30.glEnable(GL30.GL_DEPTH_TEST);
    }

    private static long pack(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }
}
