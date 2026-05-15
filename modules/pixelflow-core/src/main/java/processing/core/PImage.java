package processing.core;

/**
 * Minimal value-type stub for Processing's {@code PImage} — exposes
 * {@code width}, {@code height}, {@code pixels} and {@code format} fields
 * which the forked PixelFlow source touches without ever calling
 * Processing's image rendering pipeline. Value-type compatibility shim, not
 * affiliated with the Processing Foundation.
 */
public class PImage implements PConstants {

    public int width;
    public int height;
    public int[] pixels;
    public int format = ARGB;

    public PImage() {}

    public PImage(int width, int height) {
        this(width, height, ARGB);
    }

    public PImage(int width, int height, int format) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.pixels = new int[width * height];
    }

    public void loadPixels() {
        if (pixels == null) pixels = new int[width * height];
    }

    public void updatePixels() {
        // no-op in shim
    }

    public int get(int x, int y) {
        if (pixels == null || x < 0 || y < 0 || x >= width || y >= height) return 0;
        return pixels[y * width + x];
    }

    public void set(int x, int y, int argb) {
        if (pixels == null || x < 0 || y < 0 || x >= width || y >= height) return;
        pixels[y * width + x] = argb;
    }
}
