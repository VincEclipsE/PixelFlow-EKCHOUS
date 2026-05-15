package processing.opengl;

import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;

/** Compatibility stub. */
public class PGL {
    public GL2ES2 gl;
    public GL2ES2 getGL2ES2() { return gl; }
    public GL3 getGL3() { return (GL3) gl; }

    public void bindTexture(int target, int name) {}
    public void unbindTexture(int target) {}

    public int attachShader(int program, int shader) { return 0; }

    public void clearColor(float r, float g, float b, float a) {}
    public void clear(int mask) {}
    public void enable(int cap) {}
    public void disable(int cap) {}
    public void blendFunc(int s, int d) {}
    public void texParameteri(int target, int pname, int param) {}
    public void generateMipmap(int target) {}
    public void ortho(float l, float r, float b, float t, float n, float f) {}

    // Common GL constants exposed as PGL static fields by Processing
    public static final int COLOR_BUFFER_BIT   = 0x4000;
    public static final int DEPTH_BUFFER_BIT   = 0x0100;
    public static final int STENCIL_BUFFER_BIT = 0x0400;
    public static final int TEXTURE_2D         = 0x0DE1;
    public static final int RGBA               = 0x1908;
    public static final int RGBA8              = 0x8058;
    public static final int RGB                = 0x1907;
    public static final int FLOAT              = 0x1406;
    public static final int UNSIGNED_BYTE      = 0x1401;
    public static final int LINEAR             = 0x2601;
    public static final int NEAREST            = 0x2600;
    public static final int CLAMP_TO_EDGE      = 0x812F;
    public static final int REPEAT             = 0x2901;
    public static final int TEXTURE_MIN_FILTER = 0x2801;
    public static final int TEXTURE_MAG_FILTER = 0x2800;
    public static final int TEXTURE_WRAP_S     = 0x2802;
    public static final int TEXTURE_WRAP_T     = 0x2803;
}
