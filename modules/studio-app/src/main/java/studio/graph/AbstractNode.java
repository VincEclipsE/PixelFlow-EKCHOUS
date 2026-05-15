package studio.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Convenience base class for nodes. Owns the id, label, port lists, and
 * parameter list. Subclasses use {@link #declareInput}, {@link #declareOutput},
 * and {@link #declareParam} in their constructor to populate them.
 */
public abstract class AbstractNode implements Node {

    private final NodeId id;
    private String label;
    private final List<InputPort<?>> inputs = new ArrayList<>();
    private final List<OutputPort<?>> outputs = new ArrayList<>();
    private final List<Parameter<?>> parameters = new ArrayList<>();

    protected AbstractNode() { this(NodeId.random(), null); }

    protected AbstractNode(NodeId id) { this(id, null); }

    protected AbstractNode(NodeId id, String label) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = label != null ? label : defaultLabel();
    }

    protected String defaultLabel() {
        // Subclasses can override; default is the simple class name.
        return getClass().getSimpleName();
    }

    @Override public NodeId id() { return id; }
    @Override public String label() { return label; }
    @Override public void setLabel(String label) { this.label = label; }

    @Override public List<InputPort<?>> inputs() { return Collections.unmodifiableList(inputs); }
    @Override public List<OutputPort<?>> outputs() { return Collections.unmodifiableList(outputs); }
    @Override public List<Parameter<?>> parameters() { return Collections.unmodifiableList(parameters); }

    protected <T> InputPort<T> declareInput(String name, PortType<T> type) {
        return declareInput(name, type, true);
    }

    protected <T> InputPort<T> declareInput(String name, PortType<T> type, boolean required) {
        InputPort<T> p = new InputPort<>(this, name, type, required);
        inputs.add(p);
        return p;
    }

    protected <T> OutputPort<T> declareOutput(String name, PortType<T> type) {
        OutputPort<T> p = new OutputPort<>(this, name, type);
        outputs.add(p);
        return p;
    }

    protected <T> Parameter<T> declareParam(Parameter<T> param) {
        parameters.add(param);
        return param;
    }

    /** Default no-op init. Subclasses override when they allocate persistent state. */
    @Override public void init(GraphContext ctx) {}

    /** Default no-op dispose. */
    @Override public void dispose(GraphContext ctx) {}
}
