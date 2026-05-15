package processing.opengl;

import com.jogamp.opengl.GL2ES2;

import processing.core.PGraphics;
import processing.core.PImage;
import studio.engine.RenderTarget;

/**
 * Compatibility stub for Processing's {@code PGraphicsOpenGL}. Implements
 * {@link RenderTarget} so leftover PixelFlow code that uses PGraphics2D /
 * PGraphics3D as offscreen render targets can be passed to studio APIs
 * during the transitional period. All methods throw at runtime — any
 * actual call here indicates the upstream code path still needs a real
 * rewrite to use {@code DwGLTexture} or a real {@code RenderTarget} impl.
 */
public class PGraphicsOpenGL extends PGraphics implements RenderTarget {

    public PGraphicsOpenGL() { super(); }

    public PGL pgl = new PGL();

    public FrameBuffer getFrameBuffer() { throw u(); }
    public FrameBuffer getFrameBuffer(boolean multisample) { throw u(); }
    public Texture getTexture() { throw u(); }
    public Texture getTexture(PImage img) { throw u(); }
    public void loadTexture() { throw u(); }
    public void shader(PShader s) {}
    public void resetShader() {}
    public void pushProjection() {}
    public void popProjection() {}
    public boolean lights = false;

    // Camera/projection matrix fields exposed by Processing's PGraphicsOpenGL —
    // stubbed here so leftover code that reads pg.projmodelview etc. compiles.
    public processing.core.PMatrix3D projection    = new processing.core.PMatrix3D();
    public processing.core.PMatrix3D camera        = new processing.core.PMatrix3D();
    public processing.core.PMatrix3D cameraInv     = new processing.core.PMatrix3D();
    public processing.core.PMatrix3D modelview     = new processing.core.PMatrix3D();
    public processing.core.PMatrix3D modelviewInv  = new processing.core.PMatrix3D();
    public processing.core.PMatrix3D projmodelview = new processing.core.PMatrix3D();

    // RenderTarget bridge — throw at runtime.
    @Override public void bindAsRenderTarget(GL2ES2 gl) { throw u(); }
    @Override public void unbind(GL2ES2 gl) { throw u(); }
    @Override public int getGLTextureId() { return 0; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public boolean isSampleable() { return false; }

    private static UnsupportedOperationException u() {
        return new UnsupportedOperationException(
            "PGraphicsOpenGL stub method called — caller needs RenderTarget rewrite.");
    }
}
