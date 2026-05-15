/**
 * 
 * PixelFlow | Copyright (C) 2016 Thomas Diewald - http://thomasdiewald.com
 * 
 * A Processing/Java library for high performance GPU-Computing (GLSL).
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */


package com.thomasdiewald.pixelflow.java.imageprocessing.filter;


import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLSLProgram;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTexture;

import studio.engine.RenderTarget;

public class Clamp {
  
  public DwPixelFlow context;
  
  public DwGLSLProgram shader;
  
  public float[] lo = {0,0,0,0};
  public float[] hi = {1,1,1,1};
  
  public Clamp(DwPixelFlow context){
    this.context = context;
  }
  
  
  public void apply(RenderTarget src, RenderTarget dst) {
    apply(src, dst, lo, hi);
  }
  
  public void apply(RenderTarget src, DwGLTexture dst) {
    apply(src, dst, lo, hi);
  }
  
  public void apply(DwGLTexture src, DwGLTexture dst) {
    apply(src, dst, lo, hi);
  }
  
  
  public void apply(RenderTarget src, RenderTarget dst, float[] lo, float[] hi) {
    if(!src.isSampleable()) return;
    if(!dst.isSampleable()) return;

    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.getWidth(), dst.getHeight(), lo, hi);
    context.endDraw();
    context.end("Clamp.apply");
  }
  
  public void apply(RenderTarget src, DwGLTexture dst, float[] lo, float[] hi) {
    if (!src.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.w, dst.h, lo, hi);
    context.endDraw();
    context.end("Clamp.apply");
  }
  
  
  public void apply(DwGLTexture src, DwGLTexture dst, float[] lo, float[] hi) {
    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.w, dst.h, lo, hi);
    context.endDraw();
    context.end("Clamp.apply");
  }
  

  public void apply(int tex_handle, int w, int h, float[] lo, float[] hi){
    if(shader == null) shader = context.createShader(DwPixelFlow.SHADER_DIR+"Filter/clamp.frag");
    shader.begin();
    shader.uniform2f     ("wh_rcp" , 1f/w, 1f/h);
    shader.uniform4fv    ("lo", 1, lo);
    shader.uniform4fv    ("hi", 1, hi);
    shader.uniformTexture("tex", tex_handle);
    shader.drawFullScreenQuad();
    shader.end();
  }
  
  
}
