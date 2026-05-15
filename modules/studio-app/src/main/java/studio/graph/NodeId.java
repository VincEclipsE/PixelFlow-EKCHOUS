package studio.graph;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a node instance inside a graph. */
public final class NodeId {

    public final String value;

    private NodeId(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static NodeId of(String value) {
        return new NodeId(value);
    }

    public static NodeId random() {
        return new NodeId(UUID.randomUUID().toString());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeId other)) return false;
        return value.equals(other.value);
    }

    @Override public int hashCode() { return value.hashCode(); }

    @Override public String toString() { return value; }
}
