package studio.graph;

import java.util.Objects;

/** Typed output port on a node. */
public final class OutputPort<T> {

    public final Node owner;
    public final String name;
    public final PortType<T> type;

    OutputPort(Node owner, String name, PortType<T> type) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override public String toString() {
        return owner.label() + "." + name + " (out " + type + ")";
    }
}
