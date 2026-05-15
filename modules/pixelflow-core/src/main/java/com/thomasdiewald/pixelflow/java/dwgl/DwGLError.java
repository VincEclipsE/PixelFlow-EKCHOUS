/**
 * 
 * PixelFlow | Copyright (C) 2016 Thomas Diewald - http://thomasdiewald.com
 * 
 * A Processing/Java library for high performance GPU-Computing (GLSL).
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */



package com.thomasdiewald.pixelflow.java.dwgl;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.glu.GLU;

public class DwGLError {

  public static boolean DEBUG_OUT = false;

  public static final GLU glu = new GLU();
  public static int ERROR_CODE = 0;

  /**
   * Studio fork: messages whose error reports should be silently dropped.
   * Used to mute known-phantom GL_INVALID_OPERATION reports that appear in
   * specific JOGL/driver combinations but don't reflect a real GL fault.
   *
   * <p>A message is suppressed if any entry in this set is a prefix of the
   * {@code debug_note} argument. The check is still performed (glGetError
   * is called) so error state is drained, but the resulting print is
   * skipped.
   *
   * <p>Backed by a ConcurrentHashMap-keyset for safe concurrent additions.
   */
  public static final Set<String> SUPPRESSED_MESSAGE_PREFIXES = ConcurrentHashMap.newKeySet();

  public static boolean debug(GL2ES2 gl, String debug_note) {
    ERROR_CODE = gl.glGetError();
    if (DEBUG_OUT) System.out.println("--------------------------<  ERROR_CHECK >--------------------------( "+debug_note+" )");
    boolean has_error = (ERROR_CODE != GL2ES2.GL_NO_ERROR);
    if (has_error && !isSuppressed(debug_note)) {
      System.out.println(debug_note+" | GL_ERROR: "+glu.gluErrorString(ERROR_CODE));
    }
    if (DEBUG_OUT) System.out.println("--------------------------< /ERROR_CHECK >--------------------------");
    return has_error;
  }

  private static boolean isSuppressed(String note) {
    if (note == null || SUPPRESSED_MESSAGE_PREFIXES.isEmpty()) return false;
    for (String prefix : SUPPRESSED_MESSAGE_PREFIXES) {
      if (note.startsWith(prefix)) return true;
    }
    return false;
  }
  
  
  public static boolean FBO(GL2ES2 gl, int[] handle_fbo){
    int rval = ERROR_CODE = gl.glCheckFramebufferStatus(handle_fbo[0]);
    System.out.println("glCheckFramebufferStatus = "+rval);
    if( rval == GL2ES2.GL_FRAMEBUFFER_COMPLETE ){
      return true;
    } else {
      
      
      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS         ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS        ");
      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_FORMATS            ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_FORMATS           ");
//      if( rval == GL2ES2.GL_FRAMEBUFFER_UNDEFINED                     ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_UNDEFINED                    ");
      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT         ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT        ");
      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT");
//      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER        ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER       ");
//      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER        ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER       ");
      if( rval == GL2ES2.GL_FRAMEBUFFER_UNSUPPORTED                   ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_UNSUPPORTED                  ");
      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE        ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE       ");
      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE        ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE       ");
//      if( rval == GL2ES2.GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS      ) System.out.println("FBO-ERROR: GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS     ");
      System.out.println("FBO-ERROR: unknown errorcode");
    }
    
    return false;
  }
}
