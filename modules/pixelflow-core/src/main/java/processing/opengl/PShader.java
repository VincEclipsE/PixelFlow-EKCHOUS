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

    public void set(String name, float v) {}
    public void set(String name, float a, float b) {}
    public void set(String name, float a, float b, float c) {}
    public void set(String name, float a, float b, float c, float d) {}
    public void set(String name, int v) {}
    public void set(String name, boolean v) {}
}
