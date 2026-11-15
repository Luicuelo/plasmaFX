package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates and animates a plasma fractal on a canvas.
 * <p>
 * The base pattern comes from the diamond-square algorithm: a grid is recursively split in
 * four and the centre of every cell is randomly displaced. The resulting values live in the
 * [0, 1] range and are mapped to a 256 entry procedural colour palette.
 * <p>
 * Once generated, each frame is animated by copying small square regions of the image to a
 * slightly displaced position. Copying many random regions per frame makes the plasma look
 * like it is flowing, and the effect is intensified around the mouse cursor.
 * <p>
 * The pixels live in a single {@code int[]} which is the source of truth; the
 * {@link WritableImage} is only a mirror of it, refreshed as a whole when a fractal is generated
 * and block by block while animating. Copies are plain {@link System#arraycopy} calls on that
 * array, so the rendering loop never goes through a {@link javafx.scene.image.PixelReader} and
 * allocates no memory.
 * <p>
 * Instances are not thread safe; they must only be used from the JavaFX application thread.
 */
class PlasmaRenderer {

    /** Number of colours in the palette, which is also the number of plasma levels. */
    private static final int PALETTE_SIZE = 256;

    /** Highest value a colour channel can reach; 255 is skipped so no colour is pure white. */
    private static final int MAX_CHANNEL = 254;

    /** Number of region copies performed every frame over the whole image. */
    private static final int BASE_COPY_COUNT = 300;

    /** Side, in pixels, of the regions copied by the base effect. */
    private static final int BASE_COPY_SIZE = 30;

    /** Extra number of region copies performed every frame around the mouse cursor. */
    private static final int MOUSE_COPY_COUNT = 50;

    /** Side, in pixels, of the regions copied around the mouse cursor. */
    private static final int MOUSE_COPY_SIZE = 20;

    /** Radius, in pixels, of the circle affected by the mouse cursor. */
    private static final int MOUSE_RADIUS = 50;

    /** Maximum distance, in pixels, between the source and destination of a copy. */
    private static final int COPY_JITTER = 1;

    /** Default maximum displacement of a cell centre, relative to its size. */
    static final float DEFAULT_DISPLACEMENT_FACTOR = 5.0f;

    /** Range the displacement factor may take, from a flat grid to a noisy one. */
    static final float MIN_DISPLACEMENT_FACTOR = 0f;
    static final float MAX_DISPLACEMENT_FACTOR = 30f;

    /** Opaque black the buffer is cleared with before a pattern is drawn. */
    private static final int EMPTY_PIXEL = 0xFF000000;

    /** ARGB pixel format used for every bulk read and write. */
    private static final WritablePixelFormat<IntBuffer> FORMAT = PixelFormat.getIntArgbInstance();

    private final GraphicsContext gc;
    private final int width;
    private final int height;

    /**
     * Master pixel buffer in ARGB format: one {@code 0xAARRGGBB} int per pixel, which is exactly what
     * {@link #FORMAT} expects. An indexed buffer (one byte per pixel plus a palette) would need four
     * times less memory, but every frame would have to be expanded to real colours before it could be
     * shown, whereas these values are copied around already encoded.
     */
    private final int[] pixels;

    /** Image painted on the canvas, kept in sync with {@link #pixels}. */
    private final WritableImage image;
    private final PixelWriter writer;

    /** Palette already packed as ARGB values, ready to be written into the image. */
    private final int[] palette = new int[PALETTE_SIZE];

    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private int mouseX;
    private int mouseY;
    private boolean mouseInside;

    /** Scale applied to the random displacement of every cell centre. */
    private float displacementFactor = DEFAULT_DISPLACEMENT_FACTOR;

    /** Scale requested by the caller; the pattern is redrawn only when it differs from the applied one. */
    private float requestedFactor = DEFAULT_DISPLACEMENT_FACTOR;

    /** Seed of the current pattern, so it can be redrawn with another shape and the same palette. */
    private long patternSeed = ThreadLocalRandom.current().nextLong();

    /** Random source of the current pattern, recreated from {@link #patternSeed} on every draw. */
    private SplittableRandom patternRandom = new SplittableRandom(patternSeed);

    /**
     * Creates the renderer and immediately generates and paints the first fractal.
     *
     * @param gc     graphics context of the canvas the image is going to be painted on
     * @param width  canvas width in pixels, must be positive
     * @param height canvas height in pixels, must be positive
     * @throws IllegalArgumentException if the context is null or a dimension is not positive
     */
    PlasmaRenderer(GraphicsContext gc, int width, int height) {
        if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        this.gc = gc;
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        this.image = new WritableImage(width, height);
        this.writer = image.getPixelWriter();

        regenerate();
    }

    /**
     * Generates a brand new fractal pattern and paints it on the canvas.
     * The image object is reused, so this can be called while the animation is running.
     */
    void regenerate() {
        patternSeed = random.nextLong();
        redraw();
    }

    /**
     * Repaints the current pattern with the settings requested so far. The seed is kept, so
     * changing the displacement factor morphs the shape instead of generating an unrelated one.
     */
    void redraw() {
        displacementFactor = requestedFactor;
        patternRandom = new SplittableRandom(patternSeed);
        Arrays.fill(pixels, EMPTY_PIXEL);

        float[] corners = createPalette();
        divideGrid(0, 0, width, height, corners[0], corners[1], corners[2], corners[3]);

        // The whole pattern is uploaded at once, instead of one call per pixel.
        writer.setPixels(0, 0, width, height, FORMAT, pixels, 0, width);
        gc.drawImage(image, 0, 0);
    }

    /**
     * Builds the colour palette and returns the four seed values of the fractal.
     * <p>
     * Four "colour channels" are mixed two at a time: on every step one of the first three
     * channels absorbs the next one and the result is scaled by a random factor in [-4, 4].
     * Three running accumulators take the four channel values except the one consumed as a
     * source, and their clamped values become the RGB components of the palette entry.
     *
     * @return the four random seed values in [0, 1], one per corner of the fractal
     */
    private float[] createPalette() {
        float[] seed = {patternRandom.nextFloat(), patternRandom.nextFloat(),
                patternRandom.nextFloat(), patternRandom.nextFloat()};
        float[] channel = seed.clone();
        float[] accumulator = new float[3];

        for (int i = 0; i < PALETTE_SIZE; i++) {
            int modified = patternRandom.nextInt(3);
            float scale = (patternRandom.nextBoolean() ? 4f : -4f) * patternRandom.nextFloat();
            channel[modified] = (channel[modified] + channel[modified + 1]) * scale / 2f;

            int target = 0;
            for (int c = 0; c < channel.length; c++) {
                if (c != modified + 1) {
                    accumulator[target++] += channel[c];
                }
            }
            for (int c = 0; c < accumulator.length; c++) {
                accumulator[c] = clamp(accumulator[c], MAX_CHANNEL);
            }
            palette[i] = toArgb((int) accumulator[0], (int) accumulator[1], (int) accumulator[2]);
        }
        return seed;
    }

    /**
     * Recursive diamond-square step: splits a cell in four quadrants, displaces its centre and
     * keeps subdividing. A cell of one pixel paints its average value.
     * <p>
     * The geometry is expressed in whole pixels. An odd side cannot be halved exactly, so the
     * first half takes the integer division and the second one takes the rest ({@code w / 2} and
     * {@code w - w / 2}): the two halves always add up to the parent, so the quadrants tile the
     * image with no gap and no overlap. Halving a side of 1 px leaves one empty quadrant, which
     * is skipped because its twin already covers that strip.
     * <p>
     * <pre>
     *   c1---+---c2      c1 = topLeft      c2 = topRight     "xx" marks the quadrant
     *   |    |    |      c4 = bottomLeft   c3 = bottomRight  each call recurses into
     *   +----+----+
     *   |    |    |
     *   c4---+---c3
     * </pre>
     *
     * @param x           left edge of the cell, in pixels
     * @param y           top edge of the cell, in pixels
     * @param w           cell width in pixels, must be positive
     * @param h           cell height in pixels, must be positive
     * @param topLeft     value of the top-left corner
     * @param topRight    value of the top-right corner
     * @param bottomRight value of the bottom-right corner
     * @param bottomLeft  value of the bottom-left corner
     */
    private void divideGrid(int x, int y, int w, int h,
                            float topLeft, float topRight, float bottomRight, float bottomLeft) {
        if (w <= 0 || h <= 0) {
            return; // empty quadrant, its twin covers the same strip
        }
        if (w == 1 && h == 1) {
            plot(x, y, (topLeft + topRight + bottomRight + bottomLeft) / 4f);
            return;
        }

        int halfWidth = w / 2;
        int halfHeight = h / 2;
        int restWidth = w - halfWidth;
        int restHeight = h - halfHeight;

        float centre = clamp((topLeft + topRight + bottomRight + bottomLeft) / 4f
                + displacement(halfWidth, halfHeight), 1f);
        float top = (topLeft + topRight) / 2f;
        float right = (topRight + bottomRight) / 2f;
        float bottom = (bottomRight + bottomLeft) / 2f;
        float left = (bottomLeft + topLeft) / 2f;

        //  c1---+---c2
        //  | xx |    |
        //  +----+----+
        //  |    |    |
        //  c4---+---c3
        divideGrid(x, y, halfWidth, halfHeight, topLeft, top, centre, left);

        //  c1---+---c2
        //  |    | xx |
        //  +----+----+
        //  |    |    |
        //  c4---+---c3
        divideGrid(x + halfWidth, y, restWidth, halfHeight, top, topRight, right, centre);

        //  c1---+---c2
        //  |    |    |
        //  +----+----+
        //  |    | xx |
        //  c4---+---c3
        divideGrid(x + halfWidth, y + halfHeight, restWidth, restHeight,
                centre, right, bottomRight, bottom);

        //  c1---+---c2
        //  |    |    |
        //  +----+----+
        //  | xx |    |
        //  c4---+---c3
        divideGrid(x, y + halfHeight, halfWidth, restHeight, left, centre, bottom, bottomLeft);
    }

    /**
     * Random displacement applied to the centre of a cell: small cells move only a little, so
     * the pattern stays smooth while bigger cells can jump further.
     */
    private float displacement(int cellWidth, int cellHeight) {
        float max = (cellWidth + cellHeight) / (float) (width + height) * displacementFactor;
        return (patternRandom.nextFloat() - 0.5f) * max;
    }

    /** Writes the palette colour of a plasma level at the pixel under ({@code x}, {@code y}). */
    private void plot(int x, int y, float level) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        int paletteIndex = clamp((int) (level * (PALETTE_SIZE - 1)), PALETTE_SIZE - 1);
        pixels[y * width + x] = palette[paletteIndex];
    }

    /**
     * Advances the animation by one frame and repaints the canvas.
     * The amount of work is constant per frame, since the number of copies does not depend on
     * the canvas size; only the final repaint scales with the resolution.
     * <p>
     * When the requested displacement factor differs from the one the pattern was drawn with, it
     * is redrawn instead and the animation goes on in the next frame.
     */
    void update() {
        if (requestedFactor != displacementFactor) {
            redraw();
            return;
        }
        if (mouseInside) {
            animateAroundMouse();
        }
        for (int i = 0; i < BASE_COPY_COUNT; i++) {
            copyToRegion(random.nextInt(width), random.nextInt(height), BASE_COPY_SIZE);
        }
        gc.drawImage(image, 0, 0);
    }

    /** Copies blocks into the circle of {@link #MOUSE_RADIUS} around the cursor. */
    private void animateAroundMouse() {
        for (int i = 0; i < MOUSE_COPY_COUNT; i++) {
            // A point is picked inside the circle by choosing the horizontal offset first and
            // then limiting the vertical one to the chord at that offset.
            double offsetX = (random.nextDouble() - 0.5) * 2 * MOUSE_RADIUS;
            double halfChord = Math.sqrt(MOUSE_RADIUS * (double) MOUSE_RADIUS - offsetX * offsetX);
            double offsetY = (random.nextDouble() - 0.5) * 2 * halfChord;

            copyToRegion(mouseX + (int) offsetX, mouseY + (int) offsetY, MOUSE_COPY_SIZE);
        }
    }

    /**
     * Copies a square block of the image into the region that starts at ({@code destX},
     * {@code destY}). The block comes from the very same region, displaced by at most
     * {@link #COPY_JITTER} pixels, so the area is smeared a little and the plasma seems to boil.
     * <p>
     * The block is clipped, never moved: whatever falls outside the image is left out and the
     * source is shifted by the same amount, so every copied pixel keeps its relative position and
     * the positions near the borders are honoured instead of piling up against them. The jitter can
     * push the source outside as well, so the piece copied is the one both ends agree on.
     *
     * @param destX left edge of the destination region, in pixels; it may lie outside the image
     * @param destY top edge of the destination region, in pixels; it may lie outside the image
     * @param size  side of the square region, in pixels
     */
    private void copyToRegion(int destX, int destY, int size) {
        if (size <= 0) {
            return;
        }

        int sourceX = destX + random.nextInt(-COPY_JITTER, COPY_JITTER + 1);
        int sourceY = destY + random.nextInt(-COPY_JITTER, COPY_JITTER + 1);

        // Extent of the piece that is copied; it shrinks as each end gets clipped.
        int sizeX = size;
        int sizeY = size;

        if (destX < 0) {
            // Only the part of the block that reaches the image is copied, and the source slides
            // with it: the piece is rigid, so both ends move by the same amount.
            sizeX += destX;
            sourceX -= destX;
            destX = 0;
        }
        if (destY < 0) {
            sizeY += destY;
            sourceY -= destY;
            destY = 0;
        }
        if (destX + sizeX > width) {
            sizeX = width - destX;
        }
        if (destY + sizeY > height) {
            sizeY = height - destY;
        }

        if (sourceX < 0) {
            sizeX += sourceX;
            destX -= sourceX;       // the destination advances by the same amount
            sourceX = 0;
        }
        if (sourceY < 0) {
            sizeY += sourceY;
            destY -= sourceY;       // the destination advances by the same amount
            sourceY = 0;
        }
        if (sourceX + sizeX > width) {
            sizeX = width - sourceX;
        }
        if (sourceY + sizeY > height) {
            sizeY = height - sourceY;
        }

        if (sizeX <= 0 || sizeY <= 0) {
            return; // the piece lies entirely outside the image
        }
        if (sourceX == destX && sourceY == destY) {
            return; // copying a region onto itself cannot change any pixel
        }

        // One arraycopy per row copes with the horizontal overlap, but the rows overlap between
        // calls as well, so the copy follows memmove: bottom-up when the destination sits below.
        int sourceStart = sourceY * width + sourceX;
        int destStart = destY * width + destX;
        boolean bottomUp = destY > sourceY;
        for (int i = 0; i < sizeY; i++) {
            int row = bottomUp ? sizeY - 1 - i : i;
            System.arraycopy(pixels, sourceStart + row * width,
                    pixels, destStart + row * width, sizeX);
        }

        // The piece is already in place, so the image is refreshed straight from the master buffer.
        writer.setPixels(destX, destY, sizeX, sizeY, FORMAT, pixels, destStart, width);
    }

    /**
     * Changes how rough the pattern is. The new value is applied on the next frame, where the
     * pattern is redrawn with the same seed, so dragging a slider morphs the plasma instead of
     * generating a new one for every step. Asking for the value the pattern already has is free:
     * nothing is redrawn.
     *
     * @param factor maximum displacement of a cell centre, relative to its size; 0 produces a
     *               perfectly smooth grid
     */
    void setDisplacementFactor(float factor) {
        requestedFactor = factor;
    }

    /**
     * Updates the mouse position used by the interactive effect.
     *
     * @param x      mouse X coordinate, in canvas pixels
     * @param y      mouse Y coordinate, in canvas pixels
     * @param inside true when the cursor is over the canvas
     */
    void setMousePosition(int x, int y, boolean inside) {
        this.mouseX = x;
        this.mouseY = y;
        this.mouseInside = inside;
    }

    /** Packs three 0-255 channel values into an opaque ARGB value. */
    private static int toArgb(int red, int green, int blue) {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /** Limits a value to the range [0, max], both ends included. */
    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    /** Limits a value to the range [0, max], both ends included. */
    private static float clamp(float value, float max) {
        return Math.max(0f, Math.min(value, max));
    }
}
