package engine.rendering;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import engine.input.InputHandler;
import engine.input.InputHandler.Hit;
import engine.world.AbstractBlock;
import engine.world.AbstractBlock.Facing;
import engine.world.World;
import engine.world.Chunk;
import engine.world.block.BlockType;
import engine.world.block.BlockState;

public class Camera {
    private Vector3f position;
    private float pitch, yaw;
    private Matrix4f projection;
    private final float FOV = 71f;
    private final float NEAR = 0.1f;
    private final float FAR = 1000f;
    private int width, height, renderDistance;
    private World world;
    private InputHandler input;

    private AbstractBlock block = new AbstractBlock(BlockType.AIR);

    public Camera(int width, int height, int levelY, int renderDistance, World world) {
        this.world = world;
        this.width = width;
        this.height = height;
        this.renderDistance = renderDistance;
        position = new Vector3f(8, levelY, 30);
        pitch = -30;
        yaw = 0;
        projection = new Matrix4f().perspective((float)Math.toRadians(FOV), (float)width/height, NEAR, FAR);
    }
    
    public void setInput(InputHandler input) {
        this.input = input;
    }

    public Matrix4f getViewMatrix() {
        Matrix4f view = new Matrix4f();
        view.identity()
            .rotate((float)Math.toRadians(pitch), new Vector3f(1, 0, 0))
            .rotate((float)Math.toRadians(yaw),   new Vector3f(0, 1, 0))
            .translate(new Vector3f(-position.x, -position.y - 1.6f, -position.z));
        return view;
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projection);
    }

    public Vector3f getPosition() {
        return position;
    }
    
    public Vector3f getLookDirection() {
        float yawRad = (float)Math.toRadians(getYaw());
        float pitchRad = (float)Math.toRadians(getPitch());
        return new Vector3f(
            (float)(Math.cos(pitchRad) * Math.sin(yawRad)),
            (float)(-Math.sin(pitchRad)),
            (float)(-Math.cos(pitchRad) * Math.cos(yawRad))
        ).normalize();
    }
    
    public AbstractBlock.Facing getFacing() {
        float yaw = ((this.yaw % 360f) + 360f) % 360f;
        if (yaw >= 45f && yaw < 135f)   return Facing.EAST;
        if (yaw >= 135f && yaw < 225f)  return Facing.SOUTH;
        if (yaw >= 225f && yaw < 315f)  return Facing.WEST;
        return Facing.NORTH;
    }
    
    public AbstractBlock.Facing getFacingReversed() {
        float yaw = ((this.yaw % 360f) + 360f) % 360f;
        if (yaw >= 45f && yaw < 135f)   return Facing.WEST;
        if (yaw >= 135f && yaw < 225f)  return Facing.NORTH;
        if (yaw >= 225f && yaw < 315f)  return Facing.EAST;
        return Facing.SOUTH;
    }

    public void move(Vector3f offset) {
        position.add(offset);
    }

    public void setPitch(float pitch) { this.pitch = pitch; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public float getYaw() { return yaw; }

    public void setPosition(Vector3f pos) { this.position.set(pos); }
    public void setAspect(int w, int h) {
        this.width = w;
        this.height = h;
        projection = new Matrix4f().perspective((float)Math.toRadians(FOV), (float)width/height, NEAR, FAR);
    }
    
    public int[] getAspect() {
        return new int[]{width, height};
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public World getWorld() {
        return world;
    }
    
    public InputHandler getInputHandler() {
        return input;
    }

    public void pickBlock() {
        Hit h = input.pickBlockFromCamera();
        if (h == null) return;

        if (h.y < 0 || h.y >= Chunk.HEIGHT) return;

        final int cx = Math.floorDiv(h.x, Chunk.SIZE);
        final int cz = Math.floorDiv(h.z, Chunk.SIZE);
        final Chunk c = world.getChunk(cx, cz);
        if (c == null) return;

        final int lx = Math.floorMod(h.x, Chunk.SIZE);
        final int lz = Math.floorMod(h.z, Chunk.SIZE);
        
        this.block = c.getBlock(lx, h.y, lz);
    }
    
    public AbstractBlock getBlockInHand() {
        return block;
    }
    
    public void setBlockInHand(AbstractBlock block) {
        this.block = block;
    }
}
