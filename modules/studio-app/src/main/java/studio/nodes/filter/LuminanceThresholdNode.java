package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.LuminanceThreshold;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/** Pass-through above {@code threshold} luma, fall-off shaped by {@code exponent}. */
public final class LuminanceThresholdNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.LuminanceThreshold";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Float>   pThreshold;
    public final Parameter<Integer> pExponent;

    private LuminanceThreshold filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public LuminanceThresholdNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pThreshold = declareParam(Parameter.floatRange("threshold", 0.5f, 0.0f, 1.0f));
        this.pExponent  = declareParam(Parameter.intRange("exponent", 1, 1, 32));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new LuminanceThreshold(ctx.pixelFlow());
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

        filter.param.threshold = pThreshold.get();
        filter.param.exponent  = pExponent.get();
        filter.apply(src, dst);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
