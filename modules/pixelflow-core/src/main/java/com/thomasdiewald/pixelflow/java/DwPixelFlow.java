/**
 *
 * PixelFlow | Copyright (C) 2017 Thomas Diewald - www.thomasdiewald.com
 *
 * https://github.com/diwi/PixelFlow.git
 *
 * A Processing/Java library for high performance GPU-Computing.
 * MIT License: https://opensource.org/licenses/MIT
 *
 * --- Studio fork ---
 * Constructor decoupled from Processing/PApplet. Takes a JOGL GL2ES2 + a
 * ResourceLoader for shader/file resolution. Lifecycle is owned by the
 * GLEventListener that creates the context.
 */

package com.thomasdiewald.pixelflow.java;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLContext;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLFrameBuffer;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLRenderSettingsCallback;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLSLProgram;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTexture;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTexture3D;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLError;
import com.thomasdiewald.pixelflow.java.utils.DwUtils;

import studio.engine.RenderTarget;
import studio.engine.ResourceLoader;


public class DwPixelFlow {

  static public class PixelFlowInfo {
    static public final String version = "1.3.0";
    static public final String name    = "PixelFlow";
    static public final String author  = "Thomas Diewald";
    static public final String web     = "www.thomasdiewald.com";
    static public final String git     = "github.com/diwi/PixelFlow.git";

    public String toString() {
      return "[-] " + name + " v" + version + " - " + web;
    }
  }

  static public final PixelFlowInfo INFO = new PixelFlowInfo();

  public static final String SHADER_DIR = "/com/thomasdiewald/pixelflow/glsl/";

  /** The JOGL GL handle. Set in the constructor; current on the render thread for the
   *  lifetime of this context. */
  public final GL2ES2 gl;

  /** GLContext handle, cached for {@link #printGL()} and any vendor extension probes. */
  public final GLContext glContext;

  /** Resource loader for shaders and other classpath assets. Replaces
   *  {@code papplet.createInput / loadStrings} usage in upstream PixelFlow. */
  public final ResourceLoader resources;

  /** Math + ASCII-file helpers (gutted of PApplet-coupled methods in the studio fork). */
  public final DwUtils utils;

  /** Single shared framebuffer used by {@link #beginDraw(DwGLTexture...)} and friends. */
  public final DwGLFrameBuffer framebuffer = new DwGLFrameBuffer();

  private final HashMap<String, DwGLSLProgram> shader_cache = new HashMap<>();

  /** Objects with a {@code release()} method (or implementing {@link AutoCloseable}) that
   *  should be torn down when this context is disposed. Replaces upstream's
   *  {@code papplet.registerMethod("dispose", ...)} machinery. */
  private final List<Object> disposables = new ArrayList<>();

  private int scope_depth = 0;

  /**
   * Build a PixelFlow context against an already-current JOGL GL handle.
   * The caller (typically a {@code GLEventListener.init}) is responsible for
   * ensuring the GL context is current on the calling thread for the lifetime
   * of this object.
   */
  public DwPixelFlow(GL2ES2 gl, ResourceLoader resources) {
    this.gl = Objects.requireNonNull(gl, "gl");
    this.glContext = gl.getContext();
    this.resources = Objects.requireNonNull(resources, "resources");
    this.utils = new DwUtils(this);
    // Allocate the framebuffer eagerly; everything that follows needs it.
    framebuffer.allocate(gl);
  }


  public void dispose() {
    release();
  }


  public void release() {
    // Tear down user-registered disposables in reverse registration order.
    for (int i = disposables.size() - 1; i >= 0; i--) {
      tryRelease(disposables.get(i));
    }
    disposables.clear();

    for (String key : shader_cache.keySet()) {
      DwGLSLProgram shader = shader_cache.get(key);
      shader.release();
    }
    shader_cache.clear();

    framebuffer.release();
  }

