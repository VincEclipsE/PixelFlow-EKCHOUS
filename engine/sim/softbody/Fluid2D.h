#pragma once

// Stam-style 2D stable fluid solver. CPU port of PixelFlow's DwFluid2D
// (which is GPU/GLSL). The algorithm follows Jos Stam's classic paper
// "Real-Time Fluid Dynamics for Games" (2003): velocity + density grids
// in a (N+2)×(N+2) bordered array, step() runs vel_step → dens_step,
// each composed of add_source/diffuse/advect/project passes solved by
// 20-iteration Gauss-Seidel.
//
// This is the standalone fluid layer in the PixelFlow base — it does not
// touch softbody. Callers paint density + force via add_*_at(), call
// step() each frame, and read back density() / sample_velocity() for
// rendering or coupling to other particle systems.

#include "engine/core/Types.h"
#include <vector>

namespace ekchous::softbody {

class Fluid2D {
public:
    Fluid2D() = default;
    explicit Fluid2D(int n) { resize(n); }

    void resize(int n);
    void clear() noexcept;

    // Inject density / force at a world position. canvas_w / canvas_h are the
    // dimensions of the canvas the fluid grid maps to.
    void add_density_at(float wx, float wy, float canvas_w, float canvas_h,
                        float amount) noexcept;
    void add_force_at(float wx, float wy, float canvas_w, float canvas_h,
                      float fx, float fy) noexcept;

    void step(float dt, float visc, float diff);

    // Number of Gauss-Seidel iterations the diffusion + projection passes
    // run. Higher = more incompressible / less leakage. PixelFlow's
    // DwFluid2D uses 40; the original Stam paper uses 20.
    int iterations = 40;

    // Vorticity confinement strength. Stam advection numerically damps small
    // swirls; this re-injects rotational energy proportional to local curl
    // so dye stays "alive" longer. PixelFlow's DwFluid2D exposes this same
    // knob (vorticity_confinement_factor). 0 = off.
    float vorticity_eps = 0.0f;

    // Density decay per second. dens *= (1 - dissipation*dt) each step.
    // Caps painted dye so it fades out instead of persisting forever. 0 = off.
    float dissipation = 0.0f;

    // Buoyancy (warm rises). Each tick adds a vertical velocity source
    // proportional to (temperature - ambient_temperature) * buoyancy. Set
    // buoyancy = 0 to disable; sign convention: positive = up (i.e. push
    // toward -y), matching the screen-space convention used elsewhere.
    float buoyancy           = 0.0f;
    float ambient_temperature = 0.0f;
    // Temperature dissipation, like density dissipation but for the T field.
    float temperature_dissipation = 0.5f;

    // Mass density (ρ) of the fluid. 1.0 = "water-like" default. Used by the
    // engine to scale fluid→particle drag coupling: heavier fluid carries
    // particles along more strongly. Also multiplies fluid gravity below so
    // raising mass density makes the fluid fall harder.
    float mass_density = 1.0f;

    // Density-weighted gravity coefficient — the α in the Fedkiw-Stam-Jensen
    // 2001 smoke buoyancy formula `f_y = -α·s + β·(T - T_amb)`. Pulls dense
    // smoke down so the fluid pools and settles at a height where gravity
    // balances buoyancy. Effective α applied per cell is
    // `gravity_density_coeff * mass_density * dens_[cell]`. 0 = off.
    float gravity_density_coeff = 0.0f;

    // Optional hard ceiling: when enabled, density cells whose normalized y
    // is above this fraction (= small j in grid coords) are zeroed each tick.
    // 0 = no ceiling, 0.3 = top 30% of canvas refuses fluid, 1 = clear all.
    bool  ceiling_enabled       = false;
    float ceiling_min_y_norm    = 0.0f;

    // Display brightness multiplier — purely visual scale applied to the
    // density value before color lookup at render time. Doesn't change
    // simulation. Lets you make dim dye visible without re-painting.
    float display_brightness = 1.0f;

    // Bilinear-sample fluid velocity at a world position. Returns 0 if out of
    // grid. Used to feed FlowParticles or to drive softbody forces.
    void sample_velocity(float wx, float wy, float canvas_w, float canvas_h,
                         float& out_u, float& out_v) const noexcept;

    // Bilinear-sample fluid density at a world position. Used for per-
    // particle "is this atom wet?" logic.
    float sample_density(float wx, float wy, float canvas_w, float canvas_h) const noexcept;

    // Inject heat (or cold, with negative amount) at a world position. Wired
    // to the per-template heat-source linkage so a "stove" atom can warm
    // the fluid around it.
    void add_heat_at(float wx, float wy, float canvas_w, float canvas_h,
                     float amount) noexcept;

    float sample_temperature(float wx, float wy,
                              float canvas_w, float canvas_h) const noexcept;

    const std::vector<float>& temperature() const noexcept { return temp_; }

    // Obstacle mask. Pass a (N+2)*(N+2) byte buffer (1 = obstacle, 0 = free).
    // While the mask is set, every solver pass zeroes velocity + density in
    // obstacle cells so fluid pools against them instead of bleeding through.
    void set_obstacles(const std::vector<core::u8>& mask);
    void clear_obstacles() noexcept;
    bool has_obstacles() const noexcept { return !obstacle_.empty(); }
    const std::vector<core::u8>& obstacles() const noexcept { return obstacle_; }

    int n() const noexcept { return N_; }
    const std::vector<float>& density() const noexcept { return dens_; }
    const std::vector<float>& u() const noexcept { return u_; }
    const std::vector<float>& v() const noexcept { return v_; }

private:
    int IX(int i, int j) const noexcept { return i + (N_ + 2) * j; }

    void add_source(std::vector<float>& x, const std::vector<float>& s, float dt);
    void set_bnd(int b, std::vector<float>& x);
    void lin_solve(int b, std::vector<float>& x, const std::vector<float>& x0,
                   float a, float c);
    void diffuse(int b, std::vector<float>& x, const std::vector<float>& x0,
                 float diff, float dt);
    void advect(int b, std::vector<float>& d, const std::vector<float>& d0,
                const std::vector<float>& u, const std::vector<float>& v, float dt);
    void project(std::vector<float>& u, std::vector<float>& v,
                 std::vector<float>& p, std::vector<float>& div);

    void vel_step(float dt, float visc);
    void dens_step(float dt, float diff);
    void temp_step(float dt, float diff);
    void apply_vorticity_confinement(float dt);
    void apply_buoyancy_and_gravity(float dt);

    void apply_obstacles_to(std::vector<float>& field);

    int N_ = 0;
    std::vector<float> u_, v_, u_prev_, v_prev_;
    std::vector<float> dens_, dens_prev_;
    std::vector<float> temp_, temp_prev_;
    std::vector<float> curl_, curl_mag_;       // vorticity scratch
    std::vector<core::u8> obstacle_;  // (N+2)*(N+2); empty = no obstacles
};

} // namespace ekchous::softbody
