package studio.engine;

import com.jogamp.newt.event.KeyAdapter;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.Animator;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.fluid.DwFluid2D;

/**
 * M1 verification — runs DwFluid2D inside a NEWT GLWindow with zero
 * Processing on the classpath. ESC exits cleanly.
 *
 * <p>Pass criteria:
 * <ol>
 *   <li>Process starts and asserts {@code processing.core.PApplet} is NOT on
 *       the classpath. (Studio fork's stubs are at the same package name but
 *       are shipped inside pixelflow-core; we assert the real Processing
 *       distribution is absent.)</li>
 *   <li>{@code DwPixelFlow.printGL()} logs renderer + GLSL version — proves
 *       the GL3 context is live without going through PJOGL.</li>
 *   <li>Window shows an orange density blob being pushed upward by an
 *       injected velocity field.</li>
 *   <li>ESC closes cleanly with no GL errors.</li>
 * </ol>
 */
public final class Smoke {

    public static void main(String[] args) {
        // (1) Confirm the real Processing distribution is absent. Our shim
        // classes live inside the pixelflow-core jar at processing.core.* —
        // we detect those by inspecting the loader. The real Processing
        // would ship with classes we deliberately did not stub (e.g.
        // processing.app.Sketch).
        try {
            Class.forName("processing.app.Sketch");
            throw new IllegalStateException("Real Processing distribution leaked onto classpath");
        } catch (ClassNotFoundException expected) { /* good */ }

        final int W = 800;
        final int H = 800;

        // GL3bc gives us the GL3 forward-compatible feature set with backward
        // compatibility for legacy fixed-function state that PixelFlow's older
        // shaders sometimes assume.
        GLProfile profile = GLProfile.get(GLProfile.GL3bc);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setDoubleBuffered(true);
        caps.setSampleBuffers(false);

        GLWindow window = GLWindow.create(caps);
        window.setSize(W, H);
        window.setTitle("PixelFlow Studio — Smoke (fluid, no Processing)");

        ResourceLoader resources = ClasspathResourceLoader.production();

        SmokeScene scene = new SmokeScene(W, H);

        window.addGLEventListener(new GLEventListener() {
            DwPixelFlow ctx;

            @Override public void init(GLAutoDrawable d) {
                GL2ES2 gl = d.getGL().getGL2ES2();
                ctx = new DwPixelFlow(gl, resources);
                ctx.printGL();
                // GL3 core profile requires a VAO bound before any draw call.
                // PJOGL bound one implicitly; we have to do it ourselves.
                int[] vao = new int[1];
                gl.getGL2ES3().glGenVertexArrays(1, vao, 0);
                gl.getGL2ES3().glBindVertexArray(vao[0]);
                scene.init(ctx);
            }

            @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
                scene.resize(ctx, w, h);
            }

            @Override public void display(GLAutoDrawable d) {
                scene.frame(ctx, d);
            }

            @Override public void dispose(GLAutoDrawable d) {
                scene.dispose();
                if (ctx != null) ctx.dispose();
            }
        });

        window.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    new Thread(window::destroy).start();
                }
            }
        });

        final Animator animator = new Animator(window);
        animator.setUpdateFPSFrames(60, null);
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowDestroyed(WindowEvent e) { animator.stop(); }
        });

        window.setVisible(true);
        animator.start();
    }

    /**
     * Self-contained scene: a fluid sim with one orange density+velocity
     * injection per frame at a fixed location.
     */
    static final class SmokeScene {
        final int W, H;
        DwFluid2D fluid;
        GLTextureTarget target;
        long frame;

        SmokeScene(int w, int h) { this.W = w; this.H = h; }

        void init(DwPixelFlow ctx) {
            fluid = new DwFluid2D(ctx, W, H, 1);
            fluid.param.dissipation_velocity    = 0.85f;
            fluid.param.dissipation_density     = 0.99f;
            fluid.param.dissipation_temperature = 0.65f;
            fluid.param.vorticity               = 0.15f;
            fluid.param.num_jacobi_projection   = 30;

            target = GLTextureTarget.create(ctx, W, H);
        }

        void resize(DwPixelFlow ctx, int w, int h) {
            if (target == null) return;
            target.resize(ctx, w, h);
        }

        void frame(DwPixelFlow ctx, GLAutoDrawable d) {
            float cx = W * 0.5f;
            float cy = H * 0.25f;
            fluid.addDensity (cx, cy, 30, 1.0f, 0.45f, 0.05f, 1.0f);
            fluid.addVelocity(cx, cy, 30, 0f, +80f);

            fluid.update();
            fluid.renderFluidTextures(target, 0);

            // Blit the offscreen target onto the window default framebuffer.
            GL2ES2 gl = ctx.gl;
            ctx.framebuffer.bind(target.texture);                // bind FBO with target as color-0
            gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, ctx.framebuffer.HANDLE_fbo[0]);
            gl.glBindFramebuffer(GL.GL_DRAW_FRAMEBUFFER, 0);
            GL2ES3 gles3 = gl.getGL2ES3();
            int dw = d.getSurfaceWidth(), dh = d.getSurfaceHeight();
            gles3.glBlitFramebuffer(0, 0, W, H, 0, 0, dw, dh,
                    GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
            gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, 0);

            ctx.errorCheck("Smoke.frame");
            frame++;
        }

        void dispose() {
            if (fluid != null) fluid.release();
            if (target != null) target.release();
        }
    }
}
