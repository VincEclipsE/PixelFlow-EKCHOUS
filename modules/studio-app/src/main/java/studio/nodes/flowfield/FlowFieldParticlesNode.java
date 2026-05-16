package studio.nodes.flowfield;

import com.thomasdiewald.pixelflow.java.flowfieldparticles.DwFlowFieldParticles;
import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DwFilter;

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
 * GPU verlet particle simulator driven by an external velocity texture.
 * Wraps {@link DwFlowFieldParticles}. The simulation has its own
 * particle-position texture; this node owns it and renders particles into
 * an output canvas each frame.
 *
 * <p>Wiring:
 * <ul>
 *   <li>{@code vel} — RG velocity texture used as acceleration (e.g. the
 *       {@code velocity} output of a {@link studio.nodes.fluid.FluidNode}
 *       or the {@code vel} output of a {@link FlowFieldNode}).</li>
 *   <li>{@code bg} — optional background texture; if connected, it's
 *       copied into the output as the backdrop before particles render.</li>
 *   <li>{@code spawn_pos} — vec2 position to spawn at (typically a
 *       {@link studio.nodes.input.MouseNode#outPos}).</li>
 *   <li>{@code spawn_active} — bool gate; particles spawn only when true
 *       (typically {@link studio.nodes.input.MouseNode#outActive}).</li>
 * </ul>
 */
public final class FlowFieldParticlesNode extends AbstractNode {

    public static final String TYPE_ID = "pf.flowfield.FlowFieldParticles";

    public final InputPort<RenderTarget> inVel;
    public final InputPort<RenderTarget> inBg;
    public final InputPort<float[]>      inSpawnPos;
    public final InputPort<Boolean>      inSpawnActive;
    public final OutputPort<RenderTarget> out;

    public final Parameter<Integer> pMaxParticles;
    public final Parameter<Integer> pSpawnPerFrame;
    public final Parameter<Float>   pSpawnRadius;
    public final Parameter<Integer> pPointSize;
    public final Parameter<Float>   pVelocityDamping;
    public final Parameter<Float>   pAccelerationMul;
    public final Parameter<float[]> pColorA;
    public final Parameter<float[]> pColorB;
    public final Parameter<Integer> pBlendMode;
    public final Parameter<Boolean> pTrailMode;
    public final Parameter<Integer> pCanvasWidth;
    public final Parameter<Integer> pCanvasHeight;

    private DwFlowFieldParticles particles;
    private GLTextureTarget canvas;
    private int lastW = -1, lastH = -1;

    public FlowFieldParticlesNode() {
        this.inVel         = declareInput ("vel",          PortTypes.TEXTURE2D);
        this.inBg          = declareInput ("bg",           PortTypes.TEXTURE2D, false);
        this.inSpawnPos    = declareInput ("spawn_pos",    PortTypes.VEC2,      false);
        this.inSpawnActive = declareInput ("spawn_active", PortTypes.BOOL,      false);
        this.out           = declareOutput("dst",          PortTypes.TEXTURE2D);

        this.pMaxParticles    = declareParam(Parameter.intRange  ("max_particles",     8192, 64, 1_048_576).structural()
                .withDescription("Capacity of the particle buffer (square texture, rounded up)."));
        this.pSpawnPerFrame   = declareParam(Parameter.intRange  ("spawn_per_frame",   200, 0, 4096));
        this.pSpawnRadius     = declareParam(Parameter.floatRange("spawn_radius",      14f, 0f, 256f));
        this.pPointSize       = declareParam(Parameter.intRange  ("point_size",        8, 1, 64));
        this.pVelocityDamping = declareParam(Parameter.floatRange("velocity_damping",  0.995f, 0f, 1f));
        this.pAccelerationMul = declareParam(Parameter.floatRange("acceleration_mul",  1.0f, 0f, 16f));
        this.pColorA          = declareParam(Parameter.vec4("color_a",
                new float[]{ 1.0f, 0.6f, 0.2f, 4.0f })
                .withUiHint(Parameter.UiHint.COLOR_RGBA)
                .withDescription("Particle core colour (alpha scales additive contribution)."));
        this.pColorB          = declareParam(Parameter.vec4("color_b",
                new float[]{ 0.0f, 0.0f, 0.0f, 0.0f })
                .withUiHint(Parameter.UiHint.COLOR_RGBA)
                .withDescription("Particle edge colour."));
        this.pBlendMode       = declareParam(Parameter.intRange("blend_mode", 1, 0, 1)
                .withDescription("0 = alpha blend, 1 = additive."));
        this.pTrailMode       = declareParam(Parameter.bool("draw_trails", false)
                .withDescription("Render particles as connected lines instead of points."));
        this.pCanvasWidth     = declareParam(Parameter.intRange("canvas_width",  800, 64, 8192).structural());
        this.pCanvasHeight    = declareParam(Parameter.intRange("canvas_height", 800, 64, 8192).structural());
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void init(GraphContext ctx) {
        particles = new DwFlowFieldParticles(ctx.pixelFlow(), pMaxParticles.get());
    }

    @Override public void evaluate(Frame f) {
        if (particles == null) return;
        RenderTarget vel = f.read(inVel);
        if (!(vel instanceof GLTextureTarget velTex) || !velTex.isSampleable()) {
            return;
        }

        int w = pCanvasWidth.get();
        int h = pCanvasHeight.get();
        if (canvas == null || w != lastW || h != lastH) {
            if (canvas != null) canvas.release();
            canvas = GLTextureTarget.create(f.pixelFlow(), w, h);
            particles.resizeWorld(w, h);
            lastW = w; lastH = h;
        }

        // Sync params
        particles.param.size_display      = pPointSize.get();
        particles.param.velocity_damping  = pVelocityDamping.get();
        particles.param.mul_acc           = pAccelerationMul.get();
        particles.param.col_A             = pColorA.get();
        particles.param.col_B             = pColorB.get();
        particles.param.blend_mode        = pBlendMode.get();

        // Spawn at the mouse (or wherever spawn_pos is wired) when active.
        Boolean active = f.read(inSpawnActive);
        float[] spawn  = f.read(inSpawnPos);
        if (Boolean.TRUE.equals(active) && spawn != null && spawn.length >= 2 && pSpawnPerFrame.get() > 0) {
            DwFlowFieldParticles.SpawnRadial sr = new DwFlowFieldParticles.SpawnRadial();
            sr.num(pSpawnPerFrame.get());
            sr.pos(spawn[0], spawn[1]); // spawn shader uses the same top-down convention as fluid injection
            sr.dim(pSpawnRadius.get(), pSpawnRadius.get());
            sr.vel(0, 0);
            particles.spawn(w, h, sr);
        }

        // Advance the simulation using the input velocity texture as the
        // acceleration field, then composite into our canvas.
        particles.update(velTex.texture);

        // Background pass: optional input or solid backdrop.
        RenderTarget bg = f.read(inBg);
        if (bg != null && bg.isSampleable()) {
            DwFilter.get(f.pixelFlow()).copy.apply(bg, canvas);
        } else {
            f.pixelFlow().begin();
            f.pixelFlow().beginDraw(canvas);
            f.gl().glClearColor(0f, 0f, 0f, 1f);
            f.gl().glClear(com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT);
            f.pixelFlow().endDraw();
            f.pixelFlow().end("FlowFieldParticles.bg");
        }

        if (pTrailMode.get()) {
            particles.displayTrail(canvas);
        } else {
            particles.displayParticles(canvas);
        }
        f.publish(out, canvas);
    }

    @Override public void dispose(GraphContext ctx) {
        if (particles != null) { particles.release(); particles = null; }
        if (canvas != null)    { canvas.release(); canvas = null; }
    }
}
