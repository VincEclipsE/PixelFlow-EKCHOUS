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

public class Median {
  
  
  public static enum TYPE{
      _3x3_  ("MEDIAN_3x3")
    , _5x5_  ("MEDIAN_5x5")
    ;
    
    protected DwGLSLProgram shader;
    protected String define;
    
    private TYPE(String define){
      this.define = define;
    }
    
    private void buildShader(DwPixelFlow context){
      if(shader != null){
        return; // no need to rebuild
      }
      Object id = this.name();
      shader = context.createShader(id, DwPixelFlow.SHADER_DIR+"Filter/median.frag");
      shader.frag.setDefine(define, 1);
    }
  }
    
  public DwPixelFlow context;

  public Median(DwPixelFlow context){
    this.context = context;
  }

  public void apply(RenderTarget src, RenderTarget dst, Median.TYPE kernel) {
    if(src == dst){
      System.out.println("Median error: read-write race");
      return;
    }
    
    if(kernel == null){
      return;
    }
    kernel.buildShader(context);

    if(!src.isSampleable()) return;
    if(!dst.isSampleable()) return;
    
    context.begin();
    context.beginDraw(dst);
    apply(kernel.shader, src.getGLTextureId(), dst.getWidth(), dst.getHeight());
    context.endDraw();
    context.end("Median.apply");
  }
  
  public void apply(DwGLTexture src, DwGLTexture dst, Median.TYPE kernel) {
    if(src == dst){
      System.out.println("Median error: read-write race");
      return;
    }
    
    if(kernel == null){
      return;
    }
    kernel.buildShader(context);

    context.begin();
    context.beginDraw(dst);
    apply(kernel.shader, src.HANDLE[0], dst.w, dst.h);
    context.endDraw();
    context.end("Median.apply");
  }
  
  public void apply(DwGLSLProgram shader, int tex_handle, int w, int h){
    shader.begin();
    shader.uniform2f     ("wh_rcp"     , 1f/w, 1f/h);
    shader.uniformTexture("tex", tex_handle);
    shader.drawFullScreenQuad();
    shader.end();
  }
  
}
