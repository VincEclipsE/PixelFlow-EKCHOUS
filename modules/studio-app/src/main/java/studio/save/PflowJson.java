package studio.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plain-old-data structures matching the {@code .pflow} JSON schema. Jackson
 * deserializes into these directly; {@link PflowReader} then turns them into
 * a {@link studio.graph.Graph}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PflowJson {

    public String schema = "pflow/1";
    public String name;
    public OutputSettings output;
    public List<NodeJson> nodes;
    public List<EdgeJson> edges;

    public static final class OutputSettings {
        public Integer width;
        public Integer height;
        public Integer frames;
        public String rootOutputNode;
    }

    public static final class NodeJson {
        public String id;
        public String typeId;
        public String label;
        /** Omit when true (enabled is the default). */
        public Boolean enabled;
        /** Editor pixel coordinates in world space; null when not yet positioned. */
        public Layout layout;
        public Map<String, Object> params = new LinkedHashMap<>();
    }

    public static final class Layout {
        public Integer x;
        public Integer y;

        public Layout() {}
        public Layout(int x, int y) { this.x = x; this.y = y; }
    }

    public static final class EdgeJson {
        public EdgeEnd from;
        public EdgeEnd to;
    }

    public static final class EdgeEnd {
        public String node;
        public String port;
    }
}
