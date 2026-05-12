#version 430 core
//
// Fullscreen quad vertex shader for rendering the chunk grid as a texture.
// Render-only — float math is allowed here (no banned-ops lint).
//
// TODO_PIXELFLOW_PORT(render/Skylight uses a similar fullscreen pattern)

out vec2 v_uv;

void main() {
    // Standard fullscreen-triangle trick.
    vec2 p = vec2((gl_VertexID == 1) ? 3.0 : -1.0,
                  (gl_VertexID == 2) ? 3.0 : -1.0);
    v_uv = p * 0.5 + 0.5;
    gl_Position = vec4(p, 0.0, 1.0);
}
