package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Bloom;

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
 * Wraps {@link com.thomasdiewald.pixelflow.java.imageprocessing.filter.Bloom}
 * as a node. Reads a source Texture2D, applies the bloom shader chain, and
 * publishes the bloomed result on its output port.
 *
 * <p>Owns one {@link GLTextureTarget} as the dst buffer (allocated lazily
 * to match the source dimensions on first frame).
 */
public final class BloomNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.Bloom";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;

    public final Parameter<Float>   pMult;
    public final Parameter<Float>   pRadius;
    public final Parameter<Integer> pBlurRadius;

    private Bloom bloom;
    private GLTextureTarget composition;
    private int lastW = -1, lastH = -1;

    public BloomNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pMult       = declareParam(Parameter.floatRange("mult",         1.4f, 0f, 10f));
        this.pRadius     = declareParam(Parameter.floatRange("radius",       0.6f, 0f, 1f));
        this.pBlurRadius = declareParam(Parameter.intRange  ("blur_radius",  3, 0, 32));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        bloom = new Bloom(ctx.pixelFlow());
    }

    @Override public void evaluate(Frame f) {
        RenderTarget src = f.read(in);
        if (src == null || !src.isSampleable()) return;

        int w = src.getWidth();
        int h = src.getHeight();
        if (composition == null || w != lastW || h != lastH) {
            if (composition != null) composition.release();
            composition = GLTextureTarget.create(f.pixelFlow(), w, h);
            lastW = w; lastH = h;
        }

        // Mirror src into composition so Bloom can use composition as both
        // base color and destination; the wrapped Bloom.apply(src, dst, comp)
        // overload is the canonical "additive bloom on top of source" form.
        com.thomasdiewald.pixelflow.java.imageprocessing.filter.DwFilter
                .get(f.pixelFlow()).copy.apply(src, composition);

        bloom.param.mult        = pMult.get();
        bloom.param.radius      = pRadius.get();
        bloom.param.blur_radius = pBlurRadius.get();
        bloom.apply(src, composition, composition);

        f.publish(out, composition);
    }

    @Override public void dispose(GraphContext ctx) {
        if (composition != null) { composition.release(); composition = null; }
    }
}
