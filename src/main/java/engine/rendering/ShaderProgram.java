package engine.rendering;

import org.lwjgl.opengl.GL20;

public class ShaderProgram {
    private final int programId;

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShaderId = createShader(vertexSource, GL20.GL_VERTEX_SHADER);
        int fragmentShaderId = createShader(fragmentSource, GL20.GL_FRAGMENT_SHADER);

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Error linking shader code: " + GL20.glGetProgramInfoLog(programId));
        }

        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
    }

    private int createShader(String source, int type) {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Error compiling shader code: " + GL20.glGetShaderInfoLog(shaderId));
        }

        return shaderId;
    }

    public void use() {
        GL20.glUseProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }

    public void delete() {
        GL20.glDeleteProgram(programId);
    }
}