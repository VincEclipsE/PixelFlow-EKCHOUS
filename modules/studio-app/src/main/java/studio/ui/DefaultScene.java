package studio.ui;

import java.util.HashMap;
import java.util.Map;

import studio.graph.Graph;
import studio.graph.Node;
import studio.graph.NodeFactoryRegistry;
import studio.nodes.builtin.GraphOutputNode;
import studio.nodes.builtin.NoteNode;
import studio.nodes.flowfield.FlowFieldParticlesNode;
import studio.nodes.fluid.FluidNode;
import studio.nodes.input.MouseNode;
import studio.save.PflowJson;
import studio.save.PflowReader;

/**
 * Code-built starter graph used as the fallback when the user opens the
 * studio without choosing a {@code .pflow} file. Demonstrates the
 * mouse → fluid → flow-field-particles → screen pipeline so the user can
 * click + drag in the preview and see something happen immediately.
 *
 * <p>Lives in code (not as a {@code .pflow} starter) for two reasons:
 * <ul>
 *   <li>It lets the in-memory model start in a clean "(unsaved)" state, so
 *       Ctrl+S routes to Save As instead of silently overwriting a starter.
 *   <li>It avoids a chicken-and-egg between bundled-resources and the
 *       node registry — the wiring uses port references that are easy to
 *       set up in Java but verbose in JSON.
 * </ul>
 */
public final class DefaultScene {

    private DefaultScene() {}

    /**
     * Build the mouse-driven flow-field-particles demo as a fresh
     * {@link PflowReader.Result}. The reader-result wrapper carries the
     * source PflowJson stub (width/height/rootOutputNode), the live Graph,
     * and a nodes-by-id map — same shape the loader produces, so the rest
     * of the studio doesn't need to special-case it.
     */
    public static PflowReader.Result build(NodeFactoryRegistry registry) {
        int canvasW = 800;
        int canvasH = 800;

        MouseNode mouse = (MouseNode) registry.create(MouseNode.TYPE_ID);
        mouse.setLabel("mouse");
        mouse.pVelocityScale.set(6f);

        FluidNode fluid = (FluidNode) registry.create(FluidNode.TYPE_ID);
        fluid.setLabel("fluid");
        fluid.pWidth.set(canvasW);
        fluid.pHeight.set(canvasH);
        fluid.pDissipationVelocity.set(0.92f);
        fluid.pDissipationDensity.set(0.985f);
        fluid.pVorticity.set(0.2f);
        fluid.pInjectRadius.set(22f);
        fluid.pInjectColor.set(new float[]{ 0.95f, 0.55f, 0.15f, 1f });

        FlowFieldParticlesNode particles = (FlowFieldParticlesNode) registry.create(FlowFieldParticlesNode.TYPE_ID);
        particles.setLabel("flow particles");
        particles.pCanvasWidth.set(canvasW);
        particles.pCanvasHeight.set(canvasH);
        particles.pMaxParticles.set(32_768);
        particles.pSpawnPerFrame.set(300);
        particles.pSpawnRadius.set(18f);
        particles.pPointSize.set(7);
        particles.pColorA.set(new float[]{ 1.0f, 0.85f, 0.4f, 3.5f });
        particles.pAccelerationMul.set(2.0f);

        GraphOutputNode out = (GraphOutputNode) registry.create(GraphOutputNode.TYPE_ID);
        out.setLabel("screen");

        NoteNode note = (NoteNode) registry.create(NoteNode.TYPE_ID);
        note.setLabel("welcome");
        studio.graph.Parameter<?> rawText = note.parameter("text");
        if (rawText != null) {
            @SuppressWarnings({"rawtypes","unchecked"})
            studio.graph.Parameter raw = rawText;
            raw.set("Click + drag inside the preview\nto stir the fluid and spawn particles.\n\nUnplug edges with right-click,\nadd nodes from the palette or Shift+A.");
        }

        Graph graph = new Graph();
        graph.addNode(mouse);
        graph.addNode(fluid);
        graph.addNode(particles);
        graph.addNode(out);
        graph.addNode(note);

        // Mouse → Fluid injection
        graph.connect(mouse.outPos,    fluid.inInjectPos);
        graph.connect(mouse.outDelta,  fluid.inInjectVelocity);
        graph.connect(mouse.outActive, fluid.inInjectActive);

        // Fluid → particles (velocity field + density as backdrop)
        graph.connect(fluid.outVelocity, particles.inVel);
        graph.connect(fluid.outDensity,  particles.inBg);
        graph.connect(mouse.outPos,    particles.inSpawnPos);
        graph.connect(mouse.outActive, particles.inSpawnActive);

        // Particles → screen
        graph.connect(particles.out, out.in);

        // Build a minimal PflowJson stub so downstream consumers that read
        // source.output.width/height (HeadlessSmoke, preview's pickRootOutput)
        // get sensible values.
        PflowJson source = new PflowJson();
        source.name = "Default scene — mouse-driven flow field particles";
        source.output = new PflowJson.OutputSettings();
        source.output.width  = canvasW;
        source.output.height = canvasH;
        source.output.rootOutputNode = out.id().value;

        Map<String, Node> byId = new HashMap<>();
        for (Node n : graph.nodes()) byId.put(n.id().value, n);

        return new PflowReader.Result(graph, source, byId);
    }
}
