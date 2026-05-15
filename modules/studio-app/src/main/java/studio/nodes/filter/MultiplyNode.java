package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Multiply;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.PortTypes;

/** Per-pixel modulate: {@code src * mul}. Both inputs are textures. */
public final class MultiplyNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Multiply";

    public final InputPort<RenderTarget>  src;
    public final InputPort<RenderTarget>  mul;
    public final OutputPort<RenderTarget> out;

    private Multiply filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public MultiplyNode() {
        this.src = declareInput("src", PortTypes.TEXTURE2D);
        this.mul = declareInput("mul", PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new Multiply(ctx.pixelFlow());
    }

    @Override public void evaluate(Frame f) {
        RenderTarget ts = f.read(src);
        RenderTarget tm = f.read(mul);
        if (ts == null || tm == null) return;
        if (!ts.isSampleable() || !tm.isSampleable()) return;

        int w = ts.getWidth(), h = ts.getHeight();
        if (dst == null || w != lastW || h != lastH) {
            if (dst != null) dst.release();
            dst = GLTextureTarget.create(f.pixelFlow(), w, h);
            lastW = w; lastH = h;
        }
        filter.apply(ts, dst, tm);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
