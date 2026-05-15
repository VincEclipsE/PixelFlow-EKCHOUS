package studio.graph;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Two-stack undo/redo. Mutations to the graph and editor layout funnel
 * through {@link Command} objects pushed onto {@link #push(Command)};
 * {@link #undo()} runs the inverse and moves the entry to the redo stack.
 *
 * <p>Scope for M3.5: structural ops only (add/remove node, connect,
 * disconnect, move, mute, label). Parameter edits are not tracked yet —
 * they would need debounced coalescing per the design plan.
 */
public final class UndoStack {

    public interface Command {
        /** Move the world from the pre-state to the post-state. */
        void apply();
        /** Move the world from the post-state back to the pre-state. */
        void revert();
        /** Human label for menu/status display. */
        String description();
    }

    private static final int MAX_DEPTH = 100;
    private final Deque<Command> past = new ArrayDeque<>();
    private final Deque<Command> future = new ArrayDeque<>();
    private Runnable onMutate;

    /** Fires on every push, undo, or redo — i.e. any graph mutation. */
    public void setOnMutate(Runnable r) { this.onMutate = r; }
    private void fireMutate() { if (onMutate != null) onMutate.run(); }

    /** Push an already-applied command; clears the redo stack. */
    public void push(Command c) {
        past.push(c);
        future.clear();
        while (past.size() > MAX_DEPTH) past.pollLast();
        fireMutate();
    }

    public boolean canUndo() { return !past.isEmpty(); }
    public boolean canRedo() { return !future.isEmpty(); }

    public Command undo() {
        if (past.isEmpty()) return null;
        Command c = past.pop();
        c.revert();
        future.push(c);
        fireMutate();
        return c;
    }

    public Command redo() {
        if (future.isEmpty()) return null;
        Command c = future.pop();
        c.apply();
        past.push(c);
        fireMutate();
        return c;
    }

    public void clear() {
        past.clear();
        future.clear();
    }
}
