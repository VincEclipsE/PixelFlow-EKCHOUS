package studio.ui;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.Timer;

import studio.graph.Node;
import studio.graph.Parameter;
import studio.graph.UndoStack;

/**
 * Bridges {@link Parameter} change listeners to an {@link UndoStack}. Param
 * edits are coalesced within a 500ms idle window so a slider drag becomes a
 * single undo entry, not 60 per second.
 *
 * <p>Lifecycle: instantiate one per editor session, call {@link #track(Node)}
 * for every node in the graph (and again on add). The tracker captures
 * before/after values, deep-copies arrays for safety, and suspends the
 * listener while applying its own revert so undo doesn't cascade.
 */
public final class ParamEditTracker {

    private static final int DEBOUNCE_MS = 500;

    private final UndoStack undo;
    private final Map<Parameter<?>, Pending> pendingEdits = new IdentityHashMap<>();
    private final Map<Parameter<?>, Object> lastCommitted = new IdentityHashMap<>();
    private final Map<Parameter<?>, Node> ownerOf = new IdentityHashMap<>();
    private boolean suspend;

    public ParamEditTracker(UndoStack undo) {
        this.undo = undo;
    }

    public void track(Node node) {
        for (Parameter<?> p : node.parameters()) {
            if (ownerOf.containsKey(p)) continue; // already tracked
            ownerOf.put(p, node);
            lastCommitted.put(p, deepCopy(p.get()));
            registerListener(p);
        }
    }

    /** Cancel any pending edit for the given node's params (called on node removal). */
    public void untrack(Node node) {
        for (Parameter<?> p : node.parameters()) {
            Pending pe = pendingEdits.remove(p);
            if (pe != null && pe.timer != null) pe.timer.stop();
            lastCommitted.remove(p);
            ownerOf.remove(p);
        }
    }

    /** Cancel everything; call when the graph is replaced. */
    public void clear() {
        for (Pending pe : pendingEdits.values()) {
            if (pe.timer != null) pe.timer.stop();
        }
        pendingEdits.clear();
        lastCommitted.clear();
        ownerOf.clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerListener(Parameter<?> p) {
        ((Parameter) p).onChange(v -> handleChange(p, v));
    }

    private void handleChange(Parameter<?> p, Object newValue) {
        if (suspend) return;
        Pending pe = pendingEdits.get(p);
        if (pe == null) {
            pe = new Pending();
            pe.oldValue = deepCopy(lastCommitted.get(p));
            pe.timer = new Timer(DEBOUNCE_MS, e -> commit(p));
            pe.timer.setRepeats(false);
            pendingEdits.put(p, pe);
        }
        pe.newValue = deepCopy(newValue);
        pe.timer.restart();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void commit(Parameter<?> p) {
        Pending pe = pendingEdits.remove(p);
        if (pe == null) return;
        final Object oldV = pe.oldValue;
        final Object newV = pe.newValue;
        if (Objects.deepEquals(oldV, newV)) return;
        lastCommitted.put(p, newV);

        final Parameter rawP = (Parameter) p;
        final Node owner = ownerOf.get(p);
        undo.push(new UndoStack.Command() {
            @Override public void apply() {
                suspend = true;
                try { rawP.set(newV); } finally { suspend = false; }
                lastCommitted.put(p, newV);
            }
            @Override public void revert() {
                suspend = true;
                try { rawP.set(oldV); } finally { suspend = false; }
                lastCommitted.put(p, oldV);
            }
            @Override public String description() {
                return "edit " + (owner != null ? owner.label() + "." : "") + p.name;
            }
        });
    }

    private static Object deepCopy(Object v) {
        if (v instanceof float[] arr) return arr.clone();
        return v;
    }

    private static final class Pending {
        Object oldValue;
        Object newValue;
        Timer timer;
    }
}
