package studio.ui;

import java.awt.BorderLayout;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;

import studio.engine.ClasspathResourceLoader;
import studio.engine.RenderTarget;
import studio.graph.GraphContext;
import studio.graph.GraphRuntime;
import studio.nodes.builtin.GraphOutputNode;
import studio.save.PflowReader;

/**
 * Swing-embedded JOGL canvas that drives a {@link GraphRuntime} at 60 fps
 * and blits whichever node's output is published on the root
 * {@link GraphOutputNode} to the panel surface.
 *
 * <p>Graph swaps (when the user opens a new {@code .pflow}) happen on the
 * GL thread inside {@code display()} — we receive the new project via
 * {@link #attachRuntime}, stash it in a CAS reference, and the next
 * display() tears down the old runtime + builds the new one.
 */
public final class GLPreviewPanel extends JPanel {

    private final GLJPanel canvas;
    private final FPSAnimator animator;

    private final AtomicReference<PflowReader.Result> pendingProject = new AtomicReference<>();

    // GL-thread state (read/written only inside GLEventListener callbacks):
    private DwPixelFlow ctx;
    private GraphRuntime runtime;
    private GraphOutputNode rootOutput;

    @SuppressWarnings("unused")
    private final StudioModel model;

    private StatusBar statusBar;
    private long lastFpsNanos;
    private int framesSinceFps;
    private int nodeCount, edgeCount;

    public void setStatusBar(StatusBar bar) { this.statusBar = bar; }

    public void setGraphStats(int nodes, int edges) {
        this.nodeCount = nodes;
        this.edgeCount = edges;
        if (statusBar != null) statusBar.stats(formatStats(0));
    }

    private String formatStats(int fps) {
        StringBuilder s = new StringBuilder();
        s.append(nodeCount).append(" nodes · ").append(edgeCount).append(" edges");
        if (fps > 0) s.append(" · ").append(fps).append(" fps");
        return s.toString();
    }

    public GLPreviewPanel(StudioModel model) {
        super(new BorderLayout());
        this.model = model;

        GLProfile profile = GLProfile.get(GLProfile.GL3bc);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setDoubleBuffered(true);
        canvas = new GLJPanel(caps);
        canvas.addGLEventListener(new Listener());
        add(canvas, BorderLayout.CENTER);

        animator = new FPSAnimator(canvas, 60, true);
        animator.start();
    }

    /** Queue a new project to be activated on the next GL frame. */
    public void attachRuntime(PflowReader.Result loaded) {
        pendingProject.set(loaded);
        canvas.repaint();
    }

    public void shutdown() {
        animator.stop();
        canvas.destroy();
    }

    /* ------------------------------ GLEventListener ------------------------------ */

    private final class Listener implements GLEventListener {

        @Override public void init(GLAutoDrawable d) {
            GL2ES2 gl = d.getGL().getGL2ES2();
            ctx = new DwPixelFlow(gl, ClasspathResourceLoader.production());
            ctx.printGL();

            int[] vao = new int[1];
            gl.getGL2ES3().glGenVertexArrays(1, vao, 0);
            gl.getGL2ES3().glBindVertexArray(vao[0]);
        }

        @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
            // viewport managed per-node; nothing to do here for now
        }

        @Override public void display(GLAutoDrawable d) {
            PflowReader.Result pending = pendingProject.getAndSet(null);
            if (pending != null) swapProject(pending);
            if (runtime == null) {
                clearToBackdrop(d.getGL().getGL2ES2());
                return;
            }
            runtime.renderFrame();

            if (rootOutput != null) {
                RenderTarget rt = rootOutput.lastFrame();
                if (rt != null && rt.isSampleable()) {
                    blitToScreen(d, rt);
                } else {
                    clearToBackdrop(d.getGL().getGL2ES2());
                }
            }
            tickFps();
        }

        private void tickFps() {
            framesSinceFps++;
            long now = System.nanoTime();
            if (lastFpsNanos == 0) { lastFpsNanos = now; return; }
            long elapsed = now - lastFpsNanos;
            if (elapsed >= 500_000_000L) {
                int fps = (int) Math.round(framesSinceFps * 1e9 / elapsed);
                framesSinceFps = 0;
                lastFpsNanos = now;
                if (statusBar != null) statusBar.stats(formatStats(fps));
            }
        }

        @Override public void dispose(GLAutoDrawable d) {
            if (runtime != null) { runtime.dispose(); runtime = null; }
            if (ctx != null) { ctx.dispose(); ctx = null; }
        }

        private void swapProject(PflowReader.Result loaded) {
            if (runtime != null) runtime.dispose();
            runtime = new GraphRuntime(loaded.graph, new GraphContext(ctx));
            rootOutput = pickRootOutput(loaded);
        }

        private GraphOutputNode pickRootOutput(PflowReader.Result loaded) {
            String rootId = loaded.source != null && loaded.source.output != null
                    ? loaded.source.output.rootOutputNode : null;
            if (rootId != null) {
                var n = loaded.nodesById.get(rootId);
                if (n instanceof GraphOutputNode g) return g;
            }
            for (var n : loaded.graph.nodes()) {
                if (n instanceof GraphOutputNode g) return g;
            }
            return null;
        }

        private void clearToBackdrop(GL2ES2 gl) {
            gl.glClearColor(0.10f, 0.10f, 0.12f, 1.0f);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        }

        private void blitToScreen(GLAutoDrawable d, RenderTarget rt) {
            GL2ES2 gl = ctx.gl;
            // Bind via the int-handle overload so we don't depend on a DwGLTexture instance.
            ctx.framebuffer.bind(rt.getGLTextureId());
            gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, ctx.framebuffer.HANDLE_fbo[0]);
            gl.glBindFramebuffer(GL.GL_DRAW_FRAMEBUFFER, 0);
            GL2ES3 gles3 = gl.getGL2ES3();
            gles3.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);

            int dw = d.getSurfaceWidth(), dh = d.getSurfaceHeight();
            int sw = rt.getWidth(), sh = rt.getHeight();
            // Fit source into destination preserving aspect ratio.
            double srcAr = (double) sw / sh;
            double dstAr = (double) dw / dh;
            int outW, outH;
            if (dstAr > srcAr) {
                outH = dh;
                outW = (int) Math.round(dh * srcAr);
            } else {
                outW = dw;
                outH = (int) Math.round(dw / srcAr);
            }
            int outX = (dw - outW) / 2;
            int outY = (dh - outH) / 2;

            // Backdrop wipe so the letterbox bars look right.
            gl.glClearColor(0.10f, 0.10f, 0.12f, 1f);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);

            gles3.glBlitFramebuffer(0, 0, sw, sh,
                    outX, outY, outX + outW, outY + outH,
                    GL.GL_COLOR_BUFFER_BIT, GL.GL_LINEAR);
            gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, 0);
        }
    }
}
