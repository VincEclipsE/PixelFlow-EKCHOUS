#pragma once

// Distance constraint between two particles. Ported from PixelFlow's
// DwSpringConstraint2D. Force shape: (rest² / (d² + rest²) - 0.5),
// applied as a mass-weighted position correction to both endpoints.
// damp_inc scales the response when compressed (d < rest); damp_dec
// when stretched (d > rest).

#include "engine/sim/softbody/Particle.h"
#include <cmath>
#include <vector>

namespace ekchous::softbody {

enum class SpringType : int {
    Struct  = 0,
    Bend    = 1,
    Shear   = 2,
    Virtual = 3,
    Stiff   = 4,   // rigid-body group member; solved by the rigid pass
};

struct Spring {
    int a_idx = -1;
    int b_idx = -1;
    float dd_rest    = 0.0f;
    float dd_rest_sq = 0.0f;
    float damp_inc = 1.0f;
    float damp_dec = 1.0f;
    bool  enabled  = true;
    SpringType type = SpringType::Struct;

    // Last computed force from update(), kept for tension visualization.
    // Matches PixelFlow's DwSpringConstraint2D.force semantics.
    float force = 0.0f;

    // Per-spring tear threshold override.
    //   > 0  -> use this value instead of Physics.params.spring_tear_threshold
    //   == 0 -> fall back to the global threshold
    //   < 0  -> sentinel for "really erase me at end of tick" (set by
    //           Physics::tear_spring when the endpoints don't want memory).
    float tear_threshold_override = 0.0f;

    // Accumulated stretch damage. 0 = pristine; 1 = fully damaged (auto-tear).
    // Driven by Physics::update_spring_damage() each tick when the spring is
    // stretched beyond rest. Permanently grows dd_rest and scales damp_inc/
    // damp_dec down. Renderer fades the line color toward white in proportion.
    float damage = 0.0f;

    Spring() = default;
    Spring(int a, int b, float rest_length, SpringType t = SpringType::Struct)
        : a_idx(a > b ? a : b),
          b_idx(a > b ? b : a),
          dd_rest(rest_length),
          dd_rest_sq(rest_length * rest_length),
          type(t) {}

    void compute_rest_from_positions(const std::vector<Particle>& particles) noexcept {
        const auto& pa = particles[a_idx];
        const auto& pb = particles[b_idx];
        const float dx = pb.cx - pa.cx;
        const float dy = pb.cy - pa.cy;
        dd_rest_sq = dx*dx + dy*dy;
        dd_rest    = std::sqrt(dd_rest_sq);
    }

    // Apply one position-correction step. Caller must guarantee a_idx, b_idx
    // are valid indices into `particles`.
    //
    // At-rest endpoints are treated as infinite mass: the other endpoint
    // absorbs the FULL correction, the sleeping endpoint stays locked. If
    // both endpoints are at rest, the spring is dormant for this iteration.
    void update(std::vector<Particle>& particles) noexcept {
        if (!enabled) return;
        // Stiff springs are not solved via position correction — the rigid-
        // body pass owns their constraint enforcement. Skip them here.
        if (type == SpringType::Stiff) return;
        auto& pa = particles[a_idx];
        auto& pb = particles[b_idx];
        if (pa.at_rest && pb.at_rest) return;  // both anchored — nothing to correct

        const float dx    = pb.cx - pa.cx;
        const float dy    = pb.cy - pa.cy;
        const float dd_sq = dx*dx + dy*dy;

        force  = (dd_rest_sq / (dd_sq + dd_rest_sq) - 0.5f);
        force *= (dd_sq < dd_rest_sq) ? damp_inc : damp_dec;

        float pa_mass_factor = 2.0f * pb.mass / (pa.mass + pb.mass);
        float pb_mass_factor = 2.0f - pa_mass_factor;
        // Infinite-mass treatment for at-rest endpoints. The moving endpoint
        // absorbs all of the correction (factor = 2 = full); the sleeping
        // endpoint absorbs none (factor = 0).
        if (pa.at_rest)      { pa_mass_factor = 0.0f; pb_mass_factor = 2.0f; }
        else if (pb.at_rest) { pa_mass_factor = 2.0f; pb_mass_factor = 0.0f; }

        if (pa.enable_springs && !pa.at_rest) {
            pa.cx -= dx * force * pa_mass_factor;
            pa.cy -= dy * force * pa_mass_factor;
        }
        if (pb.enable_springs && !pb.at_rest) {
            pb.cx += dx * force * pb_mass_factor;
            pb.cy += dy * force * pb_mass_factor;
        }
    }
};

} // namespace ekchous::softbody
