package engine.world;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import engine.rendering.AnimatedTexture;
import engine.rendering.Texture;

public class ChunkMesh {
    private static final int STRIDE = 6 * Float.BYTES; // POS+UV+AO

    private static final class Batch {
        final int vboId;
        final int count;

        Batch(int vboId, int count) {
            this.vboId = vboId;
            this.count = count;
        }
    }

    private final Map<Texture, Batch> opaqueBatches = new HashMap<>();
    private final Map<Texture, Batch> transBatches  = new HashMap<>();

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

    public void addOpaque(Texture texture, int vboId, int vertexCount) {
        opaqueBatches.put(texture, new Batch(vboId, vertexCount));
    }

    public void addTranslucent(Texture texture, int vboId, int vertexCount) {
        transBatches.put(texture, new Batch(vboId, vertexCount));
    }

    private void bindForDraw(Texture tex, int offLoc, int scaleLoc) {
        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        tex.bind();

        if (offLoc < 0 && scaleLoc < 0) {
            return;
        }

        if (tex instanceof AnimatedTexture) {
            AnimatedTexture animTex = (AnimatedTexture) tex;
            if (offLoc   >= 0) GL20.glUniform1f(offLoc,   animTex.getFrameOffset());
            if (scaleLoc >= 0) GL20.glUniform1f(scaleLoc, animTex.getFrameScale());
        } else {
            if (offLoc   >= 0) GL20.glUniform1f(offLoc,   0f);
            if (scaleLoc >= 0) GL20.glUniform1f(scaleLoc, 1f);
        }
    }

    private void enableAttribs() {
        GL30.glEnableVertexAttribArray(0);
        GL30.glEnableVertexAttribArray(1);
        GL30.glEnableVertexAttribArray(2);
    }

    private void disableAttribs() {
        GL30.glDisableVertexAttribArray(2);
        GL30.glDisableVertexAttribArray(1);
        GL30.glDisableVertexAttribArray(0);
    }

    public void drawOpaque() {
        if (opaqueBatches.isEmpty()) return;
        if (programId == 0) return;

        enableAttribs();

        final int offLoc   = uFrameOffsetLoc;
        final int scaleLoc = uFrameScaleLoc;

        for (Map.Entry<Texture, Batch> e : opaqueBatches.entrySet()) {
            Texture tex = e.getKey();
            Batch   b   = e.getValue();
            if (b.count <= 0) continue;

            bindForDraw(tex, offLoc, scaleLoc);

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, b.vboId);
            GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, STRIDE, 0L);
            GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
            GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, b.count);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        disableAttribs();
    }

    public void drawTranslucent() {
        if (transBatches.isEmpty()) return;
        if (programId == 0) return; // no shader set

        GL30.glEnable(GL30.GL_BLEND);
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
        GL30.glDepthMask(false);

        enableAttribs();

        final int offLoc   = uFrameOffsetLoc;
        final int scaleLoc = uFrameScaleLoc;

        for (Map.Entry<Texture, Batch> e : transBatches.entrySet()) {
            Texture tex = e.getKey();
            Batch   b   = e.getValue();
            if (b.count <= 0) continue;

            bindForDraw(tex, offLoc, scaleLoc);

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, b.vboId);
            GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, STRIDE, 0L);
            GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
            GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, b.count);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        disableAttribs();

        GL30.glDepthMask(true);
        GL30.glDisable(GL30.GL_BLEND);
    }

    public void delete() {
        for (Batch b : opaqueBatches.values()) {
            GL30.glDeleteBuffers(b.vboId);
        }
        for (Batch b : transBatches.values()) {
            GL30.glDeleteBuffers(b.vboId);
        }
        opaqueBatches.clear();
        transBatches.clear();
    }
}
