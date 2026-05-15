package studio.nodes.image;

import com.thomasdiewald.pixelflow.java.imageprocessing.DwOpticalFlow;

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
 * Two-frame optical flow: pushes the input texture into a ping-pong pair
 * each frame, runs a Sobel-based dense flow estimate, and visualises the
 * RG velocity field via the existing shading shader.
 */
public final class OpticalFlowNode extends AbstractNode {

    public static final String TYPE_ID = "pf.image.OpticalFlow";

    public final InputPort<RenderTarget>  in;
    public final OutputPort<RenderTarget> out;
    public final Parameter<Integer> pBlurInput;
    public final Parameter<Integer> pBlurFlow;
    public final Parameter<Float>   pTemporalSmooth;
    public final Parameter<Boolean> pGrayscale;

    private DwOpticalFlow opticalFlow;
    private GLTextureTarget dst;
    private int lastW = -1, lastH = -1;

    public OpticalFlowNode() {
        this.in  = declareInput("src",  PortTypes.TEXTURE2D);
        this.out = declareOutput("dst", PortTypes.TEXTURE2D);
        this.pBlurInput      = declareParam(Parameter.intRange("blur_input", 10, 0, 64));
        this.pBlurFlow       = declareParam(Parameter.intRange("blur_flow", 5, 0, 64));
        this.pTemporalSmooth = declareParam(Parameter.floatRange("temporal_smooth", 0.5f, 0.0f, 1.0f));
        this.pGrayscale      = declareParam(Parameter.bool("grayscale", true));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        opticalFlow = new DwOpticalFlow(ctx.pixelFlow(), 32, 32);
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
        opticalFlow.param.blur_input         = pBlurInput.get();
        opticalFlow.param.blur_flow          = pBlurFlow.get();
        opticalFlow.param.temporal_smoothing = pTemporalSmooth.get();
        opticalFlow.param.grayscale          = pGrayscale.get();
        opticalFlow.update(src);
        opticalFlow.renderVelocityShading(dst);
        f.publish(out, dst);
    }

    @Override public void dispose(GraphContext ctx) {
        if (dst != null) { dst.release(); dst = null; }
    }
}
