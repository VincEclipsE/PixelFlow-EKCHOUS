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
import com.thomasdiewald.pixelflow.java.render.skylight.DwScreenSpaceGeometryBuffer;

import studio.engine.RenderTarget;


/**
 * 
 * @author Thomas Diewald
 *
 */
public class DepthOfField {
  
  public static class Param{
    public float   mult_blur = 10f;
    public float   focus     = 0.5f;
    public float[] focus_pos = {0.5f, 0.5f};
    public float   clip_z_near = 1.0f;
    public float   clip_z_far  = 6000.0f;
  }
  
  public Param param = new Param();
  public DwPixelFlow context;
  
  public DwGLSLProgram shader;

  public DepthOfField(DwPixelFlow context){
    this.context = context;
    this.shader = context.createShader(DwPixelFlow.SHADER_DIR+"Filter/depth_of_field.frag");
  }
  
  public void apply(RenderTarget src, RenderTarget dst, DwScreenSpaceGeometryBuffer geom) {
    if (!src.isSampleable()) return;
    if(!geom.pg_geom.isSampleable()) return;

    if(src == dst){
      System.out.println("DepthOfField.apply error: read-write race");
    }

    int w = dst.getWidth();
    int h = dst.getHeight();
    
//    dst.loadTexture();
    
    context.begin();
    context.beginDraw(dst);
    shader.begin();
    shader.uniform2f     ("wh"        , w, h);
    shader.uniform2f     ("clip_nf"   , param.clip_z_near, param.clip_z_far);
    shader.uniform2f     ("focus_pos" , param.focus_pos[0], param.focus_pos[1]);
    shader.uniform1f     ("mult_blur" , param.mult_blur);
    shader.uniformTexture("tex_src"   , src.getGLTextureId());
    shader.uniformTexture("tex_geom"  , geom.pg_geom.getGLTextureId());
    shader.drawFullScreenQuad();
    shader.end();
    context.endDraw();
    context.end("DepthOfField.apply");
  }
  
}
