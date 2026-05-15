package studio.save;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import studio.graph.NodeFactoryRegistry;

/**
 * Scans a directory for {@code .pftool} files and registers each one as a
 * factory in a {@link NodeFactoryRegistry}, making the compound tool a
 * first-class palette entry.
 */
public final class ToolsLibrary {

    private final Path root;
    private final NodeFactoryRegistry registry;
    private final PftoolReader reader;
    private final Set<String> registeredTypeIds = new LinkedHashSet<>();

    public ToolsLibrary(Path root, NodeFactoryRegistry registry) {
        this.root = root;
        this.registry = registry;
        this.reader = new PftoolReader(registry);
    }

    /** Re-scan the directory and register every .pftool file we find. */
    public List<String> rescan() {
        registeredTypeIds.clear();
        if (root == null || !Files.isDirectory(root)) return List.of();
        List<String> ids = new ArrayList<>();
        try (Stream<Path> walk = Files.list(root)) {
            walk.filter(p -> p.toString().toLowerCase().endsWith(".pftool"))
                .sorted()
                .forEach(p -> {
                    try {
                        String id = reader.register(p, registry);
                        registeredTypeIds.add(id);
                        ids.add(id);
                    } catch (IOException ex) {
                        System.err.println("ToolsLibrary: failed to load " + p + ": " + ex.getMessage());
                    }
                });
        } catch (IOException ex) {
            System.err.println("ToolsLibrary: failed to list " + root + ": " + ex.getMessage());
        }
        return Collections.unmodifiableList(ids);
    }

    public Set<String> registeredTypeIds() { return Collections.unmodifiableSet(registeredTypeIds); }

    public Path root() { return root; }

    private Thread watcherThread;
    private final AtomicBoolean watcherRunning = new AtomicBoolean();

    /**
     * Spin up a daemon thread watching {@link #root()} for {@code .pftool}
     * create/modify/delete events. On each event, {@link #rescan()} runs and
     * {@code onChange} is invoked.
     *
     * <p>The callback is invoked on the watcher thread; UI code should
     * marshal back to the EDT via {@code SwingUtilities.invokeLater}.
     */
    public synchronized void startWatcher(Runnable onChange) {
        if (root == null || !Files.isDirectory(root)) return;
        if (watcherRunning.get()) return;
        watcherRunning.set(true);
        watcherThread = new Thread(() -> runWatcher(onChange), "tools-library-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public synchronized void stopWatcher() {
        watcherRunning.set(false);
        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }
    }

    private void runWatcher(Runnable onChange) {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            root.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            while (watcherRunning.get() && !Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = ws.take();
                } catch (InterruptedException ie) {
                    return;
                }
                boolean changed = false;
                for (WatchEvent<?> ev : key.pollEvents()) {
                    Object ctx = ev.context();
                    if (ctx instanceof Path p && p.toString().toLowerCase().endsWith(".pftool")) {
                        changed = true;
                    }
                }
                if (changed) {
                    // Coalesce: editors typically fire create + modify in quick succession.
                    try { Thread.sleep(150); } catch (InterruptedException ie) { return; }
                    rescan();
                    if (onChange != null) {
                        try { onChange.run(); } catch (Throwable t) {
                            System.err.println("ToolsLibrary onChange threw: " + t.getMessage());
                        }
                    }
                }
                if (!key.reset()) break;
            }
        } catch (IOException ex) {
            System.err.println("ToolsLibrary watcher failed: " + ex.getMessage());
        }
    }
}
