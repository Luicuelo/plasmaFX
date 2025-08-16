package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Main JavaFX application for the interactive plasma fractal generator.
 * <p>
 * Features:
 * - Real-time plasma fractal animation at 60 FPS
 * - Mouse interaction that intensifies effects around cursor
 * - Restart functionality to generate new plasma patterns
 * <p>
 * The plasma uses the diamond-square algorithm for initial generation
 * and continuous pixel copying for morphing animation.
 */
public class PlasmaFX extends Application {
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int BUTTON_HEIGHT = 25;

    private int canvasWidth;
    private int canvasHeight;
    private Canvas canvas;
    private PlasmaRenderer plasmaRenderer;
    private AnimationTimer animationTimer;
    private boolean finished = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Parse command line arguments for size
        Parameters params = getParameters();
        parseSize(params);

        primaryStage.setTitle("PlasmaFX - Interactive Plasma Generator");

        // Create and initialize the plasma
        initializePlasma();

        // Setup mouse listeners
        setupMouseListeners();

        // Create the restart button
        Button restartButton = new Button("Restart");
        restartButton.setOnAction(e -> restartPlasma());
        restartButton.setPrefHeight(BUTTON_HEIGHT);

        // Layout
        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setBottom(restartButton);

        // Create scene and show
        Scene scene = new Scene(root, canvasWidth, canvasHeight + BUTTON_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * Parses command line arguments for canvas size.
     */
    private void parseSize(Parameters params) {
        try {
            if (params.getNamed().containsKey("width")) {
                canvasWidth = Integer.parseInt(params.getNamed().get("width"));
            } else {
                canvasWidth = DEFAULT_WIDTH;
            }

            if (params.getNamed().containsKey("height")) {
                canvasHeight = Integer.parseInt(params.getNamed().get("height"));
            } else {
                canvasHeight = DEFAULT_HEIGHT;
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid size parameters, using defaults");
            canvasWidth = DEFAULT_WIDTH;
            canvasHeight = DEFAULT_HEIGHT;
        }
    }

    /**
     * Initializes the plasma canvas and starts the animation loop.
     * Sets up the graphics context and begins 60 FPS rendering.
     */
    private void initializePlasma() {
        canvas = new Canvas(canvasWidth, canvasHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        finished = false;

        gc.setFill(Color.RED);
        gc.fillText("Generating Plasma .....", 100, 100);
        System.out.println("Generating Plasma .....");

        plasmaRenderer = new PlasmaRenderer(gc, canvasWidth, canvasHeight);

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!finished && plasmaRenderer != null) {
                    try {
                        plasmaRenderer.updateRegion();
                    } catch (Exception e) {
                        System.err.println("Error updating plasma: " + e.getMessage());
                        stopAnimation();
                    }
                }
            }
        };
        animationTimer.start();
    }

    /**
     * Configures mouse event handlers for interactive plasma effects.
     * Tracks mouse movement, entry, and exit events on the canvas.
     */
    private void setupMouseListeners() {
        canvas.setOnMouseMoved(e -> {
            if (plasmaRenderer != null) {
                plasmaRenderer.setMousePosition((int) e.getX(), (int) e.getY(), true);
            }
        });

        canvas.setOnMouseExited(e -> {
            if (plasmaRenderer != null) {
                plasmaRenderer.setMousePosition(0, 0, false);
            }
        });

        canvas.setOnMouseEntered(e -> {
            if (plasmaRenderer != null) {
                plasmaRenderer.setMousePosition((int) e.getX(), (int) e.getY(), true);
            }
        });
    }

    private void restartPlasma() {
        System.out.println("Restarting plasma...");
        if (plasmaRenderer != null) {
            plasmaRenderer.restart();
        }
    }

    private void stopAnimation() {
        finished = true;
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    @Override
    public void stop() {
        stopAnimation();
        plasmaRenderer = null;
        System.out.println("Plasma destroyed");
        Platform.exit();
    }
}