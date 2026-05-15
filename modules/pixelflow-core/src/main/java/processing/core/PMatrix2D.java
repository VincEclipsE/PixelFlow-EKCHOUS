package processing.core;

/**
 * 2D affine matrix (3x2). Row-major storage:
 * <pre>
 *   m00 m01 m02
 *   m10 m11 m12
 * </pre>
 * Value-type compatibility shim — not affiliated with the Processing
 * Foundation. Implements the subset of Processing's {@code PMatrix2D} API
 * actually used by the forked PixelFlow source.
 */
public final class PMatrix2D implements PMatrix {

    public float m00, m01, m02;
    public float m10, m11, m12;

    public PMatrix2D() { reset(); }

    public PMatrix2D(float m00, float m01, float m02,
                     float m10, float m11, float m12) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02;
        this.m10 = m10; this.m11 = m11; this.m12 = m12;
    }

    @Override
    public void reset() {
        m00 = 1; m01 = 0; m02 = 0;
        m10 = 0; m11 = 1; m12 = 0;
    }

    public PMatrix2D get() {
        return new PMatrix2D(m00, m01, m02, m10, m11, m12);
    }

    @Override
    public float[] get(float[] target) {
        if (target == null || target.length != 6) target = new float[6];
        target[0] = m00; target[1] = m01; target[2] = m02;
        target[3] = m10; target[4] = m11; target[5] = m12;
        return target;
    }

    public void set(float m00, float m01, float m02,
                    float m10, float m11, float m12) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02;
        this.m10 = m10; this.m11 = m11; this.m12 = m12;
    }

    @Override
    public void translate(float tx, float ty) {
        m02 = m00 * tx + m01 * ty + m02;
        m12 = m10 * tx + m11 * ty + m12;
    }

    @Override
    public void translate(float tx, float ty, float tz) {
        translate(tx, ty);
    }

    @Override
    public void rotate(float angle) {
        float s = (float) Math.sin(angle);
        float c = (float) Math.cos(angle);

        float t00 = m00 * c + m01 * s;
        float t01 = m00 * -s + m01 * c;
        float t10 = m10 * c + m11 * s;
        float t11 = m10 * -s + m11 * c;
        m00 = t00; m01 = t01;
        m10 = t10; m11 = t11;
    }

    @Override public void rotateX(float angle) { rotate(angle); }
    @Override public void rotateY(float angle) { rotate(angle); }
    @Override public void rotateZ(float angle) { rotate(angle); }

    @Override
    public void scale(float s) { scale(s, s); }

    @Override
    public void scale(float sx, float sy) {
        m00 *= sx; m01 *= sy;
        m10 *= sx; m11 *= sy;
    }

    @Override
    public void scale(float sx, float sy, float sz) { scale(sx, sy); }

    @Override
    public boolean invert() {
        float det = m00 * m11 - m01 * m10;
        if (Math.abs(det) <= Float.MIN_VALUE) return false;
        float inv = 1.0f / det;
        float t00 =  m11 * inv;
        float t01 = -m01 * inv;
        float t02 = (m01 * m12 - m11 * m02) * inv;
        float t10 = -m10 * inv;
        float t11 =  m00 * inv;
        float t12 = (m10 * m02 - m00 * m12) * inv;
        m00 = t00; m01 = t01; m02 = t02;
        m10 = t10; m11 = t11; m12 = t12;
        return true;
    }

    @Override
    public void apply(PMatrix2D o) {
        float t00 = m00 * o.m00 + m01 * o.m10;
        float t01 = m00 * o.m01 + m01 * o.m11;
        float t02 = m00 * o.m02 + m01 * o.m12 + m02;
        float t10 = m10 * o.m00 + m11 * o.m10;
        float t11 = m10 * o.m01 + m11 * o.m11;
        float t12 = m10 * o.m02 + m11 * o.m12 + m12;
        m00 = t00; m01 = t01; m02 = t02;
        m10 = t10; m11 = t11; m12 = t12;
    }

    @Override
    public void apply(PMatrix3D o) {
        // promote relevant 2D parts of the 3D matrix
        float t00 = m00 * o.m00 + m01 * o.m10;
        float t01 = m00 * o.m01 + m01 * o.m11;
        float t02 = m00 * o.m03 + m01 * o.m13 + m02;
        float t10 = m10 * o.m00 + m11 * o.m10;
        float t11 = m10 * o.m01 + m11 * o.m11;
        float t12 = m10 * o.m03 + m11 * o.m13 + m12;
        m00 = t00; m01 = t01; m02 = t02;
        m10 = t10; m11 = t11; m12 = t12;
    }

    @Override
    public String toString() {
        return String.format("[%f %f %f]%n[%f %f %f]%n", m00, m01, m02, m10, m11, m12);
    }
}
