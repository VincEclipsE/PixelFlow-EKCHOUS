package processing.opengl;

/** Compatibility stub. */
public class FrameBuffer {
    public int glFbo;
    public int width;
    public int height;

    public void bind() { throw new UnsupportedOperationException("FrameBuffer stub"); }
    public void unbind() {}
    public void copyColor(FrameBuffer other) {}
}
