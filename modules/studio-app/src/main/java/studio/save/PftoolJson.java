package studio.save;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * On-disk shape of a {@code .pftool} compound-tool file. v1 keeps the schema
 * intentionally lean: a subgraph (same shape as PflowJson.NodeJson[] +
 * EdgeJson[]) and an interface section describing which outputs the
 * compound exposes plus the param aliases.
 *
 * <p>v1 does not yet support compound inputs (boundary GraphInputNodes) or
 * fingerprinted param forwarding — those land alongside richer node types
 * in a later iteration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PftoolJson {

    public String schema = "pftool/1";
    public String typeId;       // unique id used by the registry, e.g. "user:InkSplash"
    public String version = "1.0.0";
    public String name;
    public String category;
    public String description;
    public String author;
    public String createdAt;

    public InterfaceSection iface = new InterfaceSection();
    public Subgraph subgraph = new Subgraph();

    public static final class InterfaceSection {
        public List<ExposedInput>  inputs  = new ArrayList<>();
        public List<ExposedOutput> outputs = new ArrayList<>();
        public List<ExposedParam>  params  = new ArrayList<>();
    }

    public static final class ExposedInput {
        public String alias;          // outer port name
        public String type;           // PortType.id
        public String innerNodeId;    // GraphInputNode in the subgraph whose .out drives this
    }

    public static final class ExposedOutput {
        public String alias;          // outer port name
        public String type;           // PortType.id (e.g. "tex2d")
        public String innerNodeId;    // sub-node holding the underlying output
        public String innerPortName;  // sub-node's output port name
    }

    public static final class ExposedParam {
        public String alias;          // outer param name (used in JSON keys when overridden)
        public String type;           // PortType.id
        public String uiHint;
        public Object defaultValue;
        public Object min;
        public Object max;
        public String description;
        public String innerNodeId;
        public String innerParamName;
    }

    public static final class Subgraph {
        public String schema = "graph/1";
        public List<PflowJson.NodeJson> nodes = new ArrayList<>();
        public List<PflowJson.EdgeJson> edges = new ArrayList<>();
    }
}
