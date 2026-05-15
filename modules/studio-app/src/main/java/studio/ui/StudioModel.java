package studio.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import studio.save.PflowReader;
import studio.save.PflowWriter;

/**
 * Application-level state for the Studio. Holds the path of the currently
 * loaded project plus the most recent {@link PflowReader.Result}, and
 * notifies listeners on each load.
 *
 * <p>Listeners are called on the EDT.
 */
public final class StudioModel {

    private final PflowReader reader;
    private Path currentPath;
    private PflowReader.Result current;
    private boolean dirty;
    private final List<Consumer<PflowReader.Result>> listeners = new ArrayList<>();
    private Runnable dirtyListener;

    public boolean isDirty() { return dirty; }
    public void markDirty() {
        if (!dirty) {
            dirty = true;
            if (dirtyListener != null) dirtyListener.run();
        }
    }
    public void clearDirty() {
        if (dirty) {
            dirty = false;
            if (dirtyListener != null) dirtyListener.run();
        }
    }
    public void setDirtyListener(Runnable r) { this.dirtyListener = r; }

    public StudioModel(PflowReader reader) {
        this.reader = reader;
    }

    public void loadProject(Path path) throws IOException {
        PflowReader.Result loaded = reader.load(path);
        this.currentPath = path;
        this.current = loaded;
        clearDirty();
        for (Consumer<PflowReader.Result> l : listeners) l.accept(loaded);
    }

    /** Reload the project from its current path. No-op if none loaded. */
    public void reload() {
        if (currentPath == null) return;
        try {
            loadProject(currentPath);
        } catch (IOException e) {
            System.err.println("reload failed: " + e.getMessage());
        }
    }

    public PflowReader.Result current() { return current; }
    public Path currentPath() { return currentPath; }

    public void addProjectLoadedListener(Consumer<PflowReader.Result> listener) {
        listeners.add(listener);
    }

    /** Write the current graph back to its source path. */
    public void save() throws IOException {
        save(null);
    }

    public void save(java.util.Map<studio.graph.NodeId, int[]> layouts) throws IOException {
        if (currentPath == null) throw new IOException("no project loaded; use Save As");
        if (current == null) throw new IOException("no graph loaded");
        PflowWriter.write(currentPath, current.graph, current.source, layouts);
        clearDirty();
    }

    /** Write the current graph to a new path. The model adopts that path. */
    public void saveAs(Path path) throws IOException {
        saveAs(path, null);
    }

    public void saveAs(Path path, java.util.Map<studio.graph.NodeId, int[]> layouts) throws IOException {
        if (current == null) throw new IOException("no graph loaded");
        PflowWriter.write(path, current.graph, current.source, layouts);
        currentPath = path;
        clearDirty();
    }
}
