package studio.save;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.graph.Edge;
import studio.graph.Graph;
import studio.graph.InputPort;
import studio.graph.Node;
import studio.graph.NodeFactoryRegistry;
import studio.graph.NodeId;
import studio.graph.OutputPort;
import studio.graph.Parameter;

/**
 * Loads a {@code .pflow} JSON file into a runnable {@link Graph}. Resolves
 * node {@code typeId}s through a {@link NodeFactoryRegistry} and applies
 * any param overrides specified in the file.
 */
public final class PflowReader {

    public static final class Result {
        public final Graph graph;
        public final PflowJson source;
        /** node-id (string) → constructed Node instance. */
        public final Map<String, Node> nodesById;

        public Result(Graph graph, PflowJson src, Map<String, Node> nodesById) {
            this.graph = graph;
            this.source = src;
            this.nodesById = nodesById;
        }
    }

    private final NodeFactoryRegistry registry;

    public PflowReader(NodeFactoryRegistry registry) {
        this.registry = registry;
    }

    public Result load(Path file) throws IOException {
        PflowJson json = JsonCodec.read(file, PflowJson.class);
        return assemble(json);
    }

    public Result loadFromClasspath(String resourcePath) throws IOException {
        var loader = Thread.currentThread().getContextClassLoader();
        try (var in = loader.getResourceAsStream(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath)) {
            if (in == null) throw new IOException("Classpath resource not found: " + resourcePath);
            PflowJson json = JsonCodec.read(in, PflowJson.class);
            return assemble(json);
        }
    }

    public Result assemble(PflowJson json) {
        if (json.nodes == null) throw new IllegalArgumentException("pflow file has no nodes array");

        Graph graph = new Graph();
        Map<String, Node> byId = new LinkedHashMap<>();

        for (PflowJson.NodeJson n : json.nodes) {
            Node node = instantiate(n);
            byId.put(n.id, node);
            graph.addNode(node);
        }

        if (json.edges != null) {
            for (PflowJson.EdgeJson e : json.edges) {
                Node from = byId.get(e.from.node);
                Node to   = byId.get(e.to.node);
                if (from == null) throw new IllegalArgumentException("edge references unknown node id (from): " + e.from.node);
                if (to == null)   throw new IllegalArgumentException("edge references unknown node id (to): " + e.to.node);

                OutputPort<?> outPort = from.output(e.from.port);
                InputPort<?>  inPort  = to.input(e.to.port);
                if (outPort == null) throw new IllegalArgumentException("node " + e.from.node + " has no output port '" + e.from.port + "'");
                if (inPort == null)  throw new IllegalArgumentException("node " + e.to.node + " has no input port '" + e.to.port + "'");

                graph.connect(outPort, inPort);
            }
        }

        return new Result(graph, json, new HashMap<>(byId));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Node instantiate(PflowJson.NodeJson n) {
        if (n.typeId == null) throw new IllegalArgumentException("node has no typeId: " + n.id);
        Node node = registry.create(n.typeId);
        // Re-key the new node's auto-generated id with the JSON-supplied one so
        // multi-load determinism + edge resolution work as expected.
        if (n.id != null) {
            try {
                java.lang.reflect.Field f = studio.graph.AbstractNode.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(node, NodeId.of(n.id));
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                // best-effort; the generated id stays
            }
        }
        if (n.label != null) node.setLabel(n.label);
        if (n.enabled != null) node.setEnabled(n.enabled);

        if (n.params != null && !n.params.isEmpty()) {
            for (Parameter<?> p : node.parameters()) {
                Object raw = n.params.get(p.name);
                if (raw == null) continue;
                Object coerced = ParamCoercion.coerce(p, raw);
                if (coerced != null) {
                    ((Parameter) p).set(coerced);
                }
            }
        }
        return node;
    }
}
