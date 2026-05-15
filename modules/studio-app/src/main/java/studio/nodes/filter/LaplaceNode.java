package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Laplace;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/** Laplacian edge-enhancement filter. kernel 0=W4, 1=W8, 2=W12 weight. */
public final class LaplaceNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Laplace";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Integer> pKernel;

    private Laplace filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public LaplaceNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pKernel = declareParam(Parameter.intRange("kernel", 1, 0, 2));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new Laplace(ctx.pixelFlow());
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
        Laplace.TYPE kernel = switch (pKernel.get()) {
            case 0  -> Laplace.TYPE._3x3_W4;
            case 2  -> Laplace.TYPE._3x3_W12;
            default -> Laplace.TYPE._3x3_W8;
        };
        filter.apply(src, dst, kernel);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
