package studio.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
}