  private static void tryRelease(Object o) {
    try {
      if (o instanceof AutoCloseable c) {
        c.close();
      } else {
        // Reflectively call release() if present — matches upstream contract
        // for primitives that exposed a release() method (DwFluid2D etc.).
        o.getClass().getMethod("release").invoke(o);
      }
    } catch (NoSuchMethodException ignored) {
      // No release() method — accept silently to match upstream behavior.
    } catch (ReflectiveOperationException | RuntimeException e) {
      System.err.println("DwPixelFlow.release: error disposing " + o.getClass().getName() + ": " + e);
    } catch (Exception e) {
      System.err.println("DwPixelFlow.release: error disposing " + o.getClass().getName() + ": " + e);
    }
  }

  /** Register an object to be released when this context is disposed. Replaces
   *  upstream's {@code papplet.registerMethod("dispose", this)} pattern. */
  public void registerDispose(Object o) {
    if (o != null) disposables.add(o);
  }


//  GLSL  |  OpenGL
// -------|---------
//  1.10  |   2.0
//  1.20  |   2.1
//  1.30  |   3.0
//  1.40  |   3.1
//  1.50  |   3.2
//  3.30  |   3.3
//  4.00  |   4.0
//  4.10  |   4.1
//  4.20  |   4.2
//  4.30  |   4.3
//  4.40  |   4.4

  /**
   * Begin a scope. In the studio fork this is purely scope-depth tracking —
   * the GL context is already current on the render thread.
   */
  public GL2ES2 begin() {
    scope_depth++;
    return gl;
  }

  public void end() {
    endDraw(); // just in case, a framebuffer is still bound
    scope_depth = Math.max(scope_depth - 1, 0);
  }

  public void end(String error_msg) {
    errorCheck(error_msg);
    end();
  }


  public boolean ACTIVE_FRAMEBUFFER = false;


  public void beginDraw(DwGLTexture ... dst) {
    ACTIVE_FRAMEBUFFER = true;
    framebuffer.bind(dst);
    defaultRenderSettings(0, 0, dst[0].w, dst[0].h);
  }


  public void beginDraw(DwGLTexture3D[] dst, int[] layer) {
    ACTIVE_FRAMEBUFFER = true;
    framebuffer.bind(dst, layer);
    defaultRenderSettings(0, 0, dst[0].w, dst[0].h);
  }

  public void beginDraw(DwGLTexture3D dst, int ... layer) {
    ACTIVE_FRAMEBUFFER = true;
    framebuffer.bind(dst, layer);
    defaultRenderSettings(0, 0, dst.w, dst.h);
  }

  public void beginDraw(DwGLTexture3D dst, int layer) {
    ACTIVE_FRAMEBUFFER = true;
    framebuffer.bind(new DwGLTexture3D[]{ dst }, new int[]{ layer });
    defaultRenderSettings(0, 0, dst.w, dst.h);
  }


  /**
   * Bind a generic studio {@link RenderTarget} as the draw destination.
   * Replaces upstream's {@code beginDraw(PGraphicsOpenGL)} overloads which
   * pulled Processing's internal {@code FrameBuffer} out of a PGraphics.
   */
  public void beginDraw(RenderTarget dst) {
    ACTIVE_FRAMEBUFFER = true;
    dst.bindAsRenderTarget(gl);
    defaultRenderSettings(0, 0, dst.getWidth(), dst.getHeight());
  }

  public void endDraw() {
    if (ACTIVE_FRAMEBUFFER) {
      if (framebuffer != null && framebuffer.isActive()) {
        framebuffer.unbind();
      } else {
        gl.glBindFramebuffer(GL2ES2.GL_FRAMEBUFFER, 0);
      }
    }
    ACTIVE_FRAMEBUFFER = false;
  }

  public void endDraw(String error_msg) {
    endDraw();
    errorCheck(error_msg);
  }


  public void defaultRenderSettings(int x, int y, int w, int h) {
    rendersettings_default.set(this, 0, 0, w, h);
    if (!rendersettings_user.isEmpty()) {
      rendersettings_user.peek().set(this, 0, 0, w, h);
    }
  }

