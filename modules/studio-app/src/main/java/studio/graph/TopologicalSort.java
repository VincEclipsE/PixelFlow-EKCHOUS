package studio.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kahn's algorithm topological sort over {@link Graph}. */
public final class TopologicalSort {

    private TopologicalSort() {}

    public static List<Node> sort(Graph graph) {
        List<Node> nodes = graph.nodes();
        Map<Node, Integer> inDegree = new HashMap<>();
        for (Node n : nodes) inDegree.put(n, 0);
        Map<Node, List<Node>> outAdj = new HashMap<>();
        for (Node n : nodes) outAdj.put(n, new ArrayList<>());

        for (Edge e : graph.edges()) {
            Node producer = e.from.owner;
            Node consumer = e.to.owner;
            outAdj.get(producer).add(consumer);
            inDegree.merge(consumer, 1, Integer::sum);
        }

        Deque<Node> ready = new ArrayDeque<>();
        for (Node n : nodes) if (inDegree.get(n) == 0) ready.add(n);

        List<Node> order = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            Node n = ready.poll();
            order.add(n);
            for (Node m : outAdj.get(n)) {
                int d = inDegree.get(m) - 1;
                inDegree.put(m, d);
                if (d == 0) ready.add(m);
            }
        }

        if (order.size() != nodes.size()) {
            List<Node> unresolved = new ArrayList<>();
            for (Node n : nodes) if (inDegree.get(n) > 0) unresolved.add(n);
            throw new GraphCycleException("graph contains a cycle; unresolved nodes: " + unresolved);
        }
        return Collections.unmodifiableList(order);
    }

    public static final class GraphCycleException extends RuntimeException {
        public GraphCycleException(String message) { super(message); }
    }
}
