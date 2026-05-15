package processing.core;

/**
 * Compatibility stub for Processing's {@code PGraphics}. Used as a parameter
 * type in upstream PixelFlow helpers; nothing in the studio runtime path
 * constructs one. Method calls throw at runtime.
 */
public class PGraphics extends PImage implements PConstants {

    public PApplet parent;

    public PGraphics() {
        super();
    }

    public boolean isGL() { return false; }

    public void beginDraw() {}
    public void endDraw() {}
    public Object beginPGL() { throw new UnsupportedOperationException(); }
    public void endPGL() {}

    public void background(int rgb) {}
    public void background(float gray) {}
    public void background(float r, float g, float b) {}
    public void background(float r, float g, float b, float a) {}

    public void clear() {}

    public void fill(int rgb) {}
    public void fill(float r, float g, float b) {}
    public void fill(float r, float g, float b, float a) {}
    public void noFill() {}
    public void stroke(int rgb) {}
    public void stroke(float r, float g, float b) {}
    public void strokeWeight(float w) {}
    public void noStroke() {}

    public void rect(float x, float y, float w, float h) {}
    public void ellipse(float x, float y, float w, float h) {}
    public void line(float x1, float y1, float x2, float y2) {}
    public void point(float x, float y) {}

    public void pushMatrix() {}
    public void popMatrix() {}
    public void translate(float x, float y) {}
    public void translate(float x, float y, float z) {}
    public void rotate(float a) {}
    public void scale(float s) {}
    public void scale(float sx, float sy) {}

    public void image(PImage img, float x, float y) {}
    public void image(PImage img, float x, float y, float w, float h) {}

    public void hint(int h) {}

    public int smooth;
    public void smooth(int level) { smooth = level; }
    public void noSmooth() { smooth = 0; }

    public void blendMode(int mode) {}
}
