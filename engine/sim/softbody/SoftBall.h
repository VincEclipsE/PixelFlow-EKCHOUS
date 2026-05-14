#pragma once

// Builder helper: spawn a ring of particles connected by structural springs
// (the ring perimeter) plus bend springs at a configurable spacing. The
// bend springs are what give the ball its bouncy/stiff feel. All particles
// in the ball share a collision_group so they don't self-collide.
//
// Mirrors PixelFlow's DwSoftBall2D minus the Processing-specific PShape
// rendering. Construction returns the indices of the spawned particles so
// the caller can address them (e.g., for mouse drag).

#include "engine/sim/softbody/Physics.h"
#include <cmath>
#include <vector>

namespace ekchous::softbody {

struct SoftBallSpec {
    float center_x        = 0.0f;
    float center_y        = 0.0f;
    float ring_radius     = 80.0f;   // distance from centre to each node
    float node_radius     = 8.0f;    // collision radius per node
    int   bend_spring_dist = 3;       // ring-index hop for bend springs
    float spring_damp_inc  = 0.9f;
    float spring_damp_dec  = 0.9f;
};

struct SoftBall {
    int collision_group = 0;
    std::vector<int> particle_indices;
    std::vector<int> spring_indices;
};

inline SoftBall build_soft_ball(Physics& physics, const SoftBallSpec& spec) {
    SoftBall ball;
    ball.collision_group = physics.next_collision_group();

    // Compute number of vertices (matching PixelFlow's heuristic).
    constexpr float kThreshold1 = 10.0f;
    constexpr float kThreshold2 = 100.0f;
    const float r = spec.ring_radius;
    const double arc1 = std::acos(std::fmax((r - kThreshold1), 0.0f) / r);
    const double arc2 = (180.0 - kThreshold2) * 3.14159265358979323846 / 180.0;
    const double arc  = std::fmin(arc1, arc2);
    int num_vtx = static_cast<int>(std::ceil(2.0 * 3.14159265358979323846 / arc));
    // PixelFlow's Java does `num_vtx += (num_vtx*0.5f)*2`, which after int/
    // float promotion and `+=` int-narrowing is exactly `num_vtx *= 2`.
    // Always-even vertex count keeps the ring left/right-symmetric — odd
    // counts make the ball tilt and roll when it lands.
    num_vtx *= 2;
    if (num_vtx < 6) num_vtx = 6;

    // 1) particles around the ring.
    ball.particle_indices.reserve(num_vtx);
    for (int i = 0; i < num_vtx; ++i) {
        const float theta = static_cast<float>(i) * 2.0f * 3.14159265358979323846f
                            / static_cast<float>(num_vtx);
        const float x = spec.center_x + std::cos(theta) * r;
        const float y = spec.center_y + std::sin(theta) * r;
        const int idx = physics.add_particle(x, y, spec.node_radius);
        physics.particles()[idx].collision_group = ball.collision_group;
        ball.particle_indices.push_back(idx);
    }

    // 2) structural + bend springs.
    auto add = [&](int a_local, int b_local, SpringType type) {
        const int a = ball.particle_indices[a_local];
        const int b = ball.particle_indices[b_local];
        const int s_idx = physics.add_spring(a, b, type);
        auto& s = physics.springs()[s_idx];
        s.damp_inc = spec.spring_damp_inc;
        s.damp_dec = spec.spring_damp_dec;
        ball.spring_indices.push_back(s_idx);
    };
    for (int i = 0; i < num_vtx; ++i) {
        // Structural: ring edge.
        const int next1 = (i + 1) % num_vtx;
        add(i, next1, SpringType::Struct);
        // Bend.
        const int bend = (i + spec.bend_spring_dist) % num_vtx;
        add(i, bend, SpringType::Bend);
    }

    return ball;
}

} // namespace ekchous::softbody
