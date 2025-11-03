package engine.input;

import engine.rendering.Camera;
import engine.world.Block;
import engine.world.BlockType;
import engine.VoxelEngine;
import engine.events.player.ClickEvent;
import engine.events.player.ClickEvent.ClickType;
import engine.physics.PhysicsEngine;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL20;
import org.joml.Vector3f;

public class InputHandler {
    private long window;
    private Camera camera;
    private PhysicsEngine physics;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private static final double CLICK_COOLDOWN_SEC = 0.25;
    private static final float  PICK_MAX_DIST = 6.0f;
    private double lastLeftClickTime = 0.0;
    private double lastRightClickTime = 0.0;
    private static final float PLAYER_WIDTH  = 0.6f;
    private static final float PLAYER_DEPTH  = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.62f;
    private static final float RAY_EPS = 1e-4f;
    private boolean leftWasDown  = false;
    private boolean rightWasDown = false;
    private VoxelEngine voxelEngine;

    public InputHandler(long window, Camera camera, PhysicsEngine physics, VoxelEngine voxelEngine) {
    	this.voxelEngine = voxelEngine;
        this.window = window;
        this.camera = camera;
        this.physics = physics;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }
            double xoffset = xpos - lastMouseX;
            double yoffset = ypos - lastMouseY;
            lastMouseX = xpos;
            lastMouseY = ypos;
            float sensitivity = 0.1f;
            camera.setYaw(camera.getYaw() + (float)xoffset * sensitivity);
            camera.setPitch(camera.getPitch() + (float)yoffset * sensitivity);
            if (camera.getPitch() > 89.0f) camera.setPitch(89.0f);
            if (camera.getPitch() < -89.0f) camera.setPitch(-89.0f);
        });
    }
    
    private boolean aabbIntersects(float minAx, float minAy, float minAz, float maxAx, float maxAy, float maxAz, float minBx, float minBy, float minBz, float maxBx, float maxBy, float maxBz) {
		return (minAx < maxBx && maxAx > minBx) && (minAy < maxBy && maxAy > minBy) && (minAz < maxBz && maxAz > minBz);
	}
    
    private boolean intersectsPlayer(int bx, int by, int bz) {
        float bMinX = bx,     bMinY = by,     bMinZ = bz;
        float bMaxX = bx + 1, bMaxY = by + 1, bMaxZ = bz + 1;

        Vector3f p = camera.getPosition();
        float halfW = PLAYER_WIDTH * 0.5f;
        float halfD = PLAYER_DEPTH * 0.5f;

        float pMinX = p.x - halfW;
        float pMaxX = p.x + halfW;
        float pMinZ = p.z - halfD;
        float pMaxZ = p.z + halfD;
        float pMinY = p.y;
        float pMaxY = p.y + PLAYER_HEIGHT;

        return aabbIntersects(pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ);
    }

    private boolean isAir(int x, int y, int z) {
        Block b = camera.getWorld().getBlock(x, y, z);
        return b == null || b.getType() == BlockType.AIR;
    }
    
    private static final class Hit {
        final int x, y, z;
        final int nx, ny, nz;
        Hit(int x, int y, int z, int nx, int ny, int nz) { this.x=x; this.y=y; this.z=z; this.nx=nx; this.ny=ny; this.nz=nz; }
    }

    private Hit pickBlockFromCamera() {
        Vector3f eye = new Vector3f(camera.getPosition()).add(0f, EYE_HEIGHT, 0f);

        float yawRad   = (float) Math.toRadians(camera.getYaw());
        float pitchRad = (float) Math.toRadians(camera.getPitch());

        float cosP = (float) Math.cos(pitchRad);
        float sinP = (float) Math.sin(pitchRad);
        float sinY = (float) Math.sin(yawRad);
        float cosY = (float) Math.cos(yawRad);

        Vector3f dir = new Vector3f(sinY * cosP, -sinP, -cosY * cosP);
        if (dir.lengthSquared() < 1e-6f) return null;

        int vx = (int) Math.floor(eye.x);
        int vy = (int) Math.floor(eye.y);
        int vz = (int) Math.floor(eye.z);

        int stepX = dir.x > 0 ? 1 : (dir.x < 0 ? -1 : 0);
        int stepY = dir.y > 0 ? 1 : (dir.y < 0 ? -1 : 0);
        int stepZ = dir.z > 0 ? 1 : (dir.z < 0 ? -1 : 0);

        final float invDx = dir.x != 0 ? 1f / Math.abs(dir.x) : Float.POSITIVE_INFINITY;
        final float invDy = dir.y != 0 ? 1f / Math.abs(dir.y) : Float.POSITIVE_INFINITY;
        final float invDz = dir.z != 0 ? 1f / Math.abs(dir.z) : Float.POSITIVE_INFINITY;

        float tMaxX, tMaxY, tMaxZ;
        if (stepX > 0) tMaxX = ((vx + 1) - eye.x) * invDx;
        else if (stepX < 0) tMaxX = (eye.x - vx) * invDx;
        else tMaxX = Float.POSITIVE_INFINITY;

        if (stepY > 0) tMaxY = ((vy + 1) - eye.y) * invDy;
        else if (stepY < 0) tMaxY = (eye.y - vy) * invDy;
        else tMaxY = Float.POSITIVE_INFINITY;

        if (stepZ > 0) tMaxZ = ((vz + 1) - eye.z) * invDz;
        else if (stepZ < 0) tMaxZ = (eye.z - vz) * invDz;
        else tMaxZ = Float.POSITIVE_INFINITY;

        float tDeltaX = stepX != 0 ? invDx : Float.POSITIVE_INFINITY;
        float tDeltaY = stepY != 0 ? invDy : Float.POSITIVE_INFINITY;
        float tDeltaZ = stepZ != 0 ? invDz : Float.POSITIVE_INFINITY;

        if (tMaxX < Float.POSITIVE_INFINITY) tMaxX += RAY_EPS;
        if (tMaxY < Float.POSITIVE_INFINITY) tMaxY += RAY_EPS;
        if (tMaxZ < Float.POSITIVE_INFINITY) tMaxZ += RAY_EPS;

        float t = 0f;
        while (t <= PICK_MAX_DIST) {
            int nx = 0, ny = 0, nz = 0;

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    vx += stepX; t = tMaxX; tMaxX += tDeltaX; nx = -stepX;
                } else {
                    vz += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; nz = -stepZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    vy += stepY; t = tMaxY; tMaxY += tDeltaY; ny = -stepY;
                } else {
                    vz += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; nz = -stepZ;
                }
            }

            if (t > PICK_MAX_DIST) break;

            Block b = camera.getWorld().getBlock(vx, vy, vz);
            if (b != null && b.getType() != BlockType.AIR) {
                return new Hit(vx, vy, vz, nx, ny, nz);
            }
        }
        return null;
    }

    public void pollEvents(float delta) {
        float runSpeed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ? 0.35f : 0.25f;
        float shiftSpeed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ? 0.12f : 0.25f;
        float moveSpeed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ? runSpeed : shiftSpeed;
        float yawRad = (float) Math.toRadians(camera.getYaw());
        float dX = 0, dZ = 0;
        Vector3f pos = camera.getPosition();
        double now = GLFW.glfwGetTime();
        
        GLFW.glfwSetWindowSizeCallback(window, (win, width, height) -> {
            GL20.glViewport(0, 0, width, height);
            camera.setAspect(width, height);
        });

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            dX += Math.sin(yawRad) * moveSpeed;
            dZ -= Math.cos(yawRad) * moveSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            dX -= Math.sin(yawRad) * moveSpeed;
            dZ += Math.cos(yawRad) * moveSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            dX -= Math.cos(yawRad) * moveSpeed;
            dZ -= Math.sin(yawRad) * moveSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            dX += Math.cos(yawRad) * moveSpeed;
            dZ += Math.sin(yawRad) * moveSpeed;
        }
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS && !leftWasDown && (now - lastLeftClickTime) >= CLICK_COOLDOWN_SEC) {
        	leftWasDown = true;
            Hit hit = pickBlockFromCamera();
            if (hit != null) voxelEngine.getEventManager().fire(new ClickEvent(ClickType.LEFT, hit.x, hit.y, hit.z));
            
            lastLeftClickTime = now;
            leftWasDown = false;
        }

        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS && !rightWasDown && (now - lastRightClickTime) >= CLICK_COOLDOWN_SEC) {
        	rightWasDown = true;
            Hit hit = pickBlockFromCamera();
            if (hit != null) {
            	int px = hit.x + hit.nx;
            	int py = hit.y + hit.ny;
            	int pz = hit.z + hit.nz;
            	if (isAir(px, py, pz) && !intersectsPlayer(px, py, pz)) voxelEngine.getEventManager().fire(new ClickEvent(ClickType.RIGHT, px, py, pz));
            }
            lastRightClickTime = now;
            rightWasDown = false;
        }
        
        if (dX != 0) {
            float newX = pos.x + dX;
            if (physics.canMoveToX(newX)) {
                pos.x = newX;
            }
        }
        if (dZ != 0) {
            float newZ = pos.z + dZ;
            if (physics.canMoveToZ(newZ)) {
                pos.z = newZ;
            }
        }
        
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
        	voxelEngine.cleanup();
        	System.exit(0);
        }
    }

    public boolean isJumpPressed() {
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }

    public boolean isCrouchPressed() {
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
    }
}