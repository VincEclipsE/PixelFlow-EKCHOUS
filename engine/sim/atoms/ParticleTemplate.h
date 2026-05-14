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

    // Spawn pattern — drives the unified RMB "spawn" tool. Single is the
    // bare drop_atom behavior; the rest dispatch to a builder.
    enum class SpawnPattern : int {
        Single = 0, Ball = 1, Grid = 2, Rope = 3,
        Disc = 4,   Star = 5, Hex  = 6, Blob = 7,
    };
    SpawnPattern spawn_pattern = SpawnPattern::Single;

    // Default spring mode for builders + the drag-to-create-spring action.
    // Stiff = rigid-body group; Flexible = position-correction (Struct).
    enum class SpringMode : int { Flexible = 0, Stiff = 1 };
    SpringMode default_spring_mode = SpringMode::Flexible;

    // Unified per-template color controller. Each particle from this
    // template renders in one of three modes:
    //   ElementDefault: use atoms::element(element_id).{r,g,b}
    //   Single:         flat color from single_color[3]
    //   Quadrant:       4-quadrant pie from quadrant_colors[4][3]
    // The single_color field replaces the old use_color_override+RGB triple;
    // the quadrant_colors array survives below.
    enum class ColorMode : int { ElementDefault = 0, Single = 1, Quadrant = 2 };
    ColorMode color_mode    = ColorMode::ElementDefault;
    float     single_color[3] = {0.85f, 0.85f, 0.90f};

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

    // Friction — per-template granular friction coefficient. Replaces the
    // old fickle-bond proxy. On collision, the relative velocity of the
    // contacting pair is attenuated by (1 - average friction) so heavily
    // frictioned particles brake sharply on contact. 0 = frictionless slide;
    // 1 = full velocity kill on every contact. Sand-like piling emerges at
    // friction ≈ 0.7-0.9.
    float friction                  = 0.0f;

    // 4-color quadrant palette. Used when color_mode == Quadrant. Drawn as
    // a 4-slice pie on each particle AND replicated as a 2×2 sub-grid on
    // each pixel-grid cell the particle occupies.
    float quadrant_colors[4][3] = {
        {1.0f, 0.6f, 0.6f},
        {0.6f, 1.0f, 0.6f},
        {0.6f, 0.6f, 1.0f},
        {1.0f, 1.0f, 0.6f},
    };

    // Bond memory. Copied to each spawned particle's
    // Particle::remember_severed_bonds.
    bool  remember_severed_bonds = true;
};

} // namespace ekchous::atoms
