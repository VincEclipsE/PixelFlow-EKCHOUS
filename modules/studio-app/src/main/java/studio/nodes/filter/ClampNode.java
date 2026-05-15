package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Clamp;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/** Per-channel value clamp to a [lo, hi] window. */
public final class ClampNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Clamp";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<float[]> pLo;
    public final Parameter<float[]> pHi;

    private Clamp filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public ClampNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pLo = declareParam(Parameter.vec4("lo", new float[]{ 0, 0, 0, 0 }));
        this.pHi = declareParam(Parameter.vec4("hi", new float[]{ 1, 1, 1, 1 }));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new Clamp(ctx.pixelFlow());
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
        filter.apply(src, dst, pLo.get(), pHi.get());
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
