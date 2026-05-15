package studio.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects per-node errors raised during graph evaluation. */
public final class GraphErrors {

    public static final class Entry {
        public final Node node;
        public final Throwable cause;
        public final String message;
        public final long frame;

        Entry(Node node, Throwable cause, String message, long frame) {
            this.node = node;
            this.cause = cause;
            this.message = message;
            this.frame = frame;
        }

        @Override public String toString() {
            return "[frame " + frame + "] " + (node != null ? node.label() : "<global>")
                    + ": " + message + (cause != null ? " (" + cause.getClass().getSimpleName() + ")" : "");
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void report(Node node, Throwable cause, long frame) {
        entries.add(new Entry(node, cause, cause.getMessage(), frame));
    }

    public void report(Node node, String message, long frame) {
        entries.add(new Entry(node, null, message, frame));
    }

    public List<Entry> snapshot() { return Collections.unmodifiableList(new ArrayList<>(entries)); }

    public boolean isEmpty() { return entries.isEmpty(); }

    public void clear() { entries.clear(); }
}
