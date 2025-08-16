package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import javafx.scene.paint.Color;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Generates and animates a plasma fractal effect using the diamond-square algorithm.
 * The plasma continuously morphs through pixel copying operations, with enhanced
 * activity around mouse cursor position.
 */
class PlasmaRenderer {
    private static final int PALETTE_SIZE = 256;
    private static final int BASE_EFFECT_ITERATIONS = 300;
    private static final int MOUSE_EFFECT_ITERATIONS = 50;
    private static final int MOUSE_EFFECT_RADIUS = 50;
    private static final int BASE_COPY_SIZE = 30;
    private static final int MOUSE_COPY_SIZE = 20;
    private static final int MAX_DISPLACEMENT = 3;

    private final GraphicsContext gc;
    private final int width;
    private final int height;
    private final Color[] palette = new Color[PALETTE_SIZE];
    private final Random random = new Random();
    private final WritableImage plasmaImage;
    private final PixelWriter pixelWriter;
    private final PixelReader pixelReader;
    private int mouseX = 0;
    private int mouseY = 0;
    private boolean mouseInside = false;

    /**
     * Creates a new plasma renderer and generates the initial fractal pattern.
     *
     * @param gc the graphics context to draw on
     * @param w  canvas width in pixels
     * @param h  canvas height in pixels
     * @throws IllegalArgumentException if gc is null or dimensions are invalid
     */
    public PlasmaRenderer(GraphicsContext gc, int w, int h) {
        if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        this.gc = gc;
        this.width = w;
        this.height = h;

        // Create writable image and pixel writer for efficient drawing
        plasmaImage = new WritableImage(width, height);
        pixelWriter = plasmaImage.getPixelWriter();
        pixelReader = plasmaImage.getPixelReader();

        initialize();
    }

    private float displace(int w, int h) {
        // The variation is very high in distant points and low in nearby ones
        float max = ((float) (w + h) / (float) (width + height)) * 5.0f;
        return ((float) random.nextDouble() - 0.5f) * (max);
    }

    public void initialDrawing() {
        float c1 = (float) random.nextDouble();
        float c2 = (float) random.nextDouble();
        float c3 = (float) random.nextDouble();
        float c4 = (float) random.nextDouble();

        initialColors(c1, c2, c3, c4);
        divideGrid(0, 0, width, height, c1, c2, c3, c4);
        System.out.println("Initial Drawing");
        Thread.yield();
    }

    private void initialColors(float c1, float c2, float c3, float c4) {
        float accum1 = 0;
        float accum2 = 0;
        float accum3 = 0;

        for (int a = 0; a < PALETTE_SIZE; a++) {
            int rd = (int) (random.nextDouble() * 3);

            float sgn = -4;
            if (random.nextDouble() > .5) {
                sgn = 4;
            }
            sgn = sgn * (float) random.nextDouble();

            if (rd == 0) {
                c1 = (c1 + c2) * sgn / 2.0f;
                accum1 = accum1 + c1;
                accum2 = accum2 + c3;
                accum3 = accum3 + c4;
            } else if (rd == 1) {
                c2 = (c2 + c3) * sgn / 2.0f;
                accum2 = accum2 + c2;
                accum1 = accum1 + c1;
                accum3 = accum3 + c4;
            } else {
                c3 = (c3 + c4) * sgn / 2.0f;
                accum3 = accum3 + c3;
                accum1 = accum1 + c1;
                accum2 = accum2 + c2;
            }

            if (accum1 < 0) {
                accum1 = 0;
            }
            if (accum2 < 0) {
                accum2 = 0;
            }
            if (accum3 < 0) {
                accum3 = 0;
            }

            if (accum1 > 254) {
                accum1 = 254;
            }
            if (accum2 > 254) {
                accum2 = 254;
            }
            if (accum3 > 254) {
                accum3 = 254;
            }

            Color cl = Color.rgb((int) accum1, (int) accum2, (int) accum3);
            palette[a] = cl;
        }
    }

