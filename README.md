# PlasmaFX - Interactive Plasma Fractal Generator

PlasmaFX is a JavaFX application that generates and animates interactive plasma fractals in real time.

## 🚀 Features

- **Fractal Generation**: Uses the diamond-square algorithm to create unique plasma patterns
- **Smooth Animation**: Renders at 60 FPS with continuous morphing effects
- **Interactivity**: Effect intensifies around the mouse cursor
- **Adjustable roughness**: A slider reshapes the pattern live, keeping its palette, from a
  smooth gradient to coarse noise
- **Dynamic Restart**: Generates new patterns without restarting the application

## 🧩 Architecture

### Main Classes

- `PlasmaFX`: Main JavaFX application. Handles UI, mouse events, and animation lifecycle
- `PlasmaRenderer`: Rendering engine implementing plasma logic and the diamond-square algorithm

### Algorithm Overview

1. **Initial Generation**: Diamond-square algorithm creates the base pattern
2. **Color Palette**: 256 procedurally generated colors, pre-packed as ARGB values
3. **Animation**: Random copying of pixel regions to simulate motion
4. **Interactivity**: Effect intensifies within a 50 px radius around the mouse
5. **Roughness**: The factor that scales every random displacement can be changed at runtime;
   the pattern is redrawn with the same seed, so the shape morphs instead of being replaced

## 📦 Requirements

- Java 11 or higher
- JavaFX 11 or higher

## ▶️ Usage

Compile and run with the JavaFX SDK on the module path:

```bash
javac --module-path "C:/path/to/javafx-sdk-XX/lib" --add-modules javafx.controls,javafx.graphics \
      -d bin src/principal/*.java

java --module-path "C:/path/to/javafx-sdk-XX/lib" --add-modules javafx.controls,javafx.graphics \
     -cp bin principal.PlasmaFX
```

The canvas size can be given in pixels, between 64 and 3840; any other value is rejected
with a warning and the default size is used instead:

```bash
java ... -cp bin principal.PlasmaFX --width=1024 --height=768
```

### Controls

- **Mouse movement**: Intensifies the plasma effect around the cursor
- **Roughness slider** (0 to 30, marks every 5): Deforms the current pattern live; 0 gives a
  smooth gradient and 30 a coarse one
- **Restart button**: Generates a new plasma pattern

## ⚙️ Configuration

The constants at the top of `PlasmaRenderer` tune the effect:

- `BASE_COPY_COUNT` (300) / `BASE_COPY_SIZE` (30 px): base effect intensity and block size
- `MOUSE_COPY_COUNT` (50) / `MOUSE_COPY_SIZE` (20 px): extra effect around the cursor
- `MOUSE_RADIUS` (50 px): radius of the area affected by the cursor
- `COPY_JITTER` (1 px): maximum displacement of a copied block
- `PALETTE_SIZE` (256) / `MAX_CHANNEL` (254): number of colors and channel ceiling
- `DEFAULT_DISPLACEMENT_FACTOR` (5) / `MIN`-`MAX_DISPLACEMENT_FACTOR` (0-30): initial roughness
  and the range offered by the slider

## ⚡ Performance

- The pixels live in a single `int[]`; the `WritableImage` is only a mirror of it
- The grid is subdivided with integer arithmetic (first half and remainder), which tiles the
  image with no gap and is about 1.8x faster than floating point geometry
- Region copies are `System.arraycopy` calls on that array: no `PixelReader`, no allocation
- The fractal is uploaded to the image with a single bulk `setPixels` call
- Random numbers come from `ThreadLocalRandom` instead of `Math.random()`
- One `drawImage()` call per frame, and the work per frame does not depend on the resolution
  except for that final repaint
