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
import engine.light.LightEngine;
import engine.physics.PhysicsEngine;
import engine.rendering.Camera;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

public class VoxelEngine {
	private static VoxelEngine engine;
	private long window;
	private final int WIDTH = 1280;
	private final int HEIGHT = 720;
	// World-side systems: null while in menu mode (see enterWorld/exitWorld).
	private Renderer renderer;
	private World world;
	private Camera camera;
	private InputHandler input;
	private PhysicsEngine physics;
	private static LightEngine lightEngine;
	private boolean worldActive = false;

	/**
	 * When set (multiplayer), block edits are routed here instead of being
	 * applied locally; the sink's echo (BLOCK_UPDATE) applies them.
	 */
	public interface BlockEditSink {
		boolean applyBlockEdit(int x, int y, int z, AbstractBlock block);
	}
	private BlockEditSink blockEditSink;
	private Runnable worldTickHook; // per game tick, world-active only (net position updates)
	private Runnable exitToMenuHandler; // game-provided "back to main menu" action

	public void setBlockEditSink(BlockEditSink sink) { this.blockEditSink = sink; }
	public void setWorldTickHook(Runnable hook) { this.worldTickHook = hook; }
	public void setExitToMenuHandler(Runnable handler) { this.exitToMenuHandler = handler; }
	public Runnable getExitToMenuHandler() { return exitToMenuHandler; }
	private boolean running = true;
	int vsync = 0;
	int renderDistance = 4;
	private String version = "dev";
	private GameEventManager eventManager;

	public VoxelEngine(int vsync, int renderDistance) {
		this.vsync = vsync;
		this.renderDistance = renderDistance;
		engine = this;
	}

	/** Shown in the window title. Callers (game client) set their own version. */
	public void setVersion(String v) { this.version = v; }

