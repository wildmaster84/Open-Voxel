package engine.rendering;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public final class OutlineRenderer {
    private int vao = 0, vbo = 0;
    private ShaderProgram shader;
    private int uProj, uView, uModel, uColor;

    public void init() {
        // shader: MVP + solid color
        String v = "#version 330 core\n" +
                   "layout(location=0) in vec3 aPos;\n" +
                   "uniform mat4 projection, view, model;\n" +
                   "void main(){ gl_Position = projection * view * model * vec4(aPos,1.0); }";
        String f = "#version 330 core\n" +
                   "out vec4 FragColor;\n" +
                   "uniform vec4 uColor;\n" +
                   "void main(){ FragColor = uColor; }";
        shader = new ShaderProgram(v, f);
        int prog = shader.getProgramId();
        uProj  = GL30.glGetUniformLocation(prog, "projection");
        uView  = GL30.glGetUniformLocation(prog, "view");
        uModel = GL30.glGetUniformLocation(prog, "model");
        uColor = GL30.glGetUniformLocation(prog, "uColor");

        vao = GL30.glGenVertexArrays();
        vbo = GL30.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, 4 * 3 * Float.BYTES, GL30.GL_DYNAMIC_DRAW);

        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 3 * Float.BYTES, 0L);

        GL30.glBindVertexArray(0);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
    }

    public void dispose() {
        if (vbo != 0) GL30.glDeleteBuffers(vbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (shader != null) shader.delete();
        vbo = vao = 0; shader = null;
    }

    public void draw(float[][] corners, Matrix4f projection, Matrix4f view) {
        if (vao == 0) init();

        // upload 4 corner positions
        FloatBuffer fb = MemoryUtil.memAllocFloat(12);
        for (int i = 0; i < 4; i++) {
            fb.put(corners[i][0]).put(corners[i][1]).put(corners[i][2]);
        }
        fb.flip();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, fb);
        MemoryUtil.memFree(fb);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        shader.use();

        FloatBuffer mb = MemoryUtil.memAllocFloat(16);
        projection.get(mb.clear()); GL30.glUniformMatrix4fv(uProj, false, mb);
        view.get(mb.clear());       GL30.glUniformMatrix4fv(uView, false, mb);
        new Matrix4f().identity().get(mb.clear());
        GL30.glUniformMatrix4fv(uModel, false, mb);
        MemoryUtil.memFree(mb);

        if (uColor >= 0) GL30.glUniform4f(uColor, 0f, 0f, 0f, 1f); // black

        GL30.glEnable(GL30.GL_DEPTH_TEST);
        GL30.glLineWidth(1.5f);

        GL30.glBindVertexArray(vao);
        GL30.glDrawArrays(GL30.GL_LINE_LOOP, 0, 4);
        GL30.glBindVertexArray(0);

        GL30.glUseProgram(0);
    }
}
