package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Interactive plasma fractal viewer.
 * <p>
 * The window hosts a canvas that is repainted by an {@link AnimationTimer} on every pulse of
 * the JavaFX render loop (about 60 frames per second), plus a toolbar with a slider that changes
 * the roughness of the pattern and a button that generates a new one without recreating the
 * window.
 * <p>
 * The canvas size can be chosen on the command line with the named arguments
 * {@code --width} and {@code --height}, for example:
 * <pre>
 * java --module-path ... --add-modules javafx.controls,javafx.graphics \
 *      -cp bin principal.PlasmaFX --width=1024 --height=768
 * </pre>
 */
public class PlasmaFX extends Application {

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;

    /** Accepted range for the sizes given on the command line, in pixels. */
    private static final int MIN_SIZE = 64;
    private static final int MAX_SIZE = 3840;

    /** Preferred height of the toolbar at the bottom of the window, in pixels. */
    private static final double BUTTON_HEIGHT = 28;

    /** Spacing and padding of the toolbar at the bottom of the window, in pixels. */
    private static final double TOOLBAR_GAP = 6;
    private static final double TOOLBAR_PADDING = 6;

    /** Width of the label that shows the current displacement factor, in pixels. */
    private static final double VALUE_LABEL_WIDTH = 32;

    /** Distance between the tick marks drawn under the slider, in roughness units. */
    private static final double SLIDER_TICK_UNIT = 5;

    private int canvasWidth = DEFAULT_WIDTH;
    private int canvasHeight = DEFAULT_HEIGHT;

    private PlasmaRenderer renderer;
    private AnimationTimer animationTimer;
    private Slider factorSlider;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        readSizeArguments(getParameters());

        Canvas canvas = new Canvas(canvasWidth, canvasHeight);
        // The renderer generates the first fractal and paints it on the canvas right away.
        renderer = new PlasmaRenderer(canvas.getGraphicsContext2D(), canvasWidth, canvasHeight);

        canvas.setOnMouseMoved(event -> moveMouse(event.getX(), event.getY()));
        canvas.setOnMouseEntered(event -> moveMouse(event.getX(), event.getY()));
        canvas.setOnMouseExited(event -> renderer.setMousePosition(0, 0, false));

        Button restartButton = new Button("Restart");
        restartButton.setPrefHeight(BUTTON_HEIGHT);
        restartButton.setMaxWidth(Double.MAX_VALUE);
        restartButton.setOnAction(event -> restartPlasma());

        Label factorLabel = new Label("Roughness");
        Label factorValue = new Label(formatFactor(PlasmaRenderer.DEFAULT_DISPLACEMENT_FACTOR));
        factorValue.setMinWidth(VALUE_LABEL_WIDTH);

        factorSlider = new Slider(PlasmaRenderer.MIN_DISPLACEMENT_FACTOR,
                PlasmaRenderer.MAX_DISPLACEMENT_FACTOR, PlasmaRenderer.DEFAULT_DISPLACEMENT_FACTOR);
        factorSlider.setTooltip(new Tooltip(String.format("0 = smooth gradient, %.0f = rough noise",
                PlasmaRenderer.MAX_DISPLACEMENT_FACTOR)));
        // Marks every five steps, so the longer travel is still easy to read at a glance.
        factorSlider.setMajorTickUnit(SLIDER_TICK_UNIT);
        factorSlider.setMinorTickCount(4);
        factorSlider.setShowTickMarks(true);
        factorSlider.setBlockIncrement(1);
        factorSlider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(factorSlider, Priority.ALWAYS);
        factorSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            // The renderer redraws the pattern on its next animation frame, not here.
            renderer.setDisplacementFactor(newValue.floatValue());
            factorValue.setText(formatFactor(newValue.floatValue()));
        });

        HBox sliderRow = new HBox(TOOLBAR_GAP, factorLabel, factorSlider, factorValue);
        sliderRow.setAlignment(Pos.CENTER_LEFT);

        VBox toolbar = new VBox(TOOLBAR_GAP, sliderRow, restartButton);
        toolbar.setPadding(new Insets(TOOLBAR_PADDING));

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setBottom(toolbar);

        stage.setTitle("PlasmaFX - Interactive Plasma Generator");
        stage.setScene(new Scene(root));
        // The window takes the preferred size of the layout: the canvas plus whatever height the
        // button really needs on the current platform, so nothing gets clipped.
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();

        startAnimation();
        System.out.println("Plasma " + canvasWidth + "x" + canvasHeight + " generated");
    }

    /**
     * Runs the animation loop. The timer is recreated every time so that a loop stopped by an
     * error can be resumed from {@link #restartPlasma()}.
     */
    private void startAnimation() {
        stopAnimation();
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                try {
                    renderer.update();
                } catch (RuntimeException e) {
                    System.err.println("Stopping the animation, the plasma could not be updated: " + e);
                    stopAnimation();
                }
            }
        };
        animationTimer.start();
    }

    private void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    /** Generates a new fractal at the default roughness and makes sure the animation loop is running. */
    private void restartPlasma() {
        System.out.println("Generating a new plasma...");
        // The slider reports the change to the renderer, which applies it while redrawing the pattern.
        factorSlider.setValue(PlasmaRenderer.DEFAULT_DISPLACEMENT_FACTOR);
        renderer.regenerate();
        startAnimation();
    }

    private void moveMouse(double x, double y) {
        renderer.setMousePosition((int) x, (int) y, true);
    }

    /** Formats the displacement factor with one decimal, as shown next to the slider. */
    private static String formatFactor(float factor) {
        return String.format("%.1f", factor);
    }

    /** Reads the optional {@code --width} and {@code --height} arguments. */
    private void readSizeArguments(Parameters params) {
        canvasWidth = parseDimension(params, "width", DEFAULT_WIDTH);
        canvasHeight = parseDimension(params, "height", DEFAULT_HEIGHT);
    }

    /**
     * Parses one size argument, falling back to the default value when it is missing, is not a
     * number or falls outside {@link #MIN_SIZE}-{@link #MAX_SIZE}.
     */
    private static int parseDimension(Parameters params, String name, int fallback) {
        String value = params.getNamed().get(name);
        if (value == null) {
            return fallback;
        }
        try {
            int size = Integer.parseInt(value.trim());
            if (size >= MIN_SIZE && size <= MAX_SIZE) {
                return size;
            }
            System.err.printf("--%s must be between %d and %d, using %d%n",
                    name, MIN_SIZE, MAX_SIZE, fallback);
        } catch (NumberFormatException e) {
            System.err.printf("--%s must be a whole number, using %d%n", name, fallback);
        }
        return fallback;
    }

    @Override
    public void stop() {
        stopAnimation();
    }
}
