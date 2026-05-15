package studio.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Tweakable parameter attached to a node. Read by the node each frame in its
 * {@code evaluate()}; written by the UI (or the {@code .pflow} loader) via
 * {@link #set(Object)}. Value changes fire listeners.
 *
 * <p>Optional fields (min/max/uiHint/description) are advisory; the runtime
 * does not enforce them — the UI layer reads them to render appropriate
 * widgets.
 */
public final class Parameter<T> {

    public final String name;
    public String label;
    public final PortType<T> type;
    public final T defaultValue;
    public T min;
    public T max;
    public UiHint uiHint = UiHint.AUTO;
    public boolean structural;
    public String description;

    private volatile T value;
    private final List<Consumer<T>> listeners = new ArrayList<>();

    public Parameter(String name, PortType<T> type, T defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.label = name;
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public T get() { return value; }

    public void set(T v) {
        if (!Objects.equals(value, v)) {
            value = v;
            for (Consumer<T> l : listeners) l.accept(v);
        }
    }

    public Parameter<T> withRange(T min, T max) {
        this.min = min;
        this.max = max;
        return this;
    }

    public Parameter<T> withLabel(String label) { this.label = label; return this; }
    public Parameter<T> withUiHint(UiHint hint)  { this.uiHint = hint; return this; }
    public Parameter<T> withDescription(String d){ this.description = d; return this; }
    public Parameter<T> structural()             { this.structural = true; return this; }

    public void onChange(Consumer<T> listener) { listeners.add(listener); }

    /** UI presentation hint. */
    public enum UiHint {
        AUTO,
        SLIDER,
        KNOB,
        TOGGLE,
        DROPDOWN,
        COLOR_RGBA,
        XY_PAD,
        VEC_FIELD,
        FILE_PICKER,
        TEXT,
    }

    // Convenience factories
    public static Parameter<Float>   floatRange(String name, float def, float min, float max) {
        Parameter<Float> p = new Parameter<>(name, PortTypes.SCALAR, def);
        p.min = min; p.max = max;
        return p;
    }
    public static Parameter<Integer> intRange(String name, int def, int min, int max) {
        Parameter<Integer> p = new Parameter<>(name, PortTypes.INT, def);
        p.min = min; p.max = max;
        return p;
    }
    public static Parameter<Boolean> bool(String name, boolean def) {
        return new Parameter<>(name, PortTypes.BOOL, def);
    }
    public static Parameter<float[]> vec4(String name, float[] def) {
        return new Parameter<>(name, PortTypes.VEC4, def);
    }
}
