package engine;

import engine.rendering.Renderer;
import engine.world.AbstractBlock;
import engine.world.World;
import engine.world.block.BlockType;
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
import demo.DemoGame;

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
	private final String version = (DemoGame.class.getPackage().getImplementationVersion() == null ? "0.0.0-Debug"
			: getClass().getPackage().getImplementationVersion());;
	private GameEventManager eventManager;
	
	// Fixed timestep configuration
	private static final float TICK_RATE = 20.0f; // 20 ticks per second
	private static final float TICK_DELTA = 1.0f / TICK_RATE; // 0.05 seconds per tick
	private static final int MAX_TICKS_PER_FRAME = 10; // Prevent spiral of death

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
		world.setPhysicsEngine(physics);
		input = new InputHandler(window, camera, physics, this);
		renderer = new Renderer(world, camera);
	}

	private void loop() {
		double lastTime = GLFW.glfwGetTime();
		double accumulator = 0.0;
		int frames = 0;
		double lastFpsTime = lastTime;
		
		while (!GLFW.glfwWindowShouldClose(window) && running) {
			double now = GLFW.glfwGetTime();
			double frameTime = now - lastTime;
			lastTime = now;
			
			// Cap frame time to prevent spiral of death
			if (frameTime > 0.25) {
				frameTime = 0.25;
			}
			
			accumulator += frameTime;
			
			// Poll input events once per frame
			input.pollEvents((float) frameTime);
			
			// Fixed timestep tick loop
			int ticksThisFrame = 0;
			while (accumulator >= TICK_DELTA && ticksThisFrame < MAX_TICKS_PER_FRAME) {
				// Tick world logic
				world.tick(TICK_DELTA);
				
				// Tick renderer (time-of-day and animations)
				renderer.tick(TICK_DELTA);
				
				// Tick physics
				physics.update(TICK_DELTA, input.isJumpPressed(), input.isCrouchPressed());
				
				accumulator -= TICK_DELTA;
				ticksThisFrame++;
			}
			
			// Compute interpolation factor
			float interp = (float) (accumulator / TICK_DELTA);
			
			// Render with interpolation
			renderer.render(interp, input);
			
			GLFW.glfwSwapBuffers(window);
			GLFW.glfwPollEvents();
			
			frames++;
			if (now - lastFpsTime >= 1.0) {
				Vector3f pos = camera.getPosition();
				String title = String.format("Open-Voxel Engine - %s | FPS: %d | Pos: (%d, %d, %d)", version, frames,
						(int) pos.x, (int) pos.y, (int) pos.z);
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
			if (e.type == ClickEvent.ClickType.LEFT && e.worldY >= 1) {
				world.setBlock(e.worldX, e.worldY, e.worldZ, new AbstractBlock(BlockType.AIR));
				renderer.invalidateBlock(e.worldX, e.worldY, e.worldZ);
			} else if (e.type == ClickEvent.ClickType.RIGHT) {
				if (camera.getBlockInHand() != new AbstractBlock(BlockType.AIR)) {
					world.setBlock(e.worldX, e.worldY, e.worldZ, camera.getBlockInHand());
					renderer.invalidateBlock(e.worldX, e.worldY, e.worldZ);
				}
			} else if (e.type == ClickEvent.ClickType.MIDDLE) {
				camera.pickBlock();
			}
		});
	}

	public static void main(String[] args) {
		new VoxelEngine().start();
	}
}