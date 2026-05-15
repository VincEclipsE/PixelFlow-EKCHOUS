package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Mad;

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
 * Brightness / contrast adjustment. Builds on the Mad shader (multiply +
 * add) with a standard mapping:
 * <pre>
 *   out = (in - 0.5) * (1 + contrast) + 0.5 + brightness
 * </pre>
 */
public final class LevelsNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Levels";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Float> pBrightness;
    public final Parameter<Float> pContrast;

    private Mad mad;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;
    private final float[] coef = new float[2];

    public LevelsNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pBrightness = declareParam(Parameter.floatRange("brightness", 0.0f, -1.0f, 1.0f));
        this.pContrast   = declareParam(Parameter.floatRange("contrast",   0.0f, -1.0f, 1.0f));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        mad = new Mad(ctx.pixelFlow());
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
        float contrast = pContrast.get();
        float brightness = pBrightness.get();
        coef[0] = 1f + contrast;
        coef[1] = -0.5f * contrast + brightness;
        mad.apply(src, dst, coef);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
