package studio.save;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import studio.graph.Edge;
import studio.graph.Graph;
import studio.graph.Node;
import studio.graph.Parameter;

/**
 * Inverse of {@link PflowReader}. Serializes a live {@link Graph} (plus any
 * preserved metadata from a previously-loaded {@link PflowJson}) to a
 * {@code .pflow} JSON file.
 *
 * <p>Parameter overrides are emitted ONLY when the current value differs
 * from the parameter's declared default, keeping files small and tolerant
 * of upstream default tweaks.
 */
public final class PflowWriter {

    private PflowWriter() {}

    /**
     * Serialize {@code graph} as a new PflowJson and write it to {@code file}.
     * {@code template} provides optional metadata (schema, name, output) to
     * preserve across save round-trips; pass null to use defaults.
     */
    public static void write(Path file, Graph graph, PflowJson template) throws IOException {
        JsonCodec.write(file, build(graph, template));
    }

    public static PflowJson build(Graph graph, PflowJson template) {
        PflowJson out = new PflowJson();
        out.schema = template != null && template.schema != null ? template.schema : "pflow/1";
        out.name   = template != null ? template.name : null;
        out.output = template != null ? template.output : null;

        out.nodes = new ArrayList<>();
        for (Node n : graph.nodes()) {
            PflowJson.NodeJson nj = new PflowJson.NodeJson();
            nj.id = n.id().value;
            nj.typeId = n.typeId();
            nj.label = n.label();
            nj.params = new LinkedHashMap<>();
            for (Parameter<?> p : n.parameters()) {
                Object v = paramValueForJson(p);
                if (v != null && !equalsDefault(p)) {
                    nj.params.put(p.name, v);
                }
            }
            out.nodes.add(nj);
        }

        out.edges = new ArrayList<>();
        for (Edge e : graph.edges()) {
            PflowJson.EdgeJson ej = new PflowJson.EdgeJson();
            ej.from = new PflowJson.EdgeEnd();
            ej.from.node = e.from.owner.id().value;
            ej.from.port = e.from.name;
            ej.to = new PflowJson.EdgeEnd();
            ej.to.node = e.to.owner.id().value;
            ej.to.port = e.to.name;
            out.edges.add(ej);
        }

        return out;
    }

    private static Object paramValueForJson(Parameter<?> p) {
        Object v = p.get();
        if (v == null) return null;
        if (v instanceof float[] arr) {
            // serialise float[] as a JSON list of numbers
            ArrayList<Float> out = new ArrayList<>(arr.length);
            for (float f : arr) out.add(f);
            return out;
        }
        return v;
    }

    private static boolean equalsDefault(Parameter<?> p) {
        Object cur = p.get();
        Object def = p.defaultValue;
        if (cur == def) return true;
        if (cur == null || def == null) return false;
        if (cur instanceof float[] a && def instanceof float[] b) {
            if (a.length != b.length) return false;
            for (int i = 0; i < a.length; i++) {
                if (Float.compare(a[i], b[i]) != 0) return false;
            }
            return true;
        }
        return cur.equals(def);
    }
}
