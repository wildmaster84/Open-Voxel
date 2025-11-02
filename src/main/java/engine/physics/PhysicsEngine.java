package engine.physics;

import engine.rendering.Camera;
import engine.world.World;
import engine.world.Chunk;
import engine.world.Block;
import engine.world.BlockType;

import org.joml.Vector3f;

/**
 * Physics engine with robust AABB collision, jump, crouch.
 */
public class PhysicsEngine {
    private World world;
    private Camera camera;

    // Player AABB dimensions (centered at camera position)
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_DEPTH = 0.6f;
    private static final float CROUCH_HEIGHT = 1.0f;

    private float velocityY = 0;
    private boolean isOnGround = false;
    private boolean crouching = false;

    private final float gravity = -25f;
    private final float jumpVelocity = 8.5f;

    public PhysicsEngine(World world, Camera camera) {
        this.world = world;
        this.camera = camera;
    }

    public void update(float delta, boolean jumpPressed, boolean crouchPressed) {
        // Handle crouch
        crouching = crouchPressed;
        float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;

        // Handle jump
        if (jumpPressed && isOnGround) {
            velocityY = jumpVelocity;
            isOnGround = false;
        }

        // Apply gravity
        velocityY += gravity * delta;

        // Proposed movement
        Vector3f pos = camera.getPosition();
        float newY = pos.y + velocityY * delta;

        // AABB for proposed position
        float halfW = PLAYER_WIDTH / 2f;
        float halfD = PLAYER_DEPTH / 2f;
        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float minY = newY;
        float maxY = newY + playerHeight;
        float minZ = pos.z - halfD;
        float maxZ = pos.z + halfD;

        // Y collision
        boolean collidedY = false;
        for (float x = minX; x <= maxX; x += 0.3f)
        for (float z = minZ; z <= maxZ; z += 0.3f)
        for (float y = minY; y <= maxY; y += 0.3f) {
            Block block = getBlockAt(world, x, y, z);
            if (block != null && block.getType() != BlockType.AIR) {
                collidedY = true;
                break;
            }
        }
        if (collidedY) {
            // If moving down, snap to ground
            if (velocityY < 0) {
                pos.y = (float)Math.floor(pos.y);
                isOnGround = true;
            }
            velocityY = 0;
        } else {
            pos.y = newY;
            isOnGround = false;
        }

        // X & Z movement are handled in InputHandler with AABB check per axis (see below)
    }

    // Check if moving to (newX, currentY, currentZ) would collide
    public boolean canMoveToX(float newX) {
        Vector3f pos = camera.getPosition();
        float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
        float halfW = PLAYER_WIDTH / 2f;
        float halfD = PLAYER_DEPTH / 2f;
        float minX = newX - halfW;
        float maxX = newX + halfW;
        float minY = pos.y;
        float maxY = pos.y + playerHeight;
        float minZ = pos.z - halfD;
        float maxZ = pos.z + halfD;
        for (float x = minX; x <= maxX; x += 0.3f)
        for (float z = minZ; z <= maxZ; z += 0.3f)
        for (float y = minY; y <= maxY; y += 0.3f) {
            Block block = getBlockAt(world, x, y, z);
            if (block != null && block.getType() != BlockType.AIR) {
                return false;
            }
        }
        return true;
    }

    // Check if moving to (currentX, currentY, newZ) would collide
    public boolean canMoveToZ(float newZ) {
        Vector3f pos = camera.getPosition();
        float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
        float halfW = PLAYER_WIDTH / 2f;
        float halfD = PLAYER_DEPTH / 2f;
        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float minY = pos.y;
        float maxY = pos.y + playerHeight;
        float minZ = newZ - halfD;
        float maxZ = newZ + halfD;
        for (float x = minX; x <= maxX; x += 0.3f)
        for (float z = minZ; z <= maxZ; z += 0.3f)
        for (float y = minY; y <= maxY; y += 0.3f) {
            Block block = getBlockAt(world, x, y, z);
            if (block != null && block.getType() != BlockType.AIR) {
                return false;
            }
        }
        return true;
    }

    private Block getBlockAt(World world, float x, float y, float z) {
        int globalX = (int)Math.floor(x);
        int globalY = (int)Math.floor(y);
        int globalZ = (int)Math.floor(z);

        int chunkX = Math.floorDiv(globalX, Chunk.SIZE);
        int chunkZ = Math.floorDiv(globalZ, Chunk.SIZE);
        int localX = Math.floorMod(globalX, Chunk.SIZE);
        int localY = globalY;
        int localZ = Math.floorMod(globalZ, Chunk.SIZE);

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk == null || localX < 0 || localX >= Chunk.SIZE || localY < 0 || localY >= Chunk.HEIGHT || localZ < 0 || localZ >= Chunk.SIZE)
            return null;
        return chunk.getBlock(localX, localY, localZ);
    }
}