package studio.engine;

import java.io.IOException;
import java.io.InputStream;

/**
 * Replaces PixelFlow's {@code DwUtils.createInputStream} ladder that mixed
 * filesystem, classloader, and {@code papplet.createInput} fallbacks. The
 * studio version is two-step: optional filesystem overlays (used for shader
 * hot-reload in dev) then classpath.
 *
 * <p>Implementations must be thread-safe — shaders may be loaded from the GL
 * render thread.
 */
public interface ResourceLoader {

    /**
     * Read a UTF-8 text resource as an array of lines (no trailing newline).
     *
     * @throws IOException if the resource is not found or unreadable
     */
    String[] readLines(String path) throws IOException;

    /**
     * Open a binary stream for a resource.
     *
     * @return {@code null} if the resource is not found (used by shader
     *         {@code #include} resolution; callers must handle null)
     */
    InputStream openStream(String path);
}
