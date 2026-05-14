#pragma once

// EKCHOUS atomic element table — the first piece of the particle physics
// layer that sits on top of the PixelFlow softbody base.
//
// Each particle carries a `core::u8 element_id` (index into this table)
// describing what kind of atom it is. The element fields drive:
//   - Render colour (when "color by element" is on)
//   - Default particle radius + mass at spawn time
//   - Maximum simultaneous bonds (valence) — used later when auto-bond
//     formation is wired up
//   - Base bond strength + bond-formation radius — same
//
// Numbers are deliberately small and tunable; this is a starter table to
// validate the data flow, not a chemistry-accurate periodic table.

#include "engine/core/Types.h"

namespace ekchous::atoms {

struct AtomElement {
    const char* symbol;        // short label, "H" / "O" / "C"
    const char* name;          // full name for tooltips/UI
    core::u8 r, g, b;          // render colour
    int   valence;             // max simultaneous bonds (0 = inert)
    float bond_strength;       // base spring damp_inc/dec
    float bond_radius;         // distance threshold for auto-bond formation
    float particle_radius;     // default Particle.rad / rad_collision at spawn
    float mass;                // default Particle.mass at spawn
};

constexpr int kNumElements = 6;

constexpr AtomElement kElements[kNumElements] = {
    //  symbol  name           r    g    b   valence  strength  bond_r  p_rad  mass
    { "H",  "Hydrogen",     230, 230, 250,    1,      0.10f,   14.0f,  4.0f,  1.0f },
    { "O",  "Oxygen",       255,  90,  90,    2,      0.18f,   14.0f,  5.0f,  1.5f },
    { "C",  "Carbon",        60,  60,  72,    4,      0.22f,   14.0f,  5.0f,  2.0f },
    { "N",  "Nitrogen",     100, 130, 220,    3,      0.18f,   14.0f,  5.0f,  1.4f },
    { "Fe", "Iron",         180, 130,  90,    6,      0.30f,   16.0f,  6.0f,  5.0f },
    { "X",  "Inert (Xeno)",  90,  95, 105,    0,      0.0f,     0.0f,  4.0f,  1.0f },
};

inline const AtomElement& element(int idx) noexcept {
    if (idx < 0 || idx >= kNumElements) return kElements[0];
    return kElements[idx];
}

} // namespace ekchous::atoms