  private static class DefaultRenderSettings implements DwGLRenderSettingsCallback {
    @Override
    public void set(DwPixelFlow context, int x, int y, int w, int h) {
      GL2ES2 gl = context.gl;
      gl.glViewport(x, y, w, h);
      gl.glColorMask(true, true, true, true);
      gl.glDepthMask(false);
      gl.glDisable(GL.GL_DEPTH_TEST);
      gl.glDisable(GL.GL_SCISSOR_TEST);
      gl.glDisable(GL.GL_STENCIL_TEST);
      gl.glDisable(GL.GL_BLEND);
      gl.glDisable(GL.GL_MULTISAMPLE);
    }
  }

  final private DwGLRenderSettingsCallback rendersettings_default = new DefaultRenderSettings();

  Stack<DwGLRenderSettingsCallback> rendersettings_user = new Stack<>();

  public void pushRenderSettings(DwGLRenderSettingsCallback rendersettings) {
    rendersettings_user.push(rendersettings);
  }

  public void popRenderSettings() {
    rendersettings_user.pop();
  }


  //////////////////////////////////////////////////////////////////////////////
  //
  // SHADER FACTORY
  //
  //////////////////////////////////////////////////////////////////////////////

  public DwGLSLProgram createShader(String path_fragmentshader) {
    return createShader((Object) null, path_fragmentshader);
  }

  public DwGLSLProgram createShader(Object o, String path_fragmentshader) {
    return createShader(o, null, path_fragmentshader);
  }

  public DwGLSLProgram createShader(String path_vertexshader, String path_fragmentshader) {
    return createShader((Object) null, path_vertexshader, path_fragmentshader);
  }

  public DwGLSLProgram createShader(Object o, String path_vertexshader, String path_fragmentshader) {
    String key = "";
    if (o != null) key += "[" + o.hashCode() + "]";
    if (path_vertexshader != null) key += path_vertexshader + "[]";
    key += path_fragmentshader;

    DwGLSLProgram shader = shader_cache.get(key);
    if (shader == null) {
      shader = new DwGLSLProgram(this, path_vertexshader, path_fragmentshader);
      shader_cache.put(key, shader);
    }
    return shader;
  }


  /**
   * Read the GL texture id attached as color-0 of the target's framebuffer.
   * Replaces the upstream PGraphicsOpenGL-typed variant. For RenderTargets
   * we already know the texture id directly, but the method is preserved so
   * callers don't need to special-case.
   */
  public void getGLTextureHandle(RenderTarget pg, int[] tex_handle) {
    tex_handle[0] = pg.getGLTextureId();
  }


  public void errorCheck(String msg) {
    DwGLError.debug(gl, msg);
  }


  //////////////////////////////////////////////////////////////////////////////
  //
  // GL/GLSL Info
  //
  //////////////////////////////////////////////////////////////////////////////

  public void printGL() {
    String opengl_renderer   = gl.glGetString(GL2ES2.GL_RENDERER).trim();
    String GLSLVersionString = glContext.getGLSLVersionString().trim();
    String GLSLVersionNumber = glContext.getGLSLVersionNumber() + "";
    String GLVersion         = glContext.getGLVersion().trim();

    System.out.println();
    System.out.println("[-] DEVICE ... " + opengl_renderer);
    System.out.println("[-] GLSL ..... " + GLSLVersionString + " / " + GLSLVersionNumber);
    System.out.println("[-] GL ....... " + GLVersion);
    System.out.println();
  }


  public void printGL_Extensions() {
    String gl_extensions = gl.glGetString(GL2ES2.GL_EXTENSIONS).trim();
    String[] list = gl_extensions.split(" ");

    System.out.println();
    System.out.printf("[-] %d extensions%n", list.length);
    for (int i = 0; i < list.length; i++) {
      System.out.printf("  [-] %d - %s%n", i, list[i].trim());
    }
    System.out.println();
  }


  public void print() {
    System.out.println(INFO);
  }


  // https://www.khronos.org/registry/OpenGL/extensions/NVX/NVX_gpu_memory_info.txt
  static public final String  GL_NVX_gpu_memory_info                       = "GL_NVX_gpu_memory_info";
  static public final int     GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX         = 0x9047;
  static public final int     GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX   = 0x9048;
  static public final int     GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
  static public final int     GPU_MEMORY_INFO_EVICTION_COUNT_NVX           = 0x904A;
  static public final int     GPU_MEMORY_INFO_EVICTED_MEMORY_NVX           = 0x904B;

