package studio.headless;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLError;

import studio.engine.ClasspathResourceLoader;
import studio.engine.RenderTarget;
import studio.engine.ResourceLoader;
import studio.graph.GraphContext;
import studio.graph.GraphRuntime;
import studio.graph.NodeFactoryRegistry;
import studio.nodes.builtin.GraphOutputNode;
import studio.nodes.filter.BloomNode;
import studio.nodes.fluid.FluidNode;
import studio.save.PflowJson;
import studio.save.PflowReader;

/**
 * M2 acceptance gate: load a {@code .pflow} JSON file, run it for N frames
 * inside a headless GL3 context, and dump the final framebuffer as PNG.
 *
 * <p>Invocation:
 * <pre>
 *   ./gradlew :studio-app:run -PmainClass=studio.headless.HeadlessSmoke \
 *       -Pproject=starters/fluid-bloom.pflow -Pframes=100 -Pout=out.png
 * </pre>
 * <p>If no system properties are passed, the smoke loads
 * {@code starters/fluid-bloom.pflow} from the classpath / working directory
 * and writes 60 frames to {@code out.png}.
 */
public final class HeadlessSmoke {

    public static void main(String[] args) throws IOException {
        DwGLError.SUPPRESSED_MESSAGE_PREFIXES.add("Fluid.addDensity");

        String projectPath = System.getProperty("project", "starters/fluid-bloom.pflow");
        int frames = Integer.parseInt(System.getProperty("frames", "60"));
        String outPath = System.getProperty("out", "out.png");

        System.out.println("HeadlessSmoke: loading " + projectPath
                + " for " + frames + " frames → " + outPath);

        studio.graph.NodeFactoryRegistry registry = defaultRegistry();
        // Make user/sample compound tools available to the loader too.
        new studio.save.ToolsLibrary(Paths.get("tools"), registry).rescan();
        PflowReader reader = new PflowReader(registry);

        // Try filesystem first, then classpath.
        PflowReader.Result loaded;
        Path projFile = Paths.get(projectPath);
        if (java.nio.file.Files.exists(projFile)) {
            loaded = reader.load(projFile);
        } else {
            loaded = reader.loadFromClasspath(projectPath);
        }

        int width  = pickInt(loaded.source.output, "width", 800);
        int height = pickInt(loaded.source.output, "height", 800);

        GLProfile profile = GLProfile.get(GLProfile.GL3bc);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setOnscreen(false);
        caps.setDoubleBuffered(false);

        GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
                .createOffscreenAutoDrawable(null, caps, null, width, height);

        ResourceLoader resources = ClasspathResourceLoader.production();
        Runner runner = new Runner(loaded, frames, outPath, resources);
        drawable.addGLEventListener(runner);

        // Drive the offscreen drawable manually — no animator needed.
        drawable.display();   // init + frame 0
        for (int i = 1; i < frames + 1; i++) {
            drawable.display();
        }
        // dispose at end
        drawable.destroy();

        if (runner.failure != null) {
            System.err.println("HeadlessSmoke FAILED: " + runner.failure);
            runner.failure.printStackTrace();
            System.exit(2);
        }
        System.out.println("HeadlessSmoke ✓  wrote " + outPath);
    }

    private static int pickInt(PflowJson.OutputSettings settings, String name, int fallback) {
        if (settings == null) return fallback;
        return switch (name) {
            case "width"  -> settings.width  != null ? settings.width  : fallback;
            case "height" -> settings.height != null ? settings.height : fallback;
            case "frames" -> settings.frames != null ? settings.frames : fallback;
            default       -> fallback;
        };
    }

    public static NodeFactoryRegistry defaultRegistry() {
        return new NodeFactoryRegistry()
                // Sources
                .register(FluidNode.TYPE_ID,                 FluidNode::new)
                // Filters
                .register(BloomNode.TYPE_ID,                 BloomNode::new)
                .register(studio.nodes.filter.GaussianBlurNode.TYPE_ID,
                          studio.nodes.filter.GaussianBlurNode::new)
                .register(studio.nodes.filter.SobelNode.TYPE_ID,
                          studio.nodes.filter.SobelNode::new)
                .register(studio.nodes.filter.LuminanceNode.TYPE_ID,
                          studio.nodes.filter.LuminanceNode::new)
                .register(studio.nodes.filter.BoxBlurNode.TYPE_ID,
                          studio.nodes.filter.BoxBlurNode::new)
                .register(studio.nodes.filter.GammaNode.TYPE_ID,
                          studio.nodes.filter.GammaNode::new)
                .register(studio.nodes.filter.LuminanceThresholdNode.TYPE_ID,
                          studio.nodes.filter.LuminanceThresholdNode::new)
                .register(studio.nodes.filter.DifferenceNode.TYPE_ID,
                          studio.nodes.filter.DifferenceNode::new)
                .register(studio.nodes.filter.MultiplyNode.TYPE_ID,
                          studio.nodes.filter.MultiplyNode::new)
                .register(studio.nodes.filter.MedianNode.TYPE_ID,
                          studio.nodes.filter.MedianNode::new)
                .register(studio.nodes.filter.BinomialBlurNode.TYPE_ID,
                          studio.nodes.filter.BinomialBlurNode::new)
                // Anti-aliasing
                .register(studio.nodes.aa.FxaaNode.TYPE_ID,
                          studio.nodes.aa.FxaaNode::new)
                .register(studio.nodes.flowfield.FlowFieldNode.TYPE_ID,
                          studio.nodes.flowfield.FlowFieldNode::new)
                // Boundaries
                .register(GraphOutputNode.TYPE_ID,           GraphOutputNode::new)
                .register(studio.nodes.builtin.GraphInputNode.TYPE_ID,
                          studio.nodes.builtin.GraphInputNode::new)
                .register(studio.nodes.builtin.NoteNode.TYPE_ID,
                          studio.nodes.builtin.NoteNode::new);
    }

