package engine.rendering;

import engine.VoxelEngine;
import engine.input.InputHandler;
import engine.light.LightEngine;
import engine.rendering.FaceRenderer.FaceDirection;
import engine.world.Chunk;
import engine.world.ChunkMesh;
import engine.world.World;
import engine.world.AbstractBlock;
import engine.world.block.BlockState;
import engine.world.block.BlockType;

import org.joml.Matrix4f;
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

    private int uProjection, uView, uModel, uBlockTexture, uSunlight;
    private int uSkyProjection, uSkyView, uSkyTime, uSkySunDir, uSkyMoonDir, uSkyStars;
    private int uOverlayStrength, uOverlayColor;

    private final FloatBuffer matBuffer = MemoryUtil.memAllocFloat(16);

    // Mesh management
    private final Map<Long, ChunkMesh> meshCache = new HashMap<>();
    private final ConcurrentLinkedQueue<PendingMesh> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PendingLightingUpdate> pendingLightingUpdates = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long, MeshState> meshStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, int[][]> radiusOffsetCache = new ConcurrentHashMap<>();

    private final ExecutorService mesherPool = Executors.newFixedThreadPool(Math.max(1, (Runtime.getRuntime().availableProcessors() / 2) / 2));
    private final ExecutorService lightingPool = Executors.newFixedThreadPool(Math.max(1, (Runtime.getRuntime().availableProcessors() / 2) / 2));

    // Lighting updates are fast (glBufferSubData only), so we can process many per frame
    private static final int MAX_LIGHTING_UPDATES_PER_FRAME = 32;
    private static final int MAX_UPDATES_PER_FRAME = 4;
    private static final int MAX_BUILDS_PER_FRAME = 2;
    // how often to refresh lighting (in game ticks)
    // Updates all visible chunks each time, so we don't need to do it every frame
    private static final int LIGHT_UPDATE_INTERVAL_TICKS = 5;
    
    private int lightTickCounter = 0;

    private final OutlineRenderer outline = new OutlineRenderer();
    
    private float timeOfDay01 = 0f;
    private static final float DAY_LENGTH_SEC = 4f * 60f;

    private enum MeshState { BUILDING, READY, GPU_LOADED }

    private static boolean isAirState(int s)   { return BlockState.typeId(s) == BlockType.AIR.getId(); }
    private static boolean isLiquidState(int s){ return BlockState.typeId(s) == BlockType.WATER.getId(); }

    private final class PendingMesh {
        final long key;
        final int cx, cz;
        final Map<Texture, float[]> opaque;
        final Map<Texture, float[]> translucent;
        final boolean computeLightingImmediately; // true for block changes to avoid flicker
        PendingMesh(long key, int cx, int cz, Map<Texture, float[]> opaque, Map<Texture, float[]> translucent, boolean computeLightingImmediately) {
            this.key = key;
            this.cx = cx;
            this.cz = cz;
            this.opaque = opaque;
            this.translucent = translucent;
            this.computeLightingImmediately = computeLightingImmediately;
        }
    }

    private final class PendingLightingUpdate {
        final long key;
        final Map<Texture, float[]> opaqueLighting;
        final Map<Texture, float[]> translucentLighting;
        PendingLightingUpdate(long key, Map<Texture, float[]> opaqueLighting, Map<Texture, float[]> translucentLighting) {
            this.key = key;
            this.opaqueLighting = opaqueLighting;
            this.translucentLighting = translucentLighting;
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

        final int STRIDE = 6 * Float.BYTES; // pos(3), uv(2), ao(1)

        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, STRIDE, 0L);

        GL30.glEnableVertexAttribArray(1);
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);

        GL30.glEnableVertexAttribArray(2);
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);

        GL30.glBindVertexArray(0);
    }

    private void setupShaders() {
        String vertexSrc =
            "#version 330 core\n" +
            "layout(location = 0) in vec3 position;\n" +
            "layout(location = 1) in vec2 texCoord;\n" +
            "layout(location = 2) in float ao;\n" +
            "layout(location = 3) in float lighting;\n" +
            "out vec2 vTexCoord;\n" +
            "out float vAO;\n" +
            "out float vLighting;\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 model;\n" +
            "void main() {\n" +
            "  vec4 worldPos = vec4(position, 1.0);\n" +
            "  gl_Position = projection * view * model * worldPos;\n" +
            "  vTexCoord = texCoord;\n" +
            "  vAO = ao;\n" +
            "  vLighting = lighting;\n" +
            "}";
        String fragmentSrc =
        	      "#version 330 core\n"
        	    + "in vec2 vTexCoord;\n"
        	    + "in float vAO;\n"
        	    + "in float vLighting;\n"
        	    + "in vec4 vLightSpacePos; // unused now, but we keep the varying\n"
        	    + "\n"
        	    + "out vec4 FragColor;\n"
        	    + "\n"
        	    + "uniform sampler2D blockTexture;\n"
        	    + "uniform float uFrameOffset;\n"
        	    + "uniform float uFrameScale;\n"
        	    + "uniform float uSunlight;\n"
        	    + "\n"
        	    + "void main() {\n"
        	    + "  // Sample from atlas (animated)\n"
        	    + "  vec2 coord = vec2(vTexCoord.x, vTexCoord.y * uFrameScale + uFrameOffset);\n"
        	    + "  vec4 tex = texture(blockTexture, coord);\n"
        	    + "  if (tex.a <= 0.1) discard;\n"
        	    + "\n"
        	    + "  // Clamp AO just in case\n"
        	    + "  float ao = clamp(vAO, 0.0, 1.0);\n"
        	    + "\n"
        	    + "  // Use per-vertex lighting if available (>= 0), otherwise fall back to uniform lighting\n"
        	    + "  // Note: vLighting is initialized to -1.0 when not yet computed, 0.0+ when computed\n"
        	    + "  float brightness;\n"
        	    + "  if (vLighting >= 0.0) {\n"
        	    + "    // Use per-vertex lighting directly (already includes day/night and skylight)\n"
        	    + "    brightness = vLighting * ao;\n"
        	    + "  } else {\n"
        	    + "    // Fallback: uniform lighting for meshes where per-vertex data hasn't arrived yet\n"
        	    + "    float day = clamp(uSunlight, 0.0, 1.0);\n"
        	    + "    brightness = mix(0.02, 0.9, day) * ao;\n"
        	    + "  }\n"
        	    + "  // Ensure we never hit pure black\n"
        	    + "  brightness = max(brightness, 0.12);\n"
        	    + "\n"
        	    + "  FragColor = vec4(tex.rgb * brightness, tex.a);\n"
        	    + "}\n";


        shader = new ShaderProgram(vertexSrc, fragmentSrc);
    }

    private void cacheUniforms() {
        shader.use();
        int prog = shader.getProgramId();
        uProjection   = GL30.glGetUniformLocation(prog, "projection");
        uView         = GL30.glGetUniformLocation(prog, "view");
        uModel        = GL30.glGetUniformLocation(prog, "model");
        uBlockTexture = GL30.glGetUniformLocation(prog, "blockTexture");
        uSunlight     = GL30.glGetUniformLocation(prog, "uSunlight");
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

        for (BlockType type : BlockType.values()) {
            if (type == null) continue;
            Texture[] textures = {type.back, type.bottom, type.front, type.left, type.right, type.top};
            for (Texture tex : textures) {
                if (tex instanceof AnimatedTexture) ((AnimatedTexture) tex).update(dt);
            }
        }
        
        lightTickCounter++;
        if (lightTickCounter >= LIGHT_UPDATE_INTERVAL_TICKS) {
            lightTickCounter = 0;
            updateLightingInView();
            
        }
    }

    public void render(InputHandler input) {
        int cameraChunkX = Math.floorDiv((int) camera.getPosition().x, Chunk.SIZE);
        int cameraChunkZ = Math.floorDiv((int) camera.getPosition().z, Chunk.SIZE);
        int renderRadius = camera.getRenderDistance();

        GL30.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);
        shader.use();

        Matrix4f projection = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        matBuffer.clear(); projection.get(matBuffer);
        GL30.glUniformMatrix4fv(uProjection, false, matBuffer);

        matBuffer.clear(); view.get(matBuffer);
        GL30.glUniformMatrix4fv(uView, false, matBuffer);

        matBuffer.clear(); new Matrix4f().identity().get(matBuffer);
        GL30.glUniformMatrix4fv(uModel, false, matBuffer);

        float sun = dayAmount(timeOfDay01);
        if (uSunlight >= 0) GL30.glUniform1f(uSunlight, sun);

        GL30.glBindVertexArray(vaoId);

        // Process lighting-only updates first (fast - just glBufferSubData calls)
        int lightUpdates = 0;
        while (lightUpdates < MAX_LIGHTING_UPDATES_PER_FRAME) {
            PendingLightingUpdate plu = pendingLightingUpdates.poll();
            if (plu == null) break;
            ChunkMesh mesh = meshCache.get(plu.key);
            if (mesh != null) {
                applyLightingUpdate(mesh, plu);
            }
            lightUpdates++;
        }

        // Process full mesh updates (slower)
        int updates = 0;
        while (updates < MAX_UPDATES_PER_FRAME) {
            
            PendingMesh pm = pendingUpdates.poll();
            if (pm == null) break;
            ChunkMesh oldMesh = meshCache.get(pm.key);
            ChunkMesh mesh = createChunkMesh(pm);
            meshCache.put(pm.key, mesh);
            meshStates.put(pm.key, MeshState.GPU_LOADED);
            if (oldMesh != null) oldMesh.delete();
            
            // Compute lighting for the new mesh
            final int cx = pm.cx, cz = pm.cz;
            if (pm.computeLightingImmediately) {
                // Block change - compute immediately to avoid flicker
                Chunk chunk = world.getChunkIfLoaded(cx, cz);
                if (chunk != null) {
                    PendingLightingUpdate lightUpdate = buildLightingUpdate(cx, cz, chunk);
                    if (lightUpdate != null) {
                        pendingLightingUpdates.add(lightUpdate);
                    }
                }
            } else {
                // Initial generation - async is fine
                lightingPool.submit(() -> {
                    Chunk chunk = world.getChunkIfLoaded(cx, cz);
                    if (chunk != null) {
                        PendingLightingUpdate lightUpdate = buildLightingUpdate(cx, cz, chunk);
                        if (lightUpdate != null) {
                            pendingLightingUpdates.add(lightUpdate);
                        }
                    }
                });
            }
            
            updates++;
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
            MeshState state = meshStates.get(key);

            if (mesh == null && state == null && buildsThisFrame < MAX_BUILDS_PER_FRAME) {
                meshStates.put(key, MeshState.BUILDING);
                final int fcx = cx, fcz = cz;
                mesherPool.submit(() -> {
                    // Initial chunk generation - async lighting is fine
                    PendingMesh built = buildChunkMesh(fcx, fcz, chunk, false);
                    pendingUpdates.add(built);
                    meshStates.put(built.key, MeshState.READY);
                });
                buildsThisFrame++;
            }

            if (mesh != null) {
            	mesh.setProgram(shader.getProgramId());
            	mesh.drawOpaque();
            }
        }

        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0], dz = offsets[i][1];
            int cx = cameraChunkX + dx, cz = cameraChunkZ + dz;
            ChunkMesh mesh = meshCache.get(pack(cx, cz));
            if (mesh != null) {
            	mesh.setProgram(shader.getProgramId());
            	mesh.drawTranslucent();
            }
        }

        GL30.glBindVertexArray(0);
        GL30.glUseProgram(0);

        removeMesh(cameraChunkX, cameraChunkZ, renderRadius + 2);
        drawSkybox();

        if (isCameraUnderwater()) {
            float strength = 0.45f * underwaterDepthFactor();
            drawUnderwaterOverlay(strength);
        }

        // outline for hovered block
        if (input != null) {
            var h = input.getHoverHit();
            if (h != null) drawHoverOutline(h);
        }
    }
    
    private void updateLightingInView() {
        int cameraChunkX = Math.floorDiv((int) camera.getPosition().x, Chunk.SIZE);
        int cameraChunkZ = Math.floorDiv((int) camera.getPosition().z, Chunk.SIZE);
        int renderRadius = camera.getRenderDistance();

        int[][] offsets = getOffsetsSortedByDistance(renderRadius);

        // Update ALL visible chunks for immediate lighting refresh
        // Since lighting computation is async on worker threads and application is fast,
        // we can handle updating all chunks without performance impact
        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0];
            int dz = offsets[i][1];
            int cx = cameraChunkX + dx;
            int cz = cameraChunkZ + dz;
            invalidateChunkLight(cx, cz);
        }
    }


    public void invalidateChunk(int cx, int cz) {
        long key = pack(cx, cz);
        meshStates.put(key, MeshState.BUILDING);        
        Chunk chunk = world.getChunkIfLoaded(cx, cz);
        if (chunk == null) return;
        mesherPool.submit(() -> {
            // Block change - compute lighting immediately to avoid flicker
            PendingMesh built = buildChunkMesh(cx, cz, chunk, true);
            pendingUpdates.add(built);
            meshStates.put(built.key, MeshState.READY);
        });
    }
    
    public void invalidateChunkLight(int cx, int cz) {
        long key = pack(cx, cz);
        Chunk chunk = world.getChunkIfLoaded(cx, cz);
        if (chunk == null) return;
        
        // Only update lighting if mesh already exists
        ChunkMesh existingMesh = meshCache.get(key);
        if (existingMesh == null) return;
        
        lightingPool.submit(() -> {
            PendingLightingUpdate lightUpdate = buildLightingUpdate(cx, cz, chunk);
            if (lightUpdate != null) {
                pendingLightingUpdates.add(lightUpdate);
            }
        });
    }

    public void invalidateBlock(int x, int y, int z) {
        int chunkX = Math.floorDiv(x, Chunk.SIZE);
        int chunkZ = Math.floorDiv(z, Chunk.SIZE);

        invalidateChunk(chunkX, chunkZ);

        int lx = Math.floorMod(x, Chunk.SIZE);
        int lz = Math.floorMod(z, Chunk.SIZE);

        boolean north = (lz == 0);
        boolean east  = (lx == Chunk.SIZE - 1);
        boolean south = (lz == Chunk.SIZE - 1);
        boolean west  = (lx == 0);

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
    }

    private PendingMesh buildChunkMesh(int cx, int cz, Chunk chunk, boolean computeLightingImmediately) {
        // NOTE: Lighting is computed separately via buildLightingUpdate() - no need to rebuild here
        Map<Texture, FaceBatch> opaque = new HashMap<>(64);
        Map<Texture, FaceBatch> trans  = new HashMap<>(16);

        final float baseX = cx * Chunk.SIZE;
        final float baseZ = cz * Chunk.SIZE;

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    final int state = chunk.getState(x, y, z);
                    if (isAirState(state)) continue;

                    final int tid = BlockState.typeId(state);
                    BlockType type = BlockType.fromId(tid);
                    if (type == null || type == BlockType.AIR) continue;

                    float wx = baseX + x, wy = y, wz = baseZ + z;

                    if (tid == BlockType.STAIR.getId()) {
                        addStairFaces(opaque, type, state, wx, wy, wz);
                        continue;
                    }

                    for (int face = 0; face < 6; face++) {
                        int nState = getNeighborStateOrAir(world, cx, cz, x, y, z, face);
                        int nTid = BlockState.typeId(nState);

                        if (tid == BlockType.WATER.getId()) {
                            handleWaterFace(opaque, trans, cx, cz, x, y, z, face, nState, type, wx, wy, wz);
                            continue;
                        }

                        if (tid == BlockType.GLASS.getId()) {
                            handleGlassFace(trans, world, cx, cz, x, y, z, face, nState, type, wx, wy, wz);
                            continue;
                        }

                        if (nTid == BlockType.GLASS.getId()) {
                            addFaceToBatch(opaque, type.getTextureForFace(face), wx, wy, wz, face, state);
                            continue;
                        }

                        if (!shouldRenderFaceByState(world, cx, cz, x, y, z, face)) continue;
                        boolean isTranslucent = (type == BlockType.WATER);
                        if (isTranslucent) {
                            addFaceToBatch(trans, type.getTextureForFace(face), wx, wy, wz, face, state);
                        } else {
                            addFaceToBatch(opaque, type.getTextureForFace(face), wx, wy, wz, face, state);
                        }
                    }
                }
            }
        }

        Map<Texture, float[]> perOpaque = new HashMap<>(opaque.size());
        for (Map.Entry<Texture, FaceBatch> e : opaque.entrySet()) perOpaque.put(e.getKey(), e.getValue().toArray());
        Map<Texture, float[]> perTrans = new HashMap<>(trans.size());
        for (Map.Entry<Texture, FaceBatch> e : trans.entrySet()) perTrans.put(e.getKey(), e.getValue().toArray());

        return new PendingMesh(pack(cx, cz), cx, cz, perOpaque, perTrans, computeLightingImmediately);
    }

    private PendingLightingUpdate buildLightingUpdate(int cx, int cz, Chunk chunk) {
        // Rebuild skylight for this chunk
        VoxelEngine.getLightEngine().rebuildSkylightForChunk(world, cx, cz, chunk);
        
        // Get the skylight array
        byte[] skylightData = VoxelEngine.getLightEngine().getSkylightArray(cx, cz);
        if (skylightData == null) return null;

        // Build lighting arrays matching EXACTLY the mesh structure from buildChunkMesh
        Map<Texture, LightBatch> opaqueLight = new HashMap<>();
        Map<Texture, LightBatch> transLight = new HashMap<>();
        
        final float baseX = cx * Chunk.SIZE;
        final float baseZ = cz * Chunk.SIZE;
        float dayFactor = dayAmount(timeOfDay01);

        // MUST follow the exact same logic as buildChunkMesh
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    final int state = chunk.getState(x, y, z);
                    if (isAirState(state)) continue;

                    final int tid = BlockState.typeId(state);
                    BlockType type = BlockType.fromId(tid);
                    if (type == null || type == BlockType.AIR) continue;

                    float wx = baseX + x, wy = y, wz = baseZ + z;

                    // Handle stairs - they generate multiple quads
                    if (tid == BlockType.STAIR.getId()) {
                        addStairLighting(opaqueLight, type, state, wx, wy, wz, dayFactor);
                        continue;
                    }

                    for (int face = 0; face < 6; face++) {
                        int nState = getNeighborStateOrAir(world, cx, cz, x, y, z, face);
                        int nTid = BlockState.typeId(nState);

                        if (tid == BlockType.WATER.getId()) {
                            handleWaterFaceLighting(opaqueLight, transLight, cx, cz, x, y, z, face, nState, type, wx, wy, wz, dayFactor);
                            continue;
                        }

                        if (tid == BlockType.GLASS.getId()) {
                            handleGlassFaceLighting(transLight, world, cx, cz, x, y, z, face, nState, type, wx, wy, wz, dayFactor);
                            continue;
                        }

                        if (nTid == BlockType.GLASS.getId()) {
                            addFaceLighting(opaqueLight, type.getTextureForFace(face), wx, wy, wz, face, dayFactor);
                            continue;
                        }

                        if (!shouldRenderFaceByState(world, cx, cz, x, y, z, face)) continue;
                        boolean isTranslucent = (type == BlockType.WATER);
                        if (isTranslucent) {
                            addFaceLighting(transLight, type.getTextureForFace(face), wx, wy, wz, face, dayFactor);
                        } else {
                            addFaceLighting(opaqueLight, type.getTextureForFace(face), wx, wy, wz, face, dayFactor);
                        }
                    }
                }
            }
        }

        Map<Texture, float[]> opaqueLighting = new HashMap<>();
        Map<Texture, float[]> translucentLighting = new HashMap<>();
        
        for (Map.Entry<Texture, LightBatch> e : opaqueLight.entrySet()) {
            opaqueLighting.put(e.getKey(), e.getValue().toArray());
        }
        for (Map.Entry<Texture, LightBatch> e : transLight.entrySet()) {
            translucentLighting.put(e.getKey(), e.getValue().toArray());
        }

        return new PendingLightingUpdate(pack(cx, cz), opaqueLighting, translucentLighting);
    }

    private void addStairLighting(Map<Texture, LightBatch> opaqueLight, BlockType type, int state, float wx, float wy, float wz, float dayFactor) {
        int f = BlockState.stairsFacing(state);
        boolean upside = BlockState.stairsUpside(state);
        Texture tex = type.getTextureForFace(0);
        if (tex == null) return;
        
        AbstractBlock.Facing facing =
                (f == BlockState.FACING_EAST)  ? AbstractBlock.Facing.WEST  :
                (f == BlockState.FACING_WEST)  ? AbstractBlock.Facing.EAST  :
                (f == BlockState.FACING_SOUTH) ? AbstractBlock.Facing.NORTH :
                                                 AbstractBlock.Facing.SOUTH;
        var quads = FaceRenderer.stairQuads(facing, upside);
        
        LightBatch lb = opaqueLight.computeIfAbsent(tex, k -> new LightBatch(256 * 6));
        for (float[] q : quads) {
            // Each quad generates 6 vertices (2 triangles)
            float lightValue = VoxelEngine.getLightEngine().sampleSkyLight01((int)wx, (int)wy, (int)wz, dayFactor);
            for (int v = 0; v < 6; v++) {
                lb.addLight(lightValue);
            }
        }
    }

    private void handleWaterFaceLighting(Map<Texture, LightBatch> opaqueLight, Map<Texture, LightBatch> transLight,
                                         int cx, int cz, int x, int y, int z, int face, int nState,
                                         BlockType type, float wx, float wy, float wz, float dayFactor) {
        int aboveState = getNeighborStateOrAir(world, cx, cz, x, y, z, 0);
        boolean isTopWater = !isLiquidState(aboveState);

        if (face == 0 && isTopWater) {
            if (!isAirState(nState)) return;
            Texture tex = type.getTextureForFace(0);
            if (tex == null) return;
            
            int[] nrm = FaceRenderer.FaceDirection.get(face);
            int gx = (int) wx + nrm[0];
            int gy = (int) wy + nrm[1];
            int gz = (int) wz + nrm[2];
            float lightValue = VoxelEngine.getLightEngine().sampleSkyLight01(gx, gy, gz, dayFactor);
            
            LightBatch lb = transLight.computeIfAbsent(tex, k -> new LightBatch(256 * 6));
            for (int v = 0; v < 6; v++) {
                lb.addLight(lightValue);
            }
            return;
        }

        if (!isLiquidState(nState)) {
            if (!isAirState(nState)) return;
            Texture tex = type.getTextureForFace(0);
            if (tex == null) return;
            
            int[] nrm = FaceRenderer.FaceDirection.get(face);
            int gx = (int) wx + nrm[0];
            int gy = (int) wy + nrm[1];
            int gz = (int) wz + nrm[2];
            float lightValue = VoxelEngine.getLightEngine().sampleSkyLight01(gx, gy, gz, dayFactor);
            
            LightBatch lb = transLight.computeIfAbsent(tex, k -> new LightBatch(256 * 6));
            for (int v = 0; v < 6; v++) {
                lb.addLight(lightValue);
            }
            return;
        }

        if (!isAirState(nState)) return;
    }

    private void handleGlassFaceLighting(Map<Texture, LightBatch> transLight, World world,
                                         int cx, int cz, int x, int y, int z, int face, int nState,
                                         BlockType type, float wx, float wy, float wz, float dayFactor) {
        int nTid = BlockState.typeId(nState);
        if (nTid == BlockType.GLASS.getId()) return;

        if (!(isAirState(nState) || isLiquidState(nState) || !stateFullyOccludes(nState, face))) return;

        Texture tex = type.getTextureForFace(face);
        if (tex == null) return;
        
        int[] nrm = FaceDirection.get(face);
        int gx = (int) wx + nrm[0];
        int gy = (int) wy + nrm[1];
        int gz = (int) wz + nrm[2];
        float lightValue = VoxelEngine.getLightEngine().sampleSkyLight01(gx, gy, gz, dayFactor);
        
        LightBatch lb = transLight.computeIfAbsent(tex, k -> new LightBatch(256 * 6));
        for (int v = 0; v < 6; v++) {
            lb.addLight(lightValue);
        }
    }

    private void addFaceLighting(Map<Texture, LightBatch> batch, Texture tex, float wx, float wy, float wz, int face, float dayFactor) {
        if (tex == null) return;
        
        int[] nrm = FaceDirection.get(face);
        int gx = (int) wx + nrm[0];
        int gy = (int) wy + nrm[1];
        int gz = (int) wz + nrm[2];
        
        float lightValue = VoxelEngine.getLightEngine().sampleSkyLight01(gx, gy, gz, dayFactor);
        
        LightBatch lb = batch.computeIfAbsent(tex, k -> new LightBatch(256 * 6));
        // Each face generates 6 vertices
        for (int v = 0; v < 6; v++) {
            lb.addLight(lightValue);
        }
    }

    private void applyLightingUpdate(ChunkMesh mesh, PendingLightingUpdate plu) {
        // Update opaque batches
        for (Map.Entry<Texture, float[]> e : plu.opaqueLighting.entrySet()) {
            mesh.updateLighting(e.getKey(), e.getValue(), true);
        }
        // Update translucent batches
        for (Map.Entry<Texture, float[]> e : plu.translucentLighting.entrySet()) {
            mesh.updateLighting(e.getKey(), e.getValue(), false);
        }
    }

    private static final class LightBatch {
        private float[] data;
        private int size;
        LightBatch(int initialFloats) {
            data = new float[Math.max(initialFloats, 6)];
            size = 0;
        }
        void ensure(int moreFloats) {
            int need = size + moreFloats;
            if (need > data.length) {
                int newCap = Math.max(need, data.length + (data.length >> 1));
                data = java.util.Arrays.copyOf(data, newCap);
            }
        }
        void addLight(float value) {
            ensure(1);
            data[size++] = value;
        }
        float[] toArray() { return Arrays.copyOf(data, size); }
    }

    private void addStairFaces(Map<Texture, FaceBatch> opaque, BlockType type, int state, float wx, float wy, float wz) {
        int f = BlockState.stairsFacing(state);
        boolean upside = BlockState.stairsUpside(state);
        Texture tex = type.getTextureForFace(0);
        if (tex == null) return;
        FaceBatch fb = opaque.computeIfAbsent(tex, k -> new FaceBatch(256 * 6 * 6));
        AbstractBlock.Facing facing =
                (f == BlockState.FACING_EAST)  ? AbstractBlock.Facing.WEST  :
                (f == BlockState.FACING_WEST)  ? AbstractBlock.Facing.EAST  :
                (f == BlockState.FACING_SOUTH) ? AbstractBlock.Facing.NORTH :
                                                 AbstractBlock.Facing.SOUTH;
        var quads = FaceRenderer.stairQuads(facing, upside);
        float[] ao4 = {1f,1f,1f,1f};
        for (float[] q : quads) fb.addFaceWithAO(wx, wy, wz, q, ao4);
    }

    private void handleWaterFace(Map<Texture, FaceBatch> opaque, Map<Texture, FaceBatch> trans,
                                 int cx, int cz, int x, int y, int z, int face, int nState,
                                 BlockType type, float wx, float wy, float wz) {
        int aboveState = getNeighborStateOrAir(world, cx, cz, x, y, z, 0);
        boolean isTopWater = !isLiquidState(aboveState);

        if (face == 0 && isTopWater) {
            if (!isAirState(nState)) return;
            Texture tex = type.getTextureForFace(0);
            if (tex == null) return;
            FaceBatch fb = trans.computeIfAbsent(tex, k -> new FaceBatch(256 * 6 * 6));
            float[] verts = adjustFaceVerts(FaceRenderer.FaceVertices.get(0), true, 0.48f);
            fb.addFaceWithAO(wx, wy, wz, verts, new float[]{1f,1f,1f,1f});
            return;
        }

        if (!isLiquidState(nState)) {
            if (!isAirState(nState)) return;
            Texture tex = type.getTextureForFace(0);
            if (tex == null) return;
            FaceBatch fb = trans.computeIfAbsent(tex, k -> new FaceBatch(256 * 6 * 6));

            float[] verts = isTopWater
                    ? adjustBlockVerts(FaceRenderer.FaceVertices.get(face), 0.96f, 0f)
                    : FaceRenderer.FaceVertices.get(face);

            fb.addFaceWithAO(wx, wy, wz, verts, new float[]{1f,1f,1f,1f});
            return;
        }

        if (!isAirState(nState)) return;
    }

    private void handleGlassFace(Map<Texture, FaceBatch> trans, World world,
                                 int cx, int cz, int x, int y, int z, int face, int nState,
                                 BlockType type, float wx, float wy, float wz) {
        int nTid = BlockState.typeId(nState);
        if (nTid == BlockType.GLASS.getId()) return;

        if (!(isAirState(nState) || isLiquidState(nState) || !stateFullyOccludes(nState, face))) return;

        Texture tex = type.getTextureForFace(face);
        if (tex == null) return;
        FaceBatch fb = trans.computeIfAbsent(tex, k -> new FaceBatch(256 * 6 * 6));
        float[] verts = FaceRenderer.FaceVertices.get(face);

        addVertsForSlabIfNeeded(verts, type, BlockState.typeId(getNeighborStateOrAir(world, cx, cz, x, y, z, face)));

        int[] nrm = FaceDirection.get(face);
        int gx = (int) wx + nrm[0], gy = (int) wy + nrm[1], gz = (int) wz + nrm[2];

        float[] ao4 = new float[4];
        ao4[0] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 0);
        ao4[1] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 1);
        ao4[2] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 2);
        ao4[3] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 3);

        fb.addFaceWithAO(wx, wy, wz, verts, ao4);
    }

    private void addFaceToBatch(Map<Texture, FaceBatch> batch, Texture tex, float wx, float wy, float wz, int face, int state) {
        if (tex == null) return;
        FaceBatch fb = batch.computeIfAbsent(tex, k -> new FaceBatch(256 * 6 * 6));
        float[] verts = FaceRenderer.FaceVertices.get(face);

        if (BlockState.typeId(state) == BlockType.SLAB.getId()) {
            int kind = BlockState.slabKind(state);
            if (kind != BlockState.SLAB_KIND_DOUBLE) {
                boolean topHalf = (kind == BlockState.SLAB_KIND_TOP);
                verts = adjustFaceVerts(verts, topHalf, 0.5f);
            }
        }

        int[] nrm = FaceDirection.get(face);
        int gx = (int) wx + nrm[0];
        int gy = (int) wy + nrm[1];
        int gz = (int) wz + nrm[2];

        float[] ao4 = new float[4];
        ao4[0] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 0);
        ao4[1] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 1);
        ao4[2] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 2);
        ao4[3] = FaceRenderer.cornerAO(world, gx, gy, gz, face, 3);

        // NOTE: Don't bake lighting into AO anymore - it's handled by separate lighting VBO
        // This allows lighting-only updates without rebuilding geometry
        
        fb.addFaceWithAO(wx, wy, wz, verts, ao4);
    }

    private float[] addVertsForSlabIfNeeded(float[] verts, BlockType type, int state) {
        if (type == null) return verts;
        if (BlockState.typeId(state) == BlockType.SLAB.getId()) {
            int kind = BlockState.slabKind(state);
            if (kind != BlockState.SLAB_KIND_DOUBLE) {
                boolean topHalf = (kind == BlockState.SLAB_KIND_TOP);
                return adjustFaceVerts(verts, topHalf, 0.5f);
            }
        }
        return verts;
    }

    private ChunkMesh createChunkMesh(PendingMesh pm) {
        ChunkMesh mesh = new ChunkMesh();

        for (Map.Entry<Texture, float[]> e : pm.opaque.entrySet()) {
            Texture tex = e.getKey();
            float[] verts = e.getValue();
            if (verts.length == 0) continue;
            int vertexCount = verts.length / 6;
            
            // Create geometry VBO
            int vbo = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
            FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
            buf.put(verts).flip();
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buf, GL30.GL_STATIC_DRAW);
            MemoryUtil.memFree(buf);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
            
            // Create lighting VBO with default values (-1.0 = use fallback uniform lighting until real data arrives)
            int lightVbo = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, lightVbo);
            float[] lightData = new float[vertexCount];
            Arrays.fill(lightData, -1.0f);
            FloatBuffer lightBuf = MemoryUtil.memAllocFloat(vertexCount);
            lightBuf.put(lightData).flip();
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, lightBuf, GL30.GL_DYNAMIC_DRAW);
            MemoryUtil.memFree(lightBuf);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
            
            mesh.addOpaque(tex, vbo, vertexCount, lightVbo);
        }

        for (Map.Entry<Texture, float[]> e : pm.translucent.entrySet()) {
            Texture tex = e.getKey();
            float[] verts = e.getValue();
            if (verts.length == 0) continue;
            int vertexCount = verts.length / 6;
            
            // Create geometry VBO
            int vbo = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
            FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
            buf.put(verts).flip();
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buf, GL30.GL_STATIC_DRAW);
            MemoryUtil.memFree(buf);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
            
            // Create lighting VBO with default values (-1.0 = use fallback uniform lighting until real data arrives)
            int lightVbo = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, lightVbo);
            float[] lightData = new float[vertexCount];
            Arrays.fill(lightData, -1.0f);
            FloatBuffer lightBuf = MemoryUtil.memAllocFloat(vertexCount);
            lightBuf.put(lightData).flip();
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, lightBuf, GL30.GL_DYNAMIC_DRAW);
            MemoryUtil.memFree(lightBuf);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
            
            mesh.addTranslucent(tex, vbo, vertexCount, lightVbo);
        }

        return mesh;
    }

    private static long pack(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

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
        float[] toArray() { return Arrays.copyOf(data, size); }
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

    private static float[] adjustFaceVerts(float[] verts, boolean topHalf, float offset) {
        float[] out = Arrays.copyOf(verts, verts.length);
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 6; i++) {
            float y = verts[i * 5 + 1];
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        boolean horizontal = (maxY - minY) < 1e-5f;

        for (int i = 0; i < 6; i++) {
            int base = i * 5;
            float y = verts[base + 1];
            float v = verts[base + 4];
            if (topHalf) {
                out[base + 1] = offset + offset * y;
                out[base + 4] = horizontal ? v : v * offset;
            } else {
                out[base + 1] = 0.5f * y;
                out[base + 4] = horizontal ? v : offset + v * offset;
            }
        }
        return out;
    }

    private static float[] adjustBlockVerts(float[] verts, float scale, float offset) {
        float[] out = Arrays.copyOf(verts, verts.length);
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 6; i++) {
            float y = verts[i * 5 + 1];
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        boolean horizontal = (maxY - minY) < 1e-5f;

        for (int i = 0; i < 6; i++) {
            int base = i * 5;
            float y = verts[base + 1];
            float v = verts[base + 4];
            out[base + 1] = y * scale + offset;
            out[base + 4] = horizontal ? v : v * scale + offset;
        }
        return out;
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
        Arrays.sort(list, Comparator.comparingInt(a -> a[0]*a[0] + a[1]*a[1]));
        radiusOffsetCache.put(radius, list);
        return list;
    }

    private boolean isCameraUnderwater() {
        int gx = (int)Math.floor(camera.getPosition().x);
        int gy = (int)Math.floor(camera.getPosition().y + 1);
        int gz = (int)Math.floor(camera.getPosition().z);
        AbstractBlock b = world.getBlock(gx, gy, gz);
        return b != null && b.getType() == BlockType.WATER;
    }

    private float underwaterDepthFactor() {
        int gx = (int)Math.floor(camera.getPosition().x);
        int gy = (int)Math.floor(camera.getPosition().y);
        int gz = (int)Math.floor(camera.getPosition().z);

        int search = 6;
        int y = gy;
        while (y < gy + search) {
            AbstractBlock b = world.getBlock(gx, y, gz);
            if (b == null || b.getType() != BlockType.WATER) break;
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

    private static boolean stateFullyOccludes(int state, int faceIndex) {
        int tid = BlockState.typeId(state);
        if (tid == BlockType.AIR.getId())   return false;
        if (tid == BlockType.WATER.getId()) return false;
        if (tid == BlockType.SLAB.getId())  return BlockState.slabKind(state) == BlockState.SLAB_KIND_DOUBLE;
        if (tid == BlockType.STAIR.getId()) return false;
        return true;
    }

    private boolean shouldRenderFaceByState(World world, int cx, int cz, int x, int y, int z, int faceIndex) {
        int nState = getNeighborStateOrAir(world, cx, cz, x, y, z, faceIndex);
        if (isAirState(nState))   return true;
        if (isLiquidState(nState)) return true;
        return !stateFullyOccludes(nState, faceIndex);
    }

    private int getNeighborStateOrAir(World world, int cx, int cz, int x, int y, int z, int faceIndex) {
        int[] dir = FaceRenderer.FaceDirection.get(faceIndex);
        int nx = x + dir[0], ny = y + dir[1], nz = z + dir[2];
        int gx = cx * Chunk.SIZE + nx, gy = ny, gz = cz * Chunk.SIZE + nz;
        if (gy < 0 || gy >= Chunk.HEIGHT) return BlockState.make(BlockType.AIR.getId());
        int ncx = Math.floorDiv(gx, Chunk.SIZE), ncz = Math.floorDiv(gz, Chunk.SIZE);
        int lx = Math.floorMod(gx, Chunk.SIZE), lz = Math.floorMod(gz, Chunk.SIZE);
        Chunk nc = world.getChunkIfLoaded(ncx, ncz);
        return (nc == null) ? BlockState.make(BlockType.AIR.getId()) : nc.getState(lx, gy, lz);
    }
}