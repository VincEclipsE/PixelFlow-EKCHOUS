package studio.engine;

import com.jogamp.opengl.GL2ES2;

/**
 * Drop-in replacement for Processing's {@code PGraphicsOpenGL} as a renderable
 * destination for PixelFlow primitives. An implementation owns a color
 * attachment (a 2D GL texture) and the framebuffer object that wraps it.
 *
 * <p>Concrete implementations:
 * <ul>
 *   <li>{@link GLTextureTarget} — wraps a {@code DwGLTexture} + dedicated FBO.
 *       The canonical offscreen-pgraphics replacement.</li>
 *   <li>{@link WindowTarget} — represents the GLWindow default framebuffer
 *       (FBO 0). Cannot be sampled from; primitives that read the
 *       destination texture will throw.</li>
 * </ul>
 */
public interface RenderTarget {

    /**
     * Bind this target's framebuffer and set the viewport to its full extent.
     * After this call, subsequent draw calls land in this target.
     */
    void bindAsRenderTarget(GL2ES2 gl);

    /**
     * Restore {@code GL_DRAW_FRAMEBUFFER} to {@code 0}. Engine usually invokes
     * this via {@code DwPixelFlow.endDraw()}.
     */
    void unbind(GL2ES2 gl);

    /**
     * GL texture name of the color attachment, used as a sampler input by
     * primitives that read their destination (e.g. {@code DwFluid2D.addDensity}).
     *
     * @return GL texture id, or {@code 0} for the default framebuffer
     */
    int getGLTextureId();

    /** Width of the target in pixels. */
    int getWidth();

    /** Height of the target in pixels. */
    int getHeight();

    /**
     * @return {@code true} if this target's color attachment can be read as a
     *         sampler input. {@code false} for window default framebuffer.
     */
    default boolean isSampleable() {
        return getGLTextureId() != 0;
    }
}
