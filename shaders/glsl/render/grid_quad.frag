#version 430 core
//
// Fullscreen-quad fragment: samples the grid texture and applies the
// material LUT to produce per-pixel color. Render-only — float math allowed.
//
// TODO_CORPUS(art-direction: 16-bit pixel art cross-section)
// TODO_PIXELFLOW_PORT(post-process AA chain after this for SMAA)

in vec2 v_uv;
layout(location = 0) out vec4 o_color;

uniform usampler2D u_grid;        // R32UI: packed element_id + flags
uniform sampler1D u_material_lut; // RGBA8: color per element_id

void main() {
    uint packed = texture(u_grid, v_uv).r;
    uint element_id = packed & 0xFFu;
    o_color = texelFetch(u_material_lut, int(element_id), 0);
}
