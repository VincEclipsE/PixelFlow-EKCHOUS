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

public class Convolution {
  
  public DwPixelFlow context;

  public Convolution(DwPixelFlow context){
    this.context = context;
  }
  
  /**
   * kernel: 0 1 2
   *         3 4 5
   *         6 7 8
   */
  public void apply(RenderTarget src, RenderTarget dst, float[] kernel) {
    if(src == dst){
      System.out.println("Convolution error: read-write race");
      return;
    }
    if(kernel.length < 9) return;
    
    if(!src.isSampleable()) return;
    if(!dst.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.getWidth(), dst.getHeight(), kernel);
    context.endDraw();
    context.end("Convolution.apply");
  }
  
  public void apply(DwGLTexture src, DwGLTexture dst, float[] kernel) {
    if(src == dst){
      System.out.println("Convolution error: read-write race");
      return;
    }
    if(kernel.length < 9) return;
    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.w, dst.h, kernel);
    context.endDraw();
    context.end("Convolution.apply");
  }
  
  DwGLSLProgram shader;
  private void apply(int tex_handle, int w, int h, float[] kernel){
    if(shader == null) shader = context.createShader(DwPixelFlow.SHADER_DIR+"Filter/convolution3x3.frag");
    shader.begin();
    shader.uniform2f     ("wh_rcp", 1f/w, 1f/h);
    shader.uniform1fv    ("kernel", 9, kernel);
    shader.uniformTexture("tex"   , tex_handle);
    shader.drawFullScreenQuad();
    shader.end();
  }
  
}
