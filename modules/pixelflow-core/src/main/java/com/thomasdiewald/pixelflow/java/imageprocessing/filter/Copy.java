/**
 *
 * PixelFlow | Copyright (C) 2016 Thomas Diewald - http://thomasdiewald.com
 *
 * A Processing/Java library for high performance GPU-Computing (GLSL).
 * MIT License: https://opensource.org/licenses/MIT
 *
 * --- Studio fork ---
 * PGraphicsOpenGL parameters rewritten to studio.engine.RenderTarget.
 * The (PImage, DwGLTexture) overload that used PImage.parent.g is dropped —
 * the studio fork has no Processing canvas to source a Texture from.
 */

package com.thomasdiewald.pixelflow.java.imageprocessing.filter;

import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLSLProgram;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTexture;

import studio.engine.RenderTarget;

public class Copy {

  public DwPixelFlow context;

  public DwGLSLProgram shader;

  public Copy(DwPixelFlow context) {
    this.context = context;
    this.shader = context.createShader(this, DwPixelFlow.SHADER_DIR + "Filter/copy.frag");
  }

  public void apply(RenderTarget src, RenderTarget dst) {
    if (!src.isSampleable()) return;
    if (!dst.isSampleable()) return;

    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.getWidth(), dst.getHeight());
    context.endDraw();
    context.end("Copy.apply");
  }

  public void apply(RenderTarget src, DwGLTexture dst) {
    if (!src.isSampleable()) return;

    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.w, dst.h);
    context.endDraw();
    context.end("Copy.apply");
  }

  public void apply(DwGLTexture src, RenderTarget dst) {
    if (!dst.isSampleable()) return;

    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.getWidth(), dst.getHeight());
    context.endDraw();
    context.end("Copy.apply");
  }

  public void apply(DwGLTexture src, DwGLTexture dst) {
    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.w, dst.h);
    context.endDraw();
    context.end("Copy.apply");
  }

  private void apply(int tex_handle, int w, int h) {
    shader.begin();
    shader.uniform2f     ("wh_rcp", 1f / w, 1f / h);
    shader.uniformTexture("tex"   , tex_handle);
    shader.drawFullScreenQuad();
    shader.end();
  }
}