    /**
     * GLEventListener that creates the DwPixelFlow + GraphRuntime on init,
     * advances one frame per display() call, and on the final frame reads
     * the GraphOutputNode's RenderTarget pixels into a PNG.
     */
    private static final class Runner implements GLEventListener {

        private final PflowReader.Result loaded;
        private final int totalFrames;
        private final String outPath;
        private final ResourceLoader resources;

        private DwPixelFlow ctx;
        private GraphRuntime runtime;
        private GraphOutputNode rootOutput;
        private int framesDriven;

        Throwable failure;

        Runner(PflowReader.Result loaded, int frames, String outPath, ResourceLoader resources) {
            this.loaded = loaded;
            this.totalFrames = frames;
            this.outPath = outPath;
            this.resources = resources;
        }

        @Override public void init(GLAutoDrawable d) {
            try {
                GL2ES2 gl = d.getGL().getGL2ES2();
                ctx = new DwPixelFlow(gl, resources);
                ctx.printGL();

                // VAO bind for GL3 core (required for draw calls).
                int[] vao = new int[1];
                gl.getGL2ES3().glGenVertexArrays(1, vao, 0);
                gl.getGL2ES3().glBindVertexArray(vao[0]);

                runtime = new GraphRuntime(loaded.graph, new GraphContext(ctx));

                String rootId = loaded.source.output != null ? loaded.source.output.rootOutputNode : null;
                if (rootId == null) {
                    // pick the first GraphOutputNode in the graph
                    for (var n : loaded.graph.nodes()) {
                        if (n instanceof GraphOutputNode g) {
                            rootOutput = g;
                            break;
                        }
                    }
                } else {
                    var n = loaded.nodesById.get(rootId);
                    if (!(n instanceof GraphOutputNode g)) {
                        throw new IllegalStateException("rootOutputNode '" + rootId + "' is not a GraphOutput");
                    }
                    rootOutput = g;
                }
                if (rootOutput == null) {
                    throw new IllegalStateException("graph has no GraphOutput node");
                }
            } catch (Throwable t) { failure = t; }
        }

        @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {}

        @Override public void display(GLAutoDrawable d) {
            if (failure != null || runtime == null) return;
            try {
                runtime.renderFrame();
                framesDriven++;
                if (framesDriven == totalFrames) {
                    RenderTarget rt = rootOutput.lastFrame();
                    if (rt == null) throw new IllegalStateException("root output node has no published frame");
                    saveTextureToPng(d.getGL().getGL2ES2(), rt, outPath);
                }
            } catch (Throwable t) { failure = t; }
        }

        @Override public void dispose(GLAutoDrawable d) {
            try {
                if (runtime != null) runtime.dispose();
                if (ctx != null)     ctx.dispose();
            } catch (Throwable t) {
                if (failure == null) failure = t;
            }
        }
    }

    private static void saveTextureToPng(GL2ES2 gl, RenderTarget rt, String outPath) throws IOException {
        int w = rt.getWidth();
        int h = rt.getHeight();
        int textureId = rt.getGLTextureId();
        if (textureId == 0) throw new IOException("RenderTarget is not sampleable");

        gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        gl.getGL().getGL3().glGetTexImage(GL.GL_TEXTURE_2D, 0,
                GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buf);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        // GL textures are bottom-row-first; flip to top-row-first for image IO.
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            int srcRow = (h - 1 - y) * w;
            for (int x = 0; x < w; x++) {
                int i = (srcRow + x) * 4;
                int r = buf.get(i) & 0xFF;
                int g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
                int a = buf.get(i + 3) & 0xFF;
                argb[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        img.setRGB(0, 0, w, h, argb, 0, w);

        Path out = Paths.get(outPath);
        if (out.getParent() != null) java.nio.file.Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }
}
