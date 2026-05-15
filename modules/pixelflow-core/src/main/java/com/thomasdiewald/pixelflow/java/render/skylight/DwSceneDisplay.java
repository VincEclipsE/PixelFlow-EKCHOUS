package com.thomasdiewald.pixelflow.java.render.skylight;

import processing.opengl.PGraphicsOpenGL;

/**
 * Skylight scene-display callback stub. The studio fork dropped the skylight
 * renderer; this stub exists so the remaining two filters that reference
 * skylight types (GBAA, DepthOfField) compile.
 */
public interface DwSceneDisplay {
    void display(PGraphicsOpenGL canvas);
}
