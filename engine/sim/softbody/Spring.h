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
    void update(std::vector<Particle>& particles) noexcept {
        if (!enabled) return;
        auto& pa = particles[a_idx];
        auto& pb = particles[b_idx];

        const float dx    = pb.cx - pa.cx;
        const float dy    = pb.cy - pa.cy;
        const float dd_sq = dx*dx + dy*dy;

        force  = (dd_rest_sq / (dd_sq + dd_rest_sq) - 0.5f);
        force *= (dd_sq < dd_rest_sq) ? damp_inc : damp_dec;

        const float pa_mass_factor = 2.0f * pb.mass / (pa.mass + pb.mass);
        const float pb_mass_factor = 2.0f - pa_mass_factor;

        if (pa.enable_springs) {
            pa.cx -= dx * force * pa_mass_factor;
            pa.cy -= dy * force * pa_mass_factor;
        }
        if (pb.enable_springs) {
            pb.cx += dx * force * pb_mass_factor;
            pb.cy += dy * force * pb_mass_factor;
        }
    }
};

} // namespace ekchous::softbody
