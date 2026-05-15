package studio.nodes.builtin;

import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.Parameter;

/**
 * Documentation-only node — a sticky note placed on the canvas to explain
 * what a section of the graph does. Has no ports; the runtime skips it
 * entirely because there is nothing to read or publish.
 */
public final class NoteNode extends AbstractNode {

    public static final String TYPE_ID = "studio.builtin.Note";

    public final Parameter<String> pText;

    public NoteNode() {
        super();
        this.pText = declareParam(Parameter.text("text", "Note"));
        setLabel("Note");
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void evaluate(Frame frame) {
        // No-op — notes don't participate in graph execution.
    }
}
