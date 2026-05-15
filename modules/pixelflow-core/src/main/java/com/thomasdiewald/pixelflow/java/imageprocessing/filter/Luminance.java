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

public class Luminance {
  
  public DwPixelFlow context;
  
  public float[] luminance = {0.2989f, 0.5870f, 0.1140f};
//  public float[] luminance = {0.2126f, 0.7152f, 0.0722f};
//  public float[] luminance = {0.3333f, 0.3333f, 0.3333f}; // rgb average
  
  public Luminance(DwPixelFlow context){
    this.context = context;
  }
  
  public void apply(RenderTarget src, RenderTarget dst) {
    if(!src.isSampleable()) return;
    if(!dst.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.getWidth(), dst.getHeight());
    context.endDraw();
    context.end("Luminance.apply");
  }
  
  public void apply(RenderTarget src, DwGLTexture dst) {
    if (!src.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.w, dst.h);
    context.endDraw();
    context.end("Luminance.apply");
  }
  
  
  public void apply(DwGLTexture src, DwGLTexture dst) {
    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.w, dst.h);
    context.endDraw();
    context.end("Luminance.apply");
  }
  
  DwGLSLProgram shader;
  public void apply(int tex_handle, int w, int h){
    if(shader == null) shader = context.createShader(DwPixelFlow.SHADER_DIR+"Filter/luminance.frag");
    shader.begin();
    shader.uniform2f     ("wh_rcp", 1f/w, 1f/h);
    shader.uniformTexture("tex", tex_handle);
    shader.uniform3fv("luminance", 1, luminance);
    shader.drawFullScreenQuad();
    shader.end();
  }
  
  
}
