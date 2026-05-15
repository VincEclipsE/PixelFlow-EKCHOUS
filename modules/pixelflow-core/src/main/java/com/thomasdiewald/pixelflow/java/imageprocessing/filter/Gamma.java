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

public class Gamma {
  
  float gamma = 2.2f;
  
  public DwPixelFlow context;

  public Gamma(DwPixelFlow context){
    this.context = context;
  }
  
  public void apply(RenderTarget src, RenderTarget dst, float gamma) {
    if(!src.isSampleable()) return;
    if(!dst.isSampleable()) return;
    
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(),dst.getWidth(), dst.getHeight(), gamma);
    context.endDraw();
    context.end("GammaCorrection.apply");
  }
  
  public void apply(RenderTarget src_dst) {
    apply(src_dst, src_dst, gamma);
  }
  
  public void apply(RenderTarget src, RenderTarget dst) {
    apply(src, dst, gamma);
  }
  
  public void apply(DwGLTexture src, DwGLTexture dst, float gamma) {
    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.w, dst.h, gamma);
    context.endDraw();
    context.end("GammaCorrection.apply");
  }
  
  public void apply(DwGLTexture src_dst) {
    apply(src_dst, src_dst, gamma);
  }
  
  public void apply(DwGLTexture src, DwGLTexture dst) {
    apply(src, dst, gamma);
  }
  
  DwGLSLProgram shader;
  private void apply(int tex_handle, int w, int h, float gamma){
    if(shader == null) shader = context.createShader(this, DwPixelFlow.SHADER_DIR+"Filter/gamma.frag");
    shader.begin();
    shader.uniform2f     ("wh_rcp" , 1f/w, 1f/h);
    shader.uniform1f     ("gamma", gamma);
    shader.uniformTexture("tex", tex_handle);
    shader.drawFullScreenQuad();
    shader.end("GammaCorrection.apply shader");
  }
  
}
