package studio.save;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import studio.graph.Edge;
import studio.graph.Graph;
import studio.graph.InputPort;
import studio.graph.Node;
import studio.graph.NodeFactoryRegistry;
import studio.graph.NodeId;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.nodes.compound.CompoundNode;

/**
 * Loads a {@code .pftool} file into a {@link Supplier} that builds a fresh
 * {@link CompoundNode} on demand. The supplier is registered with a
 * {@link NodeFactoryRegistry} so the compound appears in the palette as a
 * first-class tool.
 */
public final class PftoolReader {

    private static final int MAX_NEST_DEPTH = 16;

    private final NodeFactoryRegistry baseRegistry;

    public PftoolReader(NodeFactoryRegistry baseRegistry) {
        this.baseRegistry = baseRegistry;
    }

    public PftoolJson loadJson(Path file) throws IOException {
        return JsonCodec.read(file, PftoolJson.class);
    }

    /** Build a fresh instance from an already-parsed descriptor. */
    public CompoundNode instantiate(PftoolJson descriptor) {
        return instantiate(descriptor, 0);
    }

    private CompoundNode instantiate(PftoolJson descriptor, int depth) {
        if (depth > MAX_NEST_DEPTH) {
            throw new IllegalStateException("compound nesting exceeds " + MAX_NEST_DEPTH + " levels: " + descriptor.typeId);
        }
        Graph inner = new Graph();
        Map<String, Node> innerById = new LinkedHashMap<>();

        // Nodes
        if (descriptor.subgraph != null && descriptor.subgraph.nodes != null) {
            for (PflowJson.NodeJson nj : descriptor.subgraph.nodes) {
                Node innerNode = baseRegistry.create(nj.typeId);
                rebindId(innerNode, nj.id);
                if (nj.label != null) innerNode.setLabel(nj.label);
                if (nj.enabled != null) innerNode.setEnabled(nj.enabled);
                applyParams(innerNode, nj.params);
                inner.addNode(innerNode);
                innerById.put(nj.id, innerNode);
            }
        }

        // Edges
        if (descriptor.subgraph != null && descriptor.subgraph.edges != null) {
            for (PflowJson.EdgeJson ej : descriptor.subgraph.edges) {
                Node from = innerById.get(ej.from.node);
                Node to   = innerById.get(ej.to.node);
                if (from == null || to == null) {
                    throw new IllegalArgumentException(
                            "compound edge references unknown inner node: " + ej.from.node + " or " + ej.to.node);
                }
                OutputPort<?> op = from.output(ej.from.port);
                InputPort<?>  ip = to.input(ej.to.port);
                if (op == null || ip == null) {
                    throw new IllegalArgumentException(
                            "compound edge references unknown inner port: " + ej.from.port + " / " + ej.to.port);
                }
                inner.connect(op, ip);
            }
        }

        return CompoundNode.build(descriptor.typeId, descriptor, inner, innerById);
    }

    /**
     * Load a .pftool and register a factory in the supplied registry. The
     * factory rebuilds a fresh inner graph each time it's invoked, so two
     * palette drops produce independent instances.
     */
    public String register(Path file, NodeFactoryRegistry registry) throws IOException {
        PftoolJson descriptor = loadJson(file);
        if (descriptor.typeId == null || descriptor.typeId.isBlank()) {
            throw new IOException("pftool has no typeId: " + file);
        }
        registry.register(descriptor.typeId, () -> instantiate(descriptor));
        return descriptor.typeId;
    }

    private static void rebindId(Node node, String id) {
        if (id == null) return;
        try {
            java.lang.reflect.Field f = studio.graph.AbstractNode.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(node, NodeId.of(id));
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // best-effort; node keeps its random id
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyParams(Node node, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) return;
        for (Parameter<?> p : node.parameters()) {
            Object raw = overrides.get(p.name);
            if (raw == null) continue;
            Object coerced = ParamCoercion.coerce(p, raw);
            if (coerced != null) {
                ((Parameter) p).set(coerced);
            }
        }
    }

    /** Cleared at runtime by callers that wish to silence the leakage of an Edge import. */
    @SuppressWarnings("unused")
    private static Edge unused;
}
