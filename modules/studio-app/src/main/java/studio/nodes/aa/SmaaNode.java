package studio.nodes.aa;

import com.thomasdiewald.pixelflow.java.antialiasing.SMAA.SMAA;

import studio.engine.GLTextureTarget;
import studio.engine.RenderTarget;
import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.GraphContext;
import studio.graph.InputPort;
import studio.graph.OutputPort;
import studio.graph.PortTypes;

/** Subpixel Morphological Anti-Aliasing — slower than FXAA but sharper. */
public final class SmaaNode extends AbstractNode {

    public static final String TYPE_ID = "pf.aa.SMAA";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;

    private SMAA smaa;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public SmaaNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        smaa = new SMAA(ctx.pixelFlow());
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
        smaa.apply(src, dst);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
