package studio.nodes.image;

import com.thomasdiewald.pixelflow.java.imageprocessing.DwHarrisCorner;

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
 * Harris corner detector. Computes per-pixel corner response from the
 * source's gradients and renders the corners as additive points.
 */
public final class HarrisCornerNode extends AbstractNode {

    public static final String TYPE_ID = "pf.image.HarrisCorner";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Integer> pBlurInput;
    public final Parameter<Integer> pBlurHarris;
    public final Parameter<Integer> pBlurFinal;
    public final Parameter<Float>   pScale;
    public final Parameter<Float>   pSensitivity;
    public final Parameter<Boolean> pNonMaxSup;

    private DwHarrisCorner harris;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public HarrisCornerNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pBlurInput   = declareParam(Parameter.intRange("blur_input", 1, 0, 16));
        this.pBlurHarris  = declareParam(Parameter.intRange("blur_harris", 3, 0, 16));
        this.pBlurFinal   = declareParam(Parameter.intRange("blur_final", 2, 0, 16));
        this.pScale       = declareParam(Parameter.floatRange("scale", 500f, 1f, 5000f));
        this.pSensitivity = declareParam(Parameter.floatRange("sensitivity", 0.12f, 0.04f, 0.30f));
        this.pNonMaxSup   = declareParam(Parameter.bool("non_max_suppression", false));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        harris = new DwHarrisCorner(ctx.pixelFlow());
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
        harris.param.blur_input        = pBlurInput.get();
        harris.param.blur_harris       = pBlurHarris.get();
        harris.param.blur_final        = pBlurFinal.get();
        harris.param.scale             = pScale.get();
        harris.param.sensitivity       = pSensitivity.get();
        harris.param.nonMaxSuppression = pNonMaxSup.get();
        harris.update(src);
        harris.render(dst);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
