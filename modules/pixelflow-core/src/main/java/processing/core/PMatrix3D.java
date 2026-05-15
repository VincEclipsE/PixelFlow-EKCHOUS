package processing.core;

/**
 * 4x4 matrix, row-major storage. Value-type compatibility shim — not
 * affiliated with the Processing Foundation. Implements the subset of
 * Processing's {@code PMatrix3D} API used by the forked PixelFlow source.
 *
 * <pre>
 *   m00 m01 m02 m03
 *   m10 m11 m12 m13
 *   m20 m21 m22 m23
 *   m30 m31 m32 m33
 * </pre>
 */
public final class PMatrix3D implements PMatrix {

    public float m00, m01, m02, m03;
    public float m10, m11, m12, m13;
    public float m20, m21, m22, m23;
    public float m30, m31, m32, m33;

    public PMatrix3D() { reset(); }

    public PMatrix3D(float m00, float m01, float m02, float m03,
                     float m10, float m11, float m12, float m13,
                     float m20, float m21, float m22, float m23,
                     float m30, float m31, float m32, float m33) {
        set(m00, m01, m02, m03, m10, m11, m12, m13,
            m20, m21, m22, m23, m30, m31, m32, m33);
    }

    public PMatrix3D(PMatrix3D src) {
        set(src);
    }

    @Override
    public void reset() {
        set(1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1);
    }

    public PMatrix3D get() {
        return new PMatrix3D(this);
    }

    @Override
    public float[] get(float[] target) {
        if (target == null || target.length != 16) target = new float[16];
        target[0]  = m00; target[1]  = m01; target[2]  = m02; target[3]  = m03;
        target[4]  = m10; target[5]  = m11; target[6]  = m12; target[7]  = m13;
        target[8]  = m20; target[9]  = m21; target[10] = m22; target[11] = m23;
        target[12] = m30; target[13] = m31; target[14] = m32; target[15] = m33;
        return target;
    }

