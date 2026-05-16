package studio.nodes.input;

import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.MouseState;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/**
 * Publishes the per-frame mouse state (position, button, drag delta) from the
 * live preview window as graph outputs. Lets downstream nodes — fluid jets,
 * particle emitters, drag-to-paint masks — read interactive input without
 * each having to wire its own listener.
 *
 * <p>Coordinates are in the canvas/source-texture pixel space, NOT in screen
 * pixels: the preview panel un-letterboxes before writing to the runtime's
 * {@link MouseState}, so a node configured for an 800×800 fluid sees mouse
 * positions in the {@code 0..800} range regardless of how the preview is
 * letterboxed in the Swing surface.
 *
 * <p>When the cursor leaves the preview the {@code active}/{@code inside}
 * outputs go false; downstream consumers can use that to stop emitting.
 */
public final class MouseNode extends AbstractNode {

    public static final String TYPE_ID = "studio.input.Mouse";

    public final OutputPort<float[]> outPos;
    public final OutputPort<float[]> outDelta;
    public final OutputPort<Boolean> outDown;
    public final OutputPort<Boolean> outInside;
    /** Logical AND of {@code down} and {@code inside}. Useful as a spawn gate. */
    public final OutputPort<Boolean> outActive;

    public final Parameter<Float> pVelocityScale;
    public final Parameter<Boolean> pInvertY;

    public MouseNode() {
        this.outPos    = declareOutput("pos",    PortTypes.VEC2);
        this.outDelta  = declareOutput("delta",  PortTypes.VEC2);
        this.outDown   = declareOutput("down",   PortTypes.BOOL);
        this.outInside = declareOutput("inside", PortTypes.BOOL);
        this.outActive = declareOutput("active", PortTypes.BOOL);

        this.pVelocityScale = declareParam(Parameter.floatRange(
                "velocity_scale", 4f, 0f, 64f)
                .withDescription("Multiplier applied to the drag delta when published."));
        this.pInvertY = declareParam(Parameter.bool("invert_y", false)
                .withDescription("Flip the y component (useful when downstream expects GL-style coords)."));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void evaluate(Frame f) {
        MouseState m = f.context().mouse();
        float scale = pVelocityScale.get();
        boolean invertY = pInvertY.get();

        float dx = (m.x - m.prevX) * scale;
        float dy = (m.y - m.prevY) * scale;
        if (invertY) dy = -dy;

        f.publish(outPos,    new float[]{ m.x, invertY ? (m.height - m.y) : m.y });
        f.publish(outDelta,  new float[]{ dx, dy });
        f.publish(outDown,   m.down);
        f.publish(outInside, m.inside);
        f.publish(outActive, m.down && m.inside);
    }
}
