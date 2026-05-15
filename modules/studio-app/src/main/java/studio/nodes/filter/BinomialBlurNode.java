package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.BinomialBlur;

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
 * Two-pass separable binomial blur (Pascal-triangle weights). {@code kernel}
 * maps 0..6 → 3x3..15x15.
 */
public final class BinomialBlurNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.BinomialBlur";

    private static final BinomialBlur.TYPE[] KERNELS = {
        BinomialBlur.TYPE._3x3, BinomialBlur.TYPE._5x5, BinomialBlur.TYPE._7x7,
        BinomialBlur.TYPE._9x9, BinomialBlur.TYPE._11x11, BinomialBlur.TYPE._13x13,
        BinomialBlur.TYPE._15x15,
    };

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Integer> pKernel;

    private BinomialBlur blur;
    private GLTextureTarget dst;
    private GLTextureTarget tmp;
    private int lastW = -1, lastH = -1;

    public BinomialBlurNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pKernel = declareParam(Parameter.intRange("kernel", 1, 0, KERNELS.length - 1));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        blur = new BinomialBlur(ctx.pixelFlow());
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
        int k = Math.max(0, Math.min(KERNELS.length - 1, pKernel.get()));
        blur.apply(src, dst, tmp, KERNELS[k]);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
        if (tmp != null) { tmp.release(); tmp = null; }
    }
}
