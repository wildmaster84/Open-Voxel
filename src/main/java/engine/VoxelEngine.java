package engine;

import engine.rendering.Renderer;
import engine.world.Block;
import engine.world.BlockType;
import engine.world.Chunk;
import engine.world.World;
import engine.events.GameEventManager;
import engine.events.player.ClickEvent;
import engine.input.InputHandler;
import engine.physics.PhysicsEngine;
import engine.rendering.Camera;

import org.joml.Vector3f;
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
    private final String version = "Debug v0.0.3";
    private GameEventManager eventManager;

    public void start() {
        initGLFW();
        initEngine();
        registerEvents();
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
    	eventManager = new GameEventManager();
    	world = new World(2025L);
        camera = new Camera(WIDTH, HEIGHT, 95, 10, world);
        physics = new PhysicsEngine(world, camera);
        input = new InputHandler(window, camera, physics, this);
        renderer = new Renderer(world, camera);
    }

    private void loop() {
        double lastTime = GLFW.glfwGetTime();
        int frames = 0;
        double lastFpsTime = lastTime;
        while (!GLFW.glfwWindowShouldClose(window) && running) {
            double now = GLFW.glfwGetTime();
            float delta = (float) (now - lastTime);
            lastTime = now;
            
            if (delta > 0.1f) delta = 0.1f;

            input.pollEvents(delta);
            physics.update(delta, input.isJumpPressed(), input.isCrouchPressed());

            renderer.render(delta);

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
            frames++;
            if (now - lastFpsTime >= 1.0) {
                Vector3f pos = camera.getPosition();
                String title = String.format("Open-Voxel Engine - %s | FPS: %d | Pos: (%d, %d, %d)",
                    version, frames, (int)pos.x, (int)pos.y, (int)pos.z);
                GLFW.glfwSetWindowTitle(window, title);
                frames = 0;
                lastFpsTime = now;
            }
        }
    }

    public void cleanup() {
        renderer.cleanup();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }
    
    public GameEventManager getEventManager() {
    	return eventManager;
    }
    
    public void registerEvents() {
    	eventManager.register(ClickEvent.class, e -> {
    	    if (e.type == ClickEvent.ClickType.LEFT) {
    	    	world.setBlock(e.worldX, e.worldY, e.worldZ, new Block(BlockType.AIR));
    	    	renderer.invalidateBlock(e.worldX, e.worldY, e.worldZ);
    	    } else if (e.type == ClickEvent.ClickType.RIGHT) {
    	    	world.setBlock(e.worldX, e.worldY, e.worldZ, new Block(BlockType.DIRT));
    	    	renderer.invalidateBlock(e.worldX, e.worldY, e.worldZ);
    	    }
    	});
    }

    public static void main(String[] args) {
        new VoxelEngine().start();
    }
}