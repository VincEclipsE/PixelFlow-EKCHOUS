package processing.core;

/**
 * Common interface for {@link PMatrix2D} and {@link PMatrix3D}. Value-type
 * compatibility shim — not affiliated with the Processing Foundation.
 *
 * <p>Only the subset of methods PixelFlow actually calls is declared.
 */
public interface PMatrix {

    void reset();

    boolean invert();

    /** Returns the matrix as a flat array (row-major for 2D, column-major for 3D — see impl). */
    float[] get(float[] target);

    void translate(float tx, float ty);

    void translate(float tx, float ty, float tz);

    void rotate(float angle);

    void rotateX(float angle);

    void rotateY(float angle);

    void rotateZ(float angle);

    void scale(float s);

    void scale(float sx, float sy);

    void scale(float sx, float sy, float sz);

    void apply(PMatrix2D other);

    void apply(PMatrix3D other);
}
