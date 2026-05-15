package processing.core;

/**
 * 3D vector value type, API-compatible with Processing's {@code PVector} for
 * the methods used inside PixelFlow. Public {@code x}, {@code y}, {@code z}
 * fields preserve direct-access code patterns.
 *
 * <p>This is a value-type compatibility shim — not affiliated with the
 * Processing Foundation. It exists so the forked PixelFlow source compiles
 * without the Processing runtime on the classpath.
 */
public final class PVector {

    public float x, y, z;

    public PVector() {}

    public PVector(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public PVector(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PVector set(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        return this;
    }

    public PVector set(float x, float y) {
        this.x = x; this.y = y; this.z = 0f;
        return this;
    }

    public PVector set(PVector v) {
        this.x = v.x; this.y = v.y; this.z = v.z;
        return this;
    }

    public PVector copy() {
        return new PVector(x, y, z);
    }

    public PVector add(PVector v) {
        x += v.x; y += v.y; z += v.z;
        return this;
    }

    public PVector add(float dx, float dy, float dz) {
        x += dx; y += dy; z += dz;
        return this;
    }

    public PVector sub(PVector v) {
        x -= v.x; y -= v.y; z -= v.z;
        return this;
    }

    public PVector sub(float dx, float dy, float dz) {
        x -= dx; y -= dy; z -= dz;
        return this;
    }

    public PVector mult(float s) {
        x *= s; y *= s; z *= s;
        return this;
    }

    public PVector div(float s) {
        if (s == 0f) return this;
        x /= s; y /= s; z /= s;
        return this;
    }

    public float mag() {
        return (float) Math.sqrt((double) x * x + (double) y * y + (double) z * z);
    }

    public float magSq() {
        return x * x + y * y + z * z;
    }

    public PVector normalize() {
        float m = mag();
        if (m != 0f) {
            x /= m; y /= m; z /= m;
        }
        return this;
    }

    public PVector normalize(PVector target) {
        if (target == null) target = new PVector();
        float m = mag();
        if (m != 0f) target.set(x / m, y / m, z / m);
        else target.set(x, y, z);
        return target;
    }

    public float dot(PVector v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public float dot(float vx, float vy, float vz) {
        return x * vx + y * vy + z * vz;
    }

    public PVector cross(PVector v) {
        return new PVector(
                y * v.z - z * v.y,
                z * v.x - x * v.z,
                x * v.y - y * v.x);
    }

    public PVector cross(PVector v, PVector target) {
        float nx = y * v.z - z * v.y;
        float ny = z * v.x - x * v.z;
        float nz = x * v.y - y * v.x;
        if (target == null) target = new PVector();
        target.set(nx, ny, nz);
        return target;
    }

    public float dist(PVector v) {
        float dx = x - v.x, dy = y - v.y, dz = z - v.z;
        return (float) Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    public float heading() {
        return (float) Math.atan2((double) y, (double) x);
    }

    public PVector setMag(float m) {
        normalize();
        mult(m);
        return this;
    }

    public PVector limit(float max) {
        if (magSq() > max * max) {
            normalize();
            mult(max);
        }
        return this;
    }

    // Static factories — Processing-API-compatible
    public static PVector add(PVector a, PVector b) {
        return new PVector(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    public static PVector sub(PVector a, PVector b) {
        return new PVector(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    public static PVector mult(PVector a, float s) {
        return new PVector(a.x * s, a.y * s, a.z * s);
    }

    public static float dist(PVector a, PVector b) {
        float dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return (float) Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    public static PVector cross(PVector a, PVector b, PVector target) {
        float nx = a.y * b.z - a.z * b.y;
        float ny = a.z * b.x - a.x * b.z;
        float nz = a.x * b.y - a.y * b.x;
        if (target == null) target = new PVector();
        target.set(nx, ny, nz);
        return target;
    }

    public float[] array() {
        return new float[] { x, y, z };
    }

    @Override
    public String toString() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}
