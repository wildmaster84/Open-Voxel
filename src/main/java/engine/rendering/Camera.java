package engine.rendering;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private float pitch, yaw;
    private Matrix4f projection;
    private final float FOV = 70f;
    private final float NEAR = 0.1f;
    private final float FAR = 1000f;
    private int width, height, renderDistance;

    public Camera(int width, int height, int levelY, int renderDistance) {
        this.width = width;
        this.height = height;
        this.renderDistance = renderDistance;
        position = new Vector3f(8, levelY, 30); // Look at terrain from above
        pitch = -30;
        yaw = 0;
        projection = new Matrix4f().perspective((float)Math.toRadians(FOV), (float)width/height, NEAR, FAR);
    }

    public Matrix4f getViewMatrix() {
        Matrix4f view = new Matrix4f();
        view.identity()
            .rotate((float)Math.toRadians(pitch), new Vector3f(1, 0, 0))
            .rotate((float)Math.toRadians(yaw), new Vector3f(0, 1, 0))
            .translate(new Vector3f(-position.x, -position.y - 1.2f, -position.z));
        return view;
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projection);
    }

    public Vector3f getPosition() {
        return position;
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

	public int getRenderDistance() {
		// TODO Auto-generated method stub
		return renderDistance;
	}
}