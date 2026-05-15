package studio.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Maps stable {@code typeId} strings to factory lambdas that build fresh
 * {@link Node} instances. The {@code .pflow} loader consults this registry
 * to instantiate each node it encounters.
 */
public final class NodeFactoryRegistry {

    private final Map<String, Supplier<? extends Node>> factories = new HashMap<>();

    public NodeFactoryRegistry register(String typeId, Supplier<? extends Node> factory) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(factory, "factory");
        factories.put(typeId, factory);
        return this;
    }

    public Node create(String typeId) {
        Supplier<? extends Node> factory = factories.get(typeId);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown node typeId: " + typeId);
        }
        return factory.get();
    }

    public boolean has(String typeId) {
        return factories.containsKey(typeId);
    }

    /** Read-only view of every registered typeId, in insertion order is not guaranteed. */
    public Set<String> typeIds() {
        return Collections.unmodifiableSet(factories.keySet());
    }
}
