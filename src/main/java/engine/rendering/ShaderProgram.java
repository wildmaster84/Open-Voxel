package engine.rendering;

import org.lwjgl.opengl.GL30;

public class ShaderProgram {
    private final int programId;

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShaderId = createShader(vertexSource, GL30.GL_VERTEX_SHADER);
        int fragmentShaderId = createShader(fragmentSource, GL30.GL_FRAGMENT_SHADER);

        programId = GL30.glCreateProgram();
        GL30.glAttachShader(programId, vertexShaderId);
        GL30.glAttachShader(programId, fragmentShaderId);
        GL30.glLinkProgram(programId);

        if (GL30.glGetProgrami(programId, GL30.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Error linking shader code: " + GL30.glGetProgramInfoLog(programId));
        }

        GL30.glDeleteShader(vertexShaderId);
        GL30.glDeleteShader(fragmentShaderId);
    }

    private int createShader(String source, int type) {
        int shaderId = GL30.glCreateShader(type);
        GL30.glShaderSource(shaderId, source);
        GL30.glCompileShader(shaderId);

        if (GL30.glGetShaderi(shaderId, GL30.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Error compiling shader code: " + GL30.glGetShaderInfoLog(shaderId));
        }

        return shaderId;
    }

    public void use() {
    	GL30.glUseProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }
    
    public int getUniformLocation(String name) {
        int prog = getProgramId();
        return GL30.glGetUniformLocation(prog, name);
    }

    public void unuse() {
    	GL30.glUseProgram(0);
    }

    public void delete() {
    	GL30.glDeleteProgram(programId);
    }
}