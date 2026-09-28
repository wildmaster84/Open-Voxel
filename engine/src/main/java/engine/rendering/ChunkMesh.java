package engine.rendering;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * GPU-side mesh for one chunk. One interleaved VBO per texture batch:
 * x,y,z, u,v, ao, skylight, blockLight (8 floats per vertex). Day/night is a
 * shader uniform, so meshes never need rebuilding for lighting changes.
 */
public class ChunkMesh {
    private static final int STRIDE = 8 * Float.BYTES;

    private static final class Batch {
        final Texture texture;
        final int vaoId;
        final int vboId;
        final int vertexCount;
        Batch(Texture texture, int vaoId, int vboId, int vertexCount) {
            this.texture = texture;
            this.vaoId = vaoId;
            this.vboId = vboId;
            this.vertexCount = vertexCount;
        }
    }

    private final List<Batch> opaqueBatches = new ArrayList<>(4);
    private final List<Batch> transBatches  = new ArrayList<>(2);

    private int programId = -1;
    private int uFrameOffsetLoc = -1;
    private int uFrameScaleLoc  = -1;

    public void setProgram(int programId) {
        if (this.programId == programId) return;
        this.programId = programId;
        if (programId != 0) {
            uFrameOffsetLoc = GL20.glGetUniformLocation(programId, "uFrameOffset");
            uFrameScaleLoc  = GL20.glGetUniformLocation(programId, "uFrameScale");
        } else {
            uFrameOffsetLoc = -1;
            uFrameScaleLoc  = -1;
        }
    }

    public void addOpaque(Texture texture, float[] vertices) {
        add(opaqueBatches, texture, vertices);
    }

    public void addTranslucent(Texture texture, float[] vertices) {
        add(transBatches, texture, vertices);
    }

    private void add(List<Batch> batches, Texture texture, float[] vertices) {
        if (vertices == null || vertices.length == 0) return;
        int vertexCount = vertices.length / 8;

        // One VAO per batch: attrib pointers are recorded once here instead
        // of 5x glVertexAttribPointer + enable/disable per batch per frame.
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
        var buf = org.lwjgl.BufferUtils.createFloatBuffer(vertices.length);
        buf.put(vertices).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buf, GL30.GL_STATIC_DRAW);

        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, STRIDE, 0L);
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);
        GL30.glVertexAttribPointer(3, 1, GL30.GL_FLOAT, false, STRIDE, 6L * Float.BYTES);
        GL30.glVertexAttribPointer(4, 1, GL30.GL_FLOAT, false, STRIDE, 7L * Float.BYTES);
        GL30.glEnableVertexAttribArray(0);
        GL30.glEnableVertexAttribArray(1);
        GL30.glEnableVertexAttribArray(2);
        GL30.glEnableVertexAttribArray(3);
        GL30.glEnableVertexAttribArray(4);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        batches.add(new Batch(texture, vao, vbo, vertexCount));
    }

    private void bindForDraw(Texture tex, int offLoc, int scaleLoc) {
        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        tex.bind();

        if (offLoc < 0 && scaleLoc < 0) return;

        if (tex instanceof AnimatedTexture) {
            AnimatedTexture animTex = (AnimatedTexture) tex;
            if (offLoc   >= 0) GL20.glUniform1f(offLoc,   animTex.getFrameOffset());
            if (scaleLoc >= 0) GL20.glUniform1f(scaleLoc, animTex.getFrameScale());
        } else {
            if (offLoc   >= 0) GL20.glUniform1f(offLoc,   0f);
            if (scaleLoc >= 0) GL20.glUniform1f(scaleLoc, 1f);
        }
    }

    private void drawBatches(List<Batch> batches) {
        if (batches.isEmpty() || programId == 0) return;

        final int offLoc   = uFrameOffsetLoc;
        final int scaleLoc = uFrameScaleLoc;

        for (Batch b : batches) {
            if (b.vertexCount <= 0) continue;

            bindForDraw(b.texture, offLoc, scaleLoc);

            GL30.glBindVertexArray(b.vaoId);
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, b.vertexCount);
        }

        GL30.glBindVertexArray(0);
    }

    public void drawOpaque() {
        drawBatches(opaqueBatches);
    }

    public void drawTranslucent() {
        if (transBatches.isEmpty() || programId == 0) return;

        GL30.glEnable(GL30.GL_BLEND);
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
        GL30.glDepthMask(false);

        drawBatches(transBatches);

        GL30.glDepthMask(true);
        GL30.glDisable(GL30.GL_BLEND);
    }

    public void delete() {
        for (Batch b : opaqueBatches) GL30.glDeleteVertexArrays(b.vaoId);
        for (Batch b : opaqueBatches) GL30.glDeleteBuffers(b.vboId);
        for (Batch b : transBatches)  GL30.glDeleteVertexArrays(b.vaoId);
        for (Batch b : transBatches)  GL30.glDeleteBuffers(b.vboId);
        opaqueBatches.clear();
        transBatches.clear();
    }
}
