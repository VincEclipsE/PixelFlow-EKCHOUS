#pragma once

// Static obstacles.
//
// Two flavours coexist here:
//   1. **Geometric obstacles** (StaticDisk / StaticBox / StaticLine):
//      proper static-geometry primitives. Live in Physics's obstacle
//      vectors; resolved in the collision-iteration loop via
//      position-correction + Verlet velocity reflection. They don't
//      touch the CollisionGrid, so adding a large disk obstacle doesn't
//      blow up the broadphase cell size.
//   2. **Pinned-particle obstacles** (add_circle_obstacle / add_line_
//      obstacle): the original approximation — spawn pinned particles
//      with collisions enabled. Kept as alternatives but Engine now
//      drives the geometric path by default.

#include "engine/sim/softbody/AABB.h"
#include "engine/sim/softbody/BoundingDisk.h"
#include "engine/sim/softbody/Physics.h"
#include <algorithm>
#include <cmath>
#include <vector>

namespace ekchous::softbody {

struct StaticDisk {
    BoundingDisk disk;
    float damp = 1.0f;
};

struct StaticBox {
    AABB aabb;
    float damp = 1.0f;
};

struct StaticLine {
    float ax = 0.0f, ay = 0.0f;
    float bx = 0.0f, by = 0.0f;
    float thickness = 4.0f;
    float damp = 1.0f;
};

// --- Resolve a single particle against a static primitive ---------------

inline void resolve_disk(const BoundingDisk& d, Particle& p, float damp = 1.0f) noexcept {
    const float dx = p.cx - d.cx;
    const float dy = p.cy - d.cy;
    const float d2 = dx * dx + dy * dy;
    const float min_dist = p.rad_collision + d.radius;
    if (d2 >= min_dist * min_dist || d2 < 1e-9f) return;
    const float dist = std::sqrt(d2);
    const float nx = dx / dist;
    const float ny = dy / dist;
    const float vx = p.cx - p.px;
    const float vy = p.cy - p.py;
    p.cx = d.cx + nx * min_dist;
    p.cy = d.cy + ny * min_dist;
    const float dot = vx * nx + vy * ny;
    if (dot < 0.0f) {
        const float new_vx = (vx - 2.0f * dot * nx) * damp;
        const float new_vy = (vy - 2.0f * dot * ny) * damp;
        p.px = p.cx - new_vx;
        p.py = p.cy - new_vy;
    }
}

inline void resolve_box(const AABB& a, Particle& p, float damp = 1.0f) noexcept {
    const float r = p.rad_collision;
    const float closest_x = std::max(a.min_x, std::min(p.cx, a.max_x));
    const float closest_y = std::max(a.min_y, std::min(p.cy, a.max_y));
    const float dx = p.cx - closest_x;
    const float dy = p.cy - closest_y;
    const float d2 = dx * dx + dy * dy;
    if (d2 > r * r) return;

    if (d2 < 1e-9f) {
        // Particle centre is inside the box — push out along the nearest face.
        const float dist_left   = p.cx - a.min_x;
        const float dist_right  = a.max_x - p.cx;
        const float dist_top    = p.cy - a.min_y;
        const float dist_bottom = a.max_y - p.cy;
        const float m = std::min({dist_left, dist_right, dist_top, dist_bottom});
        const float vx = p.cx - p.px;
        const float vy = p.cy - p.py;
        if (m == dist_left)        { p.cx = a.min_x - r; p.px = p.cx + vx * damp; }
        else if (m == dist_right)  { p.cx = a.max_x + r; p.px = p.cx + vx * damp; }
        else if (m == dist_top)    { p.cy = a.min_y - r; p.py = p.cy + vy * damp; }
        else                        { p.cy = a.max_y + r; p.py = p.cy + vy * damp; }
        return;
    }

    const float dist = std::sqrt(d2);
    const float nx = dx / dist;
    const float ny = dy / dist;
    const float vx = p.cx - p.px;
    const float vy = p.cy - p.py;
    p.cx = closest_x + nx * r;
    p.cy = closest_y + ny * r;
    const float dot = vx * nx + vy * ny;
    if (dot < 0.0f) {
        const float new_vx = (vx - 2.0f * dot * nx) * damp;
        const float new_vy = (vy - 2.0f * dot * ny) * damp;
        p.px = p.cx - new_vx;
        p.py = p.cy - new_vy;
    }
}

inline void resolve_line(float ax, float ay, float bx, float by,
                         float thickness, Particle& p, float damp = 1.0f) noexcept {
    const float dx_l = bx - ax;
    const float dy_l = by - ay;
    const float len2 = dx_l * dx_l + dy_l * dy_l;
    if (len2 < 1e-9f) return;
    const float t  = ((p.cx - ax) * dx_l + (p.cy - ay) * dy_l) / len2;
    const float tc = t < 0.0f ? 0.0f : (t > 1.0f ? 1.0f : t);
    const float clx = ax + dx_l * tc;
    const float cly = ay + dy_l * tc;
    const float dx = p.cx - clx;
    const float dy = p.cy - cly;
    const float d2 = dx * dx + dy * dy;
    const float min_dist = p.rad_collision + thickness;
    if (d2 >= min_dist * min_dist || d2 < 1e-9f) return;
    const float dist = std::sqrt(d2);
    const float nx = dx / dist;
    const float ny = dy / dist;
    const float vx = p.cx - p.px;
    const float vy = p.cy - p.py;
    p.cx = clx + nx * min_dist;
    p.cy = cly + ny * min_dist;
    const float dot = vx * nx + vy * ny;
    if (dot < 0.0f) {
        const float new_vx = (vx - 2.0f * dot * nx) * damp;
        const float new_vy = (vy - 2.0f * dot * ny) * damp;
        p.px = p.cx - new_vx;
        p.py = p.cy - new_vy;
    }
}

// --- Legacy: pinned-particle obstacles ---------------------------------
// Still callable for backwards compat; engines should prefer the geometric
// path via Physics::add_static_*.

inline int add_circle_obstacle(Physics& physics, float x, float y,
                                float radius, float mass = 1.0e6f) {
    const int idx = physics.add_particle(x, y, radius);
    auto& p = physics.particles()[idx];
    p.enable_forces     = false;
    p.enable_springs    = false;
    p.enable_collisions = true;
    p.mass              = mass;
    p.collision_group   = physics.next_collision_group();
    return idx;
}

inline std::vector<int> add_line_obstacle(Physics& physics,
                                           float ax, float ay,
                                           float bx, float by,
                                           float radius,
                                           float mass = 1.0e6f) {
    std::vector<int> indices;
    const float dx = bx - ax;
    const float dy = by - ay;
    const float len = std::sqrt(dx * dx + dy * dy);
    if (len < 1e-3f) {
        indices.push_back(add_circle_obstacle(physics, ax, ay, radius, mass));
        return indices;
    }
    const float step = radius * 1.2f;
    int n = static_cast<int>(std::ceil(len / step)) + 1;
    if (n < 2) n = 2;
    const int cg = physics.next_collision_group();
    indices.reserve(n);
    for (int i = 0; i < n; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(n - 1);
        const float x = ax + dx * t;
        const float y = ay + dy * t;
        const int idx = physics.add_particle(x, y, radius);
        auto& p = physics.particles()[idx];
        p.enable_forces     = false;
        p.enable_springs    = false;
        p.enable_collisions = true;
        p.mass              = mass;
        p.collision_group   = cg;
        indices.push_back(idx);
    }
    return indices;
}

} // namespace ekchous::softbody
