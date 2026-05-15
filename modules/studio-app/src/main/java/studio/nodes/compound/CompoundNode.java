package studio.nodes.compound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.Graph;
import studio.graph.GraphContext;
import studio.graph.GraphRuntime;
import studio.graph.Node;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortType;
import studio.graph.PortTypes;
import studio.save.PftoolJson;

/**
 * A node whose implementation is itself a {@link Graph} of other nodes. From
 * the outside, indistinguishable from a primitive — it has typed input/output
 * ports and parameters; from the inside, it runs a private {@link GraphRuntime}
 * on every {@link #evaluate(Frame)} call.
 *
 * <p>v1 scope:
 * <ul>
 *   <li>No compound inputs (no GraphInputNode boundary stubs yet); the inner
 *       graph is fully self-contained. The compound's input port list is
 *       empty.</li>
 *   <li>Exposed outputs are 1:1 with inner GraphOutputNodes (or any inner
 *       OutputPort flagged by the descriptor).</li>
 *   <li>Exposed params forward to their inner counterparts at evaluate-time
 *       (snapshot the outer value, push it into the inner parameter).</li>
 *   <li>Nesting works — CompoundNode is itself a Node so a .pftool can
 *       reference another user tool via its typeId.</li>
 *   <li>Recursion bound is enforced by the LOADER, not here.</li>
 * </ul>
 */
public final class CompoundNode extends AbstractNode {

    private final String typeId;
    private final PftoolJson descriptor;
    private final Graph inner;
    private final Map<String, Node> innerById;          // inner node id → inner node
    private final List<OutputBinding> outputBindings;   // outer-output → inner-output port
    private final List<ParamBinding> paramBindings;     // outer-param → inner-param

    private GraphRuntime innerRuntime;
    private GraphOutputCapture[] innerOutputCaptures;   // shadow nodes that capture inner output values

    private CompoundNode(String typeId, PftoolJson descriptor, Graph inner,
                         Map<String, Node> innerById,
                         List<OutputBinding> outputBindings,
                         List<ParamBinding> paramBindings) {
        super();
        this.typeId = typeId;
        this.descriptor = descriptor;
        this.inner = inner;
        this.innerById = innerById;
        this.outputBindings = outputBindings;
        this.paramBindings = paramBindings;

        for (OutputBinding ob : outputBindings) {
            // The outer port shares the inner port's type for v1.
            ob.outerPort = declareOutput(ob.alias, (PortType) ob.innerPort.type);
        }
        for (ParamBinding pb : paramBindings) {
            // Mirror metadata (range, hint, description) from the inner param.
            Parameter<?> innerP = pb.innerParam;
            @SuppressWarnings({"rawtypes", "unchecked"})
            Parameter mirror = new Parameter(pb.alias, innerP.type, innerP.defaultValue);
            mirror.label = innerP.label;
            mirror.min = innerP.min;
            mirror.max = innerP.max;
            mirror.uiHint = innerP.uiHint;
            mirror.structural = innerP.structural;
            mirror.description = innerP.description;
            mirror.set(innerP.get());
            pb.outerParam = declareParam(mirror);
        }
    }

    public String descriptorName() { return descriptor != null ? descriptor.name : typeId; }

    @Override public String typeId() { return typeId; }

    @Override protected String defaultLabel() {
        return descriptor != null && descriptor.name != null ? descriptor.name : super.defaultLabel();
    }

    @Override public void init(GraphContext ctx) {
        innerRuntime = new GraphRuntime(inner, ctx);
        innerOutputCaptures = new GraphOutputCapture[outputBindings.size()];
        // Add a capture shadow node downstream of each exposed inner output so we
        // can read its value off the inner runtime's bus between frames.
        // The capture nodes are simple sinks; they're added to the inner graph at
        // construction time (see Builder.build()).
        for (int i = 0; i < outputBindings.size(); i++) {
            innerOutputCaptures[i] = outputBindings.get(i).capture;
        }
    }

