package engine.input;

import engine.rendering.Camera;
import engine.physics.PhysicsEngine;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;

public class InputHandler {
    private long window;
    private Camera camera;
    private PhysicsEngine physics;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    public InputHandler(long window, Camera camera, PhysicsEngine physics) {
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

    public void pollEvents(float delta) {
        float moveSpeed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ? 0.12f : 0.25f;

        float yawRad = (float) Math.toRadians(camera.getYaw());
        Vector3f pos = camera.getPosition();

        float dX = 0, dZ = 0;

        // Forward/back
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            dX += Math.sin(yawRad) * moveSpeed;
            dZ -= Math.cos(yawRad) * moveSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            dX -= Math.sin(yawRad) * moveSpeed;
            dZ += Math.cos(yawRad) * moveSpeed;
        }
        // Left/right
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            dX -= Math.cos(yawRad) * moveSpeed;
            dZ -= Math.sin(yawRad) * moveSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            dX += Math.cos(yawRad) * moveSpeed;
            dZ += Math.sin(yawRad) * moveSpeed;
        }

        // Per-axis AABB collision
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
    }

    public boolean isJumpPressed() {
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }

    public boolean isCrouchPressed() {
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
    }
}