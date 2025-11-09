package engine.input;

import engine.rendering.Camera;
import engine.ui.PauseMenu;
import engine.ui.UIManager;
import engine.world.AbstractBlock;
import engine.world.Chunk;
import engine.world.World;
import engine.world.block.BlockState;
import engine.world.block.BlockType;
import engine.VoxelEngine;
import engine.events.player.ClickEvent;
import engine.events.player.ClickEvent.ClickType;
import engine.gui.PlayerInventory;
import engine.physics.PhysicsEngine;
import engine.physics.PhysicsEngine.AABB;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL30;
import org.joml.Vector3f;

public class InputHandler {
    private long window;
    private Camera camera;
    private PhysicsEngine physics;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    private static final double CLICK_COOLDOWN_SEC = 0.05;
    private static final float PICK_MAX_DIST = 6.0f;

    private double lastLeftClickTime = 0.0;
    private double lastMiddleClickTime = 0.0;
    private double lastRightClickTime = 0.0;
    private double keyPressTime = 0.0;

    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_DEPTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.62f;
    private static final float RAY_EPS = 1e-4f;

    private boolean leftWasDown = false;
    private boolean middleWasDown = false;
    private boolean rightWasDown = false;
    private boolean escWasDown = false;
    private boolean eWasDown = false;

    private Hit hoverHit;
    private VoxelEngine voxelEngine;

    private boolean wPressed, sPressed, aPressed, dPressed;
    private boolean sprintPressed, crouchPressed;
    public boolean paused = false;

    PauseMenu pauseMenu;
    PlayerInventory inventoryMenu;

