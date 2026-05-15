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

public class Mad {
  
  public DwPixelFlow context;
  
  public Mad(DwPixelFlow context){
    this.context = context;
  }
  
  
  public void apply(RenderTarget src, RenderTarget dst, float[] mad) {
    if(!src.isSampleable()) return;
    if(!dst.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.getWidth(), dst.getHeight(), mad);
    context.endDraw();
    context.end("Mad.apply");
  }
  
  public void apply(RenderTarget src, DwGLTexture dst, float[] mad) {
    if (!src.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.w, dst.h, mad);
    context.endDraw();
    context.end("Mad.apply");
  }
  
  public void apply(DwGLTexture src, RenderTarget dst, float[] mad) {
    if (!dst.isSampleable()) return;

    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.getWidth(), dst.getHeight(), mad);
    context.endDraw();
    context.end("Mad.apply");
  }
  
  
  public void apply(DwGLTexture src, DwGLTexture dst, float[] mad) {
    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.w, dst.h, mad);
    context.endDraw();
    context.end("Mad.apply");
  }
  
  DwGLSLProgram shader;
  public void apply(int tex_handle, int w, int h, float[] mad){
    if(shader == null) shader = context.createShader(DwPixelFlow.SHADER_DIR+"Filter/mad.frag");
    shader.begin();
    shader.uniform2f     ("wh_rcp", 1f/w, 1f/h);
    shader.uniformTexture("tex", tex_handle);
    shader.uniform2f    ("mad", mad[0], mad[1]);
    shader.drawFullScreenQuad();
    shader.end();
  }
  
  
  
  
  
  
  
  
  
  
  
  
}
