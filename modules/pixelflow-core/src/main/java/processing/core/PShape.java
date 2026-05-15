package processing.core;

/**
 * Empty stub for Processing's {@code PShape}. Value-type compatibility shim,
 * not affiliated with the Processing Foundation.
 *
 * <p>The forked PixelFlow source uses {@code PShape} for retained-mode
 * geometry building (e.g. wireframe debug renderers in softbody / mesh
 * utils). Those code paths never execute in the studio — they would have
 * driven Processing's renderer, which is absent. The stub provides only
 * enough surface area for the source to compile; method bodies are no-ops.
 *
 * <p>If a runtime invocation surprises us, prefer routing the affected
 * code path through the studio's own rendering layer rather than expanding
 * this stub.
 */
public class PShape implements PConstants {

    public PShape() {}

    public PShape(int kind) { this.kind = kind; }

    public int kind;

    public float[] vertices;
    public int vertexCount;

    public PShape getParent() { return null; }

    public void beginShape() {}
    public void beginShape(int kind) { this.kind = kind; }
    public void endShape() {}
    public void endShape(int mode) {}

    public void vertex(float x, float y) {}
    public void vertex(float x, float y, float z) {}
    public void vertex(float x, float y, float u, float v) {}
    public void vertex(float x, float y, float z, float u, float v) {}
    public void normal(float nx, float ny, float nz) {}

    public void stroke(int rgb) {}
    public void stroke(float gray) {}
    public void stroke(float r, float g, float b) {}
    public void strokeWeight(float w) {}
    public void noStroke() {}
    public void fill(int rgb) {}
    public void fill(float gray) {}
    public void fill(float r, float g, float b) {}
    public void noFill() {}

    // PShape setters (different from stroke()/fill() — these set/enable on a built shape)
    public void setStroke(boolean enable) {}
    public void setStroke(int color) {}
    public void setStroke(float r, float g, float b) {}
    public void setStrokeWeight(float w) {}
    public void setFill(boolean enable) {}
    public void setFill(int color) {}
    public void setFill(float r, float g, float b) {}
    public void setVisible(boolean visible) {}
    public void setTint(boolean enable) {}
    public void setTint(int color) {}
    public void setName(String n) {}
    public void resetMatrix() {}
    public void translate(float x, float y) {}
    public void translate(float x, float y, float z) {}
    public void rotate(float a) {}
    public void rotate(float a, float vx, float vy, float vz) {}
    public void rotateX(float a) {}
    public void rotateY(float a) {}
    public void rotateZ(float a) {}
    public void scale(float s) {}
    public void scale(float sx, float sy) {}
    public void scale(float sx, float sy, float sz) {}

    public void textureMode(int mode) {}
    public void textureWrap(int wrap) {}
    public void texture(PImage img) {}
    public void setTexture(PImage img) {}
    public void applyMatrix(PMatrix m) {}
    public void applyMatrix(PMatrix2D m) {}
    public void applyMatrix(PMatrix3D m) {}

    public void addChild(PShape child) {}
    public int getChildCount() { return 0; }
    public PShape getChild(int i) { return null; }
    public void removeChild(int i) {}
    public String getName() { return null; }
    public int getVertexCount() { return vertexCount; }

    public void disableStyle() {}
    public void enableStyle() {}
}
