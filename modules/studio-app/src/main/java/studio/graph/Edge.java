package studio.graph;

import java.util.Objects;

/** Directed edge from an output port to an input port. */
public final class Edge {

    public final OutputPort<?> from;
    public final InputPort<?>  to;

    public Edge(OutputPort<?> from, InputPort<?> to) {
        this.from = Objects.requireNonNull(from, "from");
        this.to   = Objects.requireNonNull(to,   "to");
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge other)) return false;
        return from == other.from && to == other.to;
    }

    @Override public int hashCode() {
        return System.identityHashCode(from) * 31 + System.identityHashCode(to);
    }

    @Override public String toString() {
        return from + " -> " + to;
    }
}
