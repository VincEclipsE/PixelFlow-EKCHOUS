package com.thomasdiewald.pixelflow.java.render.skylight;

import processing.opengl.PGraphics3D;

/**
 * Skylight screen-space geometry buffer stub. The studio fork dropped the
 * skylight renderer; this stub exists so DepthOfField + GBAA compile.
 * Public field {@code pg_geom} is what filters read.
 */
public class DwScreenSpaceGeometryBuffer {
    public PGraphics3D pg_geom = new PGraphics3D();
}
