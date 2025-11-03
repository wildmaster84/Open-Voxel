package engine.physics;

import engine.rendering.Camera;
import engine.world.World;
import engine.world.Chunk;
import engine.world.Block;
import engine.world.BlockType;

import org.joml.Vector3f;

public class PhysicsEngine {
    private World world;
    private Camera camera;

    private static final float PLAYER_WIDTH  = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_DEPTH  = 0.6f;
    private static final float CROUCH_HEIGHT = 1.0f;
    private static final float EYE_HEIGHT    = 1.62f;

    private float velocityY = 0;
    private boolean isOnGround = false;
    private boolean crouching = false;

    private final float gravity    = -25f;
    private final float jumpVelocity  =  8.5f;

    private final float gravityWater  = -3.0f;
    private final float swimUpAccel   = 12.0f;
    private final float maxSwimUpVy   =  6.0f;
    private final float maxSwimDnVy   = -6.0f;

    public PhysicsEngine(World world, Camera camera) {
        this.world = world;
        this.camera = camera;
    }

    public void update(float delta, boolean jumpPressed, boolean crouchPressed) {
        crouching = crouchPressed;
        float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;

        Submersion sub = computeSubmersion(camera.getPosition());
        boolean inWater = sub.ratio > 0f;

        if (!inWater) {
            if (jumpPressed && isOnGround) {
                velocityY = jumpVelocity;
                isOnGround = false;
            }
            velocityY += gravity * delta;
        } else {
            if (jumpPressed) {
                velocityY += swimUpAccel * delta;
            } else {
                velocityY += gravityWater * delta;
            }
            if (velocityY >  maxSwimUpVy) velocityY =  maxSwimUpVy;
            if (velocityY <  maxSwimDnVy) velocityY =  maxSwimDnVy;
        }

        Vector3f pos = camera.getPosition();
        float newY = pos.y + velocityY * delta;

        float halfW = PLAYER_WIDTH / 2f;
        float halfD = PLAYER_DEPTH / 2f;
        float minX = pos.x - halfW, maxX = pos.x + halfW;
        float minZ = pos.z - halfD, maxZ = pos.z + halfD;
        float minY = newY,          maxY = newY + playerHeight;

        boolean collidedY = false;
        for (float x = minX; x <= maxX; x += 0.3f)
        for (float z = minZ; z <= maxZ; z += 0.3f)
        for (float y = minY; y <= maxY; y += 0.3f) {
            Block block = getBlockAt(world, x, y, z);
            if (block != null && block.getType() != BlockType.AIR && block.getType() != BlockType.WATER) {
                collidedY = true;
                break;
            }
        }

        if (collidedY) {
            if (velocityY < 0) {
                pos.y = (float)Math.floor(pos.y);
                isOnGround = true;
            }
            velocityY = 0;
        } else {
            pos.y = newY;
            isOnGround = false;
        }
    }

    public boolean canMoveToX(float newX) {
        Vector3f pos = camera.getPosition();
        float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
        float halfW = PLAYER_WIDTH / 2f;
        float halfD = PLAYER_DEPTH / 2f;
        float minX = newX - halfW, maxX = newX + halfW;
        float minZ = pos.z - halfD, maxZ = pos.z + halfD;
        float minY = pos.y,         maxY = pos.y + playerHeight;
        for (float x = minX; x <= maxX; x += 0.3f)
        for (float z = minZ; z <= maxZ; z += 0.3f)
        for (float y = minY; y <= maxY; y += 0.3f) {
            Block block = getBlockAt(world, x, y, z);
            if (block != null && block.getType() != BlockType.AIR && block.getType() != BlockType.WATER) return false;
        }
        return true;
    }

    public boolean canMoveToZ(float newZ) {
        Vector3f pos = camera.getPosition();
        float playerHeight = crouching ? CROUCH_HEIGHT : PLAYER_HEIGHT;
        float halfW = PLAYER_WIDTH / 2f;
        float halfD = PLAYER_DEPTH / 2f;
        float minX = pos.x - halfW, maxX = pos.x + halfW;
        float minZ = newZ - halfD,  maxZ = newZ + halfD;
        float minY = pos.y,         maxY = pos.y + playerHeight;
        for (float x = minX; x <= maxX; x += 0.3f)
        for (float z = minZ; z <= maxZ; z += 0.3f)
        for (float y = minY; y <= maxY; y += 0.3f) {
            Block block = getBlockAt(world, x, y, z);
            if (block != null && block.getType() != BlockType.AIR && block.getType() != BlockType.WATER) return false;
        }
        return true;
    }

    private static final class Submersion {
        boolean feet, torso, head;
        float ratio;
    }

    private Submersion computeSubmersion(Vector3f pos) {
        int gx = (int)Math.floor(pos.x);
        int gz = (int)Math.floor(pos.z);

        int yFeet  = (int)Math.floor(pos.y);
        int yTorso = (int)Math.floor(pos.y + EYE_HEIGHT * 0.5f);
        int yHead  = (int)Math.floor(pos.y + EYE_HEIGHT);

        Submersion s = new Submersion();
        s.feet  = isWaterAt(gx, yFeet,  gz);
        s.torso = isWaterAt(gx, yTorso, gz);
        s.head  = isWaterAt(gx, yHead,  gz);

        int count = (s.feet?1:0) + (s.torso?1:0) + (s.head?1:0);
        s.ratio = count / 3.0f;
        return s;
    }

    private boolean isWaterAt(int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        return b != null && b.getType() == BlockType.WATER;
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
