#pragma once

// Builder helper: spawn an Nx × Ny grid of particles wired with three
// spring layers — struct (orthogonal), shear (diagonal), bend (long).
// Mirrors PixelFlow's DwSoftGrid2D minus the Processing PShape rendering.
//
// Optional pinning list: each (x, y) entry disables forces + springs +
// collisions for that particle, giving you immovable anchors (top
// corners for a hanging cloth, every node along one edge for a flag).

#include "engine/sim/softbody/Physics.h"
#include <unordered_set>
#include <utility>
#include <vector>

namespace ekchous::softbody {

struct SoftGridSpec {
    float start_x = 100.0f;
    float start_y = 100.0f;
    int   nodes_x = 16;
    int   nodes_y = 16;
    float node_radius      = 6.0f;
    int   bend_spring_dist = 3;
    bool  create_struct = true;
    bool  create_shear  = true;
    bool  create_bend   = true;
    float spring_damp_inc = 1.0f;
    float spring_damp_dec = 1.0f;
    // (x, y) grid indices of particles to pin. Pinned particles have
    // enable_forces / enable_springs / enable_collisions all false.
    std::vector<std::pair<int,int>> pinned;
};

struct SoftGrid {
    int collision_group = 0;
    int nodes_x = 0;
    int nodes_y = 0;
    std::vector<int> particle_indices;  // row-major: [y * nodes_x + x]
    std::vector<int> spring_indices;

    int idx_of(int x, int y) const noexcept {
        if (x < 0 || x >= nodes_x || y < 0 || y >= nodes_y) return -1;
        return particle_indices[y * nodes_x + x];
    }
};

inline SoftGrid build_soft_grid(Physics& physics, const SoftGridSpec& spec) {
    SoftGrid grid;
    grid.collision_group = physics.next_collision_group();
    grid.nodes_x = spec.nodes_x;
    grid.nodes_y = spec.nodes_y;
    grid.particle_indices.reserve(static_cast<std::size_t>(spec.nodes_x) * spec.nodes_y);

    // 1) Spawn particles in row-major order at (start_x + x*2r, start_y + y*2r).
    for (int y = 0; y < spec.nodes_y; ++y) {
        for (int x = 0; x < spec.nodes_x; ++x) {
            const float px = spec.start_x + x * spec.node_radius * 2.0f;
            const float py = spec.start_y + y * spec.node_radius * 2.0f;
            const int idx = physics.add_particle(px, py, spec.node_radius);
            physics.particles()[idx].collision_group = grid.collision_group;
            grid.particle_indices.push_back(idx);
        }
    }

    // Pin requested nodes.
    for (const auto& [px, py] : spec.pinned) {
        const int idx = grid.idx_of(px, py);
        if (idx < 0) continue;
        auto& p = physics.particles()[idx];
        p.enable_forces     = false;
        p.enable_springs    = false;
        p.enable_collisions = false;
    }

    // 2) Spring layer. Per-pair dedup via a set of canonical (lo, hi) keys
    // so PixelFlow's spring_map behaviour carries over.
    std::unordered_set<long long> seen_pairs;
    auto encode_pair = [&](int a_idx, int b_idx) -> long long {
        const long long lo = a_idx < b_idx ? a_idx : b_idx;
        const long long hi = a_idx < b_idx ? b_idx : a_idx;
        return lo * 1'000'000LL + hi;
    };
    auto add_spring = [&](int ax, int ay, int offx, int offy, SpringType type) {
        int bx = ax + offx;
        int by = ay + offy;
        if (bx < 0)              bx = 0;
        else if (bx >= spec.nodes_x) bx = spec.nodes_x - 1;
        if (by < 0)              by = 0;
        else if (by >= spec.nodes_y) by = spec.nodes_y - 1;
        const int a_idx = grid.idx_of(ax, ay);
        const int b_idx = grid.idx_of(bx, by);
        if (a_idx == b_idx) return;  // clamp produced a self-spring
        const long long key = encode_pair(a_idx, b_idx);
        if (!seen_pairs.insert(key).second) return;  // already added
        const int s_idx = physics.add_spring(a_idx, b_idx, type);
        auto& s = physics.springs()[s_idx];
        s.damp_inc = spec.spring_damp_inc;
        s.damp_dec = spec.spring_damp_dec;
        grid.spring_indices.push_back(s_idx);
    };

    const int ox = spec.bend_spring_dist;
    const int oy = spec.bend_spring_dist;
    for (int y = 0; y < spec.nodes_y; ++y) {
        for (int x = 0; x < spec.nodes_x; ++x) {
            if (spec.create_struct) {
                add_spring(x, y, -1,  0, SpringType::Struct);
                add_spring(x, y,  0, -1, SpringType::Struct);
                add_spring(x, y, +1,  0, SpringType::Struct);
                add_spring(x, y,  0, +1, SpringType::Struct);
            }
            if (spec.create_shear) {
                add_spring(x, y, -1, -1, SpringType::Shear);
                add_spring(x, y, +1, -1, SpringType::Shear);
                add_spring(x, y, -1, +1, SpringType::Shear);
                add_spring(x, y, +1, +1, SpringType::Shear);
            }
            if (spec.create_bend && spec.bend_spring_dist > 0) {
                add_spring(x, y, -ox, -oy, SpringType::Bend);
                add_spring(x, y, +ox, -oy, SpringType::Bend);
                add_spring(x, y, -ox, +oy, SpringType::Bend);
                add_spring(x, y, +ox, +oy, SpringType::Bend);
            }
        }
    }

    return grid;
}

} // namespace ekchous::softbody
