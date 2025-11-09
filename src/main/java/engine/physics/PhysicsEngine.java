package engine.physics;

import engine.world.World;
import engine.world.AbstractBlock;
import engine.world.Chunk;
import engine.world.block.BlockState;
import engine.world.block.BlockType;
import engine.world.block.Slab;
import engine.world.block.Stairs;
import engine.rendering.Camera;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class PhysicsEngine {
    private final World world;
    private final Camera camera;

    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_DEPTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float CROUCH_HEIGHT = 1.0f;
    private static final float EYE_HEIGHT = 1.62f;

    private float velocityY = 0f;
    private boolean isOnGround = false;
    private boolean crouching = false;
    private boolean jumpedThisTick = false;

    private static final float GRAVITY = -25f;
    private static final float JUMP_VELOCITY = 8f;

    private static final float GRAVITY_WATER = -2.0f;
    private static final float SWIM_UP_ACCEL = 6.0f;
    private static final float SWIM_VY_UP_MAX = 3.0f;
    private static final float SWIM_VY_DN_MAX = -1.3f;

    private static final float STEP_HEIGHT = 0.5f;
    private float pendingStepRise = 0f;

    public PhysicsEngine(World world, Camera camera) {
        this.world = world;
        this.camera = camera;
    }

    public boolean isOnGround() { return isOnGround; }

    public float consumeStepRise() {
        float r = pendingStepRise;
        pendingStepRise = 0f;
        return r;
    }

    private boolean allowStepUp() {
        return isOnGround && !jumpedThisTick && velocityY <= 1e-4f;
    }

    public void tick(float delta, boolean jumpPressed, boolean crouchPressed) {
        crouching = crouchPressed;
        final float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;

        Submersion sub = computeSubmersion(camera.getPosition());
        boolean inWater = sub.ratio > 0f;

        if (!inWater) {
            if (jumpPressed && isOnGround) {
                velocityY = JUMP_VELOCITY;
                isOnGround = false;
                jumpedThisTick = true;
            } else {
                jumpedThisTick = false;
            }
            velocityY += GRAVITY * delta;
        } else {
            jumpedThisTick = jumpPressed;
            if (jumpPressed) {
                velocityY += SWIM_UP_ACCEL * delta;
            } else {
                velocityY += GRAVITY_WATER * delta;
            }
            if (velocityY > SWIM_VY_UP_MAX) velocityY = SWIM_VY_UP_MAX;
            if (velocityY < SWIM_VY_DN_MAX) velocityY = SWIM_VY_DN_MAX;
        }

        Vector3f pos = camera.getPosition();
        AABB player = playerAABB(pos.x, pos.y, pos.z, playerHeight);

        float dy = velocityY * delta;
        List<AABB> colliders = collectNearbyColliders(player, 0f, dy, 0f);
        float resolvedDy = collideY(player, colliders, dy);

        if (resolvedDy != dy) {
            if (dy < 0) isOnGround = true;
            velocityY = 0f;
        } else {
            isOnGround = false;
        }

        player = player.offset(0f, resolvedDy, 0f);
        pos.y = player.minY;
    }

    public float resolveHorizontalX(float dx, float playerHeight) {
        Vector3f pos = camera.getPosition();
        AABB player = playerAABB(pos.x, pos.y, pos.z, playerHeight);
        List<AABB> colliders = collectNearbyColliders(player, dx, 0f, 0f);
        float resolvedDx = collideX(player, colliders, dx);

        if (Math.abs(resolvedDx - dx) > 1e-6f && STEP_HEIGHT > 0f && allowStepUp()) {
            float stepRise = tryStepUp(player, colliders, dx, 0f, STEP_HEIGHT);
            if (stepRise > 0f) {
                pendingStepRise = Math.max(pendingStepRise, stepRise);
                AABB raised = player.offset(0f, stepRise, 0f);
                List<AABB> col2 = collectNearbyColliders(raised, dx, 0f, 0f);
                resolvedDx = collideX(raised, col2, dx);
            }
        }
        return resolvedDx;
    }

    public float resolveHorizontalZ(float dz, float playerHeight) {
        Vector3f pos = camera.getPosition();
        AABB player = playerAABB(pos.x, pos.y, pos.z, playerHeight);
        List<AABB> colliders = collectNearbyColliders(player, 0f, 0f, dz);
        float resolvedDz = collideZ(player, colliders, dz);

        if (Math.abs(resolvedDz - dz) > 1e-6f && STEP_HEIGHT > 0f && allowStepUp()) {
            float stepRise = tryStepUp(player, colliders, 0f, dz, STEP_HEIGHT);
            if (stepRise > 0f) {
                pendingStepRise = Math.max(pendingStepRise, stepRise);
                AABB raised = player.offset(0f, stepRise, 0f);
                List<AABB> col2 = collectNearbyColliders(raised, 0f, 0f, dz);
                resolvedDz = collideZ(raised, col2, dz);
            }
        }
        return resolvedDz;
    }

    public boolean canMoveToX(float newX) {
        Vector3f pos = camera.getPosition();
        float h = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
        float dx = newX - pos.x;
        AABB player = playerAABB(pos.x, pos.y, pos.z, h);
        List<AABB> colliders = collectNearbyColliders(player, dx, 0f, 0f);
        float resolved = collideX(player, colliders, dx);
        return Math.abs(resolved - dx) < 1e-6f;
    }

    public boolean canMoveToZ(float newZ) {
        Vector3f pos = camera.getPosition();
        float h = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
        float dz = newZ - pos.z;
        AABB player = playerAABB(pos.x, pos.y, pos.z, h);
        List<AABB> colliders = collectNearbyColliders(player, 0f, 0f, dz);
        float resolved = collideZ(player, colliders, dz);
        return Math.abs(resolved - dz) < 1e-6f;
    }

    private AABB playerAABB(float x, float y, float z, float height) {
        float halfW = PLAYER_WIDTH * 0.5f;
        float halfD = PLAYER_DEPTH * 0.5f;
        return new AABB(x - halfW, y, z - halfD, x + halfW, y + height, z + halfD);
    }

    private List<AABB> collectNearbyColliders(AABB player, float dx, float dy, float dz) {
        final float PAD = 0.001f;
        float minX = Math.min(player.minX, player.minX + dx) - PAD;
        float minY = Math.min(player.minY, player.minY + dy) - PAD;
        float minZ = Math.min(player.minZ, player.minZ + dz) - PAD;
        float maxX = Math.max(player.maxX, player.maxX + dx) + PAD;
        float maxY = Math.max(player.maxY, player.maxY + dy) + PAD;
        float maxZ = Math.max(player.maxZ, player.maxZ + dz) + PAD;

        int x0 = (int) Math.floor(minX);
        int y0 = (int) Math.floor(minY);
        int z0 = (int) Math.floor(minZ);
        int x1 = (int) Math.floor(maxX);
        int y1 = (int) Math.floor(maxY);
        int z1 = (int) Math.floor(maxZ);

        ArrayList<AABB> out = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            if (y < 0 || y >= Chunk.HEIGHT) continue;
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int state = getStateAt(world, x, y, z);
                    if (state == 0) continue;

                    int typeId = BlockState.typeId(state);
                    BlockType type = BlockType.fromId(typeId);
                    if (type == null) continue;
                    if (type == BlockType.AIR || type == BlockType.WATER) continue;

                    getCollisionBoxesForState(state, out, x, y, z);
                }
            }
        }
        return out;
    }

    private static void getCollisionBoxesForState(int state, List<AABB> out, int bx, int by, int bz) {
    	AbstractBlock block = AbstractBlock.fromState(state);

        for (AABB local : block.getCollisionBoxes()) {
            out.add(local.offset(bx, by, bz));
        }
    }

    private float collideX(AABB player, List<AABB> colliders, float dx) {
        float out = dx;
        for (AABB c : colliders) out = c.collideX(player, out);
        return out;
    }
    private float collideY(AABB player, List<AABB> colliders, float dy) {
        float out = dy;
        for (AABB c : colliders) out = c.collideY(player, out);
        return out;
    }
    private float collideZ(AABB player, List<AABB> colliders, float dz) {
        float out = dz;
        for (AABB c : colliders) out = c.collideZ(player, out);
        return out;
    }

    private float tryStepUp(AABB player, List<AABB> ignored, float dx, float dz, float maxStep) {
        final float stepInc = 0.05f;
        float climbed = 0f;
        while (climbed < maxStep) {
            climbed += stepInc;
            AABB raised = player.offset(0f, climbed, 0f);
            List<AABB> col = collectNearbyColliders(raised, dx, 0f, dz);
            float dyFree = collideY(player, col, climbed);
            if (Math.abs(dyFree - climbed) > 1e-4f) continue;

            float support = collideY(raised, col, -0.25f);
            boolean hasSupport = (support < 0f) && (Math.abs(support) < 1.25f - 1e-4f);
            if (!hasSupport) continue;

            float dxFree = collideX(raised, col, dx);
            float dzFree = collideZ(raised, col, dz);
            if (Math.abs(dxFree - dx) < 1e-4f && Math.abs(dzFree - dz) < 1e-4f) {
                System.out.println(climbed);
                return climbed;
            }
        }
        return 0f;
    }
    
    private static final class Submersion {
        boolean feet, torso, head;
        float ratio;
    }

    private Submersion computeSubmersion(Vector3f pos) {
        int gx = (int) Math.floor(pos.x);
        int gz = (int) Math.floor(pos.z);
        int yFeet  = (int) Math.floor(pos.y);
        int yTorso = (int) Math.floor(pos.y + EYE_HEIGHT * 0.5f);
        int yHead  = (int) Math.floor(pos.y + EYE_HEIGHT);
        Submersion s = new Submersion();
        s.feet  = isWaterAt(gx, yFeet,  gz);
        s.torso = isWaterAt(gx, yTorso, gz);
        s.head  = isWaterAt(gx, yHead,  gz);
        int count = (s.feet ? 1 : 0) + (s.torso ? 1 : 0) + (s.head ? 1 : 0);
        s.ratio = count / 3.0f;
        return s;
    }

    private boolean isWaterAt(int x, int y, int z) {
        int state = getStateAt(world, x, y, z);
        return BlockState.typeId(state) == BlockType.WATER.getId();
    }

    private static int getStateAt(World world, int x, int y, int z) {
        return world.getBlock(x, y, z).getState();
    }

    public static final class AABB {
        public final float minX, minY, minZ;
        public final float maxX, maxY, maxZ;

        public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public AABB offset(float ox, float oy, float oz) {
            return new AABB(minX + ox, minY + oy, minZ + oz, maxX + ox, maxY + oy, maxZ + oz);
        }

        private boolean overlap1D(float a0, float a1, float b0, float b1) {
            return a1 > b0 && a0 < b1;
        }
        private boolean overlapX(AABB o) { return overlap1D(minX, maxX, o.minX, o.maxX); }
        private boolean overlapY(AABB o) { return overlap1D(minY, maxY, o.minY, o.maxY); }
        private boolean overlapZ(AABB o) { return overlap1D(minZ, maxZ, o.minZ, o.maxZ); }

        public float collideX(AABB moving, float dx) {
            if (!overlapY(moving) || !overlapZ(moving)) return dx;
            if (dx > 0f && moving.maxX <= minX)      dx = Math.min(dx, minX - moving.maxX);
            else if (dx < 0f && moving.minX >= maxX) dx = Math.max(dx, maxX - moving.minX);
            return dx;
        }
        public float collideY(AABB moving, float dy) {
            if (!overlapX(moving) || !overlapZ(moving)) return dy;
            if (dy > 0f && moving.maxY <= minY)      dy = Math.min(dy, minY - moving.maxY);
            else if (dy < 0f && moving.minY >= maxY) dy = Math.max(dy, maxY - moving.minY);
            return dy;
        }
        public float collideZ(AABB moving, float dz) {
            if (!overlapX(moving) || !overlapY(moving)) return dz;
            if (dz > 0f && moving.maxZ <= minZ)      dz = Math.min(dz, minZ - moving.maxZ);
            else if (dz < 0f && moving.minZ >= maxZ) dz = Math.max(dz, maxZ - moving.minZ);
            return dz;
        }
    }
}
