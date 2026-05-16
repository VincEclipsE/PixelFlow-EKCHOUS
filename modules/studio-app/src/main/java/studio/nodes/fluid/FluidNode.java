package studio.nodes.fluid;

import com.jogamp.opengl.GL;
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
    public final Parameter<Float>   pDissipationTemperature;
    public final Parameter<Float>   pVorticity;
    public final Parameter<Integer> pJacobi;
    public final Parameter<Boolean> pApplyBuoyancy;
    public final Parameter<Float>   pInjectTemperature;
    public final Parameter<float[]> pInjectColor;
    public final Parameter<Float>   pInjectRadius;
    public final Parameter<float[]> pInjectPos;
    public final Parameter<float[]> pInjectVelocity;
    public final Parameter<Integer> pVelocityBlendMode;
    public final Parameter<Integer> pDisplayMode;
    public final Parameter<Boolean> pShowVelocityVectors;
    public final Parameter<Integer> pVectorSpacing;

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
        this.pDissipationTemperature = declareParam(Parameter.floatRange("dissipation_temperature", 0.85f, 0f, 1f)
                .withDescription("How fast injected heat decays. Lower = smoke cools and stops rising sooner."));
        this.pVorticity           = declareParam(Parameter.floatRange("vorticity",            0.15f, 0f, 1f));
        this.pJacobi              = declareParam(Parameter.intRange("jacobi_iterations",      30, 1, 200));
        this.pApplyBuoyancy       = declareParam(Parameter.bool("apply_buoyancy", false)
                .withDescription("When on, hot density rises (real smoke). Pair with inject_temperature > 0."));
        this.pInjectTemperature   = declareParam(Parameter.floatRange("inject_temperature", 0f, 0f, 4f)
                .withDescription("Heat added to the fluid alongside each density injection. 0 = cold (no buoyant rise)."));

        this.pInjectColor    = declareParam(Parameter.vec4("inject_color",
                new float[]{ 1.0f, 0.45f, 0.05f, 1.0f })
                .withUiHint(Parameter.UiHint.COLOR_RGBA));
        this.pInjectRadius   = declareParam(Parameter.floatRange("inject_radius", 30f, 1f, 500f));
        this.pInjectPos      = declareParam(new Parameter<>("inject_pos", PortTypes.VEC2,
                new float[]{ 400f, 200f }));
        this.pInjectVelocity = declareParam(new Parameter<>("inject_velocity", PortTypes.VEC2,
                new float[]{ 0f, 80f }));
        // DwFluid2D's default (MAX_MAGNITUDE, mode 2) rejects new velocity
        // injections whose magnitude is smaller than what's already in the cell,
        // which makes re-strokes follow the original drag's path because old
        // high-velocity cells stay "occupied" until natural decay frees them.
        // ADD (mode 1) lets new strokes actually steer the field per-frame.
        this.pVelocityBlendMode = declareParam(Parameter.intRange("velocity_blend_mode", 1, 0, 2)
                .withDescription("0 = REPLACE, 1 = ADD, 2 = MAX_MAGNITUDE (library default)."));
        this.pDisplayMode = declareParam(Parameter.intRange("display_mode", 0, 0, 3)
                .withDescription("What renderFluidTextures writes to the density output: 0 = density, 1 = temperature, 2 = pressure, 3 = velocity."));
        this.pShowVelocityVectors = declareParam(Parameter.bool("show_velocity_vectors", false)
                .withDescription("Overlay velocity streamline arrows on the density output. Debug aid."));
        this.pVectorSpacing = declareParam(Parameter.intRange("vector_spacing", 16, 4, 128)
                .withDescription("Pixel spacing between velocity vectors when show_velocity_vectors is on."));
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

        fluid.param.dissipation_velocity    = pDissipationVelocity.get();
        fluid.param.dissipation_density     = pDissipationDensity.get();
        fluid.param.dissipation_temperature = pDissipationTemperature.get();
        fluid.param.vorticity               = pVorticity.get();
        fluid.param.num_jacobi_projection   = pJacobi.get();
        fluid.param.apply_buoyancy          = pApplyBuoyancy.get();

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
            fluid.addVelocity(pos[0], pos[1], radius, vel[0], vel[1], pVelocityBlendMode.get(), 0.5f);
            float t = pInjectTemperature.get();
            if (t > 0f) fluid.addTemperature(pos[0], pos[1], radius, t);
        }
        fluid.update();
        // renderFluidTextures uses GL_SRC_ALPHA/GL_ONE_MINUS_SRC_ALPHA blending and
        // never clears `target` itself, so once a pixel was drawn bright the dst
        // RGB stays put forever even after tex_density's alpha decays to 0.
        // Clearing to transparent each frame makes the visible output track the
        // simulation instead of accumulating a stale phosphor trail.
        f.pixelFlow().begin();
        f.pixelFlow().beginDraw(target);
        f.gl().glClearColor(0f, 0f, 0f, 0f);
        f.gl().glClear(GL.GL_COLOR_BUFFER_BIT);
        f.pixelFlow().endDraw();
        f.pixelFlow().end("FluidNode.clearTarget");
        fluid.renderFluidTextures(target, pDisplayMode.get());
        if (pShowVelocityVectors.get()) {
            fluid.renderFluidVectors(target, pVectorSpacing.get());
        }
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
