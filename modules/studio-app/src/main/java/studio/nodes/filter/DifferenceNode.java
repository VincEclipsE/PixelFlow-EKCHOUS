package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Difference;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.PortTypes;

/** Per-pixel absolute difference: {@code |A - B|}. Two texture inputs. */
public final class DifferenceNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Difference";

    public final InputPort<RenderTarget>  a;
    public final InputPort<RenderTarget>  b;
    public final OutputPort<RenderTarget> out;

    private Difference filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public DifferenceNode() {
        this.a   = declareInput("a",   PortTypes.TEXTURE2D);
        this.b   = declareInput("b",   PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new Difference(ctx.pixelFlow());
    }

    @Override public void evaluate(Frame f) {
        RenderTarget ta = f.read(a);
        RenderTarget tb = f.read(b);
        if (ta == null || tb == null) return;
        if (!ta.isSampleable() || !tb.isSampleable()) return;

        int w = ta.getWidth(), h = ta.getHeight();
        if (dst == null || w != lastW || h != lastH) {
            if (dst != null) dst.release();
            dst = GLTextureTarget.create(f.pixelFlow(), w, h);
            lastW = w; lastH = h;
        }
        filter.apply(dst, ta, tb);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
