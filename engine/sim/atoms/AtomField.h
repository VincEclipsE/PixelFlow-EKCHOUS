#pragma once

// EKCHOUS particle physics layer 4 — radius stamp / data projection.
//
// Each Particle "casts its data values as a radius check onto the cells
// below" (per the original concept). This grid holds the result:
//   intensity[cell]  = strongest weight any particle has put on that cell
//   dominant[cell]   = element_id of the particle that won that cell
//
// Falloff is linear from 1.0 at the particle centre to 0.0 at its
// projection radius (particle.rad × radius_mul). When two particles
// project onto the same cell, the one with the higher weight wins — i.e.
// the field is a Voronoi-flavoured nearest-particle map weighted by
// projected radius. Higher-radius atoms dominate larger regions; tightly
// packed clusters create solid coloured patches.
//
// rebuild() is O(N · r²) where r = max projection radius in cells.
// For typical scenes (< 500 particles, r < 16) this is well under a
// millisecond per call.

#include "engine/core/Types.h"
#include "engine/sim/atoms/AtomElement.h"
#include "engine/sim/softbody/Particle.h"
#include <algorithm>
#include <cmath>
#include <vector>

namespace ekchous::atoms {

class AtomField {
public:
    void resize(int nx, int ny, float cell_size) {
        nx_ = nx < 1 ? 1 : nx;
        ny_ = ny < 1 ? 1 : ny;
        cell_size_ = cell_size < 1.0f ? 1.0f : cell_size;
        const std::size_t n = static_cast<std::size_t>(nx_) * ny_;
        intensity_.assign(n, 0.0f);
        dominant_.assign(n, 0);
    }

    void clear() noexcept {
        std::fill(intensity_.begin(), intensity_.end(), 0.0f);
        std::fill(dominant_.begin(), dominant_.end(), 0);
    }

    // Stamp every particle's element into cells within particle.rad *
    // radius_mul of its centre. Linear-falloff weight; the highest-weight
    // particle wins each cell.
    void rebuild(const std::vector<softbody::Particle>& parts, float radius_mul) {
        if (nx_ == 0 || ny_ == 0) return;
        clear();
        if (radius_mul <= 0.0f) return;
        for (const auto& p : parts) {
            const float r_world = p.rad * radius_mul;
            if (r_world <= 0.0f) continue;
            const float r_cells = r_world / cell_size_;
            if (r_cells <= 0.0f) continue;
            const float cx_cell = p.cx / cell_size_;
            const float cy_cell = p.cy / cell_size_;
            int x0 = static_cast<int>(std::floor(cx_cell - r_cells));
            int y0 = static_cast<int>(std::floor(cy_cell - r_cells));
            int x1 = static_cast<int>(std::ceil (cx_cell + r_cells));
            int y1 = static_cast<int>(std::ceil (cy_cell + r_cells));
            if (x0 < 0)        x0 = 0;
            if (y0 < 0)        y0 = 0;
            if (x1 > nx_ - 1)  x1 = nx_ - 1;
            if (y1 > ny_ - 1)  y1 = ny_ - 1;
            for (int y = y0; y <= y1; ++y) {
                for (int x = x0; x <= x1; ++x) {
                    const float dx = (x + 0.5f) - cx_cell;
                    const float dy = (y + 0.5f) - cy_cell;
                    const float d  = std::sqrt(dx * dx + dy * dy);
                    if (d > r_cells) continue;
                    const float w = 1.0f - d / r_cells;
                    const std::size_t i =
                        static_cast<std::size_t>(y) * nx_ + x;
                    if (w > intensity_[i]) {
                        intensity_[i] = w;
                        dominant_[i]  = p.element_id;
                    }
                }
            }
        }
    }

    int nx()        const noexcept { return nx_; }
    int ny()        const noexcept { return ny_; }
    float cell_size() const noexcept { return cell_size_; }
    const std::vector<float>&   intensity() const noexcept { return intensity_; }
    const std::vector<core::u8>& dominant() const noexcept { return dominant_; }

private:
    int   nx_        = 0;
    int   ny_        = 0;
    float cell_size_ = 16.0f;
    std::vector<float>   intensity_;
    std::vector<core::u8> dominant_;
};

} // namespace ekchous::atoms
