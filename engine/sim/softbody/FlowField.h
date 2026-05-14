#pragma once

// 2D vector field. Particles sampled at a given world position pick up a
// (vx, vy) nudge that's fed into Physics as a force.
//
// Spiritual port of PixelFlow's DwFlowField + the flowfieldparticles
// advection idea. PixelFlow stores its field on the GPU (in a texture)
// and runs the integration in a compute shader; we keep it on the CPU
// and run it inline so the rest of the engine stays simple.

#include "engine/sim/softbody/Particle.h"
#include <algorithm>
#include <cmath>
#include <vector>

namespace ekchous::softbody {

class FlowField {
public:
    FlowField() = default;
    FlowField(int nx, int ny, float cell_size) { resize(nx, ny, cell_size); }

    void resize(int nx, int ny, float cell_size) {
        nx_ = nx < 1 ? 1 : nx;
        ny_ = ny < 1 ? 1 : ny;
        cell_size_ = cell_size < 0.5f ? 0.5f : cell_size;
        const std::size_t n = static_cast<std::size_t>(nx_) * ny_;
        vx_.assign(n, 0.0f);
        vy_.assign(n, 0.0f);
    }

    void clear() {
        std::fill(vx_.begin(), vx_.end(), 0.0f);
        std::fill(vy_.begin(), vy_.end(), 0.0f);
    }

    int   nx()        const noexcept { return nx_; }
    int   ny()        const noexcept { return ny_; }
    float cell_size() const noexcept { return cell_size_; }
    const std::vector<float>& vx() const noexcept { return vx_; }
    const std::vector<float>& vy() const noexcept { return vy_; }

    void set_cell(int x, int y, float vx, float vy) noexcept {
        if (x < 0 || x >= nx_ || y < 0 || y >= ny_) return;
        const std::size_t i = static_cast<std::size_t>(y) * nx_ + x;
        vx_[i] = vx;
        vy_[i] = vy;
    }

    void add_to_cell(int x, int y, float dvx, float dvy) noexcept {
        if (x < 0 || x >= nx_ || y < 0 || y >= ny_) return;
        const std::size_t i = static_cast<std::size_t>(y) * nx_ + x;
        vx_[i] += dvx;
        vy_[i] += dvy;
    }

    // Stamp a falloff disc of (dvx, dvy) into the field around world (wx, wy).
    // Falloff is 1 at the centre and tapers linearly to 0 at radius. Used for
    // mouse painting.
    void stamp(float wx, float wy, float radius, float dvx, float dvy) noexcept {
        if (radius <= 0.0f) return;
        const float cx = wx / cell_size_;
        const float cy = wy / cell_size_;
        const float rc = radius / cell_size_;
        const int x_min = std::max(0,        static_cast<int>(std::floor(cx - rc)));
        const int y_min = std::max(0,        static_cast<int>(std::floor(cy - rc)));
        const int x_max = std::min(nx_ - 1,  static_cast<int>(std::ceil (cx + rc)));
        const int y_max = std::min(ny_ - 1,  static_cast<int>(std::ceil (cy + rc)));
        const float rc2 = rc * rc;
        for (int y = y_min; y <= y_max; ++y) {
            for (int x = x_min; x <= x_max; ++x) {
                const float dx = x + 0.5f - cx;
                const float dy = y + 0.5f - cy;
                const float d2 = dx*dx + dy*dy;
                if (d2 > rc2) continue;
                const float t = 1.0f - std::sqrt(d2) / rc;
                add_to_cell(x, y, dvx * t, dvy * t);
            }
        }
    }

    // Bilinear sample at world (wx, wy). Out-of-bounds clamps to edge cells.
    void sample(float wx, float wy, float& out_vx, float& out_vy) const noexcept {
        const float fx = wx / cell_size_ - 0.5f;
        const float fy = wy / cell_size_ - 0.5f;
        int x0 = static_cast<int>(std::floor(fx));
        int y0 = static_cast<int>(std::floor(fy));
        const float tx = fx - x0;
        const float ty = fy - y0;
        const int x1 = x0 + 1;
        const int y1 = y0 + 1;
        auto at = [&](int x, int y) -> std::size_t {
            if (x < 0)        x = 0;
            else if (x >= nx_) x = nx_ - 1;
            if (y < 0)        y = 0;
            else if (y >= ny_) y = ny_ - 1;
            return static_cast<std::size_t>(y) * nx_ + x;
        };
        const std::size_t i00 = at(x0, y0);
        const std::size_t i10 = at(x1, y0);
        const std::size_t i01 = at(x0, y1);
        const std::size_t i11 = at(x1, y1);
        const float top_x = vx_[i00] * (1 - tx) + vx_[i10] * tx;
        const float top_y = vy_[i00] * (1 - tx) + vy_[i10] * tx;
        const float bot_x = vx_[i01] * (1 - tx) + vx_[i11] * tx;
        const float bot_y = vy_[i01] * (1 - tx) + vy_[i11] * tx;
        out_vx = top_x * (1 - ty) + bot_x * ty;
        out_vy = top_y * (1 - ty) + bot_y * ty;
    }

    // Apply this tick's field force to every particle. The sampled field
    // vector is multiplied by `strength` and pushed through Particle::add_force
    // (so heavier nodes resist it).
    void apply_to(std::vector<Particle>& particles, float strength) const noexcept {
        if (strength == 0.0f) return;
        for (auto& p : particles) {
            if (!p.enable_forces) continue;
            float fx, fy;
            sample(p.cx, p.cy, fx, fy);
            p.add_force(fx * strength, fy * strength);
        }
    }

    // ----- Preset generators (overwrite the entire field) -----

    void make_wind(float vx, float vy) {
        for (std::size_t i = 0; i < vx_.size(); ++i) {
            vx_[i] = vx;
            vy_[i] = vy;
        }
    }

    void make_vortex(float center_wx, float center_wy, float strength) {
        for (int y = 0; y < ny_; ++y) {
            for (int x = 0; x < nx_; ++x) {
                const float wx = (x + 0.5f) * cell_size_;
                const float wy = (y + 0.5f) * cell_size_;
                const float dx = wx - center_wx;
                const float dy = wy - center_wy;
                const float dist = std::sqrt(dx*dx + dy*dy) + 1e-3f;
                // Tangential direction: rotate (dx, dy) by 90°.
                const float tx = -dy / dist;
                const float ty =  dx / dist;
                const std::size_t i = static_cast<std::size_t>(y) * nx_ + x;
                vx_[i] = tx * strength;
                vy_[i] = ty * strength;
            }
        }
    }

    void make_well(float center_wx, float center_wy, float strength) {
        for (int y = 0; y < ny_; ++y) {
            for (int x = 0; x < nx_; ++x) {
                const float wx = (x + 0.5f) * cell_size_;
                const float wy = (y + 0.5f) * cell_size_;
                const float dx = center_wx - wx;
                const float dy = center_wy - wy;
                const float dist = std::sqrt(dx*dx + dy*dy) + 1e-3f;
                const float nx = dx / dist;
                const float ny = dy / dist;
                const std::size_t i = static_cast<std::size_t>(y) * nx_ + x;
                vx_[i] = nx * strength;
                vy_[i] = ny * strength;
            }
        }
    }

private:
    int   nx_ = 0;
    int   ny_ = 0;
    float cell_size_ = 16.0f;
    std::vector<float> vx_;
    std::vector<float> vy_;
};

} // namespace ekchous::softbody