  // https://www.khronos.org/registry/OpenGL/extensions/ATI/ATI_meminfo.txt
  static public final String  ATI_meminfo                  = "ATI_meminfo";
  static public final int     VBO_FREE_MEMORY_ATI          = 0x87FB;
  static public final int     TEXTURE_FREE_MEMORY_ATI      = 0x87FC;
  static public final int     RENDERBUFFER_FREE_MEMORY_ATI = 0x87FD;


  static public boolean       VAL_GL_NVX_gpu_memory_info                       = false;
  static public int[]         VAL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX         = {0};
  static public int[]         VAL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX   = {0};
  static public int[]         VAL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = {0};
  static public int[]         VAL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX           = {0};
  static public int[]         VAL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX           = {0};

  static public boolean       VAL_ATI_meminfo                  = false;
  static public int[]         VAL_VBO_FREE_MEMORY_ATI          = {0,0,0,0};
  static public int[]         VAL_TEXTURE_FREE_MEMORY_ATI      = {0,0,0,0};
  static public int[]         VAL_RENDERBUFFER_FREE_MEMORY_ATI = {0,0,0,0};


  public void updateGL_MemoryInfo() {
    VAL_GL_NVX_gpu_memory_info = gl.isExtensionAvailable(GL_NVX_gpu_memory_info);
    if (VAL_GL_NVX_gpu_memory_info) {
      gl.glGetIntegerv(GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX        , VAL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX        , 0);
      gl.glGetIntegerv(GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX  , VAL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX  , 0);
      gl.glGetIntegerv(GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX, VAL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX, 0);
      gl.glGetIntegerv(GPU_MEMORY_INFO_EVICTION_COUNT_NVX          , VAL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX          , 0);
      gl.glGetIntegerv(GPU_MEMORY_INFO_EVICTED_MEMORY_NVX          , VAL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX          , 0);
    }

    VAL_ATI_meminfo = gl.isExtensionAvailable(ATI_meminfo);
    if (VAL_ATI_meminfo) {
      gl.glGetIntegerv(VBO_FREE_MEMORY_ATI         , VAL_VBO_FREE_MEMORY_ATI         , 0);
      gl.glGetIntegerv(TEXTURE_FREE_MEMORY_ATI     , VAL_TEXTURE_FREE_MEMORY_ATI     , 0);
      gl.glGetIntegerv(RENDERBUFFER_FREE_MEMORY_ATI, VAL_RENDERBUFFER_FREE_MEMORY_ATI, 0);
    }
  }


  public void printGL_MemoryInfo() {
    updateGL_MemoryInfo();

    if (VAL_GL_NVX_gpu_memory_info) {
      System.out.printf("[-] %s%n", GL_NVX_gpu_memory_info);
      System.out.printf("  [-] GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX         (MB) = %d%n", (VAL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX        [0] >> 10));
      System.out.printf("  [-] GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX   (MB) = %d%n", (VAL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX  [0] >> 10));
      System.out.printf("  [-] GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX (MB) = %d%n", (VAL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX[0] >> 10));
      System.out.printf("  [-] GPU_MEMORY_INFO_EVICTION_COUNT_NVX                = %d%n", (VAL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX          [0]      ));
      System.out.printf("  [-] GPU_MEMORY_INFO_EVICTED_MEMORY_NVX           (MB) = %d%n", (VAL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX          [0] >> 10));
      System.out.printf("%n");
    }

    if (VAL_ATI_meminfo) {
      System.out.printf("[-] %s%n", ATI_meminfo);
      System.out.printf("  [-] VBO_FREE_MEMORY_ATI          (MB) = %d%n", (VAL_VBO_FREE_MEMORY_ATI         [0] >> 10));
      System.out.printf("  [-] TEXTURE_FREE_MEMORY_ATI      (MB) = %d%n", (VAL_TEXTURE_FREE_MEMORY_ATI     [0] >> 10));
      System.out.printf("  [-] RENDERBUFFER_FREE_MEMORY_ATI (MB) = %d%n", (VAL_RENDERBUFFER_FREE_MEMORY_ATI[0] >> 10));
    }
  }
}
