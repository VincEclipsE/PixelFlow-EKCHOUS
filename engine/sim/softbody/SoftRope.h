#pragma once

// Builder helper: linear chain of N particles from (start_x, start_y) to
// (end_x, end_y), connected by structural springs between consecutive
// nodes and optional bend springs spanning `bend_spring_dist` apart.
//
// Use cases: ropes, chains, hair strands, cables. Pin one end with a
// pinned[] entry to make it hang.

#include "engine/sim/softbody/Physics.h"
#include <cmath>
#include <vector>

namespace ekchous::softbody {

struct SoftRopeSpec {
    float start_x = 0.0f;
    float start_y = 0.0f;
    float end_x   = 200.0f;
    float end_y   = 0.0f;
    int   num_nodes        = 16;
    float node_radius      = 4.0f;
    int   bend_spring_dist = 0;     // 0 disables bend springs
    float spring_damp_inc = 1.0f;
    float spring_damp_dec = 1.0f;
    std::vector<int> pinned;        // node indices (0..num_nodes-1) to pin
};

struct SoftRope {
    int collision_group = 0;
    std::vector<int> particle_indices;
    std::vector<int> spring_indices;
};

inline SoftRope build_soft_rope(Physics& physics, const SoftRopeSpec& spec) {
    SoftRope rope;
    rope.collision_group = physics.next_collision_group();
    const int n = spec.num_nodes < 2 ? 2 : spec.num_nodes;
    rope.particle_indices.reserve(n);

    const float dx = (spec.end_x - spec.start_x) / static_cast<float>(n - 1);
    const float dy = (spec.end_y - spec.start_y) / static_cast<float>(n - 1);
    for (int i = 0; i < n; ++i) {
        const float x = spec.start_x + dx * i;
        const float y = spec.start_y + dy * i;
        const int idx = physics.add_particle(x, y, spec.node_radius);
        physics.particles()[idx].collision_group = rope.collision_group;
        rope.particle_indices.push_back(idx);
    }

    for (int pin_idx : spec.pinned) {
        if (pin_idx < 0 || pin_idx >= n) continue;
        auto& p = physics.particles()[rope.particle_indices[pin_idx]];
        p.enable_forces     = false;
        p.enable_springs    = false;
        p.enable_collisions = false;
    }

    auto add = [&](int a, int b, SpringType type) {
        const int s_idx = physics.add_spring(rope.particle_indices[a],
                                              rope.particle_indices[b], type);
        auto& s = physics.springs()[s_idx];
        s.damp_inc = spec.spring_damp_inc;
        s.damp_dec = spec.spring_damp_dec;
        rope.spring_indices.push_back(s_idx);
    };

    for (int i = 0; i < n - 1; ++i) {
        add(i, i + 1, SpringType::Struct);
    }
    if (spec.bend_spring_dist > 0) {
        for (int i = 0; i + spec.bend_spring_dist < n; ++i) {
            add(i, i + spec.bend_spring_dist, SpringType::Bend);
        }
    }
    return rope;
}

} // namespace ekchous::softbody
