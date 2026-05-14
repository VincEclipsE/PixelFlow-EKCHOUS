#pragma once

// 2D Poisson disc sampling (Bridson's algorithm). Port of PixelFlow's
// PoissonDiscSamping2D — same idea, header-only and stripped of the
// Java-isms.
//
// Generates points uniformly distributed in a circular region such that no
// two points are closer than `min_dist`. Deterministic in `seed`.

#include <cmath>
#include <cstdint>
#include <utility>
#include <vector>

namespace ekchous::softbody {

struct PoissonDiscParams {
    float center_x   = 0.0f;
    float center_y   = 0.0f;
    float radius     = 100.0f;   // sample within this circular region
    float min_dist   = 12.0f;    // minimum separation between any two samples
    int   k_tries    = 30;       // attempts per active point (Bridson uses 30)
    int   max_points = 10000;    // safety cap
    std::uint32_t seed = 0xDEADBEEFu;
};

inline std::vector<std::pair<float, float>>
poisson_disc_sample(const PoissonDiscParams& params) {
    std::vector<std::pair<float, float>> points;
    if (params.min_dist <= 0.0f || params.radius <= 0.0f) return points;

    // Spatial grid: cell size = min_dist / sqrt(2) guarantees ≤1 point per cell.
    const float cell = params.min_dist / std::sqrt(2.0f);
    const float region_min_x = params.center_x - params.radius;
    const float region_min_y = params.center_y - params.radius;
    const int   grid_w = static_cast<int>(std::ceil(2.0f * params.radius / cell)) + 1;
    const int   grid_h = grid_w;
    std::vector<int> grid(static_cast<std::size_t>(grid_w) * grid_h, -1);

    auto cell_of = [&](float x, float y) -> std::pair<int, int> {
        int cx = static_cast<int>((x - region_min_x) / cell);
        int cy = static_cast<int>((y - region_min_y) / cell);
        if (cx < 0)        cx = 0;
        else if (cx >= grid_w) cx = grid_w - 1;
        if (cy < 0)        cy = 0;
        else if (cy >= grid_h) cy = grid_h - 1;
        return {cx, cy};
    };

    auto in_region = [&](float x, float y) -> bool {
        const float dx = x - params.center_x;
        const float dy = y - params.center_y;
        return dx * dx + dy * dy <= params.radius * params.radius;
    };

    auto neighbor_within = [&](float x, float y, float dist) -> bool {
        const auto [cx, cy] = cell_of(x, y);
        const float dist_sq = dist * dist;
        for (int dy = -2; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                const int nx = cx + dx;
                const int ny = cy + dy;
                if (nx < 0 || nx >= grid_w || ny < 0 || ny >= grid_h) continue;
                const int idx = grid[static_cast<std::size_t>(ny) * grid_w + nx];
                if (idx < 0) continue;
                const float ddx = points[idx].first  - x;
                const float ddy = points[idx].second - y;
                if (ddx * ddx + ddy * ddy < dist_sq) return true;
            }
        }
        return false;
    };

    // Tiny xorshift RNG for deterministic [0, 1) draws.
    std::uint32_t rng = params.seed ? params.seed : 1u;
    auto rand_unit = [&]() -> float {
        std::uint32_t x = rng;
        x ^= x << 13;
        x ^= x >> 17;
        x ^= x << 5;
        rng = x;
        return static_cast<float>(x & 0x00FFFFFFu) / static_cast<float>(0x01000000u);
    };

    // Seed point at the centre of the region.
    points.emplace_back(params.center_x, params.center_y);
    {
        const auto [cx0, cy0] = cell_of(params.center_x, params.center_y);
        grid[static_cast<std::size_t>(cy0) * grid_w + cx0] = 0;
    }
    std::vector<int> active{0};

    while (!active.empty() && static_cast<int>(points.size()) < params.max_points) {
        const int ai = static_cast<int>(rand_unit() * active.size());
        const int parent_idx = active[ai];
        const float px = points[parent_idx].first;
        const float py = points[parent_idx].second;
        bool placed = false;
        for (int attempt = 0; attempt < params.k_tries; ++attempt) {
            const float angle = rand_unit() * 6.28318530718f;
            const float dist  = params.min_dist * (1.0f + rand_unit());
            const float cx = px + std::cos(angle) * dist;
            const float cy = py + std::sin(angle) * dist;
            if (!in_region(cx, cy)) continue;
            if (neighbor_within(cx, cy, params.min_dist)) continue;
            const int new_idx = static_cast<int>(points.size());
            points.emplace_back(cx, cy);
            const auto [gx, gy] = cell_of(cx, cy);
            grid[static_cast<std::size_t>(gy) * grid_w + gx] = new_idx;
            active.push_back(new_idx);
            placed = true;
            break;
        }
        if (!placed) {
            active[ai] = active.back();
            active.pop_back();
        }
    }

    return points;
}

} // namespace ekchous::softbody