    public InputHandler(long window, Camera camera, PhysicsEngine physics, VoxelEngine voxelEngine) {
        this.voxelEngine = voxelEngine;
        this.window = window;
        this.camera = camera;
        this.physics = physics;
        camera.setInput(this);

        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (paused) return;

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
            camera.setYaw(camera.getYaw() + (float) xoffset * sensitivity);
            camera.setPitch(camera.getPitch() + (float) yoffset * sensitivity);
            if (camera.getPitch() > 89.0f) camera.setPitch(89.0f);
            if (camera.getPitch() < -89.0f) camera.setPitch(-89.0f);
        });

        GLFW.glfwSetWindowSizeCallback(window, (win, width, height) -> {
            GL30.glViewport(0, 0, width, height);
            camera.setAspect(width, height);
        });

        pauseMenu = new PauseMenu(camera, camera.getAspect()[0], camera.getAspect()[1]);
        inventoryMenu = new PlayerInventory(camera);
    }

    private boolean aabbIntersects(float minAx, float minAy, float minAz, float maxAx, float maxAy, float maxAz,
                                   float minBx, float minBy, float minBz, float maxBx, float maxBy, float maxBz) {
        return (minAx < maxBx && maxAx > minBx)
            && (minAy < maxBy && maxAy > minBy)
            && (minAz < maxBz && maxAz > minBz);
    }

    private boolean intersectsPlayer(int bx, int by, int bz) {
        float bMinX = bx, bMinY = by, bMinZ = bz;
        float bMaxX = bx + 1, bMaxY = by + 1, bMaxZ = bz + 1;
        Vector3f p = camera.getPosition();
        float halfW = PLAYER_WIDTH * 0.5f;
        float halfD = PLAYER_DEPTH * 0.5f;
        float pMinX = p.x - halfW, pMaxX = p.x + halfW;
        float pMinZ = p.z - halfD, pMaxZ = p.z + halfD;
        float pMinY = p.y, pMaxY = p.y + PLAYER_HEIGHT;
        return aabbIntersects(pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ);
    }

    private static int getStateAt(World world, int x, int y, int z) {
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        int lx = Math.floorMod(x, Chunk.SIZE);
        int lz = Math.floorMod(z, Chunk.SIZE);
        if (y < 0 || y >= Chunk.HEIGHT) return BlockState.make(BlockType.AIR.getId());
        Chunk c = world.getChunk(cx, cz);
        if (c == null) return BlockState.make(BlockType.AIR.getId());
        return c.getState(lx, y, lz);
    }

    private boolean isAir(int x, int y, int z) {
        int state = getStateAt(camera.getWorld(), x, y, z);
        return BlockState.typeId(state) == BlockType.AIR.getId() || BlockState.typeId(state) == BlockType.WATER.getId();
    }
    
    private boolean isSlab(int x, int y, int z) {
        int state = getStateAt(camera.getWorld(), x, y, z);
        AbstractBlock block = new AbstractBlock(state);
        return block.getType() == BlockType.SLAB && !block.isSlabDouble();
    }

    public static final class Hit {
        public final int x, y, z;
        public final int nx, ny, nz;

        Hit(int x, int y, int z, int nx, int ny, int nz) {
            this.x = x; this.y = y; this.z = z;
            this.nx = nx; this.ny = ny; this.nz = nz;
        }
    }

    public Hit getHoverHit() { return hoverHit; }

    public Hit pickBlockFromCamera() {
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
        if (stepX > 0)      tMaxX = ((vx + 1) - eye.x) * invDx;
        else if (stepX < 0) tMaxX = (eye.x - vx) * invDx;
        else                tMaxX = Float.POSITIVE_INFINITY;

        if (stepY > 0)      tMaxY = ((vy + 1) - eye.y) * invDy;
        else if (stepY < 0) tMaxY = (eye.y - vy) * invDy;
        else                tMaxY = Float.POSITIVE_INFINITY;

        if (stepZ > 0)      tMaxZ = ((vz + 1) - eye.z) * invDz;
        else if (stepZ < 0) tMaxZ = (eye.z - vz) * invDz;
        else                tMaxZ = Float.POSITIVE_INFINITY;

        float tDeltaX = stepX != 0 ? invDx : Float.POSITIVE_INFINITY;
        float tDeltaY = stepY != 0 ? invDy : Float.POSITIVE_INFINITY;
        float tDeltaZ = stepZ != 0 ? invDz : Float.POSITIVE_INFINITY;

        if (tMaxX < Float.POSITIVE_INFINITY) tMaxX += RAY_EPS;
        if (tMaxY < Float.POSITIVE_INFINITY) tMaxY += RAY_EPS;
        if (tMaxZ < Float.POSITIVE_INFINITY) tMaxZ += RAY_EPS;

        World w = camera.getWorld();

        float bestT = Float.POSITIVE_INFINITY;
        Hit bestHit = null;
        float t = 0f;

        while (t <= PICK_MAX_DIST) {
            int nx = 0, ny = 0, nz = 0;

            // Standard DDA step
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    vx += stepX;
                    t = tMaxX;
                    tMaxX += tDeltaX;
                    nx = -stepX;
                } else {
                    vz += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    nz = -stepZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    vy += stepY;
                    t = tMaxY;
                    tMaxY += tDeltaY;
                    ny = -stepY;
                } else {
                    vz += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    nz = -stepZ;
                }
            }
            if (t > PICK_MAX_DIST) break;

            int state = getStateAt(w, vx, vy, vz);
            if (BlockState.typeId(state) == BlockType.AIR.getId()) continue;

            AbstractBlock block = w.getBlock(vx, vy, vz);
            if (block == null) continue;

            for (AABB localBox : block.getCollisionBoxes()) {
                // convert local [0..1] box to world coordinates
                float minX = vx + localBox.minX;
                float minY = vy + localBox.minY;
                float minZ = vz + localBox.minZ;
                float maxX = vx + localBox.maxX;
                float maxY = vy + localBox.maxY;
                float maxZ = vz + localBox.maxZ;

                int[] nOut = new int[3];
                float tHit = rayIntersectAABB(eye, dir,
                        minX, minY, minZ,
                        maxX, maxY, maxZ,
                        nOut);

                if (tHit >= 0f && tHit <= PICK_MAX_DIST && tHit < bestT) {
                    bestT = tHit;
                    bestHit = new Hit(vx, vy, vz, nOut[0], nOut[1], nOut[2]);
                }
            }
        }

        return bestHit;
    }

    
 // returns entry t or +INF if no hit; outNormal[0..2] = face normal
    private float rayIntersectAABB(Vector3f origin, Vector3f dir,
                                   float minX, float minY, float minZ,
                                   float maxX, float maxY, float maxZ,
                                   int[] outNormal) {
        float tmin = 0f;
        float tmax = PICK_MAX_DIST;

        int hitAxis = -1;
        int hitSign = 0;

        // X
        if (Math.abs(dir.x) < 1e-6f) {
            if (origin.x < minX || origin.x > maxX) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dir.x;
            float t1 = (minX - origin.x) * inv;
            float t2 = (maxX - origin.x) * inv;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) {
                tmin = t1;
                hitAxis = 0;
                hitSign = dir.x > 0f ? -1 : 1;
            }
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return Float.POSITIVE_INFINITY;
        }

        // Y
        if (Math.abs(dir.y) < 1e-6f) {
            if (origin.y < minY || origin.y > maxY) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dir.y;
            float t1 = (minY - origin.y) * inv;
            float t2 = (maxY - origin.y) * inv;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) {
                tmin = t1;
                hitAxis = 1;
                hitSign = dir.y > 0f ? -1 : 1;
            }
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return Float.POSITIVE_INFINITY;
        }

        // Z
        if (Math.abs(dir.z) < 1e-6f) {
            if (origin.z < minZ || origin.z > maxZ) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dir.z;
            float t1 = (minZ - origin.z) * inv;
            float t2 = (maxZ - origin.z) * inv;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) {
                tmin = t1;
                hitAxis = 2;
                hitSign = dir.z > 0f ? -1 : 1;
            }
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return Float.POSITIVE_INFINITY;
        }

        if (hitAxis == -1) return Float.POSITIVE_INFINITY;

        outNormal[0] = 0;
        outNormal[1] = 0;
        outNormal[2] = 0;
        if (hitAxis == 0)      outNormal[0] = hitSign;
        else if (hitAxis == 1) outNormal[1] = hitSign;
        else                   outNormal[2] = hitSign;

        return tmin;
    }



    public void sampleInput() {
        wPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        sPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        aPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        dPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        sprintPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        crouchPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;

        handleClicks();
    }

    public void applyMovement(float delta) {
        if (paused) return;

        float moveSpeed = 5.0f;
        if (sprintPressed) moveSpeed = 7.0f;
        if (crouchPressed) moveSpeed = 1.4f;

        float yawRad = (float) Math.toRadians(camera.getYaw());
        float dX = 0f, dZ = 0f;

        if (wPressed) { dX += (float) Math.sin(yawRad) * moveSpeed * delta; dZ -= (float) Math.cos(yawRad) * moveSpeed * delta; }
        if (sPressed) { dX -= (float) Math.sin(yawRad) * moveSpeed * delta; dZ += (float) Math.cos(yawRad) * moveSpeed * delta; }
        if (aPressed) { dX -= (float) Math.cos(yawRad) * moveSpeed * delta; dZ -= (float) Math.sin(yawRad) * moveSpeed * delta; }
        if (dPressed) { dX += (float) Math.cos(yawRad) * moveSpeed * delta; dZ += (float) Math.sin(yawRad) * moveSpeed * delta; }

        Vector3f pos = camera.getPosition();
        float playerHeight = crouchPressed ? 1.0f : 1.8f;

        if (dX != 0f) {
            float allowedX = physics.resolveHorizontalX(dX, playerHeight);
            if (Math.abs(allowedX) > 1e-6f) pos.x += allowedX;
            float stepRise = physics.consumeStepRise();
            if (stepRise > 0f) pos.y += stepRise;
        }
        if (dZ != 0f) {
            float allowedZ = physics.resolveHorizontalZ(dZ, playerHeight);
            if (Math.abs(allowedZ) > 1e-6f) pos.z += allowedZ;
            float stepRise = physics.consumeStepRise();
            if (stepRise > 0f) pos.y += stepRise;
        }
    }

    private void handleClicks() {
        double now = GLFW.glfwGetTime();
        boolean leftDown   = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT)   == GLFW.GLFW_PRESS;
        boolean middleDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        boolean rightDown  = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT)  == GLFW.GLFW_PRESS;
        boolean escDown    = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        boolean eDown      = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_E)       == GLFW.GLFW_PRESS;

        if (leftDown && !leftWasDown && (now - lastLeftClickTime) >= CLICK_COOLDOWN_SEC) {
            lastLeftClickTime = now;
            if (UIManager.get().hasActiveGUIs()) {
                double[] xpos = new double[1], ypos = new double[1];
                GLFW.glfwGetCursorPos(window, xpos, ypos);
                UIManager.get().onMouseClick((int)xpos[0], (int)ypos[0], GLFW.GLFW_MOUSE_BUTTON_LEFT);
                leftWasDown = leftDown;
                return;
            }
            Hit hit = pickBlockFromCamera();
            if (hit != null) {
                voxelEngine.getEventManager().fire(new ClickEvent(ClickType.LEFT, hit.x, hit.y, hit.z));
            }
        }

        if (middleDown && !middleWasDown && (now - lastMiddleClickTime) >= CLICK_COOLDOWN_SEC) {
            lastMiddleClickTime = now;
            if (UIManager.get().hasActiveGUIs()) {
                double[] xpos = new double[1], ypos = new double[1];
                GLFW.glfwGetCursorPos(window, xpos, ypos);
                UIManager.get().onMouseClick((int)xpos[0], (int)ypos[0], GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
                middleWasDown = middleDown;
                return;
            }
            Hit hit = pickBlockFromCamera();
            if (hit != null) {
                voxelEngine.getEventManager().fire(new ClickEvent(ClickType.MIDDLE, hit.x, hit.y, hit.z));
            }
        }

        if (rightDown && !rightWasDown && (now - lastRightClickTime) >= CLICK_COOLDOWN_SEC) {
            lastRightClickTime = now;
            if (UIManager.get().hasActiveGUIs()) {
                double[] xpos = new double[1], ypos = new double[1];
                GLFW.glfwGetCursorPos(window, xpos, ypos);
                UIManager.get().onMouseClick((int)xpos[0], (int)ypos[0], GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                rightWasDown = rightDown;
                return;
            }
            Hit hit = pickBlockFromCamera();
            if (hit != null) {
                int px = hit.x + hit.nx;
                int py = hit.y + hit.ny;
                int pz = hit.z + hit.nz;
                if ((isAir(px, py, pz) || isSlab(px, py, pz)) && !intersectsPlayer(px, py, pz)) {
                    voxelEngine.getEventManager().fire(new ClickEvent(ClickType.RIGHT, px, py, pz));
                }
            }
        }

        if (escDown && !escWasDown && (now - keyPressTime) >= CLICK_COOLDOWN_SEC) {
            keyPressTime = now;
            if (UIManager.get().hasActiveGUIs()) {
                UIManager.get().onKeyPress(GLFW.GLFW_KEY_ESCAPE);
            } else {
                UIManager.get().openGUI(pauseMenu);
            }
            escWasDown = escDown;
            return;
        }

        if (eDown && !eWasDown && (now - keyPressTime) >= CLICK_COOLDOWN_SEC) {
            keyPressTime = now;
            if (UIManager.get().hasActiveGUIs()) {
                UIManager.get().onKeyPress(GLFW.GLFW_KEY_E);
            } else {
                UIManager.get().openGUI(inventoryMenu);
            }
            eWasDown = eDown;
            return;
        }

        leftWasDown = leftDown;
        middleWasDown = middleDown;
        rightWasDown = rightDown;
        escWasDown = escDown;
        eWasDown = eDown;

        hoverHit = pickBlockFromCamera();
    }

    public boolean isJumpPressed() {
        if (paused) return false;
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }

    public boolean isCrouchPressed() {
        if (paused) return false;
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
