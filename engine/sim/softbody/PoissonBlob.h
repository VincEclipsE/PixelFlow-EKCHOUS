#pragma once

// Builder helper: spawn an organic-shaped softbody by Poisson-sampling
// particles within a circular region and connecting each to its K nearest
// neighbours via structural springs.
//
// Visually distinct from SoftBall (regular ring) and SoftGrid (regular
// lattice): the topology is random but well-distributed, so the body
// behaves like a stiff blob.

#include "engine/sim/softbody/Physics.h"
#include "engine/sim/softbody/PoissonDiscSampling.h"
#include <algorithm>
#include <cstdint>
#include <limits>
#include <utility>
#include <vector>

namespace ekchous::softbody {

struct PoissonBlobSpec {
    float center_x = 0.0f;
    float center_y = 0.0f;
    float radius            = 90.0f;
    float node_radius       = 5.0f;
    float min_node_distance = 14.0f;  // Poisson min_dist
    int   neighbors_per_node = 5;
    float spring_damp_inc = 1.0f;
    float spring_damp_dec = 1.0f;
    std::uint32_t seed = 0xC0FFEEu;
};

struct PoissonBlob {
    int collision_group = 0;
    std::vector<int> particle_indices;
    std::vector<int> spring_indices;
};

inline PoissonBlob build_poisson_blob(Physics& physics, const PoissonBlobSpec& spec) {
    PoissonBlob blob;
    blob.collision_group = physics.next_collision_group();

    PoissonDiscParams pdp;
    pdp.center_x = spec.center_x;
    pdp.center_y = spec.center_y;
    pdp.radius   = spec.radius;
    pdp.min_dist = spec.min_node_distance;
    pdp.seed     = spec.seed;
    const auto points = poisson_disc_sample(pdp);
    if (points.empty()) return blob;

    blob.particle_indices.reserve(points.size());
    for (const auto& [x, y] : points) {
        const int idx = physics.add_particle(x, y, spec.node_radius);
        physics.particles()[idx].collision_group = blob.collision_group;
        blob.particle_indices.push_back(idx);
    }

    // For each particle, find its K nearest neighbours and add struct
    // springs. Dedup via canonical (lo, hi) ordering on the global particle
    // indices so each pair is only inserted once.
    const int n = static_cast<int>(points.size());
    const int k = std::min(spec.neighbors_per_node, n - 1);
    if (k <= 0) return blob;

    std::vector<std::pair<float, int>> dists;
    dists.resize(n);
    for (int i = 0; i < n; ++i) {
        for (int j = 0; j < n; ++j) {
            if (j == i) {
                dists[j] = {std::numeric_limits<float>::max(), j};
                continue;
            }
            const float dx = points[j].first  - points[i].first;
            const float dy = points[j].second - points[i].second;
            dists[j] = {dx * dx + dy * dy, j};
        }
        std::partial_sort(
            dists.begin(), dists.begin() + k, dists.end(),
            [](const std::pair<float, int>& a, const std::pair<float, int>& b) {
                return a.first < b.first;
            });
        for (int p = 0; p < k; ++p) {
            const int j = dists[p].second;
            const int a_idx = blob.particle_indices[i];
            const int b_idx = blob.particle_indices[j];
            // Canonical pair ordering — only add (smaller_idx, larger_idx).
            if (a_idx >= b_idx) continue;
            const int s_idx = physics.add_spring(a_idx, b_idx, SpringType::Struct);
            auto& s = physics.springs()[s_idx];
            s.damp_inc = spec.spring_damp_inc;
            s.damp_dec = spec.spring_damp_dec;
            blob.spring_indices.push_back(s_idx);
        }
    }

    return blob;
}

} // namespace ekchous::softbody
