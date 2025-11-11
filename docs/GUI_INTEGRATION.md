# GUI Integration Guide

This guide explains how to integrate and use the Open-Voxel UI/GUI backend in your game.

## Overview

The Open-Voxel UI/GUI system provides a simple, dependency-free way to create user interfaces with built-in text rendering. The system includes:

- **UI/GUI Interfaces**: Base interfaces for creating custom UI elements
- **UIManager**: Singleton manager for UI/GUI elements and input handling
- **GLUIRenderer**: OpenGL-based renderer with embedded bitmap font
- **Example implementations**: PauseMenu and PlayerInventory

## Core Components

### UI and GUI Interfaces

Both `UI` and `GUI` interfaces require implementation of two main methods:

```java
void render(GLUIRenderer renderer, int windowWidth, int windowHeight);
boolean onMouseClick(int x, int y, int button, int windowWidth, int windowHeight);
```

**Important**: The `onMouseClick` method now includes `windowWidth` and `windowHeight` parameters. These are essential for proper layout calculations and click detection. Use these dimensions to calculate element positions consistently with your render method.

### UIManager

The `UIManager` is a singleton that manages all UI and GUI elements:

```java
UIManager manager = UIManager.getInstance();

// Add UI elements
manager.pushUI(myUI);
manager.pushGUI(myGUI);

// Remove UI elements
manager.popUI();
manager.popGUI();

// Clear all
manager.clearAll();
```

**Rendering**: Call `UIManager.render(windowWidth, windowHeight)` in your render loop to draw all active UI/GUI elements.

**Input Handling**: Call `UIManager.handleMouseClick(x, y, button, windowWidth, windowHeight)` to forward mouse clicks to UI/GUI elements. The method signature requires window width and height parameters to ensure proper click detection.

### GLUIRenderer

The renderer provides several drawing methods:

#### Drawing Primitives

```java
// Filled rectangle
renderer.drawFilledRect(x, y, width, height, r, g, b, a);

// Panel with border
renderer.drawPanel(x, y, width, height, 
                  bgR, bgG, bgB, bgA,
                  borderR, borderG, borderB, borderA);
```

#### Text Rendering

The renderer includes an embedded 8x8 bitmap font supporting ASCII characters 32-126:

```java
renderer.drawText("Hello World", x, y, scale, r, g, b, a);
```

- **scale**: Text scale factor (1.0 = 8x8 pixels per character)
- **r, g, b, a**: Color components (0.0-1.0)

**Note**: The embedded font is monochrome and supports basic ASCII only. Unsupported characters are rendered as spaces.

## Integration Steps

### 1. Initialize UIManager

The UIManager is a singleton and initializes itself on first access:

```java
UIManager uiManager = UIManager.getInstance();
```

### 2. Add to Render Loop

In your main game loop, render the UI after your 3D scene:

```java
// Render 3D scene
renderer.render();

// Render UI/GUI
uiManager.render(windowWidth, windowHeight);

// Swap buffers
glfwSwapBuffers(window);
```

### 3. Handle Input

Forward mouse clicks to the UIManager. **Important**: You must provide window dimensions:

```java
// In your mouse callback or input handler
public void onMouseClick(int x, int y, int button) {
    int windowWidth = getWindowWidth();   // Get from GLFW or your window manager
    int windowHeight = getWindowHeight(); // Get from GLFW or your window manager
    
    boolean handled = uiManager.handleMouseClick(x, y, button, 
                                                 windowWidth, windowHeight);
    if (!handled) {
        // Handle game input (e.g., block placement/breaking)
    }
}
```

The click coordinates should be in window space:
- `x`: 0 = left edge of window
- `y`: 0 = top edge of window

### 4. Create Custom UI Elements

Implement the `UI` or `GUI` interface:

```java
public class MyCustomUI implements UI {
    @Override
    public void render(GLUIRenderer renderer, int windowWidth, int windowHeight) {
        // Calculate centered position using provided dimensions
        float centerX = windowWidth / 2.0f;
        float centerY = windowHeight / 2.0f;
        
        // Draw UI elements
        renderer.drawText("My UI", centerX - 40, centerY, 2.0f, 
                        1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    @Override
    public boolean onMouseClick(int x, int y, int button, 
                               int windowWidth, int windowHeight) {
        // Calculate bounds using provided dimensions
        float centerX = windowWidth / 2.0f;
        float centerY = windowHeight / 2.0f;
        
        // Check if click is within bounds
        // Return true if handled, false otherwise
        return false;
    }
}
```

**Important Layout Considerations**:
- Always use the provided `windowWidth` and `windowHeight` parameters for layout calculations
- Calculate positions in both `render()` and `onMouseClick()` using the same formulas
- This ensures UI elements appear where you expect them and clicks are detected correctly

