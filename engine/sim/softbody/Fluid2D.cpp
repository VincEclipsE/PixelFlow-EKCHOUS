#include "engine/sim/softbody/Fluid2D.h"
#include <algorithm>
#include <cmath>

namespace ekchous::softbody {

void Fluid2D::resize(int n) {
    N_ = n < 4 ? 4 : n;
    const std::size_t size = static_cast<std::size_t>(N_ + 2) * (N_ + 2);
    u_.assign(size, 0.0f);
    v_.assign(size, 0.0f);
    u_prev_.assign(size, 0.0f);
    v_prev_.assign(size, 0.0f);
    dens_.assign(size, 0.0f);
    dens_prev_.assign(size, 0.0f);
    temp_.assign(size, 0.0f);
    temp_prev_.assign(size, 0.0f);
    curl_.assign(size, 0.0f);
    curl_mag_.assign(size, 0.0f);
    // Resize obstacle mask but keep it conceptually empty (all zeros).
    if (!obstacle_.empty()) obstacle_.assign(size, 0);
}

void Fluid2D::set_obstacles(const std::vector<core::u8>& mask) {
    const std::size_t size = static_cast<std::size_t>(N_ + 2) * (N_ + 2);
    if (mask.size() == size) {
        obstacle_ = mask;
    } else if (mask.empty()) {
        obstacle_.clear();
    }
}

void Fluid2D::clear_obstacles() noexcept {
    obstacle_.clear();
}

void Fluid2D::apply_obstacles_to(std::vector<float>& field) {
    if (obstacle_.empty()) return;
    for (std::size_t i = 0; i < field.size() && i < obstacle_.size(); ++i) {
        if (obstacle_[i]) field[i] = 0.0f;
    }
}

void Fluid2D::clear() noexcept {
    std::fill(u_.begin(), u_.end(), 0.0f);
    std::fill(v_.begin(), v_.end(), 0.0f);
    std::fill(u_prev_.begin(), u_prev_.end(), 0.0f);
    std::fill(v_prev_.begin(), v_prev_.end(), 0.0f);
    std::fill(dens_.begin(), dens_.end(), 0.0f);
    std::fill(dens_prev_.begin(), dens_prev_.end(), 0.0f);
    std::fill(temp_.begin(), temp_.end(), 0.0f);
    std::fill(temp_prev_.begin(), temp_prev_.end(), 0.0f);
}

namespace {
inline int world_to_cell(float w, float canvas_dim, int N) noexcept {
    if (canvas_dim <= 0.0f) return 1;
    const float t = w / canvas_dim;
    int c = static_cast<int>(t * N) + 1;
    if (c < 1) c = 1;
    if (c > N) c = N;
    return c;
}
}

void Fluid2D::add_density_at(float wx, float wy,
                              float canvas_w, float canvas_h,
                              float amount) noexcept {
    if (N_ <= 0) return;
    const int i = world_to_cell(wx, canvas_w, N_);
    const int j = world_to_cell(wy, canvas_h, N_);
    dens_prev_[IX(i, j)] += amount;
}

void Fluid2D::add_force_at(float wx, float wy,
                            float canvas_w, float canvas_h,
                            float fx, float fy) noexcept {
    if (N_ <= 0) return;
    const int i = world_to_cell(wx, canvas_w, N_);
    const int j = world_to_cell(wy, canvas_h, N_);
    u_prev_[IX(i, j)] += fx;
    v_prev_[IX(i, j)] += fy;
}

void Fluid2D::add_heat_at(float wx, float wy,
                           float canvas_w, float canvas_h,
                           float amount) noexcept {
    if (N_ <= 0) return;
    const int i = world_to_cell(wx, canvas_w, N_);
    const int j = world_to_cell(wy, canvas_h, N_);
    temp_prev_[IX(i, j)] += amount;
}

float Fluid2D::sample_temperature(float wx, float wy,
                                   float canvas_w, float canvas_h) const noexcept {
    if (N_ <= 0 || canvas_w <= 0.0f || canvas_h <= 0.0f) return 0.0f;
    const float fx = wx / canvas_w * N_ + 0.5f;
    const float fy = wy / canvas_h * N_ + 0.5f;
    int i0 = static_cast<int>(std::floor(fx));
    int j0 = static_cast<int>(std::floor(fy));
    if (i0 < 0)        i0 = 0;
    else if (i0 > N_)  i0 = N_;
    if (j0 < 0)        j0 = 0;
    else if (j0 > N_)  j0 = N_;
    const int i1 = i0 + 1 > N_ + 1 ? N_ + 1 : i0 + 1;
    const int j1 = j0 + 1 > N_ + 1 ? N_ + 1 : j0 + 1;
    const float tx = fx - i0;
    const float ty = fy - j0;
    const float a = temp_[IX(i0, j0)] * (1 - tx) + temp_[IX(i1, j0)] * tx;
    const float b = temp_[IX(i0, j1)] * (1 - tx) + temp_[IX(i1, j1)] * tx;
    return a * (1 - ty) + b * ty;
}

float Fluid2D::sample_density(float wx, float wy,
                               float canvas_w, float canvas_h) const noexcept {
    if (N_ <= 0 || canvas_w <= 0.0f || canvas_h <= 0.0f) return 0.0f;
    const float fx = wx / canvas_w * N_ + 0.5f;
    const float fy = wy / canvas_h * N_ + 0.5f;
    int i0 = static_cast<int>(std::floor(fx));
    int j0 = static_cast<int>(std::floor(fy));
    if (i0 < 0)        i0 = 0;
    else if (i0 > N_)  i0 = N_;
    if (j0 < 0)        j0 = 0;
    else if (j0 > N_)  j0 = N_;
    const int i1 = i0 + 1 > N_ + 1 ? N_ + 1 : i0 + 1;
    const int j1 = j0 + 1 > N_ + 1 ? N_ + 1 : j0 + 1;
    const float tx = fx - i0;
    const float ty = fy - j0;
    const float a = dens_[IX(i0, j0)] * (1 - tx) + dens_[IX(i1, j0)] * tx;
    const float b = dens_[IX(i0, j1)] * (1 - tx) + dens_[IX(i1, j1)] * tx;
    return a * (1 - ty) + b * ty;
}

void Fluid2D::sample_velocity(float wx, float wy,
                               float canvas_w, float canvas_h,
                               float& out_u, float& out_v) const noexcept {
    if (N_ <= 0 || canvas_w <= 0.0f || canvas_h <= 0.0f) {
        out_u = out_v = 0.0f;
        return;
    }
    // Convert world to cell-space, with the inner grid covering [0.5, N+0.5].
    const float fx = wx / canvas_w * N_ + 0.5f;
    const float fy = wy / canvas_h * N_ + 0.5f;
    int i0 = static_cast<int>(std::floor(fx));
    int j0 = static_cast<int>(std::floor(fy));
    if (i0 < 0)        i0 = 0;
    else if (i0 > N_)  i0 = N_;
    if (j0 < 0)        j0 = 0;
    else if (j0 > N_)  j0 = N_;
    const int i1 = i0 + 1 > N_ + 1 ? N_ + 1 : i0 + 1;
    const int j1 = j0 + 1 > N_ + 1 ? N_ + 1 : j0 + 1;
    const float tx = fx - i0;
    const float ty = fy - j0;
    auto blerp = [&](const std::vector<float>& g) -> float {
        const float a = g[IX(i0, j0)] * (1 - tx) + g[IX(i1, j0)] * tx;
        const float b = g[IX(i0, j1)] * (1 - tx) + g[IX(i1, j1)] * tx;
        return a * (1 - ty) + b * ty;
    };
    out_u = blerp(u_);
    out_v = blerp(v_);
}

void Fluid2D::add_source(std::vector<float>& x, const std::vector<float>& s, float dt) {
    for (std::size_t i = 0; i < x.size(); ++i) x[i] += dt * s[i];
}

void Fluid2D::set_bnd(int b, std::vector<float>& x) {
    for (int i = 1; i <= N_; ++i) {
        x[IX(0,     i)]    = b == 1 ? -x[IX(1,     i)] : x[IX(1,  i)];
        x[IX(N_ + 1, i)]   = b == 1 ? -x[IX(N_,    i)] : x[IX(N_, i)];
        x[IX(i,     0)]    = b == 2 ? -x[IX(i,     1)] : x[IX(i,  1)];
        x[IX(i, N_ + 1)]   = b == 2 ? -x[IX(i,    N_)] : x[IX(i, N_)];
    }
    x[IX(0,       0)]       = 0.5f * (x[IX(1,       0)]       + x[IX(0,       1)]);
    x[IX(0,       N_ + 1)]  = 0.5f * (x[IX(1,       N_ + 1)]  + x[IX(0,       N_)]);
    x[IX(N_ + 1,  0)]       = 0.5f * (x[IX(N_,      0)]       + x[IX(N_ + 1,  1)]);
    x[IX(N_ + 1,  N_ + 1)]  = 0.5f * (x[IX(N_,      N_ + 1)]  + x[IX(N_ + 1,  N_)]);
}

void Fluid2D::lin_solve(int b, std::vector<float>& x, const std::vector<float>& x0,
                         float a, float c) {
    const int iters = iterations > 0 ? iterations : 20;
    for (int k = 0; k < iters; ++k) {
        for (int j = 1; j <= N_; ++j) {
            for (int i = 1; i <= N_; ++i) {
                x[IX(i, j)] = (x0[IX(i, j)] +
                               a * (x[IX(i - 1, j)] + x[IX(i + 1, j)] +
                                    x[IX(i, j - 1)] + x[IX(i, j + 1)])) / c;
            }
        }
        set_bnd(b, x);
    }
}

void Fluid2D::diffuse(int b, std::vector<float>& x, const std::vector<float>& x0,
                       float diff, float dt) {
    const float a = dt * diff * N_ * N_;
    lin_solve(b, x, x0, a, 1.0f + 4.0f * a);
}

void Fluid2D::advect(int b, std::vector<float>& d, const std::vector<float>& d0,
                      const std::vector<float>& u, const std::vector<float>& v,
                      float dt) {
    const float dt0 = dt * N_;
    for (int j = 1; j <= N_; ++j) {
        for (int i = 1; i <= N_; ++i) {
            float x = i - dt0 * u[IX(i, j)];
            float y = j - dt0 * v[IX(i, j)];
            if (x < 0.5f)         x = 0.5f;
            if (x > N_ + 0.5f)    x = N_ + 0.5f;
            const int i0 = static_cast<int>(x);
            const int i1 = i0 + 1;
            if (y < 0.5f)         y = 0.5f;
            if (y > N_ + 0.5f)    y = N_ + 0.5f;
            const int j0 = static_cast<int>(y);
            const int j1 = j0 + 1;
            const float s1 = x - i0;
            const float s0 = 1.0f - s1;
            const float t1 = y - j0;
            const float t0 = 1.0f - t1;
            d[IX(i, j)] = s0 * (t0 * d0[IX(i0, j0)] + t1 * d0[IX(i0, j1)]) +
                          s1 * (t0 * d0[IX(i1, j0)] + t1 * d0[IX(i1, j1)]);
        }
    }
    set_bnd(b, d);
}

void Fluid2D::project(std::vector<float>& u, std::vector<float>& v,
                       std::vector<float>& p, std::vector<float>& div) {
    for (int j = 1; j <= N_; ++j) {
        for (int i = 1; i <= N_; ++i) {
            div[IX(i, j)] = -0.5f *
                (u[IX(i + 1, j)] - u[IX(i - 1, j)] +
                 v[IX(i, j + 1)] - v[IX(i, j - 1)]) / N_;
            p[IX(i, j)] = 0.0f;
        }
    }
    set_bnd(0, div);
    set_bnd(0, p);
    lin_solve(0, p, div, 1.0f, 4.0f);
    for (int j = 1; j <= N_; ++j) {
        for (int i = 1; i <= N_; ++i) {
            u[IX(i, j)] -= 0.5f * N_ * (p[IX(i + 1, j)] - p[IX(i - 1, j)]);
            v[IX(i, j)] -= 0.5f * N_ * (p[IX(i, j + 1)] - p[IX(i, j - 1)]);
        }
    }
    set_bnd(1, u);
    set_bnd(2, v);
}

void Fluid2D::vel_step(float dt, float visc) {
    add_source(u_, u_prev_, dt);
    add_source(v_, v_prev_, dt);
    std::swap(u_, u_prev_); diffuse(1, u_, u_prev_, visc, dt);
    std::swap(v_, v_prev_); diffuse(2, v_, v_prev_, visc, dt);
    project(u_, v_, u_prev_, v_prev_);
    std::swap(u_, u_prev_); std::swap(v_, v_prev_);
    advect(1, u_, u_prev_, u_prev_, v_prev_, dt);
    advect(2, v_, v_prev_, u_prev_, v_prev_, dt);
    project(u_, v_, u_prev_, v_prev_);
}

void Fluid2D::dens_step(float dt, float diff) {
    add_source(dens_, dens_prev_, dt);
    std::swap(dens_, dens_prev_);
    diffuse(0, dens_, dens_prev_, diff, dt);
    std::swap(dens_, dens_prev_);
    advect(0, dens_, dens_prev_, u_, v_, dt);
}

void Fluid2D::temp_step(float dt, float diff) {
    add_source(temp_, temp_prev_, dt);
    std::swap(temp_, temp_prev_);
    diffuse(0, temp_, temp_prev_, diff, dt);
    std::swap(temp_, temp_prev_);
    advect(0, temp_, temp_prev_, u_, v_, dt);
}

void Fluid2D::apply_vorticity_confinement(float dt) {
    if (vorticity_eps <= 0.0f) return;
    // 1) curl[i,j] = dv/dx - du/dy (central differences).
    for (int j = 1; j <= N_; ++j) {
        for (int i = 1; i <= N_; ++i) {
            const float dvdx = 0.5f * (v_[IX(i + 1, j)] - v_[IX(i - 1, j)]);
            const float dudy = 0.5f * (u_[IX(i, j + 1)] - u_[IX(i, j - 1)]);
            curl_[IX(i, j)] = dvdx - dudy;
            curl_mag_[IX(i, j)] = std::fabs(curl_[IX(i, j)]);
        }
    }
    // 2) Gradient of |curl|, normalize, cross with curl, add as force.
    for (int j = 2; j <= N_ - 1; ++j) {
        for (int i = 2; i <= N_ - 1; ++i) {
            float dx = 0.5f * (curl_mag_[IX(i + 1, j)] - curl_mag_[IX(i - 1, j)]);
            float dy = 0.5f * (curl_mag_[IX(i, j + 1)] - curl_mag_[IX(i, j - 1)]);
            const float len = std::sqrt(dx * dx + dy * dy) + 1e-5f;
            dx /= len;
            dy /= len;
            const float w = curl_[IX(i, j)];
            // Force = eps * h * (N.y * w, -N.x * w); using h = 1/N
            u_[IX(i, j)] += vorticity_eps * (dy * w) * dt;
            v_[IX(i, j)] += vorticity_eps * (-dx * w) * dt;
        }
    }
    set_bnd(1, u_);
    set_bnd(2, v_);
}

void Fluid2D::apply_buoyancy_and_gravity(float dt) {
    if (buoyancy == 0.0f && gravity_density_coeff == 0.0f) return;
    // Fedkiw-Stam-Jensen 2001 eq. 8:  f_y = -α·s + β·(T - T_amb)
    //   β term (buoyancy):  negative when hot → upward in screen-y
    //   α term (gravity):   positive scaled by density → downward
    for (int j = 1; j <= N_; ++j) {
        for (int i = 1; i <= N_; ++i) {
            const float f  = -buoyancy * (temp_[IX(i, j)] - ambient_temperature);
            const float gy = gravity_density_coeff * mass_density * dens_[IX(i, j)];
            v_[IX(i, j)] += (f + gy) * dt;
        }
    }
    set_bnd(2, v_);
}

void Fluid2D::step(float dt, float visc, float diff) {
    if (N_ <= 0) return;
    // Temperature drives buoyancy → must be advected with the current
    // velocity field BEFORE we project velocity, so heat-from-prev-frame
    // applies its buoyant force this frame.
    temp_step(dt, diff);
    apply_buoyancy_and_gravity(dt);
    apply_vorticity_confinement(dt);
    vel_step(dt, visc);
    dens_step(dt, diff);

    // Hard ceiling — zero density above (smaller j than) the configured
    // threshold. Done after dens_step so it overrides whatever the advection
    // just placed there. Default off, runs only if min y > 0.
    if (ceiling_enabled && N_ > 0 && ceiling_min_y_norm > 0.0f) {
        int j_thresh = static_cast<int>(ceiling_min_y_norm * N_);
        if (j_thresh < 1) j_thresh = 1;
        if (j_thresh > N_ + 1) j_thresh = N_ + 1;
        for (int j = 1; j < j_thresh; ++j) {
            for (int i = 1; i <= N_; ++i) dens_[IX(i, j)] = 0.0f;
        }
    }

    // Density / temperature dissipation. dt is small (~0.1), so a per-tick
    // multiplier of (1 - rate*dt) gives smooth exponential decay.
    if (dissipation > 0.0f) {
        const float k = 1.0f - dissipation * dt;
        const float mul = k < 0.0f ? 0.0f : k;
        for (auto& d : dens_) d *= mul;
    }
    if (temperature_dissipation > 0.0f) {
        const float k = 1.0f - temperature_dissipation * dt;
        const float mul = k < 0.0f ? 0.0f : k;
        for (auto& t : temp_) t *= mul;
    }

    // Zero out solver fields in obstacle cells so fluid pools against
    // walls instead of bleeding through. Done at the end of every step
    // so paint-in-during-step still appears for one frame on its way out.
    if (!obstacle_.empty()) {
        apply_obstacles_to(u_);
        apply_obstacles_to(v_);
        apply_obstacles_to(dens_);
        apply_obstacles_to(temp_);
    }
    // Zero source arrays so callers can build up next frame's sources fresh.
    std::fill(u_prev_.begin(), u_prev_.end(), 0.0f);
    std::fill(v_prev_.begin(), v_prev_.end(), 0.0f);
    std::fill(dens_prev_.begin(), dens_prev_.end(), 0.0f);
    std::fill(temp_prev_.begin(), temp_prev_.end(), 0.0f);
}

} // namespace ekchous::softbody
