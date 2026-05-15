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

    // Retained-mode geometry — all no-ops in the studio's shim (no Processing renderer behind it)
    public void beginShape() {}
    public void beginShape(int kind) {}
    public void endShape() {}
    public void endShape(int mode) {}
    public void vertex(float x, float y) {}
    public void vertex(float x, float y, float z) {}
    public void vertex(float x, float y, float u, float v) {}
    public void vertex(float x, float y, float z, float u, float v) {}
    public void normal(float nx, float ny, float nz) {}
    public void texture(PImage img) {}
    public void textureMode(int mode) {}
    public void textureWrap(int wrap) {}
    public void textureSampling(int mode) {}
    public void shape(PShape s) {}
    public void shape(PShape s, float x, float y) {}
    public void resetShader() {}

    // Lighting / camera / matrix stack — no-ops; never reach a real renderer
    public void ambientLight(float r, float g, float b) {}
    public void directionalLight(float r, float g, float b, float nx, float ny, float nz) {}
    public void pointLight(float r, float g, float b, float x, float y, float z) {}
    public void spotLight(float r, float g, float b, float x, float y, float z, float nx, float ny, float nz, float angle, float concentration) {}
    public void lights() {}
    public void noLights() {}
    public void camera(float eyeX, float eyeY, float eyeZ, float centerX, float centerY, float centerZ, float upX, float upY, float upZ) {}
    public void ortho(float left, float right, float bottom, float top, float near, float far) {}
    public void perspective(float fovy, float aspect, float near, float far) {}

    public void pushStyle() {}
    public void popStyle() {}
    public void resetMatrix() {}

    public boolean is3D() { return false; }

    // Pixel access — minimal
    public void loadPixels() {}
    public void updatePixels() {}
}
