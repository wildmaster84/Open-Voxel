package engine.rendering;

import engine.world.World;
import engine.world.Chunk;
import engine.world.Block;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class Renderer {
    private World world;
    private Camera camera;
    private ShaderProgram shader;

    private int vaoId;
    private int vboId;

    // Vertex data for each face (2 triangles per face, 6 vertices)
    private static final float[][] FACE_VERTICES = {
        // Top (+Y)
        {0,1,0, 1,1,0, 1,1,1, 0,1,0, 1,1,1, 0,1,1},
        // Bottom (-Y)
        {0,0,0, 1,0,0, 1,0,1, 0,0,0, 1,0,1, 0,0,1},
        // Left (-X)
        {0,0,0, 0,0,1, 0,1,1, 0,0,0, 0,1,1, 0,1,0},
        // Right (+X)
        {1,0,0, 1,0,1, 1,1,1, 1,0,0, 1,1,1, 1,1,0},
        // Front (-Z)
        {0,0,0, 1,0,0, 1,1,0, 0,0,0, 1,1,0, 0,1,0},
        // Back (+Z)
        {0,0,1, 1,0,1, 1,1,1, 0,0,1, 1,1,1, 0,1,1}
    };

    // Face directions: [dx, dy, dz]
    private static final int[][] FACE_DIRECTIONS = {
        {0, 1, 0},   // Top
        {0, -1, 0},  // Bottom
        {-1, 0, 0},  // Left
        {1, 0, 0},   // Right
        {0, 0, -1},  // Front
        {0, 0, 1}    // Back
    };

    public Renderer(World world, Camera camera) {
        this.world = world;
        this.camera = camera;
        setupGL();
        setupShaders();
    }

    private void setupGL() {
        GL11.glClearColor(0.6f, 0.6f, 0.6f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GL30.glBindVertexArray(0);
    }

    private void setupShaders() {
        String vertexSrc =
                "#version 330 core\n" +
                "layout(location = 0) in vec3 position;\n" +
                "uniform mat4 projection;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 model;\n" +
                "void main() {\n" +
                "    gl_Position = projection * view * model * vec4(position, 1.3);\n" +
                "}";
        String fragmentSrc =
                "#version 330 core\n" +
                "out vec4 FragColor;\n" +
                "uniform vec3 blockColor;\n" +
                "void main() {\n" +
                "    FragColor = vec4(blockColor, 1.0);\n" +
                "}";
        shader = new ShaderProgram(vertexSrc, fragmentSrc);
    }

    public void render() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        shader.use();

        // Set camera matrices
        int projLoc = GL20.glGetUniformLocation(shader.getProgramId(), "projection");
        int viewLoc = GL20.glGetUniformLocation(shader.getProgramId(), "view");
        Matrix4f projection = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        FloatBuffer projBuffer = MemoryUtil.memAllocFloat(16);
        FloatBuffer viewBuffer = MemoryUtil.memAllocFloat(16);
        projection.get(projBuffer);
        view.get(viewBuffer);
        GL20.glUniformMatrix4fv(projLoc, false, projBuffer);
        GL20.glUniformMatrix4fv(viewLoc, false, viewBuffer);
        MemoryUtil.memFree(projBuffer);
        MemoryUtil.memFree(viewBuffer);

        GL30.glBindVertexArray(vaoId);

        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk == null) continue;
                for (int x = 0; x < Chunk.SIZE; x++) {
                    for (int y = 0; y < Chunk.SIZE; y++) {
                        for (int z = 0; z < Chunk.SIZE; z++) {
                            Block block = chunk.getBlock(x, y, z);
                            if (block.getType() == Block.Type.AIR) continue;

                            int colorLoc = GL20.glGetUniformLocation(shader.getProgramId(), "blockColor");
                            float[] color = getColorForBlock(block.getType());
                            GL20.glUniform3f(colorLoc, color[0], color[1], color[2]);

                            int modelLoc = GL20.glGetUniformLocation(shader.getProgramId(), "model");
                            Matrix4f model = new Matrix4f().translation(
                                    x + cx * Chunk.SIZE,
                                    y,
                                    z + cz * Chunk.SIZE
                            );
                            FloatBuffer modelBuffer = MemoryUtil.memAllocFloat(16);
                            model.get(modelBuffer);
                            GL20.glUniformMatrix4fv(modelLoc, false, modelBuffer);
                            MemoryUtil.memFree(modelBuffer);

                            // For each face, check if that face should be rendered (AIR neighbor only)
                            for (int face = 0; face < 6; face++) {
                                if (shouldRenderFace(world, cx, cz, x, y, z, FACE_DIRECTIONS[face])) {
                                    FloatBuffer faceBuffer = MemoryUtil.memAllocFloat(18);
                                    faceBuffer.put(FACE_VERTICES[face]).flip();

                                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
                                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, faceBuffer, GL15.GL_STREAM_DRAW);

                                    GL20.glEnableVertexAttribArray(0);
                                    GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

                                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

                                    MemoryUtil.memFree(faceBuffer);
                                }
                            }
                        }
                    }
                }
            }
        }

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
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

        if (neighborChunk == null || localX < 0 || localX >= Chunk.SIZE || localY < 0 || localY >= Chunk.SIZE || localZ < 0 || localZ >= Chunk.SIZE)
            return true; // Out of bounds, treat as AIR
        Block neighbor = neighborChunk.getBlock(localX, localY, localZ);
        return neighbor == null || neighbor.getType() == Block.Type.AIR;
    }

    private float[] getColorForBlock(Block.Type type) {
        switch (type) {
            case STONE: return new float[]{0.5f, 0.5f, 0.5f};
            case DIRT: return new float[]{0.6f, 0.4f, 0.2f};
            case GRASS: return new float[]{0.2f, 0.8f, 0.1f};
            default: return new float[]{1f, 1f, 1f};
        }
    }

    public void cleanup() {
        shader.delete();
        GL15.glDeleteBuffers(vboId);
        GL30.glDeleteVertexArrays(vaoId);
    }
}