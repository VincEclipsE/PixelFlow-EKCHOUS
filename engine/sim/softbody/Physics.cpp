#include "engine/sim/softbody/Physics.h"
#include "engine/sim/softbody/Obstacles.h"
#include <algorithm>
#include <chrono>
#include <cmath>

namespace ekchous::softbody {

void Physics::reset() {
    particles_.clear();
    springs_.clear();
    static_disks_.clear();
    static_boxes_.clear();
    static_lines_.clear();
    point_gravity_.clear();
    drag_field_.clear();
    next_collision_group_ = 1;
}

int Physics::add_particle(float x, float y, float radius) {
    Particle p(static_cast<int>(particles_.size()), x, y, radius);
    p.damp = &particle_damp;
    particles_.push_back(p);
    return p.idx;
}

int Physics::add_particle(const Particle& p_in) {
    Particle p = p_in;
    p.idx = static_cast<int>(particles_.size());
    if (p.damp == nullptr) p.damp = &particle_damp;
    particles_.push_back(p);
    return p.idx;
}

int Physics::add_spring(int a_idx, int b_idx, SpringType type) {
    Spring s(a_idx, b_idx, 0.0f, type);
    s.compute_rest_from_positions(particles_);
    springs_.push_back(s);
    return static_cast<int>(springs_.size()) - 1;
}

int Physics::add_spring(int a_idx, int b_idx, float rest_length, SpringType type) {
    Spring s(a_idx, b_idx, rest_length, type);
    springs_.push_back(s);
    return static_cast<int>(springs_.size()) - 1;
}

void Physics::update_bounds(Particle& p) const {
    if (!p.enable_collisions) return;
    const float damping = p.damp ? p.damp->bounds : 1.0f;
    const float r = p.rad_collision;
    float vx, vy;
    if (p.cx - r < params.bounds_xmin) {
        vx = p.cx - p.px; vy = p.cy - p.py;
        p.cx = params.bounds_xmin + r;
        p.px = p.cx + vx * damping;
        p.py = p.cy - vy * damping;
    }
    if (p.cx + r > params.bounds_xmax) {
        vx = p.cx - p.px; vy = p.cy - p.py;
        p.cx = params.bounds_xmax - r;
        p.px = p.cx + vx * damping;
        p.py = p.cy - vy * damping;
    }
    if (p.cy - r < params.bounds_ymin) {
        vx = p.cx - p.px; vy = p.cy - p.py;
        p.cy = params.bounds_ymin + r;
        p.px = p.cx - vx * damping;
        p.py = p.cy + vy * damping;
    }
    if (p.cy + r > params.bounds_ymax) {
        vx = p.cx - p.px; vy = p.cy - p.py;
        p.cy = params.bounds_ymax - r;
        p.px = p.cx - vx * damping;
        p.py = p.cy + vy * damping;
    }
}

void Physics::integrate_one(Particle& p, float timestep) const {
    if (!p.enable_forces) {
        p.ax = p.ay = 0;
        return;
    }
    const float damp_vel = p.damp ? p.damp->velocity : 1.0f;

    float vx = (p.cx - p.px) * damp_vel;
    float vy = (p.cy - p.py) * damp_vel;

    p.px = p.cx;
    p.py = p.cy;

    // Clamp velocity to prevent tunneling (per PixelFlow: max v = r * sqrt(8)).
    const float vv_cur = vx*vx + vy*vy;
    const float vv_max = p.rad_collision * p.rad_collision * 8.0f;
    if (vv_cur > vv_max) {
        const float damp = std::sqrt(vv_max / vv_cur);
        vx *= damp;
        vy *= damp;
    }

    p.cx += vx + p.ax * 0.5f * timestep * timestep;
    p.cy += vy + p.ay * 0.5f * timestep * timestep;

    p.ax = p.ay = 0;
}

void Physics::update(float timestep) {
    if (particles_.empty()) return;

    using clock = std::chrono::high_resolution_clock;
    using ms = std::chrono::duration<double, std::milli>;
    const auto t_start = clock::now();

    // 1) Spring iterations.
    for (int k = 0; k < params.iterations_springs; ++k) {
        for (auto& s : springs_) s.update(particles_);
        for (auto& p : particles_) update_bounds(p);
    }

    // 1b) Spring tearing: disable springs whose |force| crossed the user-set
    //     threshold during the last iteration. Disabled springs stay in the
    //     vector (kept for hash + draw call cost is tiny) but stop applying
    //     forces. Threshold = 0 means tearing is off.
    if (params.spring_tear_threshold > 0.0f) {
        for (auto& s : springs_) {
            if (!s.enabled) continue;
            const float mag = s.force < 0 ? -s.force : s.force;
            if (mag > params.spring_tear_threshold) s.enabled = false;
        }
    }
    const auto t_springs = clock::now();

    // 2) Collision iterations.
    for (int k = 0; k < params.iterations_collisions; ++k) {
        for (auto& p : particles_) {
            p.collision_x = p.collision_y = 0;
            p.collision_count = 0;
        }
        grid_.update_collisions(particles_);
        for (auto& p : particles_) {
            // Static obstacles (enable_forces=false) keep their seat — they
            // still push other particles but don't get pushed themselves.
            if (!p.enable_forces) continue;
            p.cx += p.collision_x;
            p.cy += p.collision_y;
            // Geometric static obstacles (Disk / Box / Line) live outside
            // the broadphase grid so they don't bloat its cell size. Resolve
            // each one against this particle's current position.
            for (const auto& d : static_disks_) resolve_disk(d.disk, p, d.damp);
            for (const auto& b : static_boxes_) resolve_box (b.aabb, p, b.damp);
            for (const auto& l : static_lines_) resolve_line(l.ax, l.ay, l.bx, l.by,
                                                              l.thickness, p, l.damp);
            update_bounds(p);
        }
    }
    const auto t_collisions = clock::now();

    // 3) Verlet integration: apply gravity + force generators, advance pos.
    for (auto& p : particles_) {
        if (p.enable_forces) {
            p.ax += params.gravity_x;
            p.ay += params.gravity_y;
            for (const auto& pg : point_gravity_) pg(p);
            for (const auto& df : drag_field_)    df(p);
        }
        integrate_one(p, timestep);
        update_bounds(p);
    }

    // 3b) Cell-residency rest (EKCHOUS layer 2). For each movable particle,
    //     compute integer cell from current position; if unchanged from the
    //     previous tick, increment rest_ticks; once it crosses the threshold
    //     mark at_rest=true and zero the implicit velocity by snapping
    //     (px, py) onto (cx, cy). External forces will move the particle
    //     in a future tick, the cell will change, and rest is released.
    if (params.cell_rest_enabled && params.rest_cell_size > 0.0f) {
        const float inv_cell = 1.0f / params.rest_cell_size;
        for (auto& p : particles_) {
            if (!p.enable_forces) continue;
            const core::i16 cx_cell = static_cast<core::i16>(p.cx * inv_cell);
            const core::i16 cy_cell = static_cast<core::i16>(p.cy * inv_cell);
            if (cx_cell == p.last_cell_x && cy_cell == p.last_cell_y) {
                if (p.rest_ticks < 255) ++p.rest_ticks;
            } else {
                p.rest_ticks   = 0;
                p.last_cell_x  = cx_cell;
                p.last_cell_y  = cy_cell;
                p.at_rest      = false;
            }
            if (static_cast<int>(p.rest_ticks) >= params.rest_threshold_ticks) {
                p.at_rest = true;
                p.px = p.cx;
                p.py = p.cy;
            }
        }
    }
    const auto t_end = clock::now();

    last_timings_.springs_ms    = ms(t_springs    - t_start).count();
    last_timings_.collisions_ms = ms(t_collisions - t_springs).count();
    last_timings_.integrate_ms  = ms(t_end        - t_collisions).count();
}

} // namespace ekchous::softbody
