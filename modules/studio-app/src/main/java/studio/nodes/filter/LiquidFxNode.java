package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DwLiquidFX;

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
 * Two-pass post-process that turns sparse particle-like inputs into a
 * connected liquid surface with shading and subsurface scattering. Wraps
 * {@link DwLiquidFX}.
 */
public final class LiquidFxNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.LiquidFX";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Integer> pBaseBlurRadius;
    public final Parameter<Float>   pBaseThreshold;
    public final Parameter<Float>   pHighlightDecay;
    public final Parameter<Float>   pSssDecay;
    public final Parameter<Boolean> pSssEnabled;

    private DwLiquidFX fx;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public LiquidFxNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pBaseBlurRadius = declareParam(Parameter.intRange("base_blur_radius", 2, 0, 16));
        this.pBaseThreshold  = declareParam(Parameter.floatRange("base_threshold", 0.7f, 0.0f, 1.0f));
        this.pHighlightDecay = declareParam(Parameter.floatRange("highlight_decay", 0.6f, 0.0f, 1.0f));
        this.pSssDecay       = declareParam(Parameter.floatRange("sss_decay", 0.7f, 0.0f, 1.0f));
        this.pSssEnabled     = declareParam(Parameter.bool("sss_enabled", true));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        fx = new DwLiquidFX(ctx.pixelFlow());
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
        fx.param.base_blur_radius = pBaseBlurRadius.get();
        fx.param.base_threshold   = pBaseThreshold.get();
        fx.param.highlight_decay  = pHighlightDecay.get();
        fx.param.sss_decay        = pSssDecay.get();
        fx.param.sss_enabled      = pSssEnabled.get();
        fx.apply(src, dst);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
