/**
 *
 * PixelFlow | Copyright (C) 2016 Thomas Diewald - http://thomasdiewald.com
 *
 * A Processing/Java library for high performance GPU-Computing (GLSL).
 * MIT License: https://opensource.org/licenses/MIT
 *
 * --- Studio fork ---
 * Math + ASCII helpers from upstream preserved. PApplet/PGraphics-coupled
 * texture and matrix helpers stubbed (throw at runtime) because the studio
 * doesn't use a Processing canvas. Same method signatures are preserved so
 * call sites compile; the stubs throw a clear message if reached.
 */

package com.thomasdiewald.pixelflow.java.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PVector;
import processing.opengl.PGraphics2D;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;


public class DwUtils {

  public static final String NL = System.getProperty("line.separator");

  public static final double _1_DIV_3 = 1.0 / 3.0;
  public static final float SQRT2 = (float) Math.sqrt(2);

  DwPixelFlow context;

  public DwUtils(DwPixelFlow context) {
    this.context = context;
  }


  //////////////////////////////////////////////////////////////////////////////
  // Math, Color
  //////////////////////////////////////////////////////////////////////////////

  public static int logNceil(double val, double n) {
    return (int) Math.ceil(Math.log(val) / Math.log(n));
  }

  public static int log2ceil(double val) {
    return (int) Math.ceil(Math.log(val) / Math.log(2));
  }

  public static int log4ceil(double val) {
    return (int) Math.ceil(Math.log(val) / Math.log(4));
  }

  public static float mix(float a, float b, float t) {
    return a * (1f - t) + b * t;
  }

  public static float[] mix(float[] a4, float[] b4, float t, float[] dst4) {
    if (dst4 == null || dst4.length < 4) dst4 = new float[4];
    dst4[0] = mix(a4[0], b4[0], t);
    dst4[1] = mix(a4[1], b4[1], t);
    dst4[2] = mix(a4[2], b4[2], t);
    dst4[3] = mix(a4[3], b4[3], t);
    return dst4;
  }

  private static final float[] CL = new float[4];
  private static final float[] CR = new float[4];

  public static float[] mixBilinear(float[] TL, float[] BL, float[] TR, float[] BR,
                                    float mix_LR, float mix_TB, float[] CC) {
    if (CC == null || CC.length < 4) CC = new float[4];
    mix(TL, BL, mix_TB, CL);
    mix(TR, BR, mix_TB, CR);
    mix(CL, CR, mix_LR, CC);
    return CC;
  }

  public static void mult(float[] argb, float m) {
    argb[0] *= m; argb[1] *= m; argb[2] *= m; argb[3] *= m;
  }

  public static double clamp(double a, double lo, double hi) {
    return a < lo ? lo : (a > hi ? hi : a);
  }

  public static float clamp(float a, float lo, float hi) {
    return a < lo ? lo : (a > hi ? hi : a);
  }

  public static int clamp(int a, int lo, int hi) {
    return a < lo ? lo : (a > hi ? hi : a);
  }

  public static void clamp(float[] argb, float lo, float hi) {
    argb[0] = clamp(argb[0], lo, hi);
    argb[1] = clamp(argb[1], lo, hi);
    argb[2] = clamp(argb[2], lo, hi);
    argb[3] = clamp(argb[3], lo, hi);
  }

  public static float smoothstep(float edge0, float edge1, float x) {
    x = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
    return smoothstep(x);
  }

  public static double smoothstep(double edge0, double edge1, double x) {
    x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return smoothstep(x);
  }

  public static float smootherstep(float edge0, float edge1, float x) {
    x = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
    return smootherstep(x);
  }

  public static double smootherstep(double edge0, double edge1, double x) {
    x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return smootherstep(x);
  }

  public static float smoothstep(float x)  { return x * x * (3 - 2 * x); }
  public static double smoothstep(double x){ return x * x * (3 - 2 * x); }
  public static float smootherstep(float x){ return x * x * x * (x * (x * 6 - 15) + 10); }
  public static double smootherstep(double x){ return x * x * x * (x * (x * 6 - 15) + 10); }

