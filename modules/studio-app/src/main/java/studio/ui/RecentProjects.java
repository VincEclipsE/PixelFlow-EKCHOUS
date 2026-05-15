package studio.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Persists the most-recently-opened project paths so the File menu can
 * surface them. Stored as one path per line in
 * {@code ~/.pixelflow-studio/recent.txt}.
 */
public final class RecentProjects {

    private static final int MAX_ENTRIES = 8;
    private static final Path STORE = Paths.get(System.getProperty("user.home", "."),
            ".pixelflow-studio", "recent.txt");

    private final LinkedHashSet<String> entries = new LinkedHashSet<>();

    public RecentProjects() {
        load();
    }

    public List<Path> entries() {
        List<Path> out = new ArrayList<>(entries.size());
        for (String s : entries) out.add(Paths.get(s));
        return out;
    }

    /** Insert a path at the head; older entries are pushed down. */
    public void add(Path p) {
        if (p == null) return;
        String key = p.toAbsolutePath().normalize().toString();
        entries.remove(key);
        LinkedHashSet<String> rebuilt = new LinkedHashSet<>();
        rebuilt.add(key);
        rebuilt.addAll(entries);
        entries.clear();
        int i = 0;
        for (String s : rebuilt) {
            if (i++ >= MAX_ENTRIES) break;
            entries.add(s);
        }
        save();
    }

    private void load() {
        try {
            if (!Files.exists(STORE)) return;
            for (String line : Files.readAllLines(STORE)) {
                String s = line.trim();
                if (!s.isEmpty()) entries.add(s);
                if (entries.size() >= MAX_ENTRIES) break;
            }
        } catch (IOException ignored) { /* first launch */ }
    }

    private void save() {
        try {
            Files.createDirectories(STORE.getParent());
            Files.write(STORE, entries);
        } catch (IOException ignored) { /* non-fatal — recents are best-effort */ }
    }
}
