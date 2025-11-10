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
	int vsync = 0;
	int renderDistance = 4;
	private final String version = (DemoGame.class.getPackage().getImplementationVersion() == null ? "0.0.0-Debug"
			: DemoGame.class.getPackage().getImplementationVersion());;
	private GameEventManager eventManager;
	
	public VoxelEngine(int vsync, int renderDistance) {
		this.vsync = vsync;
		this.renderDistance = renderDistance;
	}

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
		GLFW.glfwSwapInterval(this.vsync);
	}

	private void initEngine() {
		eventManager = new GameEventManager();
		world = new World(2025L);
		camera = new Camera(WIDTH, HEIGHT, 95, this.renderDistance, world);
		physics = new PhysicsEngine(world, camera);
		input = new InputHandler(window, camera, physics, this);
		renderer = new Renderer(world, camera);
	}

	private void loop() {
		final float TICK_RATE = 60.0f; 
		final float TICK_DT = 1.0f / TICK_RATE;
		final int MAX_TICKS_PER_FRAME = 5;
		
		double lastTime = GLFW.glfwGetTime();
		float accumulator = 0f;
		int frames = 0;
		double lastFpsTime = lastTime;
		
		while (!GLFW.glfwWindowShouldClose(window) && running) {
			double now = GLFW.glfwGetTime();
			float frameTime = (float) (now - lastTime);
			lastTime = now;
			
			if (frameTime > 0.25f)
				frameTime = 0.25f;
			
			accumulator += frameTime;
			
			input.sampleInput();
			
			int ticksThisFrame = 0;
			while (accumulator >= TICK_DT && ticksThisFrame < MAX_TICKS_PER_FRAME) {
				input.applyMovement(TICK_DT);
				physics.tick(TICK_DT, input.isJumpPressed(), input.isCrouchPressed());
				renderer.tick(TICK_DT);
				accumulator -= TICK_DT;
				ticksThisFrame++;
			}
			
			renderer.render(input);
			
			GLFW.glfwSwapBuffers(window);
			GLFW.glfwPollEvents();
			
			frames++;
			if (now - lastFpsTime >= 1.0) {
				Vector3f pos = camera.getPosition();
				String title = String.format("Open-Voxel Engine - %s | FPS: %d | Pos: (%d, %d, %d) | Chunks: %s", version, frames,
						(int) pos.x, (int) pos.y, (int) pos.z, world.getChunks().entrySet().size());
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

}