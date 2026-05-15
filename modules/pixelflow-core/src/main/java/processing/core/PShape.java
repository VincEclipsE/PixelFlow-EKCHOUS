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

    public void addChild(PShape child) {}

    public int getVertexCount() { return vertexCount; }

    public void disableStyle() {}
    public void enableStyle() {}
}
