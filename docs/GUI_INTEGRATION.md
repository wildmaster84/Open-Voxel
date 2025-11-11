# GUI Integration Guide

This document explains how to integrate the UI/GUI backend into the Open-Voxel game engine.

## Overview

The UI/GUI backend provides a simple framework for creating in-game menus and interfaces. It consists of:

- **UI**: Render-only components that draw off the game tick (e.g., HUDs, overlays)
- **GUI**: Interactive components that tick with the game loop and receive input events (e.g., inventories, menus)
- **UIManager**: Singleton manager that maintains stacks of active UIs and GUIs
- **GLUIRenderer**: Helper class with minimal OpenGL utilities for drawing UI elements

## Architecture

### UI vs GUI

- **UI** (extends `com.openvoxel.ui.UI`):
  - Render-only, no tick updates
  - Lifecycle: `onOpen()` → `render()` (every frame) → `onClose()`
  - Use for: HUDs, overlays, non-interactive visual elements

- **GUI** (extends `com.openvoxel.ui.GUI`):
  - Receives tick updates and input events
  - Lifecycle: `onOpen()` → `onTick()` (every game tick) + `render()` (every frame) → `onClose()`
  - Methods: `onMouseClick()`, `onKeyPress()`
  - Use for: Interactive menus, inventories, settings screens

### UIManager

The UIManager is a singleton that manages all active UIs and GUIs:

```java
UIManager.get().openUI(new PauseMenu(width, height));
UIManager.get().openGUI(new PlayerInventory(width, height));
UIManager.get().closeTopUI();
UIManager.get().closeTopGUI();
UIManager.get().closeAll();
```

## Integration Steps

### 0. Initialization (Required for Cursor Management)

Set the window handle in UIManager during engine initialization to enable automatic cursor management:

**File**: `engine/VoxelEngine.java`

**Location**: In the `initEngine()` method or after window creation

```java
private void initEngine() {
    eventManager = new GameEventManager();
    world = new World(2025L);
    camera = new Camera(WIDTH, HEIGHT, 95, this.renderDistance, world);
    physics = new PhysicsEngine(world, camera);
    input = new InputHandler(window, camera, physics, this);
    renderer = new Renderer(world, camera);
    
    // ADD THIS: Set window for cursor management
    com.openvoxel.ui.UIManager.get().setWindow(window);
}
```

This enables automatic cursor show/hide functionality when UIs/GUIs are opened/closed.

### 1. Game Tick Loop Integration

Add UIManager tick call to your main game loop where you update game logic:

**File**: `engine/VoxelEngine.java`

**Location**: In the `loop()` method, inside the tick loop

```java
// In VoxelEngine.loop(), inside the tick loop:
while (accumulator >= TICK_DT && ticksThisFrame < MAX_TICKS_PER_FRAME) {
    input.applyMovement(TICK_DT);
    physics.tick(TICK_DT, input.isJumpPressed(), input.isCrouchPressed());
    renderer.tick(TICK_DT);
    
    // ADD THIS: Tick all active GUIs
    com.openvoxel.ui.UIManager.get().tick((long)(TICK_DT * 1000)); // Convert to milliseconds
    
    accumulator -= TICK_DT;
    ticksThisFrame++;
}
```

### 2. Render Loop Integration

Add UIManager render call after your main rendering but before buffer swap:

**File**: `engine/VoxelEngine.java`

**Location**: In the `loop()` method, after `renderer.render()` and before `glfwSwapBuffers()`

```java
// In VoxelEngine.loop():
renderer.render(input);

// ADD THIS: Render all active UIs and GUIs
com.openvoxel.ui.UIManager.get().render();

GLFW.glfwSwapBuffers(window);
```

### 3. Input Handling Integration

Forward mouse clicks and key presses to UIManager.

#### Option A: Modify InputHandler (Recommended)

**File**: `engine/input/InputHandler.java`

Add a field to track if UI/GUI is consuming input:

```java
private void handleClicks() {
    double now = GLFW.glfwGetTime();
    boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    boolean middleDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    
    // ADD THIS: Forward clicks to UIManager if GUIs are active
    if (com.openvoxel.ui.UIManager.get().hasActiveGUIs()) {
        if (leftDown && !leftWasDown) {
            // Get mouse position
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(window, xpos, ypos);
            com.openvoxel.ui.UIManager.get().onMouseClick((int)xpos[0], (int)ypos[0], 0);
        }
        // Don't process game clicks when GUI is open
        leftWasDown = leftDown;
        middleWasDown = middleDown;
        rightWasDown = rightDown;
        return;
    }
    
    // Existing click handling code...
}
```

Add key event forwarding:

```java
private void handleClicks() {
    // ... existing mouse handling ...
    
    // Check for ESC key
    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
        // ADD THIS: Forward to UIManager first
        if (com.openvoxel.ui.UIManager.get().hasActiveGUIs()) {
            com.openvoxel.ui.UIManager.get().onKeyPress(GLFW.GLFW_KEY_ESCAPE);
            return;
        }
        
        // Original code to exit game
        voxelEngine.cleanup();
        System.exit(0);
    }
    
    hoverHit = pickBlockFromCamera();
}
```

