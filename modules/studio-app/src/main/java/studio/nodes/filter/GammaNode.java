package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Gamma;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/** Per-pixel gamma curve. 1.0 = identity; &gt;1 darkens, &lt;1 brightens. */
public final class GammaNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Gamma";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Float> pGamma;

    private Gamma gamma;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public GammaNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pGamma = declareParam(Parameter.floatRange("gamma", 2.2f, 0.1f, 5.0f));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        gamma = new Gamma(ctx.pixelFlow());
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

        gamma.apply(src, dst, pGamma.get());
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
