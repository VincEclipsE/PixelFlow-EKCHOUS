package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.BilateralFilter;

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
 * Edge-preserving smoothing. Blurs pixels in a {@code radius} neighbourhood
 * weighted by both spatial distance and color similarity, so high-contrast
 * edges survive while flat regions get smoothed.
 */
public final class BilateralFilterNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Bilateral";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Integer> pRadius;
    public final Parameter<Float>   pSigmaColor;
    public final Parameter<Float>   pSigmaSpace;

    private BilateralFilter filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public BilateralFilterNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pRadius     = declareParam(Parameter.intRange("radius", 5, 0, 32));
        this.pSigmaColor = declareParam(Parameter.floatRange("sigma_color", 0.3f, 0.01f, 2.0f));
        this.pSigmaSpace = declareParam(Parameter.floatRange("sigma_space", 5.0f, 0.5f, 32.0f));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new BilateralFilter(ctx.pixelFlow());
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
        filter.apply(src, dst, pRadius.get(), pSigmaColor.get(), pSigmaSpace.get());
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
