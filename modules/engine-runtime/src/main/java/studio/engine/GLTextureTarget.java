package studio.engine;

import com.jogamp.opengl.GL2ES2;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTexture;

/**
 * RenderTarget backed by a {@link DwGLTexture} + the shared DwGLFrameBuffer
 * inside {@link DwPixelFlow}. The canonical offscreen-target implementation
 * for the studio.
 */
public final class GLTextureTarget implements RenderTarget {

    public final DwGLTexture texture;
    private final DwPixelFlow context;

    public GLTextureTarget(DwPixelFlow context, DwGLTexture texture) {
        this.context = context;
        this.texture = texture;
    }

    /**
     * Build an RGBA8 target sized w×h.
     */
    public static GLTextureTarget create(DwPixelFlow context, int w, int h) {
        DwGLTexture tex = new DwGLTexture();
        // RGBA8, linear filter, clamp-to-edge — sensible defaults for a 2D fluid canvas.
        tex.resize(context, com.jogamp.opengl.GL2.GL_RGBA8, w, h,
                com.jogamp.opengl.GL2.GL_RGBA, com.jogamp.opengl.GL2.GL_UNSIGNED_BYTE,
                com.jogamp.opengl.GL2.GL_LINEAR, com.jogamp.opengl.GL2.GL_CLAMP_TO_EDGE, 4, 1);
        return new GLTextureTarget(context, tex);
    }

    /** Reallocate the backing texture if the dimensions have changed. */
    public void resize(DwPixelFlow context, int w, int h) {
        texture.resize(context, com.jogamp.opengl.GL2.GL_RGBA8, w, h,
                com.jogamp.opengl.GL2.GL_RGBA, com.jogamp.opengl.GL2.GL_UNSIGNED_BYTE,
                com.jogamp.opengl.GL2.GL_LINEAR, com.jogamp.opengl.GL2.GL_CLAMP_TO_EDGE, 4, 1);
    }

    @Override public void bindAsRenderTarget(GL2ES2 gl) {
        context.framebuffer.bind(texture);
    }

    @Override public void unbind(GL2ES2 gl) {
        if (context.framebuffer.isActive()) context.framebuffer.unbind();
    }

    @Override public int getGLTextureId() {
        return texture.HANDLE[0];
    }

    @Override public int getWidth()  { return texture.w; }
    @Override public int getHeight() { return texture.h; }
    @Override public boolean isSampleable() { return texture.HANDLE[0] != 0; }

    public void release() {
        texture.release();
    }
}
