#pragma once

// Builder helper: spawn an N-pointed star — a ring of alternating outer /
// inner radius vertices plus a centre anchor, all wired with structural
// springs (ring perimeter) and radial struts (each ring vertex to the
// centre). The star geometry is visually distinct from SoftBall (uniform
// ring) and SoftGrid (square lattice), and the centre hub gives it more
// internal rigidity.

#include "engine/sim/softbody/Physics.h"
#include <cmath>
#include <vector>

namespace ekchous::softbody {

struct SoftStarSpec {
    float center_x        = 0.0f;
    float center_y        = 0.0f;
    int   num_points      = 5;
    float outer_radius    = 80.0f;
    float inner_radius    = 32.0f;
    float node_radius     = 5.0f;
    float spring_damp_inc = 1.0f;
    float spring_damp_dec = 1.0f;
};

struct SoftStar {
    int collision_group = 0;
    std::vector<int> particle_indices;  // ring vertices first, then centre
    std::vector<int> spring_indices;
};

inline SoftStar build_soft_star(Physics& physics, const SoftStarSpec& spec) {
    SoftStar star;
    star.collision_group = physics.next_collision_group();

    const int ring_count = spec.num_points * 2;
    if (ring_count < 4) return star;

    star.particle_indices.reserve(ring_count + 1);

    constexpr float kTau = 6.28318530718f;
    for (int i = 0; i < ring_count; ++i) {
        const float angle = static_cast<float>(i) * kTau / static_cast<float>(ring_count);
        const float r = (i % 2 == 0) ? spec.outer_radius : spec.inner_radius;
        const float x = spec.center_x + std::cos(angle) * r;
        const float y = spec.center_y + std::sin(angle) * r;
        const int idx = physics.add_particle(x, y, spec.node_radius);
        physics.particles()[idx].collision_group = star.collision_group;
        star.particle_indices.push_back(idx);
    }
    // Centre anchor.
    const int centre_idx = physics.add_particle(spec.center_x, spec.center_y,
                                                 spec.node_radius);
    physics.particles()[centre_idx].collision_group = star.collision_group;
    star.particle_indices.push_back(centre_idx);

    auto add = [&](int a_idx, int b_idx) {
        const int s_idx = physics.add_spring(a_idx, b_idx, SpringType::Struct);
        auto& s = physics.springs()[s_idx];
        s.damp_inc = spec.spring_damp_inc;
        s.damp_dec = spec.spring_damp_dec;
        star.spring_indices.push_back(s_idx);
    };

    // Ring perimeter.
    for (int i = 0; i < ring_count; ++i) {
        const int next = (i + 1) % ring_count;
        add(star.particle_indices[i], star.particle_indices[next]);
    }
    // Radial struts: every ring vertex to the centre.
    for (int i = 0; i < ring_count; ++i) {
        add(star.particle_indices[i], centre_idx);
    }
    return star;
}

} // namespace ekchous::softbody
