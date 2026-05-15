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
}
