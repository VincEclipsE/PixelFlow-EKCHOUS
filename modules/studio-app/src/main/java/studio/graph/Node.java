package studio.graph;

import java.util.List;

/**
 * A node in the studio DAG. Every node — primitive wrapper, filter, source,
 * sink, or compound — implements this interface. Compound nodes
 * (saved {@code .pftool} files) re-implement this in terms of a sub-graph
 * so they appear identical to primitives from the outside.
 */
public interface Node {

    NodeId id();

    /** Stable type id used by the factory registry. E.g. {@code "pf.fluid.DwFluid2D"}. */
    String typeId();

    /** Human label shown in the editor. Mutable via {@link #setLabel(String)}. */
    String label();

    void setLabel(String label);

    /** Whether the runtime should evaluate this node each frame. Disabled nodes publish nothing. */
    default boolean isEnabled() { return true; }

    default void setEnabled(boolean enabled) { /* default mutable on AbstractNode */ }

    List<InputPort<?>> inputs();
    List<OutputPort<?>> outputs();
    List<Parameter<?>> parameters();

    /**
     * Called once before the first {@link #evaluate(Frame)} call. The GL
     * context is current on the calling thread. Allocate textures, compile
     * shaders, set up persistent state.
     */
    void init(GraphContext ctx);

    /** Per-frame execution. Reads inputs via {@code frame.read(port)} and publishes outputs via {@code frame.publish(port, value)}. */
    void evaluate(Frame frame);

    /** Called when the node is removed or the graph shuts down. Release GL resources. */
    void dispose(GraphContext ctx);

    /** Convenience to look up a parameter by name. Returns null if missing. */
    default Parameter<?> parameter(String name) {
        for (Parameter<?> p : parameters()) if (p.name.equals(name)) return p;
        return null;
    }

    /** Convenience to look up an input port by name. Returns null if missing. */
    default InputPort<?> input(String name) {
        for (InputPort<?> p : inputs()) if (p.name.equals(name)) return p;
        return null;
    }

    /** Convenience to look up an output port by name. Returns null if missing. */
    default OutputPort<?> output(String name) {
        for (OutputPort<?> p : outputs()) if (p.name.equals(name)) return p;
        return null;
    }
}
