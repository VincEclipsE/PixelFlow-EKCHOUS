package studio.nodes.filter;

import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DistanceTransform;

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
 * Voronoi-distance transform — produces, per pixel, a normalised distance
 * to the nearest foreground pixel (where "foreground" is the FG_mask
 * tint). Useful as a seed for obstacle/SDF fields downstream.
 */
public final class DistanceTransformNode extends AbstractNode {

    public static final String TYPE_ID = "pf.filter.DistanceTransform";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Float> pNormalize;

    private DistanceTransform filter;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public DistanceTransformNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pNormalize = declareParam(Parameter.floatRange("voronoi_distance_normalization", 0.1f, 0.0f, 1.0f));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        filter = new DistanceTransform(ctx.pixelFlow());
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
        filter.param.voronoi_distance_normalization = pNormalize.get();
        filter.apply(src, dst);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
