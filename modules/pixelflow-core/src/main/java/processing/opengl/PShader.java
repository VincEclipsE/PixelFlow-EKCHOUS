package processing.opengl;

import processing.core.PApplet;

/**
 * Compatibility stub for Processing's {@code PShader}. Only the forked
 * {@code DwGeometryShader} extends this. After M1's geometry-shader rewrite
 * that subclass should be migrated to extend {@code DwGLSLProgram} directly;
 * until then this stub allows compilation.
 */
public class PShader {

    public int glProgram;
    public PGL pgl;

    public PShader() {}
    public PShader(PApplet papplet, String vert, String frag) {}
    public PShader(PApplet papplet, String[] vert, String[] frag) {}

    protected void setup() {}
}
