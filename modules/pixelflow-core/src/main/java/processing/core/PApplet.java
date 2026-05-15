package processing.core;

import java.io.InputStream;

/**
 * Compatibility stub for Processing's {@code PApplet}. Source code in the
 * forked PixelFlow library that still references PApplet compiles against
 * this stub; ALL method calls throw {@link UnsupportedOperationException}
 * at runtime.
 *
 * <p>The studio's new code path does not construct PApplet — only the
 * upstream library's helper methods that were never rewritten still
 * reference it. Any runtime call here means we missed a rewrite site;
 * fix the caller, not this stub.
 */
public class PApplet implements PConstants {

    public int width;
    public int height;
    public PGraphics g;

    public PApplet() {}

    public void registerMethod(String when, Object target) { throwUnsupported(); }

    public Object beginPGL() { return throwUnsupported(); }
    public void endPGL() { throwUnsupported(); }

    public InputStream createInput(String filename) { return (InputStream) throwUnsupported(); }
    public String[] loadStrings(String filename) { return (String[]) throwUnsupported(); }

    public PGraphics createGraphics(int w, int h) { return (PGraphics) throwUnsupported(); }
    public PGraphics createGraphics(int w, int h, String renderer) { return (PGraphics) throwUnsupported(); }
    public PImage createImage(int w, int h, int format) { return (PImage) throwUnsupported(); }

    public PShape createShape() { return new PShape(); }
    public PShape createShape(int kind) { return new PShape(kind); }
    public PShape createShape(int kind, float... params) { return new PShape(kind); }

    public PImage loadImage(String filename) { return (PImage) throwUnsupported(); }
    public PImage loadImage(String filename, String extension) { return (PImage) throwUnsupported(); }

    public float noise(float x) { return 0; }
    public float noise(float x, float y) { return 0; }
    public float noise(float x, float y, float z) { return 0; }
    public float random(float n) { return 0; }
    public float random(float lo, float hi) { return 0; }

    public int color(int gray) { return gray; }
    public int color(float r, float g, float b) { return 0xFF000000; }
    public int color(float r, float g, float b, float a) { return 0; }

    public void colorMode(int mode) {}
    public void colorMode(int mode, float max) {}

    // Static helpers ported from upstream PApplet
    public static float constrain(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    public static int   constrain(int v, int lo, int hi)       { return v < lo ? lo : (v > hi ? hi : v); }
    public static float map(float v, float a0, float a1, float b0, float b1) { return b0 + (v - a0) * (b1 - b0) / (a1 - a0); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static float sq(float x) { return x * x; }
    public static float min(float a, float b) { return Math.min(a, b); }
    public static float max(float a, float b) { return Math.max(a, b); }
    public static int   min(int a, int b) { return Math.min(a, b); }
    public static int   max(int a, int b) { return Math.max(a, b); }
    public static float abs(float a) { return Math.abs(a); }
    public static int   abs(int a) { return Math.abs(a); }
    public static float sqrt(float a) { return (float) Math.sqrt(a); }
    public static float floor(float a) { return (float) Math.floor(a); }
    public static int   floor(double a) { return (int) Math.floor(a); }
    public static float ceil(float a) { return (float) Math.ceil(a); }
    public static int   ceil(double a) { return (int) Math.ceil(a); }
    public static float round(float a) { return Math.round(a); }
    public static int   round(double a) { return (int) Math.round(a); }
    public static float pow(float a, float b) { return (float) Math.pow(a, b); }
    public static float sin(float a) { return (float) Math.sin(a); }
    public static float cos(float a) { return (float) Math.cos(a); }
    public static float tan(float a) { return (float) Math.tan(a); }
    public static float atan2(float a, float b) { return (float) Math.atan2(a, b); }
    public static float radians(float deg) { return deg * DEG_TO_RAD; }
    public static float degrees(float rad) { return rad * RAD_TO_DEG; }
    public static int   parseInt(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }
    public static float parseFloat(String s) { try { return Float.parseFloat(s); } catch (NumberFormatException e) { return Float.NaN; } }
    public static void  println(Object o) { System.out.println(o); }
    public static void  println(int i) { System.out.println(i); }
    public static void  println(float f) { System.out.println(f); }

    private static Object throwUnsupported() {
        throw new UnsupportedOperationException(
                "PApplet stub method called at runtime — caller needs to be rewritten to use studio.engine APIs.");
    }
}
