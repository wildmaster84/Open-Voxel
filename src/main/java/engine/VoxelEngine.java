package engine;

import engine.rendering.Renderer;
import engine.world.World;
import engine.input.InputHandler;
import engine.physics.PhysicsEngine;
import engine.rendering.Camera;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/**
 * Main entry for the voxel engine.
 */
public class VoxelEngine {
    private long window;
    private final int WIDTH = 1280;
    private final int HEIGHT = 720;
    private Renderer renderer;
    private World world;
    private Camera camera;
    private InputHandler input;
    private PhysicsEngine physics;
    private boolean running = true;

    public void start() {
        initGLFW();
        initEngine();
        loop();
        cleanup();
    }

    private void initGLFW() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "Open-Voxel Engine", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);
    }

    private void initEngine() {
        camera = new Camera(WIDTH, HEIGHT);
        world = new World();
        physics = new PhysicsEngine(world, camera);
        input = new InputHandler(window, camera, physics);
        renderer = new Renderer(world, camera);
    }

    private void loop() {
        double lastTime = GLFW.glfwGetTime();
        while (!GLFW.glfwWindowShouldClose(window) && running) {
            double now = GLFW.glfwGetTime();
            float delta = (float) (now - lastTime);
            lastTime = now;

            input.pollEvents(delta);
            physics.update(delta, input.isJumpPressed(), input.isCrouchPressed());

            renderer.render();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }

    private void cleanup() {
        renderer.cleanup();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }

    public static void main(String[] args) {
        new VoxelEngine().start();
    }
}