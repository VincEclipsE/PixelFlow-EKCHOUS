package studio.graph;

/**
 * Per-graph mouse state, written by the host UI (preview panel) before each
 * frame and read by {@link studio.nodes.input.MouseNode}. Coordinates are in
 * the canvas/source-texture pixel space — i.e. the {@code 0..width × 0..height}
 * range of the texture the preview is rendering, not the on-screen panel
 * coordinates. The preview panel is responsible for un-letterboxing.
 *
 * <p>This is a tiny mutable holder. Reads happen on the GL thread inside
 * {@code evaluate()}; writes happen on the EDT inside Swing mouse events.
 * The fields are {@code volatile} so a fresh read from the GL thread sees
 * the most recent write.
 */
public final class MouseState {

    /** Current x in canvas pixels (0..width). */
    public volatile float x;
    /** Current y in canvas pixels (0..height). */
    public volatile float y;
    /** x from the previous frame. */
    public volatile float prevX;
    /** y from the previous frame. */
    public volatile float prevY;

    /** True while the primary mouse button is held down. */
    public volatile boolean down;
    /** True while the right mouse button is held down. */
    public volatile boolean rightDown;

    /** True if the cursor is inside the preview surface. */
    public volatile boolean inside;

    /** Canvas pixel width the coordinates are normalised against. */
    public volatile int width  = 800;
    /** Canvas pixel height the coordinates are normalised against. */
    public volatile int height = 800;

    /**
     * Called by the runtime once per frame, after nodes have read the
     * current state, to snap prevX/prevY = current. Keeps the per-frame
     * delta stable even when the EDT fires multiple drag events between
     * frames.
     */
    public void snapshotPrev() {
        prevX = x;
        prevY = y;
    }
}
