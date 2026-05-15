package processing.opengl;

import processing.core.PGraphics;
import processing.core.PImage;

/**
 * Compatibility stub for Processing's {@code PGraphicsOpenGL}. The studio
 * has replaced upstream PixelFlow's signatures that took PGraphicsOpenGL
 * with {@code studio.engine.RenderTarget}; this stub exists so a small
 * number of remaining sites compile. Method calls throw at runtime.
 */
public class PGraphicsOpenGL extends PGraphics {

    public PGraphicsOpenGL() { super(); }

    public FrameBuffer getFrameBuffer() { throw u(); }
    public FrameBuffer getFrameBuffer(boolean multisample) { throw u(); }
    public Texture getTexture() { throw u(); }
    public Texture getTexture(PImage img) { throw u(); }
    public void loadTexture() { throw u(); }

    private static UnsupportedOperationException u() {
        return new UnsupportedOperationException(
            "PGraphicsOpenGL stub method called — caller needs RenderTarget rewrite.");
    }
}
