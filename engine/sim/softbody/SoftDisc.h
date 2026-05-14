#pragma once

// Builder helper: spawn a filled disc made of a centre particle plus
// `num_rings` concentric rings of `num_angular` particles each. Struct
// springs link centre→ring0, around each ring's perimeter, and between
// matching angular positions in adjacent rings (radial spokes).
//
// Visually distinct from SoftBall (just a ring outline) and SoftStar
// (alternating outer/inner spikes). The filled lattice gives it
// rotational symmetry and resists shearing.

#include "engine/sim/softbody/Physics.h"
#include <cmath>
#include <vector>

namespace ekchous::softbody {

struct SoftDiscSpec {
    float center_x        = 0.0f;
    float center_y        = 0.0f;
    float outer_radius    = 80.0f;
    float node_radius     = 5.0f;
    int   num_rings       = 4;
    int   num_angular     = 14;
    float spring_damp_inc = 1.0f;
    float spring_damp_dec = 1.0f;
};

struct SoftDisc {
    int collision_group = 0;
    int centre_idx      = -1;
    std::vector<int> particle_indices;  // centre first, then rings flat
    std::vector<int> spring_indices;
};

inline SoftDisc build_soft_disc(Physics& physics, const SoftDiscSpec& spec) {
    SoftDisc disc;
    if (spec.num_rings < 1 || spec.num_angular < 3) return disc;
    disc.collision_group = physics.next_collision_group();

    // Centre.
    disc.centre_idx = physics.add_particle(spec.center_x, spec.center_y, spec.node_radius);
    physics.particles()[disc.centre_idx].collision_group = disc.collision_group;
    disc.particle_indices.push_back(disc.centre_idx);

    // Rings.
    constexpr float kTau = 6.28318530718f;
    std::vector<std::vector<int>> ring_indices(spec.num_rings);
    for (int r = 0; r < spec.num_rings; ++r) {
        const float ring_r =
            (static_cast<float>(r + 1) / spec.num_rings) * spec.outer_radius;
        ring_indices[r].reserve(spec.num_angular);
        for (int a = 0; a < spec.num_angular; ++a) {
            const float angle =
                static_cast<float>(a) * kTau / static_cast<float>(spec.num_angular);
            const float x = spec.center_x + std::cos(angle) * ring_r;
            const float y = spec.center_y + std::sin(angle) * ring_r;
            const int idx = physics.add_particle(x, y, spec.node_radius);
            physics.particles()[idx].collision_group = disc.collision_group;
            disc.particle_indices.push_back(idx);
            ring_indices[r].push_back(idx);
        }
    }

    auto add = [&](int a, int b) {
        const int s_idx = physics.add_spring(a, b, SpringType::Struct);
        auto& s = physics.springs()[s_idx];
        s.damp_inc = spec.spring_damp_inc;
        s.damp_dec = spec.spring_damp_dec;
        disc.spring_indices.push_back(s_idx);
    };

    // Centre → ring-0 (radial spokes from hub).
    for (int idx : ring_indices[0]) add(disc.centre_idx, idx);
    // Each ring's perimeter.
    for (auto& ring : ring_indices) {
        const int n = static_cast<int>(ring.size());
        for (int i = 0; i < n; ++i) add(ring[i], ring[(i + 1) % n]);
    }
    // Spokes between adjacent rings at each angle.
    for (int r = 0; r < spec.num_rings - 1; ++r) {
        for (int a = 0; a < spec.num_angular; ++a) {
            add(ring_indices[r][a], ring_indices[r + 1][a]);
        }
    }
    return disc;
}

} // namespace ekchous::softbody
