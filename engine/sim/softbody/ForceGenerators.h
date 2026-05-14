#pragma once

// Per-particle force generators placed in world space. Each generator type
// is a small POD struct whose operator() applies a force (or velocity
// damping) to one particle.
//
// Physics owns vectors of each generator type and walks them in the
// integrate phase alongside gravity. Splitting by concrete type (rather
// than std::function type erasure) keeps the inner loop tight and lets
// the engine render them without downcasting.

#include "engine/sim/softbody/AABB.h"
#include "engine/sim/softbody/Particle.h"
#include <cmath>

namespace ekchous::softbody {

// Radial attractor / repeller. Quadratic falloff toward max_radius.
// strength > 0  = attract, strength < 0 = repel. max_radius == 0 means
// unlimited falloff (1/(1+0.01r) attenuation as a safety).
struct PointGravity {
    float cx = 0.0f;
    float cy = 0.0f;
    float strength   = 0.5f;
    float max_radius = 200.0f;

    void operator()(Particle& p) const noexcept {
        const float dx = cx - p.cx;
        const float dy = cy - p.cy;
        const float d2 = dx * dx + dy * dy;
        if (d2 < 1.0f) return;
        if (max_radius > 0.0f && d2 > max_radius * max_radius) return;
        const float d = std::sqrt(d2);
        const float falloff = max_radius > 0.0f
            ? (1.0f - d / max_radius)
            : 1.0f / (1.0f + d * 0.01f);
        const float fmag = strength * falloff * falloff;
        p.add_force(dx / d * fmag, dy / d * fmag);
    }
};

// Viscous patch. Any particle inside the AABB has its implicit Verlet
// velocity scaled toward zero by `drag` per tick — bypasses add_force so
// the damping is mass-independent and visually obvious.
struct DragField {
    AABB  aabb;
    float drag = 0.15f;  // 0 = none, 1 = halt

    void operator()(Particle& p) const noexcept {
        if (!aabb.contains(p.cx, p.cy)) return;
        const float vx = p.cx - p.px;
        const float vy = p.cy - p.py;
        const float keep = 1.0f - drag;
        p.px = p.cx - vx * keep;
        p.py = p.cy - vy * keep;
    }
};

} // namespace ekchous::softbody
