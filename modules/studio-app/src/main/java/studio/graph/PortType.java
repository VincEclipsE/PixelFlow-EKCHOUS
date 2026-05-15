package studio.graph;

import java.util.Objects;

/**
 * Type marker for a port (input or output). Two ports may connect iff the
 * upstream output's type is assignable to the downstream input's type per
 * {@link #isAssignableFrom(PortType)}.
 *
 * <p>v1 keeps the model simple: each PortType has a stable id and a Java
 * value class. The full lattice (VelocityField → Texture2D, etc.) lives in
 * {@link PortTypes}.
 */
public final class PortType<T> {

    public final String id;
    public final Class<T> javaClass;
    @SuppressWarnings("rawtypes")
    private final PortType parent;

    @SuppressWarnings({"rawtypes", "unchecked"})
    PortType(String id, Class<T> javaClass, PortType parent) {
        this.id = Objects.requireNonNull(id, "id");
        this.javaClass = Objects.requireNonNull(javaClass, "javaClass");
        this.parent = parent;
    }

    /** True if {@code other} can be plugged into a port of this type. */
    public boolean isAssignableFrom(PortType<?> other) {
        if (other == null) return false;
        if (other == this) return true;
        return other.parent != null && this.isAssignableFrom(other.parent);
    }

    @Override public String toString() { return id; }
}
