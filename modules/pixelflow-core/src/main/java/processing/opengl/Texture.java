package processing.opengl;

/**
 * Compatibility stub for Processing's internal {@code Texture}. Real usage
 * inside the forked PixelFlow code has been rewritten to use
 * {@code studio.engine.RenderTarget.getGLTextureId()}; this stub exists so
 * any leftover references compile. Field access returns 0; {@link #available()}
 * returns false.
 */
public class Texture {

    public int glName = 0;
    public int glTarget = 0;
    public int glWidth = 0;
    public int glHeight = 0;
    public int width = 0;
    public int height = 0;

    public boolean available() { return false; }

    public void bind() {}
    public void unbind() {}
}
