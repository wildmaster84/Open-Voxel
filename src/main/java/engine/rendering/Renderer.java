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

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class Renderer {
    private World world;
    private Camera camera;
    private ShaderProgram shader;

    private int vaoId;
    private int vboId;

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

        // Dynamically pick chunk range around camera
        int cameraChunkX = Math.floorDiv((int)camera.getPosition().x, Chunk.SIZE);
        int cameraChunkZ = Math.floorDiv((int)camera.getPosition().z, Chunk.SIZE);
        int renderRadius = camera.getRenderDistance();
        for (int cx = cameraChunkX - renderRadius; cx <= cameraChunkX + renderRadius; cx++) {
            for (int cz = cameraChunkZ - renderRadius; cz <= cameraChunkZ + renderRadius; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk == null) continue;
                for (int x = 0; x < Chunk.SIZE; x++) {
                    for (int y = 0; y < Chunk.HEIGHT; y++) {
                        for (int z = 0; z < Chunk.SIZE; z++) {
                            Block block = chunk.getBlock(x, y, z);
                            if (block.getType() == null) continue;
                            if (block.getType() == BlockType.AIR) continue;

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

                            for (int face = 0; face < 6; face++) {
                                if (shouldRenderFace(world, cx, cz, x, y, z, FaceDirection.get(face))) {
                                    Texture texture = block.getType().getTextureForFace(face);
                                    if (texture != null) {
                                        texture.bind();
                                        int texLoc = GL20.glGetUniformLocation(shader.getProgramId(), "blockTexture");
                                        GL20.glUniform1i(texLoc, 0); // Texture unit 0
                                    }

                                    FloatBuffer faceBuffer = MemoryUtil.memAllocFloat(6 * 5); // 6 vertices, 3 pos + 2 uv
                                    faceBuffer.put(FaceVertices.get(face)).flip();

                                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
                                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, faceBuffer, GL15.GL_STREAM_DRAW);

                                    GL20.glEnableVertexAttribArray(0);
                                    GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
                                    GL20.glEnableVertexAttribArray(1);
                                    GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);

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

        if (neighborChunk == null || localX < 0 || localX >= Chunk.SIZE || localY < 0 || localY >= Chunk.HEIGHT || localZ < 0 || localZ >= Chunk.SIZE)
            return false; // Out of bounds, treat as AIR
        Block neighbor = neighborChunk.getBlock(localX, localY, localZ);
        return neighbor == null || neighbor.getType() == BlockType.AIR;
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
        } catch(NullPointerException e) {}
        
        GL15.glDeleteBuffers(vboId);
        GL30.glDeleteVertexArrays(vaoId);
    }
}