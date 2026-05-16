package studio.graph;

import java.util.Objects;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;

/**
 * Shared per-graph state — the underlying PixelFlow context, error sink, and
 * any other graph-wide handles. Passed to every node's {@link Node#init} and
 * {@link Node#dispose}; reachable from {@link Frame} during evaluation.
 */
public final class GraphContext {

    private final DwPixelFlow pixelFlow;
    private final GraphErrors errors = new GraphErrors();
    private final MouseState mouse = new MouseState();

    public GraphContext(DwPixelFlow pixelFlow) {
        this.pixelFlow = Objects.requireNonNull(pixelFlow, "pixelFlow");
    }

    public DwPixelFlow pixelFlow() { return pixelFlow; }
    public GraphErrors errors()    { return errors; }

    /** Shared per-frame mouse state. The host UI writes; nodes read. */
    public MouseState mouse() { return mouse; }
}
