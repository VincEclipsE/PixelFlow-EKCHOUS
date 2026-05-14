#pragma once

// Streamline visualization for a Fluid2D field. Spiritual port of
// PixelFlow's DwFluidStreamLines2D — minus the GPU geometry-shader path.
//
// A grid of seed points; each frame, every seed traces a forward polyline
// by stepping along the normalized fluid velocity. Each step is a fixed
// canvas-pixel distance, so paths visually space out evenly regardless
// of velocity magnitude.

#include "engine/sim/softbody/Fluid2D.h"
#include <cmath>
#include <utility>
#include <vector>

namespace ekchous::softbody {

struct Streamline {
    std::vector<float> xs;
    std::vector<float> ys;
};

class Streamlines {
public:
    Streamlines() = default;

    // Lay out a `cols × rows` seed grid filling the canvas.
    void resize_seeds(int cols, int rows, float canvas_w, float canvas_h) {
        seeds_.clear();
        if (cols < 1) cols = 1;
        if (rows < 1) rows = 1;
        seeds_.reserve(static_cast<std::size_t>(cols) * rows);
        for (int r = 0; r < rows; ++r) {
            for (int c = 0; c < cols; ++c) {
                seeds_.emplace_back(
                    (c + 0.5f) / cols * canvas_w,
                    (r + 0.5f) / rows * canvas_h);
            }
        }
    }

    // Re-trace each line from its seed.
    //   steps_per_line: max polyline vertices
    //   step_size:      pixels of canvas per step
    void update(const Fluid2D& fluid, int steps_per_line, float step_size,
                float canvas_w, float canvas_h) {
        lines_.clear();
        if (fluid.n() <= 0 || seeds_.empty()) return;
        lines_.resize(seeds_.size());
        for (std::size_t i = 0; i < seeds_.size(); ++i) {
            auto& line = lines_[i];
            line.xs.clear();
            line.ys.clear();
            line.xs.reserve(steps_per_line);
            line.ys.reserve(steps_per_line);
            float x = seeds_[i].first;
            float y = seeds_[i].second;
            for (int s = 0; s < steps_per_line; ++s) {
                line.xs.push_back(x);
                line.ys.push_back(y);
                float u, v;
                fluid.sample_velocity(x, y, canvas_w, canvas_h, u, v);
                const float mag = std::sqrt(u*u + v*v);
                if (mag < 1e-5f) break;
                x += (u / mag) * step_size;
                y += (v / mag) * step_size;
                if (x < 0.0f || x >= canvas_w || y < 0.0f || y >= canvas_h) break;
            }
        }
    }

    void clear() {
        lines_.clear();
        seeds_.clear();
    }

    const std::vector<Streamline>& lines() const noexcept { return lines_; }
    int seed_count() const noexcept { return static_cast<int>(seeds_.size()); }

private:
    std::vector<std::pair<float, float>> seeds_;
    std::vector<Streamline> lines_;
};

} // namespace ekchous::softbody
