package processing.core;

/**
 * Constants ported from Processing's {@code processing.core.PConstants}. This
 * is a value-type compatibility shim that lets the forked PixelFlow source
 * compile without depending on the Processing runtime. None of these constants
 * are read by the studio's render pipeline — they show up only in PShape
 * builder calls (TRIANGLES, QUADS, NORMAL, etc.) and blend-mode arguments
 * that no longer reach an actual Processing renderer.
 *
 * <p>Values match the upstream PixelFlow / Processing 3.x source so that any
 * comparisons in PixelFlow code continue to evaluate correctly.
 *
 * <p><b>This shim is not affiliated with the Processing Foundation.</b>
 */
public interface PConstants {

    // Render mode hints (Processing values)
    String JAVA2D = "processing.awt.PGraphicsJava2D";
    String P2D    = "processing.opengl.PGraphics2D";
    String P3D    = "processing.opengl.PGraphics3D";

    // Pixel formats
    int RGB   = 1;
    int ARGB  = 2;
    int HSB   = 3;
    int ALPHA = 4;

    // Shape modes
    int POINT          = 2;
    int POINTS         = 3;
    int LINE           = 4;
    int LINES          = 5;
    int LINE_STRIP     = 50;
    int LINE_LOOP      = 51;
    int TRIANGLE       = 8;
    int TRIANGLES      = 9;
    int TRIANGLE_STRIP = 10;
    int TRIANGLE_FAN   = 11;
    int QUAD           = 16;
    int QUADS          = 17;
    int QUAD_STRIP     = 18;
    int POLYGON        = 20;
    int PATH           = 21;
    int RECT           = 30;
    int ELLIPSE        = 31;
    int ARC            = 32;
    int SPHERE         = 40;
    int BOX            = 41;
    int GROUP          = 0;
    int PRIMITIVE      = 101;
    int GEOMETRY       = 103;

    // Vertex flags
    int VERTEX         = 0;
    int BEZIER_VERTEX  = 1;
    int QUADRATIC_VERTEX = 2;
    int CURVE_VERTEX   = 3;
    int BREAK          = 4;

    // Path commands
    int OPEN  = 1;
    int CLOSE = 2;

    // Shape attributes
    int NORMAL    = 1;
    int TEXTURE   = 2;
    int VERTICES  = 3;

    // Blend modes
    int REPLACE    = 0;
    int BLEND      = 1 << 0;
    int ADD        = 1 << 1;
    int SUBTRACT   = 1 << 2;
    int LIGHTEST   = 1 << 3;
    int DARKEST    = 1 << 4;
    int DIFFERENCE = 1 << 5;
    int EXCLUSION  = 1 << 6;
    int MULTIPLY   = 1 << 7;
    int SCREEN     = 1 << 8;
    int OVERLAY    = 1 << 9;
    int HARD_LIGHT = 1 << 10;
    int SOFT_LIGHT = 1 << 11;
    int DODGE      = 1 << 12;
    int BURN       = 1 << 13;

    // Texture wrap
    int CLAMP  = 0;
    int REPEAT = 1;

    // OpenGL hints (used by hint())
    int ENABLE_OPTIMIZED_STROKE         = -1;
    int DISABLE_OPTIMIZED_STROKE        = 1;
    int ENABLE_STROKE_PURE              = -2;
    int DISABLE_STROKE_PURE             = 2;
    int ENABLE_DEPTH_TEST               = -3;
    int DISABLE_DEPTH_TEST              = 3;
    int ENABLE_DEPTH_SORT               = -4;
    int DISABLE_DEPTH_SORT              = 4;
    int ENABLE_DEPTH_MASK               = -5;
    int DISABLE_DEPTH_MASK              = 5;
    int ENABLE_OPENGL_ERRORS            = -6;
    int DISABLE_OPENGL_ERRORS           = 6;
    int ENABLE_STROKE_PERSPECTIVE       = -7;
    int DISABLE_STROKE_PERSPECTIVE      = 7;
    int ENABLE_TEXTURE_MIPMAPS          = -8;
    int DISABLE_TEXTURE_MIPMAPS         = 8;
    int ENABLE_ASYNC_SAVEFRAME          = -9;
    int DISABLE_ASYNC_SAVEFRAME         = 9;
    int ENABLE_BUFFER_READING           = -10;
    int DISABLE_BUFFER_READING          = 10;
    int ENABLE_KEY_REPEAT               = -11;
    int DISABLE_KEY_REPEAT              = 11;

    // Color modes / image-rect modes (rarely used by PixelFlow)
    int CORNER  = 0;
    int CORNERS = 1;
    int RADIUS  = 2;
    int CENTER  = 3;
    int DIAMETER = 3;

    // Math
    float PI         = (float) Math.PI;
    float HALF_PI    = (float) (Math.PI / 2.0);
    float QUARTER_PI = (float) (Math.PI / 4.0);
    float TWO_PI     = (float) (Math.PI * 2.0);
    float TAU        = TWO_PI;
    float DEG_TO_RAD = PI / 180.0f;
    float RAD_TO_DEG = 180.0f / PI;
    float EPSILON    = 0.0001f;

    // Limits
    float MAX_FLOAT = Float.MAX_VALUE;
    float MIN_FLOAT = -Float.MAX_VALUE;
    int   MAX_INT   = Integer.MAX_VALUE;
    int   MIN_INT   = Integer.MIN_VALUE;
}
