package studio.save;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import studio.graph.Edge;
import studio.graph.Graph;
import studio.graph.Node;
import studio.graph.OutputPort;
import studio.graph.Parameter;

/**
 * Serialises a {@link Graph} as a {@code .pftool} compound tool.
 *
 * <p>v1 exposure model is "expose everything":
 * <ul>
 *   <li>Every {@code OutputPort} of every {@link studio.nodes.builtin.GraphOutputNode}
 *       in the subgraph becomes an exposed output. (No GraphOutputNode? The
 *       compound has zero outputs.)</li>
 *   <li>Every {@link Parameter} of every inner node is exposed with the alias
 *       {@code <nodeLabel>.<paramName>}.</li>
 * </ul>
 *
 * <p>The 5-step "hand-pick what to expose" wizard described in the plan
 * will refine these defaults in M3.4 once the UX is right.
 */
public final class PftoolWriter {

    private PftoolWriter() {}

    public static PftoolJson build(String typeId, String name, String category,
                                   String description, Graph subgraph) {
        PftoolJson out = new PftoolJson();
        out.typeId = typeId;
        out.name = name;
        out.category = category != null ? category : "My Tools";
        out.description = description;
        out.createdAt = Instant.now().toString();

        // -------- Inputs: every inner GraphInputNode becomes an exposed input --------
        List<String> boundaryIds = new ArrayList<>();
        for (Node n : subgraph.nodes()) {
            if (n.typeId().equals(studio.nodes.builtin.GraphInputNode.TYPE_ID)) {
                boundaryIds.add(n.id().value);
                PftoolJson.ExposedInput ei = new PftoolJson.ExposedInput();
                ei.alias = n.label();
                ei.type = "tex2d";
                ei.innerNodeId = n.id().value;
                out.iface.inputs.add(ei);
            }
        }

        // -------- Outputs: every inner GraphOutputNode's input becomes an output --------
        List<String> graphOutputIds = new ArrayList<>();
        for (Node n : subgraph.nodes()) {
            if (n.typeId().equals(studio.nodes.builtin.GraphOutputNode.TYPE_ID)) {
                graphOutputIds.add(n.id().value);
                // GraphOutputNode has a single input "in" of type tex2d.
                // We expose the edge that feeds it.
                Edge e = null;
                for (Edge candidate : subgraph.edges()) {
                    if (candidate.to.owner == n) { e = candidate; break; }
                }
                if (e == null) continue;
                PftoolJson.ExposedOutput eo = new PftoolJson.ExposedOutput();
                eo.alias = n.label();
                eo.type = e.from.type.id;
                eo.innerNodeId = e.from.owner.id().value;
                eo.innerPortName = e.from.name;
                out.iface.outputs.add(eo);
            }
        }

        // -------- Params: prefix each by inner node label --------
        for (Node n : subgraph.nodes()) {
            if (graphOutputIds.contains(n.id().value)) continue; // skip output boundary nodes
            if (boundaryIds.contains(n.id().value)) continue;    // skip input boundary nodes
            for (Parameter<?> p : n.parameters()) {
                PftoolJson.ExposedParam ep = new PftoolJson.ExposedParam();
                ep.alias = n.label() + "." + p.name;
                ep.type = p.type.id;
                ep.uiHint = p.uiHint != null ? p.uiHint.name() : null;
                ep.defaultValue = jsonifyValue(p.defaultValue);
                ep.min = jsonifyValue(p.min);
                ep.max = jsonifyValue(p.max);
                ep.description = p.description;
                ep.innerNodeId = n.id().value;
                ep.innerParamName = p.name;
                out.iface.params.add(ep);
            }
        }

        // -------- Subgraph (nodes + edges) --------
        for (Node n : subgraph.nodes()) {
            PflowJson.NodeJson nj = new PflowJson.NodeJson();
            nj.id = n.id().value;
            nj.typeId = n.typeId();
            nj.label = n.label();
            nj.params = new LinkedHashMap<>();
            for (Parameter<?> p : n.parameters()) {
                Object v = jsonifyValue(p.get());
                if (v != null) nj.params.put(p.name, v);
            }
            out.subgraph.nodes.add(nj);
        }
        for (Edge e : subgraph.edges()) {
            PflowJson.EdgeJson ej = new PflowJson.EdgeJson();
            ej.from = new PflowJson.EdgeEnd();
            ej.from.node = e.from.owner.id().value;
            ej.from.port = e.from.name;
            ej.to = new PflowJson.EdgeEnd();
            ej.to.node = e.to.owner.id().value;
            ej.to.port = e.to.name;
            out.subgraph.edges.add(ej);
        }

        return out;
    }

    public static void write(Path file, String typeId, String name, String category,
                             String description, Graph subgraph) throws IOException {
        JsonCodec.write(file, build(typeId, name, category, description, subgraph));
    }

    private static Object jsonifyValue(Object v) {
        if (v == null) return null;
        if (v instanceof float[] arr) {
            ArrayList<Float> out = new ArrayList<>(arr.length);
            for (float f : arr) out.add(f);
            return out;
        }
        return v;
    }
}
