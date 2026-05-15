package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Median;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/** Median filter for salt-and-pepper denoise. {@code kernel} 0=3x3, 1=5x5. */
public final class MedianNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Median";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Integer> pKernel;

    private Median filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public MedianNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pKernel = declareParam(Parameter.intRange("kernel", 0, 0, 1));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new Median(ctx.pixelFlow());
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
        Median.TYPE kernel = pKernel.get() >= 1 ? Median.TYPE._5x5_ : Median.TYPE._3x3_;
        filter.apply(src, dst, kernel);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
