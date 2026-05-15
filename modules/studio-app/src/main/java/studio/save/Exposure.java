package studio.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import studio.graph.Graph;
import studio.graph.Node;
import studio.graph.Parameter;

/**
 * Hand-picked exposure spec for a compound tool save. Drives
 * {@link PftoolWriter#build(String, String, String, String, Graph, Exposure)}
 * — only the entries listed here are exposed on the compound's outer
 * interface.
 *
 * <p>Use {@link #allOf(Graph)} for the legacy "expose everything"
 * default; the Save-as-Tool dialog populates this from checkboxes.
 */
public final class Exposure {

    /** Inner GraphInputNode ids whose external face is exposed. */
    public final Set<String> includedInputs = new LinkedHashSet<>();

    /** Inner GraphOutputNode ids whose external face is exposed. */
    public final Set<String> includedOutputs = new LinkedHashSet<>();

    /** Per-param exposure pick. */
    public final Set<ParamKey> includedParams = new LinkedHashSet<>();

    /** Optional alias override (default = {@code <nodeLabel>.<paramName>}). */
    public final Map<ParamKey, String> aliases = new LinkedHashMap<>();

    public static Exposure allOf(Graph g) {
        Exposure e = new Exposure();
        for (Node n : g.nodes()) {
            String tid = n.typeId();
            if (tid.equals(studio.nodes.builtin.GraphInputNode.TYPE_ID)) {
                e.includedInputs.add(n.id().value);
            } else if (tid.equals(studio.nodes.builtin.GraphOutputNode.TYPE_ID)) {
                e.includedOutputs.add(n.id().value);
            } else {
                for (Parameter<?> p : n.parameters()) {
                    e.includedParams.add(new ParamKey(n.id().value, p.name));
                }
            }
        }
        return e;
    }

    /** Build an Exposure listing every param of every non-boundary node, with no aliases set. */
    public static List<ParamKey> enumerateParams(Graph g) {
        List<ParamKey> out = new ArrayList<>();
        for (Node n : g.nodes()) {
            String tid = n.typeId();
            if (tid.equals(studio.nodes.builtin.GraphInputNode.TYPE_ID)) continue;
            if (tid.equals(studio.nodes.builtin.GraphOutputNode.TYPE_ID)) continue;
            for (Parameter<?> p : n.parameters()) {
                out.add(new ParamKey(n.id().value, p.name));
            }
        }
        return out;
    }

    public String aliasOf(ParamKey k, String fallback) {
        String a = aliases.get(k);
        return (a == null || a.isBlank()) ? fallback : a;
    }

    public static final class ParamKey {
        public final String nodeId;
        public final String paramName;

        public ParamKey(String nodeId, String paramName) {
            this.nodeId = Objects.requireNonNull(nodeId);
            this.paramName = Objects.requireNonNull(paramName);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParamKey k)) return false;
            return nodeId.equals(k.nodeId) && paramName.equals(k.paramName);
        }
        @Override public int hashCode() { return Objects.hash(nodeId, paramName); }
        @Override public String toString() { return nodeId + "." + paramName; }
    }
}
