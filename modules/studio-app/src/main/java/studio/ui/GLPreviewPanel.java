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
    private final AtomicReference<CaptureRequest> pendingCapture = new AtomicReference<>();

    /** Queue a one-shot PNG export of the next post-render frame. */
    public void captureNextFrameAsPng(java.nio.file.Path out, java.util.function.Consumer<Throwable> onDone) {
        pendingCapture.set(new CaptureRequest(out, onDone));
        canvas.repaint();
    }

    private record CaptureRequest(java.nio.file.Path out, java.util.function.Consumer<Throwable> onDone) {}

    // GL-thread state (read/written only inside GLEventListener callbacks):
    private DwPixelFlow ctx;
    private GraphRuntime runtime;
    private GraphOutputNode rootOutput;
    private PflowReader.Result lastLoaded;

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

        // Wire mouse → runtime.mouseState. We map preview-screen pixels back
        // to source-canvas pixels (un-letterbox) so downstream nodes that
        // expect coords in an 800×800 fluid space see them that way.
        java.awt.event.MouseAdapter mouseAdapter = new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                updateMouse(e.getX(), e.getY(), true);
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) setPrimaryDown(true);
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) setRightDown(true);
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                updateMouse(e.getX(), e.getY(), true);
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) setPrimaryDown(false);
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) setRightDown(false);
            }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) { updateMouse(e.getX(), e.getY(), true); }
            @Override public void mouseMoved(java.awt.event.MouseEvent e)   { updateMouse(e.getX(), e.getY(), true); }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { setInside(true); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { setInside(false); }
        };
        canvas.addMouseListener(mouseAdapter);
        canvas.addMouseMotionListener(mouseAdapter);

        animator = new FPSAnimator(canvas, 60, true);
        animator.start();
    }

    /* ----------------------- mouse → runtime bridge ----------------------- */
    private volatile int sourceW = 800, sourceH = 800;

    // Published from the GL thread inside swapProject() so the EDT mouse
    // listeners have a happens-before view of the live MouseState. We can't
    // reach through `runtime` here because that field isn't volatile and
    // would leave the EDT seeing a stale null indefinitely.
    private volatile studio.graph.MouseState mouseRef;

    private void updateMouse(int sx, int sy, boolean inside) {
        studio.graph.MouseState m = mouseRef;
        if (m == null) return;
        // Recompute letterbox in Swing-component coords (not GL surface coords,
        // which differ on HiDPI). Same aspect-fit math as the blit.
        int dw = canvas.getWidth();
        int dh = canvas.getHeight();
        if (dw <= 0 || dh <= 0 || sourceW <= 0 || sourceH <= 0) return;
        double srcAr = (double) sourceW / sourceH;
        double dstAr = (double) dw / dh;
        int outW, outH;
        if (dstAr > srcAr) { outH = dh; outW = (int) Math.round(dh * srcAr); }
        else               { outW = dw; outH = (int) Math.round(dw / srcAr); }
        int outX = (dw - outW) / 2;
        int outY = (dh - outH) / 2;
        float fx = (sx - outX) * (sourceW / (float) Math.max(1, outW));
        float fy = (sy - outY) * (sourceH / (float) Math.max(1, outH));
        // Drop "inside" when the click landed in a letterbox bar so spawn /
        // inject gates downstream see the click as off-canvas. Then clamp
        // coords so the value we publish is at least sensible.
        boolean withinRender = (sx >= outX && sx < outX + outW && sy >= outY && sy < outY + outH);
        fx = Math.max(0f, Math.min(sourceW, fx));
        fy = Math.max(0f, Math.min(sourceH, fy));
        // Swing reports y growing downward; the fluid/particles render via a
        // textured quad whose UV (0,0) is at the bottom of the viewport, so a
        // raw Swing y would inject at the visually mirrored row. Flip here so
        // downstream nodes see y in the same orientation the user clicked.
        m.x = fx;
        m.y = sourceH - fy;
        m.inside = inside && withinRender;
        m.width  = sourceW;
        m.height = sourceH;
    }

    private void setPrimaryDown(boolean v) {
        studio.graph.MouseState m = mouseRef;
        if (m == null) return;
        m.down = v;
    }
    private void setRightDown(boolean v) {
        studio.graph.MouseState m = mouseRef;
        if (m == null) return;
        m.rightDown = v;
    }
    private void setInside(boolean v) {
        studio.graph.MouseState m = mouseRef;
        if (m == null) return;
        m.inside = v;
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

    private volatile boolean paused;
    private final java.util.concurrent.atomic.AtomicInteger pendingSteps = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean resetPending = new java.util.concurrent.atomic.AtomicBoolean();

    public boolean isPaused() { return paused; }
    public void setPaused(boolean p) { this.paused = p; }
    /** Advance the runtime by one frame on the next display(), regardless of paused state. */
    public void stepOnce() { pendingSteps.incrementAndGet(); canvas.repaint(); }
    /** Recreate the runtime so the simulation restarts from frame 0 on the next display(). */
    public void resetRuntime() { resetPending.set(true); canvas.repaint(); }

    // Thumbnail subscription: the canvas asks for the latest output of the
    // currently-selected node ~1Hz; the parameter panel renders it.
    private volatile studio.graph.Node thumbnailNode;
    private volatile java.util.function.Consumer<java.awt.image.BufferedImage> thumbnailSink;
    private long lastThumbnailNanos;

    public void setThumbnailTarget(studio.graph.Node node,
                                   java.util.function.Consumer<java.awt.image.BufferedImage> sink) {
        this.thumbnailNode = node;
        this.thumbnailSink = sink;
        // Clear stale frame from the panel when the target changes.
        if (sink != null) javax.swing.SwingUtilities.invokeLater(() -> sink.accept(null));
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
            if (resetPending.compareAndSet(true, false) && lastLoaded != null) {
                swapProject(lastLoaded);
            }
            if (runtime == null) {
                clearToBackdrop(d.getGL().getGL2ES2());
                return;
            }
            int steps = pendingSteps.getAndSet(0);
            if (!paused || steps > 0) {
                int count = paused ? steps : (1 + Math.max(0, steps));
                for (int i = 0; i < count; i++) runtime.renderFrame();
            }

            // Throttled thumbnail of the currently-selected node (~1 Hz).
            captureSelectedNodeThumbnail(d.getGL().getGL2ES2());

            if (rootOutput != null) {
                RenderTarget rt = rootOutput.lastFrame();
                if (rt != null && rt.isSampleable()) {
                    blitToScreen(d, rt);
                    CaptureRequest req = pendingCapture.getAndSet(null);
                    if (req != null) {
                        Throwable err = null;
                        try {
                            studio.engine.TextureCapture.writePng(d.getGL().getGL2ES2(), rt, req.out);
                        } catch (Throwable t) {
                            err = t;
                        }
                        if (req.onDone != null) {
                            final Throwable e = err;
                            javax.swing.SwingUtilities.invokeLater(() -> req.onDone.accept(e));
                        }
                    }
                } else {
                    clearToBackdrop(d.getGL().getGL2ES2());
                }
            }
            tickFps();
        }

        private void captureSelectedNodeThumbnail(GL2ES2 gl) {
            studio.graph.Node target = thumbnailNode;
            java.util.function.Consumer<java.awt.image.BufferedImage> sink = thumbnailSink;
            if (target == null || sink == null || runtime == null) return;
            long now = System.nanoTime();
            if (now - lastThumbnailNanos < 1_000_000_000L) return;
            // Find the first tex2d output port and snapshot its latest published value.
            for (studio.graph.OutputPort<?> p : target.outputs()) {
                if (p.type != studio.graph.PortTypes.TEXTURE2D) continue;
                Object v = runtime.latestPublished(p);
                if (!(v instanceof RenderTarget rt) || !rt.isSampleable()) break;
                java.awt.image.BufferedImage full = studio.engine.TextureCapture.readToImage(gl, rt);
                if (full == null) break;
                // Downscale to a thumbnail-friendly size on the EDT side later.
                int targetW = 160, targetH;
                targetH = Math.max(1, (int) Math.round(targetW * (full.getHeight() / (double) full.getWidth())));
                java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(
                        targetW, targetH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = small.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(full, 0, 0, targetW, targetH, null);
                g2.dispose();
                javax.swing.SwingUtilities.invokeLater(() -> sink.accept(small));
                lastThumbnailNanos = now;
                break;
            }
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
            mouseRef = null;
            if (runtime != null) { runtime.dispose(); runtime = null; }
            if (ctx != null) { ctx.dispose(); ctx = null; }
        }

        private void swapProject(PflowReader.Result loaded) {
            if (runtime != null) runtime.dispose();
            runtime = new GraphRuntime(loaded.graph, new GraphContext(ctx));
            mouseRef = runtime.context().mouse();
            rootOutput = pickRootOutput(loaded);
            lastLoaded = loaded;
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
            // Slightly darker than the surrounding Swing chrome so the active
            // canvas (cleared in blitToScreen) reads as a distinct surface.
            gl.glClearColor(0.04f, 0.04f, 0.06f, 1.0f);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        }

        private void blitToScreen(GLAutoDrawable d, RenderTarget rt) {
            GL2ES2 gl = ctx.gl;
            GL2ES3 gles3 = gl.getGL2ES3();
            ensureBlitProgram(gl);

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

            // Record source dims so mouse → source mapping stays correct.
            sourceW = sw;
            sourceH = sh;

            // Bind GLJPanel's draw FBO. JOGL hooks framebuffer=0 to redirect
            // to the offscreen surface Swing reads back. (glBlitFramebuffer is
            // unreliable against this hook on AMD/Windows, so we draw a
            // textured fullscreen quad instead — that path works fine.)
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
            gl.glDisable(GL.GL_SCISSOR_TEST);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glDisable(GL.GL_CULL_FACE);
            gl.glDisable(GL.GL_BLEND);
            gl.glColorMask(true, true, true, true);

            // Backdrop clear (darker than surrounding chrome so the canvas
            // edge reads as distinct).
            gl.glClearColor(0.04f, 0.04f, 0.06f, 1f);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);

            // Draw the source texture into the letterboxed active region.
            gl.glViewport(outX, outY, outW, outH);
            gl.glUseProgram(blitProgram);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, rt.getGLTextureId());
            gl.glUniform1i(blitTexLoc, 0);
            gles3.glBindVertexArray(blitVao);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gles3.glBindVertexArray(0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glUseProgram(0);
            // Restore full-surface viewport for the outline pass.
            gl.glViewport(0, 0, dw, dh);

            // Outline the active region in a noticeable colour so the user
            // can see exactly where click-and-drag is meaningful.
            gl.glEnable(GL.GL_SCISSOR_TEST);
            gl.glClearColor(0.35f, 0.65f, 0.85f, 1f);
            int bw = 2;
            // top
            gl.glScissor(outX - bw, outY + outH, outW + 2 * bw, bw);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            // bottom
            gl.glScissor(outX - bw, outY - bw, outW + 2 * bw, bw);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            // left
            gl.glScissor(outX - bw, outY, bw, outH);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            // right
            gl.glScissor(outX + outW, outY, bw, outH);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            gl.glDisable(GL.GL_SCISSOR_TEST);
        }

        private void ensureBlitProgram(GL2ES2 gl) {
            if (blitProgram != 0) return;
            String vs = String.join("\n",
                    "#version 330 core",
                    "out vec2 vUv;",
                    "void main() {",
                    "  vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);",
                    "  vUv = p;",
                    "  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);",
                    "}");
            String fs = String.join("\n",
                    "#version 330 core",
                    "in vec2 vUv;",
                    "out vec4 fragColor;",
                    "uniform sampler2D tex;",
                    "void main() {",
                    "  fragColor = texture(tex, vUv);",
                    "}");
            int v = compileShader(gl, GL2ES2.GL_VERTEX_SHADER,   vs);
            int f = compileShader(gl, GL2ES2.GL_FRAGMENT_SHADER, fs);
            blitProgram = gl.glCreateProgram();
            gl.glAttachShader(blitProgram, v);
            gl.glAttachShader(blitProgram, f);
            gl.glLinkProgram(blitProgram);
            int[] linkStatus = new int[1];
            gl.glGetProgramiv(blitProgram, GL2ES2.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                int[] logLen = new int[1];
                gl.glGetProgramiv(blitProgram, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
                byte[] log = new byte[Math.max(1, logLen[0])];
                gl.glGetProgramInfoLog(blitProgram, log.length, null, 0, log, 0);
                throw new RuntimeException("blit program link failed: " + new String(log));
            }
            gl.glDeleteShader(v);
            gl.glDeleteShader(f);
            blitTexLoc = gl.glGetUniformLocation(blitProgram, "tex");
            int[] vao = new int[1];
            gl.getGL2ES3().glGenVertexArrays(1, vao, 0);
            blitVao = vao[0];
        }

        private int compileShader(GL2ES2 gl, int type, String source) {
            int s = gl.glCreateShader(type);
            gl.glShaderSource(s, 1, new String[]{ source }, new int[]{ source.length() }, 0);
            gl.glCompileShader(s);
            int[] status = new int[1];
            gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                int[] logLen = new int[1];
                gl.glGetShaderiv(s, GL2ES2.GL_INFO_LOG_LENGTH, logLen, 0);
                byte[] log = new byte[Math.max(1, logLen[0])];
                gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
                throw new RuntimeException("blit shader compile failed (type=0x"
                        + Integer.toHexString(type) + "): " + new String(log));
            }
            return s;
        }

        private int blitProgram;
        private int blitVao;
        private int blitTexLoc;
    }
}
