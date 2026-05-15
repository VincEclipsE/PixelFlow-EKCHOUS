package studio.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One-shot copy of bundled samples from the classpath into the user's tools
 * directory. Runs on every startup but is idempotent — the bundled file is
 * only copied when the destination is missing, so user edits persist.
 */
final class AppDataInstaller {

    private static final String[] BUNDLED_SAMPLES = {
        "sample-soft-edges.pftool",
    };

    private AppDataInstaller() {}

    static void ensureBundledSamplesInstalled(Path toolsDir) {
        try {
            Files.createDirectories(toolsDir);
        } catch (IOException ex) {
            System.err.println("Could not create tools dir " + toolsDir + ": " + ex.getMessage());
            return;
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String name : BUNDLED_SAMPLES) {
            Path dest = toolsDir.resolve(name);
            if (Files.exists(dest)) continue;
            try (InputStream in = cl.getResourceAsStream("tools/" + name)) {
                if (in == null) continue;
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("Could not install bundled sample " + name + ": " + ex.getMessage());
            }
        }
    }
}
