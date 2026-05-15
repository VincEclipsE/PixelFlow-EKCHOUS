package studio.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import studio.save.PflowReader;

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
    private final List<Consumer<PflowReader.Result>> listeners = new ArrayList<>();

    public StudioModel(PflowReader reader) {
        this.reader = reader;
    }

    public void loadProject(Path path) throws IOException {
        PflowReader.Result loaded = reader.load(path);
        this.currentPath = path;
        this.current = loaded;
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
}
