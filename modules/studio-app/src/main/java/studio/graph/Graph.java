package studio.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable container of nodes and edges. The runtime executes nodes in
 * topological order each frame; this class is the source of truth for the
 * structure but does not run anything itself.
 */
public final class Graph {

    private final Map<NodeId, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private List<Node> topoCache;
    private boolean topoDirty = true;

    public void addNode(Node n) {
        if (nodes.put(n.id(), n) != null) {
            throw new IllegalArgumentException("duplicate node id: " + n.id());
        }
        topoDirty = true;
    }

    public Node node(NodeId id) {
        return nodes.get(id);
    }

    public List<Node> nodes() {
        return List.copyOf(nodes.values());
    }

    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /** Add an edge. Performs a cycle check; throws on type mismatch or cycle. */
    public Edge connect(OutputPort<?> from, InputPort<?> to) {
        Objects.requireNonNull(from); Objects.requireNonNull(to);
        if (from.owner == to.owner) {
            throw new IllegalArgumentException("self-loop: " + from + " -> " + to);
        }
        if (!to.type.isAssignableFrom(from.type)) {
            throw new IllegalArgumentException(
                    "type mismatch: " + from.type + " not assignable to " + to.type
                    + " (" + from + " -> " + to + ")");
        }
        Edge e = new Edge(from, to);
        edges.add(e);
        if (hasCycle()) {
            edges.remove(edges.size() - 1);
            throw new IllegalArgumentException("connection would create a cycle: " + e);
        }
        topoDirty = true;
        return e;
    }

    public List<Node> topology() {
        if (topoDirty || topoCache == null) {
            topoCache = TopologicalSort.sort(this);
            topoDirty = false;
        }
        return topoCache;
    }

    /** Find the producer-side edge feeding a given input port, or null if unconnected. */
    public Edge edgeInto(InputPort<?> in) {
        for (Edge e : edges) {
            if (e.to == in) return e;
        }
        return null;
    }

    private boolean hasCycle() {
        try {
            TopologicalSort.sort(this);
            return false;
        } catch (TopologicalSort.GraphCycleException ex) {
            return true;
        }
    }
}
