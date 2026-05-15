package studio.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.jogamp.opengl.GL2ES2;
import com.thomasdiewald.pixelflow.java.DwPixelFlow;

/**
 * Drives a {@link Graph} forward one frame at a time. Topology is cached;
 * each {@code renderFrame()} walks nodes in topo order, hands each a
 * {@link Frame} to read inputs and publish outputs, and forwards exceptions
 * to {@link GraphErrors} without stopping the rest of the frame.
 */
public final class GraphRuntime {

    private final Graph graph;
    private final GraphContext context;
    private final long startNanos = System.nanoTime();
    private long frameIndex;
    private double lastTimeSec;

    /** Per-output-port latest value for the current frame. Cleared each frame. */
    private final Map<OutputPort<?>, Object> bus = new IdentityHashMap<>();
    private final Set<Node> initialized = new HashSet<>();

    public GraphRuntime(Graph graph, GraphContext context) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.context = Objects.requireNonNull(context, "context");
    }

    public Graph graph() { return graph; }
    public GraphContext context() { return context; }
    public long frameIndex() { return frameIndex; }

    /** Initialise nodes lazily, then execute one frame in topological order. */
    public void renderFrame() {
        List<Node> order = graph.topology();
        for (Node n : order) {
            if (!initialized.contains(n)) {
                try {
                    n.init(context);
                } catch (Throwable t) {
                    context.errors().report(n, t, frameIndex);
                }
                initialized.add(n);
            }
        }

        bus.clear();
        double nowSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        double dt = frameIndex == 0 ? 0.0 : (nowSec - lastTimeSec);
        FrameImpl frame = new FrameImpl(this, nowSec, dt);

        for (Node n : order) {
            frame.currentNode = n;
            try {
                n.evaluate(frame);
            } catch (Throwable t) {
                context.errors().report(n, t, frameIndex);
            }
        }

        lastTimeSec = nowSec;
        frameIndex++;
    }

    /** Release all node-owned resources. */
    public void dispose() {
        for (Node n : graph.nodes()) {
            try {
                n.dispose(context);
            } catch (Throwable t) {
                context.errors().report(n, t, frameIndex);
            }
        }
        initialized.clear();
    }

    Map<OutputPort<?>, Object> bus() { return bus; }

    static final class FrameImpl implements Frame {
        private final GraphRuntime owner;
        private final double timeSec;
        private final double dt;
        Node currentNode;

        FrameImpl(GraphRuntime owner, double timeSec, double dt) {
            this.owner = owner;
            this.timeSec = timeSec;
            this.dt = dt;
        }

        @Override public GL2ES2 gl()               { return owner.context.pixelFlow().gl; }
        @Override public DwPixelFlow pixelFlow()   { return owner.context.pixelFlow(); }
        @Override public GraphContext context()    { return owner.context; }
        @Override public double timeSeconds()      { return timeSec; }
        @Override public long frameIndex()         { return owner.frameIndex; }
        @Override public double deltaSeconds()     { return dt; }

        @Override @SuppressWarnings("unchecked")
        public <T> T read(InputPort<T> port) {
            Edge e = owner.graph.edgeInto(port);
            if (e == null) return null;
            return (T) owner.bus.get(e.from);
        }

        @Override
        public <T> void publish(OutputPort<T> port, T value) {
            owner.bus.put(port, value);
        }
    }
}