#### Option B: Use GLFW Callbacks (Alternative)

Set up callbacks in VoxelEngine initialization:

```java
private void initGLFW() {
    // ... existing GLFW setup ...
    
    // ADD THIS: Mouse button callback for GUI input
    GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
        if (action == GLFW.GLFW_PRESS && com.openvoxel.ui.UIManager.get().hasActiveGUIs()) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(win, xpos, ypos);
            com.openvoxel.ui.UIManager.get().onMouseClick((int)xpos[0], (int)ypos[0], button);
        }
    });
    
    // ADD THIS: Key callback for GUI input
    GLFW.glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
        if (action == GLFW.GLFW_PRESS && com.openvoxel.ui.UIManager.get().hasActiveGUIs()) {
            com.openvoxel.ui.UIManager.get().onKeyPress(key);
        }
    });
}
```

### 4. Cursor Management (Automatic)

The UIManager automatically handles cursor visibility:

- **When a UI or GUI is opened**: Cursor is shown (set to GLFW_CURSOR_NORMAL)
- **When the last UI/GUI is closed**: Cursor is hidden (restored to GLFW_CURSOR_DISABLED)
- **Camera snap prevention**: The last mouse position is saved before showing the cursor and restored when hiding it, preventing camera jumps

**No manual cursor management required** - just call `UIManager.get().setWindow(window)` during initialization.

#### How it works:

1. When the first UI/GUI is opened, UIManager:
   - Saves the current cursor mode (disabled/normal)
   - Saves the current mouse position
   - Shows the cursor

2. When the last UI/GUI is closed, UIManager:
   - Restores the cursor to the saved position (prevents camera snap)
   - Hides the cursor if it was previously disabled

This ensures smooth transitions without camera jumps when opening/closing menus.

## Usage Examples

### Example 1: Opening a Pause Menu

```java
// Create and open an interactive pause menu GUI
import com.openvoxel.ui.UIManager;
import com.openvoxel.ui.examples.PauseMenu;

// In your game code (e.g., when P key is pressed):
UIManager.get().openGUI(new PauseMenu(windowWidth, windowHeight));

// The pause menu will:
// - Automatically show the cursor
// - Handle clicks on the Resume button
// - Handle ESC key to close
// - Automatically hide cursor when closed (without camera snap)
```

### Example 2: Opening an Inventory

```java
// Create and open an interactive inventory GUI
import com.openvoxel.ui.UIManager;
import com.openvoxel.ui.examples.PlayerInventory;

// In your game code (e.g., when E key is pressed):
UIManager.get().openGUI(new PlayerInventory(windowWidth, windowHeight));

// The inventory will automatically handle ESC key to close itself
```

### Example 3: Creating a Custom UI

```java
import com.openvoxel.ui.UI;
import com.openvoxel.ui.GLUIRenderer;

public class HealthBar extends UI {
    private int health = 100;
    
    @Override
    public void render() {
        GLUIRenderer.setup2DRendering(1280, 720);
        
        // Draw health bar background
        GLUIRenderer.drawRect(10, 10, 200, 30, 0.2f, 0.2f, 0.2f, 0.8f);
        
        // Draw health bar fill (red)
        float fillWidth = (health / 100.0f) * 200;
        GLUIRenderer.drawRect(10, 10, fillWidth, 30, 0.8f, 0.2f, 0.2f, 1.0f);
        
        // Draw border
        GLUIRenderer.drawBorder(10, 10, 200, 30, 2.0f, 0.5f, 0.5f, 0.5f, 1.0f);
        
        GLUIRenderer.restore3DRendering();
    }
    
    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
    }
}
```

### Example 4: Creating a Custom GUI

```java
import com.openvoxel.ui.GUI;
import com.openvoxel.ui.GLUIRenderer;
import com.openvoxel.ui.UIManager;
import org.lwjgl.glfw.GLFW;

public class SettingsMenu extends GUI {
    private int windowWidth;
    private int windowHeight;
    private float volume = 0.5f;
    
    public SettingsMenu(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
    }
    
    @Override
    public void onTick(long tickDelta) {
        // Update settings logic if needed
    }
    
    @Override
    public void render() {
        GLUIRenderer.setup2DRendering(windowWidth, windowHeight);
        
        // Draw settings panel
        float x = (windowWidth - 400) / 2;
        float y = (windowHeight - 300) / 2;
        GLUIRenderer.drawPanel(x, y, 400, 300, 0.15f, 0.15f, 0.15f, 0.95f);
        
        // Draw title
        GLUIRenderer.drawText("Settings", x + 150, y + 20);
        
        // Draw volume slider (simplified)
        GLUIRenderer.drawText("Volume:", x + 20, y + 60);
        GLUIRenderer.drawRect(x + 100, y + 60, 200, 20, 0.3f, 0.3f, 0.3f, 1.0f);
        GLUIRenderer.drawRect(x + 100, y + 60, volume * 200, 20, 0.5f, 0.7f, 0.5f, 1.0f);
        
        // Draw close button
        GLUIRenderer.drawButton(x + 150, y + 240, 100, 40, "Close",
                                0.4f, 0.4f, 0.4f, 1.0f);
        
        GLUIRenderer.restore3DRendering();
    }
    
    @Override
    public void onMouseClick(int x, int y, int button) {
        // Check if close button was clicked
        float btnX = (windowWidth - 400) / 2 + 150;
        float btnY = (windowHeight - 300) / 2 + 240;
        if (GLUIRenderer.isPointInRect(x, y, btnX, btnY, 100, 40)) {
            UIManager.get().closeTopGUI();
        }
        
        // Check volume slider clicks
        // ... implement slider logic ...
    }
    
    @Override
    public void onKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            UIManager.get().closeTopGUI();
        }
    }
}
```