	public void start() {
		initGLFW();
		initBase();
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

	/** Window + UI only — no world. The game opens its menu screens now. */
	private void initBase() {
		eventManager = new GameEventManager();
		UIManager.get().setWindow(window);
		installMenuInput();
	}

	/**
	 * Menu-mode input routing. In-game input lives in InputHandler (created in
	 * enterWorld, which POLLS keys/buttons rather than registering callbacks,
	 * so these stay installed) — gate on !worldActive so a press is never
	 * handled twice (once here, once by the in-game poll).
	 */
	private void installMenuInput() {
		GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
		// Resize handling lives here (process lifetime), not in InputHandler —
		// InputHandler only exists while a world is active, so in menu mode the
		// viewport never tracked the window and layouts broke on resize.
		GLFW.glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
			GL11.glViewport(0, 0, w, h);
			if (camera != null) camera.setAspect(w, h);
		});
		GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
			// GUI clicks route here in both modes — the in-game poll only
			// handles block clicks when NO GUI is open, so nothing is double-fired.
			if (action == GLFW.GLFW_PRESS && UIManager.get().hasActiveGUIs()) {
				double[] cx = new double[1], cy = new double[1];
				GLFW.glfwGetCursorPos(window, cx, cy);
				UIManager.get().onMouseClick((int) cx[0], (int) cy[0], button);
			}
		});
		GLFW.glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
			if (action == GLFW.GLFW_PRESS && UIManager.get().hasActiveGUIs()) {
				UIManager.get().onKeyPress(key);
			}
		});
		GLFW.glfwSetCharCallback(window, (win, codepoint) -> {
			if (UIManager.get().hasActiveGUIs()) {
				UIManager.get().onCharTyped(codepoint);
			}
		});
		GLFW.glfwSetCursorPosCallback(window, null); // no mouse-look in menu mode
	}

	/** Creates world-side systems and switches the loop to in-game rendering. */
	public void enterWorld(long seed) {
		exitWorld(); // no-op if already in menu mode
		enterWorld(new World(seed));
	}

	/** Enters an externally-provided world (e.g. a network-fed RemoteWorld). */
	public void enterWorld(World w) {
		exitWorld(); // no-op if already in menu mode
		world = w;
		lightEngine = new LightEngine();
		camera = new Camera(WIDTH, HEIGHT, 95, this.renderDistance, world);
		physics = new PhysicsEngine(world, camera);
		input = new InputHandler(window, camera, physics, this);
		renderer = new Renderer(world, camera);
		worldActive = true;
	}

	/** Tears down world-side systems, back to menu mode. */
	public void exitWorld() {
		if (!worldActive) return;
		worldActive = false;
		worldTickHook = null;
		blockEditSink = null;
		UIManager.get().closeAll();
		installMenuInput();
		renderer.cleanup();
		renderer = null;
		input = null;
		physics = null;
		camera = null;
		world = null;
	}

	public boolean isWorldActive() { return worldActive; }
	public World getWorld() { return world; }
	public Camera getCamera() { return camera; }
	public Renderer getRenderer() { return renderer; }
	public void stop() { running = false; }

	private void loop() {
	    final float GAME_TICK_RATE = 60.0f;
	    final float GAME_DT = 1.0f / GAME_TICK_RATE;
	    final int   MAX_GAME_TICKS_PER_FRAME = 5;

	    final float WORLD_TICK_RATE = 20.0f;
	    final float WORLD_DT = 1.0f / WORLD_TICK_RATE;
	    final int   MAX_WORLD_TICKS_PER_FRAME = 2;

	    double lastTime = GLFW.glfwGetTime();
	    float gameAccum  = 0f;
	    float worldAccum = 0f;
	    float renderAccum = 0f;

	    int frames = 0;
	    double lastFpsTime = lastTime;

	    while (!GLFW.glfwWindowShouldClose(window) && running) {
	    	if (worldActive) {
	    		renderer.render(input);
	    	} else {
	    		// Menu mode: flat backdrop, screens draw on top.
	    		GL11.glClearColor(0.11f, 0.11f, 0.13f, 1.0f);
	    		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	    	}
	        UIManager.get().render();
	        
	        double now = GLFW.glfwGetTime();
	        float frameTime = (float) (now - lastTime);
	        lastTime = now;

	        if (frameTime > 0.25f) frameTime = 0.25f;

	        gameAccum  += frameTime;
	        worldAccum += frameTime;
	        renderAccum += frameTime;

	        frames++;
	        if (now - lastFpsTime >= 1.0) {
	        	if (worldActive) {
		            Vector3f pos = camera.getPosition();
		            String title = String.format(
		                "Open-Voxel Engine - %s | FPS: %d | Pos: (%d, %d, %d) | Chunks: %s | Facing: %s",
		                version, frames,
		                (int) pos.x, (int) pos.y, (int) pos.z,
		                world.getChunks().entrySet().size(),
		                camera.getFacing()
		            );
		            GLFW.glfwSetWindowTitle(window, title);
	        	} else {
	        		GLFW.glfwSetWindowTitle(window, "Open-Voxel - " + version + " | FPS: " + frames);
	        	}
	            frames = 0;
	            lastFpsTime = now;
	        }

	        if (worldActive) input.sampleInput();

	        int gameTicks = 0;
	        while (gameAccum >= GAME_DT && gameTicks < MAX_GAME_TICKS_PER_FRAME) {
        	if (worldActive) {
	            input.applyMovement(GAME_DT);
	            physics.tick(GAME_DT, input.isJumpPressed(), input.isCrouchPressed());
	            renderer.tick(GAME_DT);
	            if (worldTickHook != null) worldTickHook.run();
        	}
	            UIManager.get().tick((long)(GAME_DT * 1000));

	            gameAccum -= GAME_DT;
	            gameTicks++;
	        }

	        int worldTicks = 0;
	        while (worldAccum >= WORLD_DT && worldTicks < MAX_WORLD_TICKS_PER_FRAME) {
	        	if (worldActive) world.tick(camera, WORLD_DT); // can be slow, but capped
	            worldAccum -= WORLD_DT;
	            worldTicks++;
	        }
	        
	        //renderer.rebuildLightingInView();

	        GLFW.glfwSwapBuffers(window);
	        GLFW.glfwPollEvents();

	        
	    }
	}

	public void cleanup() {
		if (renderer != null) renderer.cleanup();
		GLFW.glfwDestroyWindow(window);
		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
	}

	public GameEventManager getEventManager() {
		return eventManager;
	}

	public void registerEvents() {
		eventManager.register(ClickEvent.class, e -> {
			if (!worldActive) return;
		    final int x = e.worldX, y = e.worldY, z = e.worldZ;
		    if (y < 0 || y >= Chunk.HEIGHT) return;


		    final int lx = Math.floorMod(x, Chunk.SIZE);
		    final int lz = Math.floorMod(z, Chunk.SIZE);
		    final Chunk chunk = world.getChunk(x, y, z);
		    if (chunk == null) return;

	    if (e.type == ClickEvent.ClickType.LEFT) {
	        if (y >= 1) {
	            AbstractBlock air = new AbstractBlock(BlockType.AIR);
	            if (blockEditSink == null || !blockEditSink.applyBlockEdit(x, y, z, air)) {
	            chunk.setBlock(lx, y, lz, air);
	            renderer.invalidateBlock(x, y, z);
	            }
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
	                            if (blockEditSink == null || !blockEditSink.applyBlockEdit(h.x, h.y, h.z, inHand)) {
	                                hChunk.setBlock(hlx, h.y, hlz, inHand);
	                                renderer.invalidateBlock(h.x, h.y, h.z);
	                            }
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
	        if (blockEditSink == null || !blockEditSink.applyBlockEdit(x, y, z, inHand)) {
	            chunk.setBlock(lx, y, lz, inHand);
	            renderer.invalidateBlock(x, y, z);
	        }
	    }
	});
	}
	
	public static VoxelEngine getEngine() {
		return engine;
	}
	public static LightEngine getLightEngine() {
		return lightEngine;
	}


}
