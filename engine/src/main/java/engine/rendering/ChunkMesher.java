package engine.rendering;

import engine.light.LightEngine;
import engine.world.block.BlockType.RenderLayer;
import engine.rendering.FaceRenderer.FaceDirection;
import engine.world.Chunk;
import engine.world.World;
import engine.world.block.BlockShape;
import engine.world.block.BlockState;
import engine.world.block.BlockType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Single-pass chunk mesher. Emits vertices (x,y,z,u,v,ao,skylight) per texture
 * batch, driven entirely by BlockType properties — no per-block special cases.
 *
 * Skylight is baked day-INDEPENDENT (0..1); the shader applies the day/night
 * factor as a uniform, so the time of day never forces a remesh.
 */
public final class ChunkMesher {
    private static final int FLOATS_PER_VERTEX = 8; // x,y,z,u,v,ao,skylight,blockLight

    /** One growable vertex buffer per texture. */
    private static final class Batch {
        float[] data = new float[256 * 6 * 7];
        int size;
        void ensure(int more) {
            int need = size + more;
            if (need > data.length) {
                data = Arrays.copyOf(data, Math.max(need, data.length + (data.length >> 1)));
            }
        }
        float[] toArray() { return Arrays.copyOf(data, size); }
    }

    /** Result of meshing one chunk: opaque + translucent vertex batches per texture. */
    public static final class Result {
        public final Map<Texture, float[]> opaque;
        public final Map<Texture, float[]> translucent;
        /** Skylight versions of the 4 neighbor chunks at mesh time (-1 if unloaded). */
        public final int[] neighborLightVersions = new int[4];
        /** Chunk blockVersion at mesh start; renderer drops meshes built from
         *  older block data than what's now in the chunk (stale in-flight meshes). */
        public final int blockVersionAtBuild;
        Result(Map<Texture, float[]> opaque, Map<Texture, float[]> translucent,
               int[] neighborLightVersions, int blockVersionAtBuild) {
            this.opaque = opaque;
            this.translucent = translucent;
            System.arraycopy(neighborLightVersions, 0, this.neighborLightVersions, 0, 4);
            this.blockVersionAtBuild = blockVersionAtBuild;
        }
    }

    private final World world;
    private final LightEngine lightEngine;

    public ChunkMesher(World world, LightEngine lightEngine) {
        this.world = world;
        this.lightEngine = lightEngine;
    }

