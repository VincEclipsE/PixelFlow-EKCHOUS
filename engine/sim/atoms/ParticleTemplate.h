#pragma once

// A named bundle of per-particle settings. Each call to drop_atom uses the
// currently-selected template to spawn a particle pre-configured with the
// right element, radius, mass, enable flags, and user_data. Saved/loaded
// as a flat text library so the user can build up a library of "kinds of
// particle" once and then drop them anywhere.
//
// This is the architectural pivot from global per-element settings to
// per-template settings — every tool in the engine that reads a particle
// property already works unchanged; this struct just controls what those
// properties are at spawn time.

#include "engine/core/Types.h"
#include <string>

namespace ekchous::atoms {

struct ParticleTemplate {
    std::string name              = "Default";
    int         element_id        = 0;          // index into kElements
    float       radius            = 5.0f;
    float       mass              = 1.0f;
    bool        enable_forces     = true;
    bool        enable_springs    = true;
    bool        enable_collisions = true;
    core::u32   user_data         = 0;

    // Per-template color override. When use_color_override is true, particle
    // renderer uses color_override_{r,g,b} instead of the element's default
    // color. Independent of element_id so you can have e.g. two iron
    // templates render differently.
    bool  use_color_override = false;
    float color_override_r   = 1.0f;
    float color_override_g   = 1.0f;
    float color_override_b   = 1.0f;

    // ---- Linked tools ----
    // Linking a tool to a template makes every particle from this template
    // participate in that tool with the template's per-tool parameters.
    // Two flavors:
    //   * Parameter links (e.g. fluid coupling) — pure knob exposure, no
    //     behavior beyond what the tool normally does to particles.
    //   * Emitter links (e.g. fluid emit, heat emit, flow-particle emit) —
    //     opt-in behavior: each tick the particle injects something into
    //     the linked tool. Default off so iron doesn't suddenly become a
    //     fluid source on day one.
    // All linkages can be edited per-template in the editor, or via the
    // Inspector for the focused particle's template.

    // Parameter link: fluid drag strength used by the global fluid drag pass.
    bool  link_fluid_coupling   = false;
    float fluid_drag_strength   = 1.0f;

    // Emitter link: inject density into the Stam fluid at this particle's
    // position each tick. Lets you mark some particles as "this is the fluid
    // source" without painting manually.
    bool  link_fluid_emit       = false;
    float fluid_emit_amount     = 30.0f;

    // Emitter link: inject heat into the Stam fluid temperature field. Pair
    // with buoyancy > 0 in the fluid solver to make hot regions rise.
    bool  link_heat_emit        = false;
    float heat_emit_amount      = 10.0f;

    // Emitter link: spawn flow particles at this particle's position each
    // tick. Unconditional emit (no wet-threshold gating); intentionally
    // simple so you can chain it with other tools.
    bool  link_fp_emit          = false;
    int   fp_emit_per_tick      = 2;
    float fp_emit_vx_jitter     = 0.6f;
    float fp_emit_vy_jitter     = 0.6f;
};

} // namespace ekchous::atoms
