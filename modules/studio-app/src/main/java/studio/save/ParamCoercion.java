package studio.save;

import java.util.List;

import studio.graph.Parameter;
import studio.graph.PortTypes;

/**
 * Coerces JSON-decoded values (Integer, Double, Boolean, List, String) into
 * the concrete Java type a {@link Parameter} expects. Used by the
 * {@code .pflow} loader to apply param overrides.
 */
public final class ParamCoercion {

    private ParamCoercion() {}

    public static Object coerce(Parameter<?> param, Object raw) {
        if (raw == null) return null;

        if (param.type == PortTypes.SCALAR) {
            if (raw instanceof Number n) return n.floatValue();
        } else if (param.type == PortTypes.INT) {
            if (raw instanceof Number n) return n.intValue();
        } else if (param.type == PortTypes.BOOL) {
            if (raw instanceof Boolean b) return b;
        } else if (param.type == PortTypes.VEC2 || param.type == PortTypes.VEC3 || param.type == PortTypes.VEC4) {
            if (raw instanceof List<?> list) {
                float[] arr = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Object v = list.get(i);
                    arr[i] = v instanceof Number ? ((Number) v).floatValue() : 0f;
                }
                return arr;
            }
        } else if (param.type.javaClass == String.class) {
            return raw.toString();
        }
        return null;
    }
}
