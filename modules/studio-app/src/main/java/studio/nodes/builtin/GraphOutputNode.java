package studio.nodes.builtin;

import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.InputPort;
import studio.graph.PortTypes;

/**
 * Sink node that grabs whatever RenderTarget feeds its single input and
 * makes it available via {@link #lastFrame()} for external code (the
 * headless smoke or live preview) to read.
 */
public final class GraphOutputNode extends AbstractNode {

    public static final String TYPE_ID = "studio.builtin.GraphOutput";

    public final InputPort<RenderTarget> in;
    private RenderTarget lastFrame;

    public GraphOutputNode() {
        super();
        this.in = declareInput("in", PortTypes.TEXTURE2D);
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void evaluate(Frame frame) {
        lastFrame = frame.read(in);
    }

    /** Most recent RenderTarget read from the input port, or null if unconnected. */
    public RenderTarget lastFrame() { return lastFrame; }
}
