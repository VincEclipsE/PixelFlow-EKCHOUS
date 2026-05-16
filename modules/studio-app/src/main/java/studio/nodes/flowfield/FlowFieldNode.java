package studio.nodes.flowfield;

import com.thomasdiewald.pixelflow.java.imageprocessing.DwFlowField;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/**
 * Builds a flow field (RG velocity) from the source's gradient and renders
 * it as line integral convolution. Useful for visualising the motion
 * implied by a density texture (e.g. fluid output) as smooth streamlines.
 */
public final class FlowFieldNode extends AbstractNode {

    public static final String TYPE_ID = "pf.flowfield.FlowField";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    /** Raw RG velocity texture (DwFlowField.tex_vel) wrapped as a RenderTarget.
     *  Downstream particle/advection consumers should sample this, not the
     *  visualised {@code dst}. */
    public final OutputPort<RenderTarget> outVel;
    public final Parameter<Integer> pBlurIterations;
    public final Parameter<Integer> pBlurRadius;

    private DwFlowField field;
    private GLTextureTarget dst;
    private GLTextureTarget velTarget;
    private int lastW = -1, lastH = -1;

    public FlowFieldNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.outVel = declareOutput("vel", PortTypes.TEXTURE2D);
        this.pBlurIterations = declareParam(Parameter.intRange("blur_iterations", 1, 0, 8));
        this.pBlurRadius     = declareParam(Parameter.intRange("blur_radius", 2, 0, 16));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        field = new DwFlowField(ctx.pixelFlow());
    }

    @Override public void evaluate(Frame f) {
        RenderTarget src = f.read(in);
        if (src == null || !src.isSampleable()) return;

        int w = src.getWidth(), h = src.getHeight();
        if (dst == null || w != lastW || h != lastH) {
            if (dst != null) dst.release();
            dst = GLTextureTarget.create(f.pixelFlow(), w, h);
            lastW = w; lastH = h;
        }
        field.param.blur_iterations = pBlurIterations.get();
        field.param.blur_radius     = pBlurRadius.get();
        field.create(src);
        field.displayLineIntegralConvolution(dst, src);
        velTarget = new GLTextureTarget(f.pixelFlow(), field.tex_vel);
        f.publish(out, dst);
        f.publish(outVel, velTarget);
    }

    /** Direct access to the underlying DwFlowField for downstream consumers
     *  that need it (FlowFieldParticlesNode binds to it via the velocity tex). */
    public DwFlowField field() { return field; }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
        // velTarget wraps field.tex_vel; field.release happens via its owner.
        velTarget = null;
    }
}