## Example Usage

### Pause Menu

```java
// Create pause menu
PauseMenu pauseMenu = new PauseMenu(() -> {
    // Resume callback
    UIManager.getInstance().popGUI(pauseMenu);
});

// Show pause menu
UIManager.getInstance().pushGUI(pauseMenu);
```

### Inventory

```java
// Create inventory
PlayerInventory inventory = new PlayerInventory();
inventory.setSlotClickListener(slotIndex -> {
    System.out.println("Clicked slot: " + slotIndex);
});

// Show inventory
UIManager.getInstance().pushGUI(inventory);

// Hide inventory later
UIManager.getInstance().popGUI(inventory);
```

## Best Practices

1. **Use window dimensions consistently**: Always calculate layout using the provided `windowWidth` and `windowHeight` parameters in both rendering and input handling
2. **Clean up**: Remove UI elements when they're no longer needed using `popUI()` or `popGUI()`
3. **Layer management**: UIs are rendered below GUIs. Use the appropriate interface for your use case
4. **Input blocking**: Override `blocksInput()` to control whether a UI/GUI blocks input to elements beneath it
5. **Keep it simple**: The embedded font is basic. For complex UIs, consider implementing custom text rendering with external fonts
6. **Performance**: The renderer uses immediate mode OpenGL, which is simple but not optimized for complex UIs with many elements

## Troubleshooting

### Text doesn't appear
- Ensure you're calling `renderer.drawText()` with a valid scale > 0
- Check that the color alpha value is > 0
- Verify the text position is within the window bounds

### Clicks don't register
- **Most common issue**: Verify you're passing `windowWidth` and `windowHeight` to `handleMouseClick()`
- Ensure you're calculating bounds consistently between `render()` and `onMouseClick()`
- Check that your `onMouseClick()` method returns `true` when the click is handled
- Verify click coordinates are in window space (0,0 = top-left)

### UI doesn't render
- Ensure `UIManager.render()` is called after 3D rendering but before buffer swap
- Check that the UI element has been added to the UIManager with `pushUI()` or `pushGUI()`
- Verify OpenGL state is not interfering (depth test, blend mode, etc.)

## Technical Notes

- **OpenGL Version**: Uses OpenGL 1.1 immediate mode for maximum compatibility
- **Font Format**: 8x8 monochrome bitmap, one byte per row
- **Coordinate System**: Top-left origin (0,0 = top-left corner of window)
- **Thread Safety**: UIManager is not thread-safe. All UI operations should occur on the main/render thread
- **Window Dimensions**: Must be provided to both `render()` and `handleMouseClick()` for proper layout and input handling

## Migration Notes

If you're updating from an older version of the UI system:

1. **Update `onMouseClick` signatures**: Add `windowWidth` and `windowHeight` parameters to all `onMouseClick` implementations
2. **Update `handleMouseClick` calls**: Pass window dimensions when calling `UIManager.handleMouseClick()`
3. **Update layout calculations**: Use provided dimensions instead of hardcoded values or cached dimensions

## API Reference

### UI Interface
- `void render(GLUIRenderer renderer, int windowWidth, int windowHeight)`
- `boolean onMouseClick(int x, int y, int button, int windowWidth, int windowHeight)`
- `default boolean blocksInput()` - Returns true

### GUI Interface
- `void render(GLUIRenderer renderer, int windowWidth, int windowHeight)`
- `boolean onMouseClick(int x, int y, int button, int windowWidth, int windowHeight)`
- `default boolean blocksInput()` - Returns true

### UIManager Methods
- `static UIManager getInstance()`
- `void pushUI(UI ui)`
- `void popUI(UI ui)`
- `UI popUI()`
- `void pushGUI(GUI gui)`
- `void popGUI(GUI gui)`
- `GUI popGUI()`
- `void clearUIs()`
- `void clearGUIs()`
- `void clearAll()`
- `void render(int windowWidth, int windowHeight)`
- `boolean handleMouseClick(int x, int y, int button, int windowWidth, int windowHeight)`
- `boolean hasActiveElements()`
- `GLUIRenderer getRenderer()`

### GLUIRenderer Methods
- `void drawFilledRect(float x, float y, float width, float height, float r, float g, float b, float a)`
- `void drawPanel(float x, float y, float width, float height, float bgR, float bgG, float bgB, float bgA, float borderR, float borderG, float borderB, float borderA)`
- `void drawText(String text, float x, float y, float scale, float r, float g, float b, float a)`
- `void begin2D(int windowWidth, int windowHeight)`
- `void end2D()`
