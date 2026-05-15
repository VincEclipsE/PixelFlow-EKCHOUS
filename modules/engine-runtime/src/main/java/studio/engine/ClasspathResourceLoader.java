package studio.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Filesystem-overlay-first, then classpath. Dev builds pass one or more
 * filesystem overlay roots (typically the module's {@code src/main/resources}
 * directory) so shader edits are picked up without a rebuild. Production
 * builds pass no overlays — only the classpath is consulted.
 */
public final class ClasspathResourceLoader implements ResourceLoader {

    private final ClassLoader classLoader;
    private final List<Path> overlays;

    public ClasspathResourceLoader(ClassLoader classLoader, Path... overlays) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.overlays = List.copyOf(List.of(overlays));
    }

    public static ClasspathResourceLoader production() {
        return new ClasspathResourceLoader(ClasspathResourceLoader.class.getClassLoader());
    }

    @Override
    public String[] readLines(String path) throws IOException {
        try (InputStream in = openStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines.toArray(new String[0]);
        }
    }

    @Override
    public InputStream openStream(String path) {
        String stripped = stripLeading(path);
        for (Path root : overlays) {
            Path candidate = root.resolve(stripped);
            if (Files.exists(candidate)) {
                try {
                    return Files.newInputStream(candidate);
                } catch (IOException e) {
                    // fall through to classpath
                }
            }
        }
        return classLoader.getResourceAsStream(stripped);
    }

    private static String stripLeading(String path) {
        if (path == null) return "";
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