  public static float[] getColor(float[][] palette, float val_norm, float[] rgb) {
    if (rgb == null || rgb.length < 3) rgb = new float[3];
    val_norm = clamp(val_norm, 0, 1);
    int idx_max = palette.length - 1;
    float val_map = val_norm * idx_max;
    int idx = (int) val_map;
    float frac = val_map - idx;
    if (idx == idx_max) {
      rgb[0] = palette[idx][0]; rgb[1] = palette[idx][1]; rgb[2] = palette[idx][2];
    } else {
      rgb[0] = mix(palette[idx][0], palette[idx + 1][0], frac);
      rgb[1] = mix(palette[idx][1], palette[idx + 1][1], frac);
      rgb[2] = mix(palette[idx][2], palette[idx + 1][2], frac);
    }
    return rgb;
  }

  public static float[] COL_TL = { 64,   0,   0, 255 };
  public static float[] COL_TR = { 255, 128,   0, 255 };
  public static float[] COL_BL = {   0, 128, 255, 255 };
  public static float[] COL_BR = { 255, 255, 255, 255 };
  public static float[] COL_CC = {   0,   0,   0,   0 };


  //////////////////////////////////////////////////////////////////////////////
  // Texture generators — PApplet/PGraphics-coupled; signatures preserved for
  // callers, bodies throw at runtime. The studio uses DwGLTexture sources.
  //////////////////////////////////////////////////////////////////////////////

  public static PImage createSprite(PApplet papplet, int size, float exp1, float exp2, float mult) {
    throw stubbed("createSprite");
  }

  public static PGraphics2D createCheckerBoard(PApplet papplet, int dimx, int dimy, int size, int colA, int colB) {
    throw stubbed("createCheckerBoard");
  }

  public static PGraphics2D createBackgroundNoiseTexture(PApplet papplet, int dimx, int dimy) {
    throw stubbed("createBackgroundNoiseTexture");
  }


  //////////////////////////////////////////////////////////////////////////////
  // File IO — real, simplified
  //////////////////////////////////////////////////////////////////////////////

