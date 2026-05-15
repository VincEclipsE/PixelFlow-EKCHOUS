package studio.graph;

import java.util.Objects;

/** Typed input port on a node. */
public final class InputPort<T> {

    public final Node owner;
    public final String name;
    public final PortType<T> type;
    public final boolean required;

    InputPort(Node owner, String name, PortType<T> type, boolean required) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.required = required;
    }

    @Override public String toString() {
        return owner.label() + "." + name + " (in " + type + ")";
    }
}
