package engine.world;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL30;

import engine.rendering.AnimatedTexture;
import engine.rendering.Texture;

public class ChunkMesh {
    private static final int STRIDE = 6 * Float.BYTES; // POS+UV+AO

    private final Map<Texture, Integer> vboOpaque = new HashMap<>();
    private final Map<Texture, Integer> vboTrans  = new HashMap<>();
    private final Map<Texture, Integer> cntOpaque = new HashMap<>();
    private final Map<Texture, Integer> cntTrans  = new HashMap<>();

    public void addOpaque(Texture texture, int vboId, int vertexCount) {
        vboOpaque.put(texture, vboId);
        cntOpaque.put(texture, vertexCount);
    }
    public void addTranslucent(Texture texture, int vboId, int vertexCount) {
        vboTrans.put(texture, vboId);
        cntTrans.put(texture, vertexCount);
    }

    private void bindForDraw(Texture tex, int offLoc, int scaleLoc) {
    	GL30.glActiveTexture(GL30.GL_TEXTURE0);
        if (tex instanceof AnimatedTexture) {
            AnimatedTexture animTex = (AnimatedTexture) tex;
            tex.bind();
            if (offLoc   >= 0) GL30.glUniform1f(offLoc,   animTex.getFrameOffset());
            if (scaleLoc >= 0) GL30.glUniform1f(scaleLoc, animTex.getFrameScale());
        } else {
            tex.bind();
            if (offLoc   >= 0) GL30.glUniform1f(offLoc,   0f);
            if (scaleLoc >= 0) GL30.glUniform1f(scaleLoc, 1f);
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
        enableAttribs();

        int prog = GL30.glGetInteger(GL30.GL_CURRENT_PROGRAM);
        int offLoc = -1, scaleLoc = -1;
        if (prog != 0) {
            offLoc   = GL30.glGetUniformLocation(prog, "uFrameOffset");
            scaleLoc = GL30.glGetUniformLocation(prog, "uFrameScale");
        }

        for (Map.Entry<Texture, Integer> e : vboOpaque.entrySet()) {
            Texture tex = e.getKey();
            int vboId = e.getValue();
            int count = cntOpaque.get(tex);

            bindForDraw(tex, offLoc, scaleLoc);

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
            GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, STRIDE, 0L);
            GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
            GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, count);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        disableAttribs();
    }
    
    public void drawTranslucent() {
    	GL30.glEnable(GL30.GL_BLEND);
    	GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
    	GL30.glDepthMask(false);

        enableAttribs();

        int prog = GL30.glGetInteger(GL30.GL_CURRENT_PROGRAM);
        int offLoc = -1, scaleLoc = -1;
        if (prog != 0) {
            offLoc   = GL30.glGetUniformLocation(prog, "uFrameOffset");
            scaleLoc = GL30.glGetUniformLocation(prog, "uFrameScale");
        }

        for (Map.Entry<Texture, Integer> e : vboTrans.entrySet()) {
            Texture tex = e.getKey();
            int vboId = e.getValue();
            int count = cntTrans.get(tex);

            bindForDraw(tex, offLoc, scaleLoc);

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
            GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, STRIDE, 0L);
            GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
            GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, STRIDE, 5L * Float.BYTES);
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, count);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        disableAttribs();
        GL30.glDepthMask(true);
        GL30.glDisable(GL30.GL_BLEND);
    }

    public void delete() {
        for (int vbo : vboOpaque.values()) GL30.glDeleteBuffers(vbo);
        for (int vbo : vboTrans.values())  GL30.glDeleteBuffers(vbo);
        vboOpaque.clear(); vboTrans.clear();
        cntOpaque.clear(); cntTrans.clear();
    }
}