  public String[] readASCIIfile(InputStream inputstream) {
    if (inputstream == null) return null;
    int num_lines = 0;
    String[] lines = new String[2048];
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputstream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (num_lines == lines.length) lines = Arrays.copyOf(lines, num_lines << 1);
        lines[num_lines++] = line;
      }
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    return Arrays.copyOf(lines, num_lines);
  }

  /**
   * Resolve a resource to a stream. Tries filesystem first (useful for dev
   * hot-reload of shaders), then the classpath. Drops the upstream's
   * PApplet-based fallbacks since the studio fork has no PApplet.
   */
  public InputStream createInputStream(String path) {
    if (path == null) return null;

    File file = new File(path);
    if (file.exists()) {
      try {
        return new FileInputStream(file);
      } catch (FileNotFoundException e) {
        // fall through
      }
    }

    URL url = DwUtils.class.getClassLoader().getResource(path);
    if (url != null) {
      return DwUtils.class.getClassLoader().getResourceAsStream(path);
    }

    // Tolerate a leading slash on classpath-style paths.
    String stripped = path.startsWith("/") ? path.substring(1) : path;
    if (!stripped.equals(path)) {
      InputStream s = DwUtils.class.getClassLoader().getResourceAsStream(stripped);
      if (s != null) return s;
    }

    // Last resort: ask the studio's resource loader if available.
    if (context != null && context.resources != null) {
      return context.resources.openStream(path);
    }
    return null;
  }

  public String[] readASCIIfile(String path) {
    return readASCIIfile(createInputStream(path));
  }

  public String[] readASCIIfileNL(String path) {
    String[] lines = readASCIIfile(path);
    if (lines == null) return null;
    for (int i = 0; i < lines.length; i++) lines[i] += NL;
    return lines;
  }


  //////////////////////////////////////////////////////////////////////////////
  // Texture-resize / format helpers — PGraphics-coupled. Stubbed.
  //////////////////////////////////////////////////////////////////////////////

  public static PGraphics2D changeTextureSize(PApplet papplet, PGraphics2D pg, int w, int h, int smooth, boolean[] resized) {
    throw stubbed("changeTextureSize(PApplet, PGraphics2D, ...)");
  }

  public static PGraphics2D changeTextureSize(PApplet papplet, PGraphics2D pg, int w, int h, int smooth, boolean[] resized,
                                              int internal_format, int format, int type) {
    throw stubbed("changeTextureSize(PApplet, PGraphics2D, ..., format)");
  }

  public static PGraphics3D changeTextureSize(PApplet papplet, PGraphics3D pg, int w, int h, int smooth, boolean[] resized) {
    throw stubbed("changeTextureSize(PApplet, PGraphics3D, ...)");
  }

  public static PGraphics3D changeTextureSize(PApplet papplet, PGraphics3D pg, int w, int h, int smooth, boolean[] resized,
                                              int internal_format, int format, int type) {
    throw stubbed("changeTextureSize(PApplet, PGraphics3D, ..., format)");
  }

  public static boolean changeTextureSize(PGraphicsOpenGL pg, int w, int h, int internal_format, int format, int type) {
    throw stubbed("changeTextureSize(PGraphicsOpenGL, ..., format)");
  }

  public static boolean changeTextureSize(PGraphicsOpenGL pg, int w, int h) {
    throw stubbed("changeTextureSize(PGraphicsOpenGL, ...)");
  }

  public static void changeTextureFormat(PGraphicsOpenGL pg, int internal_format, int format, int type) {
    throw stubbed("changeTextureFormat");
  }

  public static void changeTextureFormat(PGraphicsOpenGL pg, int internal_format, int format, int type, int filter) {
    throw stubbed("changeTextureFormat");
  }

  public static void changeTextureFormat(PGraphicsOpenGL pg, int internal_format, int format, int type, int filter, int wrap) {
    throw stubbed("changeTextureFormat");
  }

  public static void changeTextureWrap(PGraphicsOpenGL pg, int wrap) {
    throw stubbed("changeTextureWrap");
  }

  public static void changeTextureFilter(PGraphicsOpenGL pg, int min_filter, int mag_filter) {
    throw stubbed("changeTextureFilter");
  }

  public static void changeTextureParam(PGraphicsOpenGL pg, int pname, int param) {
    throw stubbed("changeTextureParam");
  }

  public static void generateMipMaps(PGraphicsOpenGL pg) {
    throw stubbed("generateMipMaps");
  }


  //////////////////////////////////////////////////////////////////////////////
  // Matrix / camera helpers — PGraphics-coupled. Stubbed.
  //////////////////////////////////////////////////////////////////////////////

  public static void copyMatrices(PGraphicsOpenGL src, PGraphicsOpenGL dst) {
    throw stubbed("copyMatrices");
  }

  public static void setLookAt(PGraphicsOpenGL dst, float[] eye, float[] center, float[] up) {
    throw stubbed("setLookAt");
  }

  public static void setLookAt(PGraphicsOpenGL dst, PVector eye, PVector center, PVector up) {
    throw stubbed("setLookAt");
  }

  public static void swap(Object[] obj) {
    Object tmp = obj[0]; obj[0] = obj[1]; obj[1] = tmp;
  }

  public static void beginScreen2D(PGraphics pg) {
    throw stubbed("beginScreen2D");
  }

  public static void endScreen2D(PGraphics pg) {
    throw stubbed("endScreen2D");
  }


  private static UnsupportedOperationException stubbed(String method) {
    return new UnsupportedOperationException(
        "DwUtils." + method + " is stubbed in the studio fork — caller needs a "
            + "DwGLTexture / studio.engine.RenderTarget-based replacement.");
  }
}