    public void set(float m00, float m01, float m02, float m03,
                    float m10, float m11, float m12, float m13,
                    float m20, float m21, float m22, float m23,
                    float m30, float m31, float m32, float m33) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02; this.m03 = m03;
        this.m10 = m10; this.m11 = m11; this.m12 = m12; this.m13 = m13;
        this.m20 = m20; this.m21 = m21; this.m22 = m22; this.m23 = m23;
        this.m30 = m30; this.m31 = m31; this.m32 = m32; this.m33 = m33;
    }

    public void set(PMatrix3D src) {
        set(src.m00, src.m01, src.m02, src.m03,
            src.m10, src.m11, src.m12, src.m13,
            src.m20, src.m21, src.m22, src.m23,
            src.m30, src.m31, src.m32, src.m33);
    }

    @Override
    public void translate(float tx, float ty) { translate(tx, ty, 0); }

    @Override
    public void translate(float tx, float ty, float tz) {
        m03 += tx * m00 + ty * m01 + tz * m02;
        m13 += tx * m10 + ty * m11 + tz * m12;
        m23 += tx * m20 + ty * m21 + tz * m22;
        m33 += tx * m30 + ty * m31 + tz * m32;
    }

    @Override public void rotate(float angle) { rotateZ(angle); }

    @Override
    public void rotateX(float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        apply(1, 0, 0, 0,
              0, c, -s, 0,
              0, s,  c, 0,
              0, 0, 0, 1);
    }

    @Override
    public void rotateY(float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        apply( c, 0, s, 0,
               0, 1, 0, 0,
              -s, 0, c, 0,
               0, 0, 0, 1);
    }

    @Override
    public void rotateZ(float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        apply(c, -s, 0, 0,
              s,  c, 0, 0,
              0,  0, 1, 0,
              0,  0, 0, 1);
    }

    @Override public void scale(float s) { scale(s, s, s); }
    @Override public void scale(float sx, float sy) { scale(sx, sy, 1); }

    @Override
    public void scale(float sx, float sy, float sz) {
        m00 *= sx; m01 *= sy; m02 *= sz;
        m10 *= sx; m11 *= sy; m12 *= sz;
        m20 *= sx; m21 *= sy; m22 *= sz;
        m30 *= sx; m31 *= sy; m32 *= sz;
    }

    public void apply(float n00, float n01, float n02, float n03,
                      float n10, float n11, float n12, float n13,
                      float n20, float n21, float n22, float n23,
                      float n30, float n31, float n32, float n33) {
        float t00 = m00*n00 + m01*n10 + m02*n20 + m03*n30;
        float t01 = m00*n01 + m01*n11 + m02*n21 + m03*n31;
        float t02 = m00*n02 + m01*n12 + m02*n22 + m03*n32;
        float t03 = m00*n03 + m01*n13 + m02*n23 + m03*n33;
        float t10 = m10*n00 + m11*n10 + m12*n20 + m13*n30;
        float t11 = m10*n01 + m11*n11 + m12*n21 + m13*n31;
        float t12 = m10*n02 + m11*n12 + m12*n22 + m13*n32;
        float t13 = m10*n03 + m11*n13 + m12*n23 + m13*n33;
        float t20 = m20*n00 + m21*n10 + m22*n20 + m23*n30;
        float t21 = m20*n01 + m21*n11 + m22*n21 + m23*n31;
        float t22 = m20*n02 + m21*n12 + m22*n22 + m23*n32;
        float t23 = m20*n03 + m21*n13 + m22*n23 + m23*n33;
        float t30 = m30*n00 + m31*n10 + m32*n20 + m33*n30;
        float t31 = m30*n01 + m31*n11 + m32*n21 + m33*n31;
        float t32 = m30*n02 + m31*n12 + m32*n22 + m33*n32;
        float t33 = m30*n03 + m31*n13 + m32*n23 + m33*n33;
        m00 = t00; m01 = t01; m02 = t02; m03 = t03;
        m10 = t10; m11 = t11; m12 = t12; m13 = t13;
        m20 = t20; m21 = t21; m22 = t22; m23 = t23;
        m30 = t30; m31 = t31; m32 = t32; m33 = t33;
    }

    @Override
    public void apply(PMatrix2D o) {
        apply(o.m00, o.m01, 0, o.m02,
              o.m10, o.m11, 0, o.m12,
                 0,    0,   1,    0,
                 0,    0,   0,    1);
    }

    @Override
    public void apply(PMatrix3D o) {
        apply(o.m00, o.m01, o.m02, o.m03,
              o.m10, o.m11, o.m12, o.m13,
              o.m20, o.m21, o.m22, o.m23,
              o.m30, o.m31, o.m32, o.m33);
    }

    @Override
    public boolean invert() {
        // Standard 4x4 cofactor inverse
        float a0 = m00 * m11 - m01 * m10;
        float a1 = m00 * m12 - m02 * m10;
        float a2 = m00 * m13 - m03 * m10;
        float a3 = m01 * m12 - m02 * m11;
        float a4 = m01 * m13 - m03 * m11;
        float a5 = m02 * m13 - m03 * m12;
        float b0 = m20 * m31 - m21 * m30;
        float b1 = m20 * m32 - m22 * m30;
        float b2 = m20 * m33 - m23 * m30;
        float b3 = m21 * m32 - m22 * m31;
        float b4 = m21 * m33 - m23 * m31;
        float b5 = m22 * m33 - m23 * m32;

        float det = a0 * b5 - a1 * b4 + a2 * b3 + a3 * b2 - a4 * b1 + a5 * b0;
        if (Math.abs(det) <= Float.MIN_VALUE) return false;

        float invDet = 1.0f / det;

        float n00 = ( m11 * b5 - m12 * b4 + m13 * b3) * invDet;
        float n01 = (-m01 * b5 + m02 * b4 - m03 * b3) * invDet;
        float n02 = ( m31 * a5 - m32 * a4 + m33 * a3) * invDet;
        float n03 = (-m21 * a5 + m22 * a4 - m23 * a3) * invDet;
        float n10 = (-m10 * b5 + m12 * b2 - m13 * b1) * invDet;
        float n11 = ( m00 * b5 - m02 * b2 + m03 * b1) * invDet;
        float n12 = (-m30 * a5 + m32 * a2 - m33 * a1) * invDet;
        float n13 = ( m20 * a5 - m22 * a2 + m23 * a1) * invDet;
        float n20 = ( m10 * b4 - m11 * b2 + m13 * b0) * invDet;
        float n21 = (-m00 * b4 + m01 * b2 - m03 * b0) * invDet;
        float n22 = ( m30 * a4 - m31 * a2 + m33 * a0) * invDet;
        float n23 = (-m20 * a4 + m21 * a2 - m23 * a0) * invDet;
        float n30 = (-m10 * b3 + m11 * b1 - m12 * b0) * invDet;
        float n31 = ( m00 * b3 - m01 * b1 + m02 * b0) * invDet;
        float n32 = (-m30 * a3 + m31 * a1 - m32 * a0) * invDet;
        float n33 = ( m20 * a3 - m21 * a1 + m22 * a0) * invDet;

        set(n00, n01, n02, n03,
            n10, n11, n12, n13,
            n20, n21, n22, n23,
            n30, n31, n32, n33);
        return true;
    }

    public PVector mult(PVector source, PVector target) {
        if (target == null) target = new PVector();
        float vx = source.x, vy = source.y, vz = source.z;
        target.x = m00 * vx + m01 * vy + m02 * vz + m03;
        target.y = m10 * vx + m11 * vy + m12 * vz + m13;
        target.z = m20 * vx + m21 * vy + m22 * vz + m23;
        return target;
    }

    @Override
    public String toString() {
        return String.format("[%f %f %f %f]%n[%f %f %f %f]%n[%f %f %f %f]%n[%f %f %f %f]%n",
                m00, m01, m02, m03, m10, m11, m12, m13,
                m20, m21, m22, m23, m30, m31, m32, m33);
    }
}
