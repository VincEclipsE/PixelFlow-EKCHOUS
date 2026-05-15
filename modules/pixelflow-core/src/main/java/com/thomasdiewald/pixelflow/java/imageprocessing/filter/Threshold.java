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

public class Threshold {
  
  static public class Param{
    /** threshold ... if(val < threshold_val) */
    float[] threshold_val = { 1f, 1f, 1f, 1f };
    /** exponential multiplier ... pow(val normalized, threshold_pow) */
    float[] threshold_pow = { 5f, 5f, 5f, 5f };
    /** linear multiplier ... val * threshold_mul */
    float[] threshold_mul = { 1f, 1f, 1f, 1f };
  }
  
  public DwPixelFlow context;
  
  public Param param = new Param();
  
  public Threshold(DwPixelFlow context){
    this.context = context;
  }
  
  public void apply(RenderTarget src, RenderTarget dst) {
    if(!src.isSampleable()) return;
    if(!dst.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.getWidth(), dst.getHeight());
    context.endDraw();
    context.end("Threshold.apply");
  }
  
  public void apply(RenderTarget src, DwGLTexture dst) {
    if (!src.isSampleable()) return;
       
    context.begin();
    context.beginDraw(dst);
    apply(src.getGLTextureId(), dst.w, dst.h);
    context.endDraw();
    context.end("Threshold.apply");
  }
  
  
  public void apply(DwGLTexture src, DwGLTexture dst) {
    context.begin();
    context.beginDraw(dst);
    apply(src.HANDLE[0], dst.w, dst.h);
    context.endDraw();
    context.end("Threshold.apply");
  }
  
  public DwGLSLProgram shader;
  public void apply(int tex_handle, int w, int h){    
    if(shader == null) shader = context.createShader(DwPixelFlow.SHADER_DIR+"Filter/threshold.frag");
    shader.begin();
    shader.uniform2f     ("wh_rcp"   , 1f/w, 1f/h);
    shader.uniform4fv    ("threshold_val", 1, param.threshold_val);
    shader.uniform4fv    ("threshold_pow", 1, param.threshold_pow);
    shader.uniform4fv    ("threshold_mul", 1, param.threshold_mul);
    shader.uniformTexture("tex"      , tex_handle);
    shader.drawFullScreenQuad();
    shader.end();
  }
  
  
}