    @Override public void evaluate(Frame outerFrame) {
        if (innerRuntime == null) return;

        // 1) Push outer-param values into inner params so the inner sim sees them.
        for (ParamBinding pb : paramBindings) {
            forwardParam(pb);
        }

        // 2) Drive the inner graph one tick.
        innerRuntime.renderFrame();

        // 3) Publish each exposed inner output as our own outer output.
        for (int i = 0; i < outputBindings.size(); i++) {
            OutputBinding ob = outputBindings.get(i);
            Object value = innerOutputCaptures[i].lastValue;
            if (value != null) {
                publishUnchecked(outerFrame, ob.outerPort, value);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void publishUnchecked(Frame frame, OutputPort port, Object value) {
        frame.publish(port, value);
    }

    @Override public void dispose(GraphContext ctx) {
        if (innerRuntime != null) {
            innerRuntime.dispose();
            innerRuntime = null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void forwardParam(ParamBinding pb) {
        Parameter outer = pb.outerParam;
        Parameter inner = pb.innerParam;
        if (outer == null) return;
        Object v = outer.get();
        if (v != null && !Objects.equals(v, inner.get())) {
            inner.set(v);
        }
    }

    /* ------------------------------ Builder ------------------------------ */

    /**
     * Build a CompoundNode from an already-parsed {@link PftoolJson} and the
     * inner-graph nodes/edges constructed by the loader.
     */
    public static CompoundNode build(String typeId, PftoolJson descriptor,
                                     Graph inner, Map<String, Node> innerById) {
        // Bind outputs.
        List<OutputBinding> outputs = new ArrayList<>();
        if (descriptor.iface != null && descriptor.iface.outputs != null) {
            for (PftoolJson.ExposedOutput eo : descriptor.iface.outputs) {
                Node inn = innerById.get(eo.innerNodeId);
                if (inn == null) throw new IllegalArgumentException(
                        "compound output references missing inner node id: " + eo.innerNodeId);
                OutputPort<?> innerPort = inn.output(eo.innerPortName);
                if (innerPort == null) throw new IllegalArgumentException(
                        "compound output references missing port: " + eo.innerNodeId + "." + eo.innerPortName);

                GraphOutputCapture cap = new GraphOutputCapture(innerPort);
                inner.addNode(cap);
                inner.connect(innerPort, cap.in());

                OutputBinding ob = new OutputBinding();
                ob.alias = eo.alias;
                ob.innerPort = innerPort;
                ob.capture = cap;
                outputs.add(ob);
            }
        }

        // Bind params.
        List<ParamBinding> params = new ArrayList<>();
        if (descriptor.iface != null && descriptor.iface.params != null) {
            for (PftoolJson.ExposedParam ep : descriptor.iface.params) {
                Node inn = innerById.get(ep.innerNodeId);
                if (inn == null) throw new IllegalArgumentException(
                        "compound param references missing inner node id: " + ep.innerNodeId);
                Parameter<?> innerParam = inn.parameter(ep.innerParamName);
                if (innerParam == null) throw new IllegalArgumentException(
                        "compound param references missing param: " + ep.innerNodeId + "." + ep.innerParamName);

                ParamBinding pb = new ParamBinding();
                pb.alias = ep.alias != null ? ep.alias : ep.innerParamName;
                pb.innerParam = innerParam;
                params.add(pb);
            }
        }

        return new CompoundNode(typeId, descriptor, inner, innerById, outputs, params);
    }

    /** Look up the inner Node by its descriptor id. Useful for tests / introspection. */
    public Node innerNode(String id) { return innerById.get(id); }

    /* ------------------------------ helpers ------------------------------ */

    private static final class OutputBinding {
        String alias;
        OutputPort<?> innerPort;
        GraphOutputCapture capture;
        OutputPort<?> outerPort;   // populated in ctor
    }

    private static final class ParamBinding {
        String alias;
        Parameter<?> innerParam;
        Parameter<?> outerParam;   // populated in ctor
    }

    /**
     * Sink node added to the inner graph so we can read the latest value
     * produced by an exposed inner output port without scanning the bus.
     */
    public static final class GraphOutputCapture extends AbstractNode {
        public static final String TYPE_ID = "studio.builtin.GraphOutputCapture";

        private final studio.graph.InputPort<Object> inPort;
        volatile Object lastValue;

        @SuppressWarnings({"rawtypes", "unchecked"})
        public GraphOutputCapture(OutputPort<?> mirror) {
            super();
            this.inPort = declareInput("in", (PortType) mirror.type);
        }

        public studio.graph.InputPort<Object> in() { return inPort; }

        @Override public String typeId() { return TYPE_ID; }

        @Override public void evaluate(Frame frame) {
            lastValue = frame.read(inPort);
        }
    }

    /** Compact Objects.equals stand-in to avoid pulling in another import. */
    private static final class Objects {
        static boolean equals(Object a, Object b) {
            return a == b || (a != null && a.equals(b));
        }
    }
}
