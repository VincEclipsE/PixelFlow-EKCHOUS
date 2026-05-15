package studio.nodes.builtin;

import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.OutputPort;
import studio.graph.PortTypes;

/**
 * Boundary node used inside a compound. From the inner graph's POV it has
 * one output port that "produces" a value each frame; from the compound
 * wrapper's POV it has one slot whose value is injected from the matching
 * outer input.
 *
 * <p>v1 only supports {@code Texture2D} inputs; richer port types can be
 * added once we wire param ports through the graph.
 */
public final class GraphInputNode extends AbstractNode {

    public static final String TYPE_ID = "studio.builtin.GraphInput";

    public final OutputPort<RenderTarget> out;
    private volatile RenderTarget pending;

    public GraphInputNode() {
        super();
        this.out = declareOutput("out", PortTypes.TEXTURE2D);
    }

    @Override public String typeId() { return TYPE_ID; }

    /** Called by the surrounding {@link studio.nodes.compound.CompoundNode}
     *  to push the outer input value into this inner-graph entry point. */
    public void inject(RenderTarget value) {
        pending = value;
    }

    @Override public void evaluate(Frame frame) {
        if (pending != null) frame.publish(out, pending);
    }
}
