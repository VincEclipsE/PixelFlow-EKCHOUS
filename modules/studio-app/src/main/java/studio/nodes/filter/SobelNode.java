package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Sobel;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.Parameter;
import studio.graph.PortTypes;

/** Sobel edge-detection filter. Kernel is fixed to 3x3 in v1. */
public final class SobelNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Sobel";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;

    public final Parameter<Integer> pKernel;   // 0=horz, 1=vert, 2=tlbr (3x3 family)

    private Sobel sobel;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public SobelNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pKernel = declareParam(Parameter.intRange("kernel", 0, 0, 2));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        sobel = new Sobel(ctx.pixelFlow());
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
        Sobel.TYPE kernel = switch (pKernel.get()) {
            case 1  -> Sobel.TYPE._3x3_VERT;
            case 2  -> Sobel.TYPE._3x3_TLBR;
            default -> Sobel.TYPE._3x3_HORZ;
        };
        sobel.apply(src, dst, kernel);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
