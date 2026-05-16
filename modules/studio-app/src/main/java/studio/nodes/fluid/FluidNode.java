package studio.nodes.fluid;

import com.thomasdiewald.pixelflow.java.fluid.DwFluid2D;

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
 * Wraps {@link DwFluid2D} as a node. Each frame, the node injects density at
 * its configured (x, y) with the parameter color/radius, advances the
 * simulation one step, and publishes the rendered density texture on its
 * single output port.
 *
 * <p>v1 keeps the injection mechanism simple — a single fixed injection per
 * frame driven by parameters. Future variants can take a Texture2D input
 * as an injection mask.
 */
public final class FluidNode extends AbstractNode {

    public static final String TYPE_ID = "pf.fluid.DwFluid2D";

    public final OutputPort<RenderTarget> outDensity;
    public final OutputPort<RenderTarget> outVelocity;

    /**
     * Optional inputs. When connected, they override the corresponding
     * parameter values for that frame — letting upstream nodes (e.g. a
     * {@link studio.nodes.input.MouseNode}) drive the injection without the
     * user manually editing the param fields.
     */
    public final InputPort<float[]> inInjectPos;
    public final InputPort<float[]> inInjectVelocity;
    public final InputPort<Boolean> inInjectActive;

    public final Parameter<Integer> pWidth;
    public final Parameter<Integer> pHeight;
    public final Parameter<Integer> pGridScale;
    public final Parameter<Float>   pDissipationVelocity;
    public final Parameter<Float>   pDissipationDensity;
    public final Parameter<Float>   pVorticity;
    public final Parameter<Integer> pJacobi;
    public final Parameter<float[]> pInjectColor;
    public final Parameter<Float>   pInjectRadius;
    public final Parameter<float[]> pInjectPos;
    public final Parameter<float[]> pInjectVelocity;

    private DwFluid2D fluid;
    private GLTextureTarget target;
    private GLTextureTarget velocityTarget;

    public FluidNode() {
        this.outDensity  = declareOutput("density",  PortTypes.TEXTURE2D);
        this.outVelocity = declareOutput("velocity", PortTypes.TEXTURE2D);
        this.inInjectPos      = declareInput("inject_pos",      PortTypes.VEC2, false);
        this.inInjectVelocity = declareInput("inject_velocity", PortTypes.VEC2, false);
        this.inInjectActive   = declareInput("inject_active",   PortTypes.BOOL, false);

        this.pWidth      = declareParam(Parameter.intRange("width",  800, 64, 8192).structural());
        this.pHeight     = declareParam(Parameter.intRange("height", 800, 64, 8192).structural());
        this.pGridScale  = declareParam(Parameter.intRange("grid_scale", 1, 1, 16).structural());

        this.pDissipationVelocity = declareParam(Parameter.floatRange("dissipation_velocity", 0.85f, 0f, 1f));
        this.pDissipationDensity  = declareParam(Parameter.floatRange("dissipation_density",  0.99f, 0f, 1f));
        this.pVorticity           = declareParam(Parameter.floatRange("vorticity",            0.15f, 0f, 1f));
        this.pJacobi              = declareParam(Parameter.intRange("jacobi_iterations",      30, 1, 200));

        this.pInjectColor    = declareParam(Parameter.vec4("inject_color",
                new float[]{ 1.0f, 0.45f, 0.05f, 1.0f })
                .withUiHint(Parameter.UiHint.COLOR_RGBA));
        this.pInjectRadius   = declareParam(Parameter.floatRange("inject_radius", 30f, 1f, 500f));
        this.pInjectPos      = declareParam(new Parameter<>("inject_pos", PortTypes.VEC2,
                new float[]{ 400f, 200f }));
        this.pInjectVelocity = declareParam(new Parameter<>("inject_velocity", PortTypes.VEC2,
                new float[]{ 0f, 80f }));
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        int w = pWidth.get();
        int h = pHeight.get();
        int gs = pGridScale.get();
        this.fluid = new DwFluid2D(ctx.pixelFlow(), w, h, gs);
        this.target = GLTextureTarget.create(ctx.pixelFlow(), w, h);
    }

    @Override public void evaluate(Frame f) {
        if (fluid == null) return;

        fluid.param.dissipation_velocity  = pDissipationVelocity.get();
        fluid.param.dissipation_density   = pDissipationDensity.get();
        fluid.param.vorticity             = pVorticity.get();
        fluid.param.num_jacobi_projection = pJacobi.get();

        // Inputs override params when wired.
        float[] inPos = f.read(inInjectPos);
        float[] inVel = f.read(inInjectVelocity);
        Boolean inAct = f.read(inInjectActive);

        float[] pos = inPos != null ? inPos : pInjectPos.get();
        float[] vel = inVel != null ? inVel : pInjectVelocity.get();
        float[] col = pInjectColor.get();
        float radius = pInjectRadius.get();
        boolean active = inAct != null ? inAct : true;

        if (active) {
            fluid.addDensity(pos[0], pos[1], radius, col[0], col[1], col[2], col[3]);
            fluid.addVelocity(pos[0], pos[1], radius, vel[0], vel[1]);
        }
        fluid.update();
        fluid.renderFluidTextures(target, 0);
        // tex_velocity.src ping-pongs each frame; wrap fresh so consumers
        // (FlowFieldParticlesNode) see the latest. The wrapper is a thin
        // adapter — no extra GPU resources.
        velocityTarget = new GLTextureTarget(f.pixelFlow(), fluid.tex_velocity.src);
        f.publish(outDensity, target);
        f.publish(outVelocity, velocityTarget);
    }

    @Override public void dispose(GraphContext ctx) {
        if (fluid != null)  { fluid.release();  fluid = null; }
        if (target != null) { target.release(); target = null; }
        // velocityTarget wraps the fluid's own DwGLTexture — do not release.
        velocityTarget = null;
    }
}