    /**
     * Recursive diamond-square algorithm implementation for fractal generation.
     * Subdivides grid and applies random displacement to create plasma pattern.
     */
    private void divideGrid(float x, float y, float width, float height, float c1, float c2, float c3, float c4) {
        float Edge1, Edge2, Edge3, Edge4, Middle;
        float newWidth = width / 2;
        float newHeight = height / 2;

        if (width > 1 || height > 1) {
            Middle = (c1 + c2 + c3 + c4) / 4 + displace((int) newWidth, (int) newHeight);    //Randomly displace the midpoint!
            Edge1 = (c1 + c2) / 2;    //Calculate the edges by averaging the two corners of each edge.
            Edge2 = (c2 + c3) / 2;
            Edge3 = (c3 + c4) / 2;
            Edge4 = (c4 + c1) / 2;

            //Make sure that the midpoint doesn't accidentally "randomly displaced" past the boundaries!
            if (Middle < 0) {
                Middle = 0;
            } else if (Middle > 1.0f) {
                Middle = 1.0f;
            }

            //Do the operation over again for each of the four new grids.
            divideGrid(x, y, newWidth, newHeight, c1, Edge1, Middle, Edge4);
            divideGrid(x + newWidth, y, newWidth, newHeight, Edge1, c2, Edge2, Middle);
            divideGrid(x + newWidth, y + newHeight, newWidth, newHeight, Middle, Edge2, c3, Edge3);
            divideGrid(x, y + newHeight, newWidth, newHeight, Edge4, Middle, Edge3, c4);
        } else {
            //This is the "base case," where each grid piece is less than the size of a pixel.
            //The four corners of the grid piece will be averaged and drawn as a single pixel.
            float c = (c1 + c2 + c3 + c4) / 4;

            // Store ARGB values for later manipulation
            int xPos = (int) x;
            int yPos = (int) y;
            if (xPos < this.width && yPos < this.height) {
                if ((int) (c * 255) < palette.length && palette[(int) (c * 255)] != null) {
                    int argb = colorToARGB(palette[(int) (c * 255)]);
                    pixelWriter.setArgb(xPos, yPos, argb);
                }
            }
        }
    }

    private void initialize() {
        initialDrawing();
        // Draw the entire image at once
        gc.drawImage(plasmaImage, 0, 0);
        System.out.println("Image Drawn");
    }

    // Helper method to convert JavaFX Color to ARGB integer
    private int colorToARGB(Color color) {
        int alpha = ((int) (color.getOpacity() * 255)) << 24;
        int red = ((int) (color.getRed() * 255)) << 16;
        int green = ((int) (color.getGreen() * 255)) << 8;
        int blue = (int) (color.getBlue() * 255);
        return alpha | red | green | blue;
    }


    /**
     * Updates the plasma effect for one frame (called at 60 FPS).
     * Processes pixel copying operations and redraws the canvas.
     */
    public void updateRegion() {
        processPlasmaEffect();
        gc.drawImage(plasmaImage, 0, 0);
    }

    /**
     * Updates mouse position for interactive plasma effects.
     *
     * @param x      mouse X coordinate
     * @param y      mouse Y coordinate
     * @param inside true if mouse is inside the canvas
     */
    public void setMousePosition(int x, int y, boolean inside) {
        this.mouseX = x;
        this.mouseY = y;
        this.mouseInside = inside;
    }

    /**
     * Regenerates the plasma fractal pattern without recreating the renderer.
     * Reuses existing WritableImage and draws a new fractal pattern.
     */
    public void restart() {
        initialize();
    }

    /**
     * Processes plasma morphing by copying random pixel regions.
     * Creates base animation across entire canvas, with intensified
     * effect around mouse cursor when present.
     */
    private void processPlasmaEffect() {
        if (mouseInside) {
            for (int i = 0; i < MOUSE_EFFECT_ITERATIONS; i++) {

                int offsetX = (int) ((Math.random() - 0.5) * MOUSE_EFFECT_RADIUS * 2);

                double maxY = Math.sqrt(MOUSE_EFFECT_RADIUS * MOUSE_EFFECT_RADIUS - offsetX * offsetX);

                // random Y inside the circle.
                int offsetY = (int) ((Math.random() - 0.5) * maxY * 2);

                int px = mouseX + offsetX;
                int py = mouseY + offsetY;

                px = Math.max(0, Math.min(width - 1, px));
                py = Math.max(0, Math.min(height - 1, py));

                copyPixelRegion(px, py, MOUSE_COPY_SIZE);
            }
        }

        for (int i = 0; i < BASE_EFFECT_ITERATIONS; i++) {
            int px = (int) Math.floor((Math.random() * width));
            int py = (int) Math.floor((Math.random() * height));
            copyPixelRegion(px, py, BASE_COPY_SIZE);
        }
    }

    private void copyPixelRegion(int px, int py, int size) {
        int offsetX = ((int) Math.floor((Math.random() * MAX_DISPLACEMENT)) - 1);
        int offsetY = ((int) Math.floor((Math.random() * MAX_DISPLACEMENT)) - 1);
        if (px > 0 && py > 0 && px + size - 1 < width && py + size - 1 < height) {
            int sourceX = Math.max(0, px + offsetX);
            int sourceY = Math.max(0, py + offsetY);

            int actualWidth = size;
            int actualHeight = size;

            if (sourceX + size > width) {
                actualWidth = width - sourceX;
            }
            if (sourceY + size > height) {
                actualHeight = height - sourceY;
            }

            if (actualWidth > 0 && actualHeight > 0) {
                byte[] buffer = new byte[actualWidth * actualHeight * 4];
                WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();

                pixelReader.getPixels(sourceX, sourceY, actualWidth, actualHeight, pixelFormat, buffer, 0, actualWidth * 4);
                pixelWriter.setPixels(px, py, actualWidth, actualHeight, pixelFormat, buffer, 0, actualWidth * 4);
            }
        }
    }
}