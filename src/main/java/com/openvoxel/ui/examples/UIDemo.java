package com.openvoxel.ui.examples;

import com.openvoxel.ui.UIManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * Simple demo application to test the UI/GUI backend.
 * 
 * <p>This demo creates a window and demonstrates the PauseMenu and PlayerInventory
 * examples. Press 'P' to toggle the pause menu and 'I' to toggle the inventory.</p>
 */
public class UIDemo {
    
    private long window;
    private final int WIDTH = 800;
    private final int HEIGHT = 600;
    
    private UIManager uiManager;
    private PauseMenu pauseMenu;
    private PlayerInventory inventory;
    private boolean showingPause = false;
    private boolean showingInventory = false;
    
    public static void main(String[] args) {
        new UIDemo().run();
    }
    
    public void run() {
        init();
        loop();
        cleanup();
    }
    
    private void init() {
        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set();
        
        // Initialize GLFW
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        
        // Create window
        window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "UI Demo - Press P for Pause, I for Inventory", 
                                      MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        
        // Setup OpenGL
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(1); // Enable vsync
        
        // Setup key callback
        GLFW.glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    if (showingPause) {
                        togglePauseMenu();
                    }
                } else if (key == GLFW.GLFW_KEY_P) {
                    togglePauseMenu();
                } else if (key == GLFW.GLFW_KEY_I) {
                    toggleInventory();
                } else if (key == GLFW.GLFW_KEY_Q) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
            }
        });
        
        // Setup mouse button callback
        GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                GLFW.glfwGetCursorPos(window, xpos, ypos);
                
                int[] width = new int[1];
                int[] height = new int[1];
                GLFW.glfwGetWindowSize(window, width, height);
                
                uiManager.handleMouseClick((int)xpos[0], (int)ypos[0], button, 
                                          width[0], height[0]);
            }
        });
        
        // Initialize UI system
        uiManager = UIManager.getInstance();
        
        // Create UI elements
        pauseMenu = new PauseMenu(() -> togglePauseMenu());
        inventory = new PlayerInventory();
        inventory.setSlotClickListener(slotIndex -> {
            System.out.println("Selected inventory slot: " + slotIndex);
        });
    }
    
    private void togglePauseMenu() {
        if (showingPause) {
            uiManager.popGUI(pauseMenu);
            showingPause = false;
            System.out.println("Pause menu closed");
        } else {
            uiManager.pushGUI(pauseMenu);
            showingPause = true;
            System.out.println("Pause menu opened");
        }
    }
    
    private void toggleInventory() {
        if (showingInventory) {
            uiManager.popGUI(inventory);
            showingInventory = false;
            System.out.println("Inventory closed");
        } else {
            uiManager.pushGUI(inventory);
            showingInventory = true;
            System.out.println("Inventory opened");
        }
    }
    
    private void loop() {
        // Set clear color
        GL11.glClearColor(0.1f, 0.1f, 0.2f, 1.0f);
        
        while (!GLFW.glfwWindowShouldClose(window)) {
            // Clear the framebuffer
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            
            // Get window size
            int[] width = new int[1];
            int[] height = new int[1];
            GLFW.glfwGetWindowSize(window, width, height);
            
            // Update viewport
            GL11.glViewport(0, 0, width[0], height[0]);
            
            // Render some 3D content placeholder
            render3DScene();
            
            // Render UI
            uiManager.render(width[0], height[0]);
            
            // Swap buffers and poll events
            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }
    
    private void render3DScene() {
        // Simple 3D scene placeholder - just a rotating triangle
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(-1, 1, -1, 1, -1, 1);
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glRotatef((float)(GLFW.glfwGetTime() * 50), 0, 0, 1);
        
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glColor3f(1.0f, 0.0f, 0.0f);
        GL11.glVertex2f(0.0f, 0.5f);
        GL11.glColor3f(0.0f, 1.0f, 0.0f);
        GL11.glVertex2f(-0.5f, -0.5f);
        GL11.glColor3f(0.0f, 0.0f, 1.0f);
        GL11.glVertex2f(0.5f, -0.5f);
        GL11.glEnd();
    }
    
    private void cleanup() {
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }
}
