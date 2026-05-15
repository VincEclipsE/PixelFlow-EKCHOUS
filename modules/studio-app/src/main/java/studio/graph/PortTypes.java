package studio.graph;

import studio.engine.RenderTarget;

/**
 * Canonical port-type registry for v1 of the studio DAG.
 *
 * <p>The full lattice planned in the design doc (VelocityField,
 * DensityField, ObstacleMask, ParticleSystemHandle, etc.) is reserved for
 * follow-up; the v1 runtime only needs Texture2D, plus scalar/vec/bool
 * data ports for parameter wiring.
 */
public final class PortTypes {

    /** Catch-all for unspecified/debug ports. Avoid in production node specs. */
    public static final PortType<Object>     ANY     = new PortType<>("any",     Object.class, null);

    /** Anything that produces a 2D color attachment usable as both sampler input and FBO target. */
    public static final PortType<RenderTarget> TEXTURE2D = new PortType<>("tex2d", RenderTarget.class, null);

    /** Scalar number ports. */
    public static final PortType<Float>   SCALAR = new PortType<>("scalar", Float.class,   null);
    public static final PortType<Integer> INT    = new PortType<>("int",    Integer.class, SCALAR);
    public static final PortType<Boolean> BOOL   = new PortType<>("bool",   Boolean.class, null);

    public static final PortType<float[]> VEC2   = new PortType<>("vec2",   float[].class, null);
    public static final PortType<float[]> VEC3   = new PortType<>("vec3",   float[].class, null);
    public static final PortType<float[]> VEC4   = new PortType<>("vec4",   float[].class, null);

    public static final PortType<String>  STRING = new PortType<>("string", String.class,  null);

    private PortTypes() {}

    /** Look up a registered PortType by its stable id. Returns null if unknown. */
    public static PortType<?> byId(String id) {
        if (id == null) return null;
        return switch (id) {
            case "any"    -> ANY;
            case "tex2d"  -> TEXTURE2D;
            case "scalar" -> SCALAR;
            case "int"    -> INT;
            case "bool"   -> BOOL;
            case "vec2"   -> VEC2;
            case "vec3"   -> VEC3;
            case "vec4"   -> VEC4;
            case "string" -> STRING;
            default       -> null;
        };
    }
}
