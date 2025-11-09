package engine;

import engine.rendering.Renderer;
import engine.ui.UIManager;
import engine.world.AbstractBlock;
import engine.world.Chunk;
import engine.world.World;
import engine.world.block.BlockState;
import engine.world.block.BlockType;
import engine.events.GameEventManager;
import engine.events.player.ClickEvent;
import engine.input.InputHandler;
import engine.input.InputHandler.Hit;
import engine.physics.PhysicsEngine;
import engine.rendering.Camera;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import demo.DemoGame;

public class VoxelEngine {
	private static VoxelEngine engine;
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
		engine = this;
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
		UIManager.get().setWindow(window);
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
				UIManager.get().tick((long)(TICK_DT * 1000));
				accumulator -= TICK_DT;
				ticksThisFrame++;
			}
			
			renderer.render(input);
			UIManager.get().render();
			
			GLFW.glfwSwapBuffers(window);
			GLFW.glfwPollEvents();
			
			frames++;
			if (now - lastFpsTime >= 1.0) {
				Vector3f pos = camera.getPosition();
				String title = String.format("Open-Voxel Engine - %s | FPS: %d | Pos: (%d, %d, %d) | Chunks: %s | Facing: %s", version, frames,
						(int) pos.x, (int) pos.y, (int) pos.z, world.getChunks().entrySet().size(), camera.getFacing());
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
		    final int x = e.worldX, y = e.worldY, z = e.worldZ;
		    if (y < 0 || y >= Chunk.HEIGHT) return;


		    final int lx = Math.floorMod(x, Chunk.SIZE);
		    final int lz = Math.floorMod(z, Chunk.SIZE);
		    final Chunk chunk = world.getChunk(x, y, z);
		    if (chunk == null) return;

		    if (e.type == ClickEvent.ClickType.LEFT) {
		        if (y >= 1) {
		            chunk.setBlock(lx, y, lz, new AbstractBlock(BlockType.AIR));
		            renderer.invalidateBlock(x, y, z);
		        }
		        return;
		    }

		    if (e.type == ClickEvent.ClickType.MIDDLE) {
		        camera.pickBlock();
		        return;
		    }

		    if (e.type == ClickEvent.ClickType.RIGHT) {
		        final AbstractBlock inHand = camera.getBlockInHand();
		        if (inHand == null) return;
		        final BlockType t = inHand.getType();
		        if (t == null || t == BlockType.AIR) return;

		        final Hit h = camera.getInputHandler().pickBlockFromCamera();
		        if (h == null) return;

		        int outState = inHand.getState();

		        if (t == BlockType.SLAB) {
		            if (h.y >= 0 && h.y < Chunk.HEIGHT) {
		                final int hcx = Math.floorDiv(h.x, Chunk.SIZE);
		                final int hcz = Math.floorDiv(h.z, Chunk.SIZE);
		                final int hlx = Math.floorMod(h.x, Chunk.SIZE);
		                final int hlz = Math.floorMod(h.z, Chunk.SIZE);
		                final engine.world.Chunk hChunk = world.getChunk(hcx, hcz);
		                if (hChunk != null) {
		                    final int existing = hChunk.getState(hlx, h.y, hlz);
		                    if (BlockType.fromId(BlockState.typeId(existing)) == BlockType.SLAB) {
		                        final int kind = BlockState.slabKind(existing);
		                        final boolean clickedTopFace    = (h.ny == +1);
		                        final boolean clickedBottomFace = (h.ny == -1);
		                        

		                        final boolean shouldMerge =
		                               (clickedTopFace    && kind == BlockState.SLAB_KIND_BOTTOM)
		                            || (clickedBottomFace && kind == BlockState.SLAB_KIND_TOP);

		                        if (shouldMerge) {
		                            final int merged = BlockState.asSlab(
		                                    BlockState.make(BlockType.SLAB.getId()),
		                                    BlockState.SLAB_KIND_DOUBLE
		                            );
		                            inHand.setState(merged);
		                            hChunk.setBlock(hlx, h.y, hlz, inHand);
		                            renderer.invalidateBlock(h.x, h.y, h.z);
		                            return;
		                        }
		                    }
		                }
		            }

		            final int slabKind =
		                    (h.ny == +1) ? BlockState.SLAB_KIND_BOTTOM :
		                    (h.ny == -1) ? BlockState.SLAB_KIND_TOP    :
		                                   BlockState.SLAB_KIND_BOTTOM;

		            outState = BlockState.asSlab(
		                    BlockState.make(BlockType.SLAB.getId()),
		                    slabKind
		            );
		        } else if (t == BlockType.STAIR) {
		            final boolean upside = (camera.getPosition().y + 1.5f) > (y + 0.5f);
		            outState = BlockState.asStairs(
		                    BlockState.make(BlockType.STAIR.getId()),
		                    camera.getFacingReversed().ordinal(),
		                    !upside
		            );
		        } else {
		        }

		        inHand.setState(outState);
		        chunk.setBlock(lx, y, lz, inHand);
		        renderer.invalidateBlock(x, y, z);
		    }
		});
	}
	
	public static VoxelEngine getEngine() {
		return engine;
	}

}