## OpenGL State Management

The GLUIRenderer manages OpenGL state automatically:

- `setup2DRendering()` saves current matrices and sets up orthographic projection
- `restore3DRendering()` restores previous OpenGL state
- Always call these in pairs within your `render()` method

## Best Practices

1. **Always pair setup and restore calls**:
   ```java
   GLUIRenderer.setup2DRendering(width, height);
   // ... draw UI elements ...
   GLUIRenderer.restore3DRendering();
   ```

2. **Handle ESC key in GUIs**:
   ```java
   @Override
   public void onKeyPress(int key) {
       if (key == GLFW.GLFW_KEY_ESCAPE) {
           UIManager.get().closeTopGUI();
       }
   }
   ```

3. **Use UIManager methods consistently**:
   - `openUI()` / `openGUI()` for opening
   - `closeTopUI()` / `closeTopGUI()` for closing
   - `closeAll()` for cleanup (e.g., when exiting to main menu)

4. **Store window dimensions**:
   Pass window width/height to your UI/GUI constructors so they can render correctly at any resolution.

5. **Test with multiple UIs/GUIs**:
   The UIManager supports stacking, so test that your UI/GUI works correctly when opened on top of others.

## Troubleshooting

### UI not rendering
- Check that `UIManager.get().render()` is called in the render loop after main game rendering
- Verify that `setup2DRendering()` and `restore3DRendering()` are called correctly
- Ensure OpenGL context is current when rendering

### GUI not receiving clicks
- Verify that `UIManager.get().onMouseClick()` is being called with correct coordinates
- Check that the GUI is actually open with `UIManager.get().hasActiveGUIs()`
- Mouse coordinates should be in screen space (pixels from top-left)

### GUI not ticking
- Ensure `UIManager.get().tick()` is called from the game tick loop
- Pass tick delta in milliseconds (not seconds)

### Text not rendering properly
- The font system requires ARIAL.TTF to be present in the resources directory
- Place ARIAL.TTF in `./resources/` or `src/main/resources/`
- The system automatically loads the font on first use
- If font loading fails, check console for error messages

## Font System

The `GLUIRenderer` includes a fully functional bitmap font rendering system:

- **Font File Required**: Place `ARIAL.TTF` in `./resources/` or `src/main/resources/` directory
- **Automatic Initialization**: Font system initializes automatically on first `drawText()` call
- **Fallback Paths**: Also tries to load from system font paths as backup
- **Text Measurement**: Use `getTextWidth(text)` to measure text for layout calculations
- **Font Height**: Use `getFontHeight()` to get the font height in pixels

### Setting up the Font

1. Obtain ARIAL.TTF (or any TrueType font file)
2. Place it in one of these locations:
   - `./resources/ARIAL.TTF` (relative to working directory)
   - `src/main/resources/ARIAL.TTF` (will be packaged in JAR)
3. The font will be loaded automatically when text rendering is first used

**Note**: A DejaVuSans.ttf font is included in `src/main/resources/ARIAL.TTF` by default for convenience.

Example:
```java
// Draw text with automatic width calculation
String text = "Hello World";
float textWidth = GLUIRenderer.getTextWidth(text);
float centerX = (screenWidth - textWidth) / 2;
GLUIRenderer.drawText(text, centerX, 100);
```

## Future Enhancements

Consider these improvements for production use:

1. **Custom Fonts**: Add ability to load custom TTF font files
2. **Texture Support**: Add methods to draw textured panels and sprites
3. **Layout System**: Implement automatic layout management (anchors, padding, etc.)
4. **Widget Library**: Build reusable UI components (buttons, sliders, checkboxes)
5. **Event System**: Extend input handling to support hover, drag, scroll events
6. **Styling**: Add theme/style system for consistent UI appearance
7. **Performance**: Use vertex buffers and batched rendering for better performance

## Summary

The UI/GUI backend is now ready to use. Key integration points:

1. **Game Tick**: `UIManager.get().tick(tickDelta)` 
2. **Render Loop**: `UIManager.get().render()`
3. **Input Events**: `UIManager.get().onMouseClick()` and `UIManager.get().onKeyPress()`

See the example classes (`PauseMenu` and `PlayerInventory`) for complete implementation examples.
