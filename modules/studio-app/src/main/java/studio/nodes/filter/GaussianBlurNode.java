package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.GaussianBlur;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/** Two-pass separable Gaussian blur, parameterised by radius. */
public final class GaussianBlurNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.GaussianBlur";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;

    public final Parameter<Integer> pRadius;

    private GaussianBlur blur;
    private GLTextureTarget dst;
    private GLTextureTarget tmp;
    private int lastW = -1, lastH = -1;

    public GaussianBlurNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pRadius = declareParam(Parameter.intRange("radius", 4, 0, 64));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        blur = new GaussianBlur(ctx.pixelFlow());
    }

    @Override public void evaluate(Frame f) {
        RenderTarget src = f.read(in);
        if (src == null || !src.isSampleable()) return;

        int w = src.getWidth(), h = src.getHeight();
        if (dst == null || w != lastW || h != lastH) {
            if (dst != null) dst.release();
            if (tmp != null) tmp.release();
            dst = GLTextureTarget.create(f.pixelFlow(), w, h);
            tmp = GLTextureTarget.create(f.pixelFlow(), w, h);
            lastW = w; lastH = h;
        }

        blur.apply(src, dst, tmp, pRadius.get());
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
        if (tmp != null) { tmp.release(); tmp = null; }
    }
}
