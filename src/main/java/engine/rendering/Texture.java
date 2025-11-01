package engine.rendering;

import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.stb.STBImage;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;

public class Texture {
    private int id;
    private int width;
    private int height;

    public Texture(String filepath) {
        ByteBuffer image = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            STBImage.stbi_set_flip_vertically_on_load(true);

            // Try loading from classpath (inside jar)
            image = loadImageFromResource(filepath, w, h, channels);
            if (image == null) {
                // Fallback to disk (for dev convenience)
                image = STBImage.stbi_load(Path.of("./src/main", filepath).toString(), w, h, channels, 4);
                if (image == null) {
                    // Also try debug.png from disk (for dev)
                	image = STBImage.stbi_load(Path.of("./src/main", "resources/textures/debug.png").toString(), w, h, channels, 4);
                    if (image == null) {
                        // Also try debug.png from disk (for dev)
                        image = STBImage.stbi_load(Path.of("./src/main", "textures/debug.png").toString(), w, h, channels, 4);
                    }
                }
            }
            if (image == null) {
                throw new RuntimeException("Failed to load texture: " + filepath);
            }

            width = w.get();
            height = h.get();

            id = GL30.glGenTextures();
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, id);

            GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA, width, height, 0,
            		GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, image);

            GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST);
            GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST);

            STBImage.stbi_image_free(image);
        }
    }

    private ByteBuffer loadImageFromResource(String resourcePath, IntBuffer w, IntBuffer h, IntBuffer channels) {
        try {
            // Remove leading "./src/main" if present
            String res = resourcePath.replaceFirst("^\\.\\/src\\/main\\/?", "");
            // Try both with and without leading slash
            InputStream stream = Texture.class.getClassLoader().getResourceAsStream(res);
            if (stream == null) {
                stream = Texture.class.getClassLoader().getResourceAsStream("/" + res);
            }
            if (stream == null) return null;

            // Read stream into ByteBuffer
            ByteBuffer buffer = readStreamToByteBuffer(stream, 8192);
            return STBImage.stbi_load_from_memory(buffer, w, h, channels, 4);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Utility: read InputStream fully into direct ByteBuffer
    private static ByteBuffer readStreamToByteBuffer(InputStream stream, int bufferSize) throws IOException {
        byte[] data = stream.readAllBytes();
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    public void bind() {
    	GL30.glBindTexture(GL30.GL_TEXTURE_2D, id);
    }

    public void cleanup() {
    	GL30.glDeleteTextures(id);
    }
}