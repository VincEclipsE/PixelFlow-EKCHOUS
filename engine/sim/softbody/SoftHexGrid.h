#pragma once

// Hexagonal-lattice softbody. Pointy-top hex layout: odd rows shifted
// half a cell to the right. Each node connects to up to 6 neighbours
// (2 horizontal + 4 diagonals whose offsets flip by row parity).
//
// Compared to SoftGrid (square lattice with 4-neighbour connectivity),
// the hex grid is closer-packed and resists bending more isotropically.

#include "engine/sim/softbody/Physics.h"
#include <unordered_set>
#include <utility>
#include <vector>

namespace ekchous::softbody {

struct SoftHexGridSpec {
    float start_x = 100.0f;
    float start_y = 100.0f;
    int   nodes_x = 12;   // columns
    int   nodes_y = 10;   // rows
    float node_radius      = 5.0f;
    float spring_damp_inc  = 1.0f;
    float spring_damp_dec  = 1.0f;
    std::vector<std::pair<int,int>> pinned;  // (col, row) indices to pin
};

struct SoftHexGrid {
    int collision_group = 0;
    int nodes_x = 0;
    int nodes_y = 0;
    std::vector<int> particle_indices;  // [row * nodes_x + col]
    std::vector<int> spring_indices;

    int idx_of(int col, int row) const noexcept {
        if (col < 0 || col >= nodes_x || row < 0 || row >= nodes_y) return -1;
        return particle_indices[row * nodes_x + col];
    }
};

inline SoftHexGrid build_soft_hex_grid(Physics& physics, const SoftHexGridSpec& spec) {
    SoftHexGrid grid;
    grid.collision_group = physics.next_collision_group();
    grid.nodes_x = spec.nodes_x;
    grid.nodes_y = spec.nodes_y;
    grid.particle_indices.reserve(static_cast<std::size_t>(spec.nodes_x) * spec.nodes_y);

    // Cell spacing matched to node spacing so neighbours just touch.
    const float dx_h = spec.node_radius * 2.0f;
    const float dy_h = spec.node_radius * 1.732f;  // ≈ sqrt(3)
    for (int row = 0; row < spec.nodes_y; ++row) {
        const float row_offset = (row & 1) ? dx_h * 0.5f : 0.0f;
        for (int col = 0; col < spec.nodes_x; ++col) {
            const float x = spec.start_x + row_offset + col * dx_h;
            const float y = spec.start_y + row * dy_h;
            const int idx = physics.add_particle(x, y, spec.node_radius);
            physics.particles()[idx].collision_group = grid.collision_group;
            grid.particle_indices.push_back(idx);
        }
    }

    for (const auto& [pc, pr] : spec.pinned) {
        const int idx = grid.idx_of(pc, pr);
        if (idx < 0) continue;
        auto& p = physics.particles()[idx];
        p.enable_forces     = false;
        p.enable_springs    = false;
        p.enable_collisions = false;
    }

    std::unordered_set<long long> seen_pairs;
    auto encode = [](int a, int b) -> long long {
        const long long lo = a < b ? a : b;
        const long long hi = a < b ? b : a;
        return lo * 1'000'000LL + hi;
    };
    auto add_spring_at = [&](int ca, int ra, int cb, int rb) {
        const int a = grid.idx_of(ca, ra);
        const int b = grid.idx_of(cb, rb);
        if (a < 0 || b < 0 || a == b) return;
        const long long key = encode(a, b);
        if (!seen_pairs.insert(key).second) return;
        const int s_idx = physics.add_spring(a, b, SpringType::Struct);
        auto& s = physics.springs()[s_idx];
        s.damp_inc = spec.spring_damp_inc;
        s.damp_dec = spec.spring_damp_dec;
        grid.spring_indices.push_back(s_idx);
    };

    for (int row = 0; row < spec.nodes_y; ++row) {
        for (int col = 0; col < spec.nodes_x; ++col) {
            // Horizontal neighbours (same row).
            add_spring_at(col, row, col - 1, row);
            add_spring_at(col, row, col + 1, row);
            // Diagonals: offset depends on row parity for pointy-top hexes.
            // Even rows pick (col-1, row±1) and (col, row±1).
            // Odd rows pick  (col, row±1)   and (col+1, row±1).
            if ((row & 1) == 0) {
                add_spring_at(col, row, col - 1, row - 1);
                add_spring_at(col, row, col,     row - 1);
                add_spring_at(col, row, col - 1, row + 1);
                add_spring_at(col, row, col,     row + 1);
            } else {
                add_spring_at(col, row, col,     row - 1);
                add_spring_at(col, row, col + 1, row - 1);
                add_spring_at(col, row, col,     row + 1);
                add_spring_at(col, row, col + 1, row + 1);
            }
        }
    }
    return grid;
}

} // namespace ekchous::softbody