    /**
     * Builds the mesh for one chunk. Skips air states fast, so tall
     * mostly-air chunks cost little beyond the state reads.
     */
    public Result mesh(int cx, int cz, Chunk chunk) {
        final int blockVersionAtBuild = chunk.blockVersion();

        // Relight only when light inputs changed since the last build of
        // this chunk: its own blocks, or a neighbor's light spreading in
        // (which bumps the neighbor's skylightVersion). A single block edit
        // still relights once — but re-meshing for unrelated reasons no
        // longer re-runs the 131k-cell column scan + BFS floods.
        int[][] NB = {{0,-1},{0,1},{-1,0},{1,0}};
        int lightVersionAtBuild = chunk.lightVersion();
        for (int i = 0; i < 4; i++) {
            Chunk nb = world.getChunkIfLoaded(cx + NB[i][0], cz + NB[i][1]);
            if (nb != null) lightVersionAtBuild += nb.skylightVersion();
        }
        if (lightVersionAtBuild != chunk.lastBuiltLightInputs()) {
            lightEngine.rebuildSkylightForChunk(world, cx, cz, chunk);
            chunk.setLastBuiltLightInputs(lightVersionAtBuild);
        }

        int[] neighborLightVersions = new int[4];
        for (int i = 0; i < 4; i++) {
            Chunk nb = world.getChunkIfLoaded(cx + NB[i][0], cz + NB[i][1]);
            neighborLightVersions[i] = (nb != null) ? nb.skylightVersion() : -1;
        }

        Map<Texture, Batch> opaque = new HashMap<>(16);
        Map<Texture, Batch> trans  = new HashMap<>(8);

        final int baseX = cx * Chunk.SIZE;
        final int baseZ = cz * Chunk.SIZE;

        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    int state = chunk.getState(x, y, z);
                    int tid = BlockState.typeId(state);
                    if (tid == BlockType.AIR.getId()) continue;

                    BlockType type = BlockType.fromId(tid);
                    if (type == BlockType.AIR) continue;

                    float wx = baseX + x, wy = y, wz = baseZ + z;
                    boolean translucentBlock = type.renderLayer() == RenderLayer.TRANSLUCENT;

                    // Liquid surface: lower the top face when no liquid above.
                    float topOffset = 0f;
                    if (type.isLiquid() && !isLiquidAt(world, (int) wx, y + 1, (int) wz)) {
                        topOffset = -0.04f; // surface at y=0.96, matching the old look
                    }

                    for (int face = 0; face < 6; face++) {
                        int nState = neighborState(world, cx, cz, chunk, x, y, z, face);
                        if (!shouldDrawFace(type, state, nState)) continue;

                        Texture tex = type.getTextureForFace(face);
                        if (tex == null) continue;

                        Map<Texture, Batch> batchMap = translucentBlock ? trans : opaque;
                        Batch b = batchMap.computeIfAbsent(tex, k -> new Batch());
                        emitFace(b, type, state, face, wx, wy, wz, topOffset);
                    }
                }
            }
        }

        Map<Texture, float[]> o = new HashMap<>(opaque.size());
        for (Map.Entry<Texture, Batch> e : opaque.entrySet()) o.put(e.getKey(), e.getValue().toArray());
        Map<Texture, float[]> t = new HashMap<>(trans.size());
        for (Map.Entry<Texture, Batch> e : trans.entrySet()) t.put(e.getKey(), e.getValue().toArray());
        return new Result(o, t, neighborLightVersions, blockVersionAtBuild);
    }

    // ---- face culling (property-driven) ----

    private static boolean shouldDrawFace(BlockType type, int state, int nState) {
        int nTid = BlockState.typeId(nState);
        if (nTid == BlockType.AIR.getId()) return true;

        BlockType nType = BlockType.fromId(nTid);

        if (type.renderLayer() == RenderLayer.TRANSLUCENT) {
            if (nType.renderLayer() != RenderLayer.TRANSLUCENT) {
                // Against opaque-ish neighbors: draw only if the neighbor doesn't occlude
                // (visible through slabs/stairs, hidden against full cubes).
                return !nType.getShape().occludes(nState);
            }
            // Translucent vs translucent:
            if (type.isLiquid()) return false;   // water never draws against water/glass
            return nType.isLiquid();             // glass draws against water (matches old behavior)
        }

        // Opaque face: translucent neighbors (water/glass) never cull it —
        // only a fully opaque, occluding neighbor does.
        if (nType.renderLayer() == RenderLayer.TRANSLUCENT) return true;
        return !nType.getShape().occludes(nState);
    }

    // ---- vertex emission ----

    private void emitFace(Batch b, BlockType type, int state, int face,
                          float wx, float wy, float wz, float topOffset) {
        float[] verts;

        if (type.getShape() == BlockShape.CUBE) {
            verts = FaceRenderer.FaceVertices.get(face);
        } else {
            float[][] quads = type.getShape().quads(state);
            if (face >= quads.length) return;
            verts = quads[face];
        }

        int[] nrm = FaceDirection.get(face);
        int gx = (int) wx + nrm[0], gy = (int) wy + nrm[1], gz = (int) wz + nrm[2];

        float skylight = lightEngine.sampleSkyLight01(world, gx, gy, gz, 1.0f);
        float blockLight = lightEngine.sampleBlockLight01(world, gx, gy, gz);

        // Face arrays are already 6-vertex tri-strips laid out linearly as
        // (c0,c1,c2, c0,c2,c3). Read them verbatim (src = v*5). The corner map
        // is used ONLY to assign per-corner AO to each of the 6 verts.
        int[] cornerOfVert = {0, 1, 2, 0, 2, 3};
        float[] ao = new float[4];
        for (int c = 0; c < 4; c++) ao[c] = aoFor(face, c, type, gx, gy, gz);

        b.ensure(6 * FLOATS_PER_VERTEX);
        for (int v = 0; v < 6; v++) {
            int corner = cornerOfVert[v];
            int src = v * 5;
            float y = verts[src + 1];
            b.data[b.size++] = verts[src]     + wx;
            b.data[b.size++] = y + wy + (y > 0.99f ? topOffset : 0f);
            b.data[b.size++] = verts[src + 2] + wz;
            b.data[b.size++] = verts[src + 3];
            b.data[b.size++] = verts[src + 4];
            b.data[b.size++] = ao[corner];
            b.data[b.size++] = skylight;
            b.data[b.size++] = blockLight;
        }
    }

    private float aoFor(int face, int corner, BlockType type, int gx, int gy, int gz) {
        if (type.renderLayer() == RenderLayer.TRANSLUCENT) return 1f;
        return FaceRenderer.cornerAO(world, gx, gy, gz, face, corner);
    }

    private static boolean isLiquidAt(World world, int x, int y, int z) {
        return BlockType.fromId(BlockState.typeId(world.getState(x, y, z))).isLiquid();
    }

    private static int neighborState(World world, int cx, int cz, Chunk chunk, int x, int y, int z, int face) {
        int[] dir = FaceDirection.get(face);
        int nx = x + dir[0], ny = y + dir[1], nz = z + dir[2];
        if (ny < 0 || ny >= Chunk.HEIGHT) return BlockState.make(BlockType.AIR.getId());
        if (nx >= 0 && nx < Chunk.SIZE && nz >= 0 && nz < Chunk.SIZE) {
            return chunk.getState(nx, ny, nz); // fast path: same chunk
        }
        return world.getState(cx * Chunk.SIZE + nx, ny, cz * Chunk.SIZE + nz);
    }
}
