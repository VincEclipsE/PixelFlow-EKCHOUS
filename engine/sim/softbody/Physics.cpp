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
    body_particle_count_.clear();
    body_count_ = 0;
    body_recompute_countdown_ = 0;
}

void Physics::tear_spring(Spring& s) {
    if (!s.enabled) return;
    const bool a_ok = s.a_idx >= 0 && s.a_idx < static_cast<int>(particles_.size());
    const bool b_ok = s.b_idx >= 0 && s.b_idx < static_cast<int>(particles_.size());
    bool remember = true;
    if (a_ok && b_ok) {
        remember = particles_[s.a_idx].remember_severed_bonds ||
                   particles_[s.b_idx].remember_severed_bonds;
    }
    s.enabled = false;
    if (!remember) s.tear_threshold_override = -1.0f;  // sentinel: end-of-tick erase
}

void Physics::tear_springs_attached_to(int p_idx) {
    for (auto& s : springs_) {
        if (!s.enabled) continue;
        if (s.a_idx == p_idx || s.b_idx == p_idx) tear_spring(s);
    }
}

void Physics::recompute_bodies() {
    const int n = static_cast<int>(particles_.size());
    for (auto& p : particles_) p.body_id = 0;
    body_count_ = 0;
    body_particle_count_.assign(1, 0);  // index 0 reserved
    if (n == 0) return;

    // Adjacency: walk enabled non-Fickle springs to build a per-particle
    // neighbor list (vector<vector<int>>). Spring count is comparable to
    // particle count so this fits.
    std::vector<std::vector<int>> adj(n);
    for (const auto& s : springs_) {
        if (!s.enabled) continue;
        if (s.a_idx < 0 || s.a_idx >= n || s.b_idx < 0 || s.b_idx >= n) continue;
        adj[s.a_idx].push_back(s.b_idx);
        adj[s.b_idx].push_back(s.a_idx);
    }

    std::vector<int> stack;
    stack.reserve(64);
    for (int start = 0; start < n; ++start) {
        if (particles_[start].body_id != 0) continue;
        if (adj[start].empty()) continue;  // lone particle stays body_id=0
        ++body_count_;
        const core::u16 bid = static_cast<core::u16>(body_count_);
        int count = 0;
        stack.clear();
        stack.push_back(start);
        particles_[start].body_id = bid;
        while (!stack.empty()) {
            const int v = stack.back();
            stack.pop_back();
            ++count;
            for (int u : adj[v]) {
                if (particles_[u].body_id == 0) {
                    particles_[u].body_id = bid;
                    stack.push_back(u);
                }
            }
        }
        body_particle_count_.push_back(count);
    }
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

void Physics::aggregate_effective_mass() {
    for (auto& p : particles_) p.effective_mass = p.mass;
    const int n = static_cast<int>(particles_.size());
    for (const auto& s : springs_) {
        if (!s.enabled) continue;
        if (s.a_idx < 0 || s.a_idx >= n || s.b_idx < 0 || s.b_idx >= n) continue;
        const auto& pa = particles_[s.a_idx];
        const auto& pb = particles_[s.b_idx];
        if (s.type == SpringType::Stiff) {
            // Rigid bond: full neighbor-mass contribution both ways.
            particles_[s.a_idx].effective_mass += pb.mass;
            particles_[s.b_idx].effective_mass += pa.mass;
        } else {
            // Flexible: contribution scales with stretch ratio (0 at rest,
            // 1 at +100% stretch, clamped).
            const float dx = pb.cx - pa.cx;
            const float dy = pb.cy - pa.cy;
            const float d2 = dx*dx + dy*dy;
            float stretch = 0.0f;
            if (s.dd_rest > 0.0001f) {
                stretch = std::sqrt(d2) / s.dd_rest - 1.0f;
                if (stretch < 0.0f) stretch = 0.0f;
                if (stretch > 1.0f) stretch = 1.0f;
            }
            particles_[s.a_idx].effective_mass += pb.mass * stretch;
            particles_[s.b_idx].effective_mass += pa.mass * stretch;
        }
    }
}

void Physics::update_rigid_groups() {
    if (body_count_ <= 0 || particles_.empty()) return;

    const std::size_t bcount = static_cast<std::size_t>(body_count_) + 1;

    // 1. Tag bodies as rigid (contain any Stiff spring).
    std::vector<core::u8> is_rigid(bcount, 0);
    for (const auto& s : springs_) {
        if (!s.enabled || s.type != SpringType::Stiff) continue;
        if (s.a_idx < 0 || s.a_idx >= static_cast<int>(particles_.size())) continue;
        const core::u16 bid = particles_[s.a_idx].body_id;
        if (bid > 0 && bid <= body_count_) is_rigid[bid] = 1;
    }

    // 2. Per-rigid-body COM (mass-weighted) and group velocity.
    std::vector<float>  com_x(bcount, 0.0f), com_y(bcount, 0.0f);
    std::vector<float>  vel_x(bcount, 0.0f), vel_y(bcount, 0.0f);
    std::vector<float>  mass_sum(bcount, 0.0f);
    for (const auto& p : particles_) {
        const core::u16 bid = p.body_id;
        if (bid == 0 || bid > body_count_ || !is_rigid[bid]) continue;
        com_x[bid] += p.mass * p.cx;
        com_y[bid] += p.mass * p.cy;
        vel_x[bid] += p.mass * (p.cx - p.px);
        vel_y[bid] += p.mass * (p.cy - p.py);
        mass_sum[bid] += p.mass;
    }
    for (std::size_t b = 1; b < bcount; ++b) {
        if (!is_rigid[b] || mass_sum[b] <= 0.0f) continue;
        com_x[b] /= mass_sum[b];
        com_y[b] /= mass_sum[b];
        vel_x[b] /= mass_sum[b];
        vel_y[b] /= mass_sum[b];
    }

    // 3. Snapshot rest offsets for any rigid group where ALL members still
    //    have (rest_offset_x, rest_offset_y) == (0, 0) (i.e., a fresh group
    //    that needs its pose captured). Otherwise keep the existing pose.
    std::vector<core::u8> needs_snapshot(bcount, 1);
    for (const auto& p : particles_) {
        const core::u16 bid = p.body_id;
        if (bid == 0 || bid > body_count_ || !is_rigid[bid]) continue;
        if (p.rest_offset_x != 0.0f || p.rest_offset_y != 0.0f) {
            needs_snapshot[bid] = 0;
        }
    }
    for (auto& p : particles_) {
        const core::u16 bid = p.body_id;
        if (bid == 0 || bid > body_count_ || !is_rigid[bid]) continue;
        if (!needs_snapshot[bid]) continue;
        p.rest_offset_x = p.cx - com_x[bid];
        p.rest_offset_y = p.cy - com_y[bid];
    }

    // 4. Procrustes-style 2D rotation fit per group.
    //    A = Σ m * (rx*cx' + ry*cy'), B = Σ m * (rx*cy' - ry*cx')
    //    θ = atan2(B, A)
    std::vector<float> sum_a(bcount, 0.0f), sum_b(bcount, 0.0f);
    for (const auto& p : particles_) {
        const core::u16 bid = p.body_id;
        if (bid == 0 || bid > body_count_ || !is_rigid[bid]) continue;
        const float cx_ = p.cx - com_x[bid];
        const float cy_ = p.cy - com_y[bid];
        sum_a[bid] += p.mass * (p.rest_offset_x * cx_ + p.rest_offset_y * cy_);
        sum_b[bid] += p.mass * (p.rest_offset_x * cy_ - p.rest_offset_y * cx_);
    }
    std::vector<float> theta_cos(bcount, 1.0f), theta_sin(bcount, 0.0f);
    for (std::size_t b = 1; b < bcount; ++b) {
        if (!is_rigid[b]) continue;
        const float theta = std::atan2(sum_b[b], sum_a[b]);
        theta_cos[b] = std::cos(theta);
        theta_sin[b] = std::sin(theta);
    }

    // 5. Apply the rigid transform. Velocity preserved by shifting px/py by
    //    the same delta as cx/cy — every group particle adopts the group's
    //    translational velocity.
    for (auto& p : particles_) {
        const core::u16 bid = p.body_id;
        if (bid == 0 || bid > body_count_ || !is_rigid[bid]) continue;
        const float c = theta_cos[bid];
        const float s = theta_sin[bid];
        const float new_cx = com_x[bid] + p.rest_offset_x * c - p.rest_offset_y * s;
        const float new_cy = com_y[bid] + p.rest_offset_x * s + p.rest_offset_y * c;
        p.cx = new_cx;
        p.cy = new_cy;
        p.px = new_cx - vel_x[bid];
        p.py = new_cy - vel_y[bid];
    }
}

void Physics::update_spring_damage() {
    const int n = static_cast<int>(particles_.size());
    for (auto& s : springs_) {
        if (!s.enabled) continue;
        if (s.a_idx < 0 || s.a_idx >= n || s.b_idx < 0 || s.b_idx >= n) continue;
        const auto& pa = particles_[s.a_idx];
        const auto& pb = particles_[s.b_idx];
        const float dx = pb.cx - pa.cx;
        const float dy = pb.cy - pa.cy;
        const float d2 = dx*dx + dy*dy;
        if (s.dd_rest <= 0.0001f) continue;
        const float stretch = std::sqrt(d2) / s.dd_rest;
        if (s.type == SpringType::Stiff) {
            if (stretch > params.stiff_fracture_stretch) tear_spring(s);
            continue;
        }
        if (!params.spring_damage_enabled) continue;
        if (stretch <= 1.0f) continue;
        const float over = stretch - 1.0f;
        s.damage += over * params.spring_damage_rate;
        if (s.damage > 1.0f) s.damage = 1.0f;
        // Permanent stretch: rest grows toward current length.
        s.dd_rest    += over * params.spring_permanent_stretch_rate;
        s.dd_rest_sq  = s.dd_rest * s.dd_rest;
        // Strength fade.
        const float loss = over * params.spring_damage_strength_loss;
        s.damp_inc *= std::max(0.0f, 1.0f - loss);
        s.damp_dec *= std::max(0.0f, 1.0f - loss);
        if (s.damage >= 1.0f) tear_spring(s);
    }
}

void Physics::update(float timestep) {
    if (particles_.empty()) return;

    using clock = std::chrono::high_resolution_clock;
    using ms = std::chrono::duration<double, std::milli>;
    const auto t_start = clock::now();

    aggregate_effective_mass();
    update_spring_damage();
    // Rigid-body groups need a fresh body_id each tick if any Stiff fracture
    // happened the previous tick. We could be tighter here, but recomputing
    // every tick is cheap relative to springs/collisions for typical N.
    if (body_count_ <= 0) recompute_bodies();
    update_rigid_groups();

    // 1) Spring iterations.
    for (int k = 0; k < params.iterations_springs; ++k) {
        for (auto& s : springs_) s.update(particles_);
        for (auto& p : particles_) update_bounds(p);
    }

    // 1b) Spring tearing — damped-force gate. Per-spring override beats the
    //     global threshold; 0 falls back to global. Tears go through
    //     tear_spring() so per-particle bond memory decides whether to keep
    //     the torn spring with enabled=false or queue it for erase.
    for (auto& s : springs_) {
        if (!s.enabled) continue;
        const float thr = (s.tear_threshold_override > 0.0f)
                          ? s.tear_threshold_override
                          : params.spring_tear_threshold;
        if (thr <= 0.0f) continue;
        const float mag = s.force < 0 ? -s.force : s.force;
        if (mag > thr) tear_spring(s);
    }
    const auto t_springs = clock::now();

    // 2) Collision iterations. At-rest particles act as infinite-mass anchors:
    //    other particles still see and push against them in update_collisions,
    //    but the at-rest particle's OWN response (collision_x/y) is gated by
    //    a two-stage wake threshold:
    //      < break: absorbed (no movement, no tear)
    //      ≥ break, < wake: springs to this particle tear, stays asleep
    //      ≥ wake: springs tear AND particle wakes and adopts the impulse
    const float wake_brk2 = params.wake_spring_break_threshold *
                            params.wake_spring_break_threshold;
    const bool wake_gates_enabled = (params.wake_spring_break_threshold > 0.0f) ||
                                     (params.wake_mass_multiplier > 0.0f);
    for (int k = 0; k < params.iterations_collisions; ++k) {
        const bool is_last_collision_iter = (k == params.iterations_collisions - 1);
        for (auto& p : particles_) {
            p.collision_x = p.collision_y = 0;
            p.collision_count = 0;
        }
        grid_.update_collisions(particles_);
        for (auto& p : particles_) {
            if (!p.enable_forces) continue;
            if (p.at_rest) {
                if (!wake_gates_enabled) continue;  // locked, no wake gates set
                const float i2 = p.collision_x * p.collision_x +
                                  p.collision_y * p.collision_y;
                if (i2 < wake_brk2) continue;       // absorb
                tear_springs_attached_to(p.idx);
                // Wake threshold scales with the particle's effective mass —
                // heavier (more-bonded) bodies need bigger impulse to wake.
                const float wake_thr = params.wake_mass_multiplier *
                                        p.effective_mass;
                const float wake_thr2 = wake_thr * wake_thr;
                if (i2 < wake_thr2) continue;       // tear springs, stay asleep
                p.at_rest    = false;
                p.rest_ticks = 0;
                // fall through — adopt the impulse this tick
            }
            p.cx += p.collision_x;
            p.cy += p.collision_y;
            for (const auto& d : static_disks_) resolve_disk(d.disk, p, d.damp);
            for (const auto& b : static_boxes_) resolve_box (b.aabb, p, b.damp);
            for (const auto& l : static_lines_) resolve_line(l.ax, l.ay, l.bx, l.by,
                                                              l.thickness, p, l.damp);
            update_bounds(p);
            // Friction: only on the LAST collision iteration so the
            // dampening isn't compounded N times. Friction kills velocity
            // proportional to collision_count (an approximation of contact
            // pressure); high-friction particles brake hard the more
            // neighbors they touch.
            if (is_last_collision_iter && p.friction > 0.0f && p.collision_count > 0) {
                const float per_contact = p.friction;
                const float damp = std::max(0.0f, 1.0f - per_contact);
                p.px = p.cx - (p.cx - p.px) * damp;
                p.py = p.cy - (p.cy - p.py) * damp;
            }
        }
    }
    const auto t_collisions = clock::now();

    // 3) Verlet integration: apply gravity + force generators, advance pos.
    //    At-rest particles are skipped entirely so they can't drift on their
    //    own. Wake only happens via the collision-phase impulse gate above
    //    (or the cell-rest pass clearing at_rest when the cell changes due
    //    to that wake).
    for (auto& p : particles_) {
        if (p.at_rest) { p.ax = p.ay = 0.0f; continue; }
        if (p.enable_forces) {
            p.ax += params.gravity_x;
            p.ay += params.gravity_y;
            for (const auto& pg : point_gravity_) pg(p);
            for (const auto& df : drag_field_)    df(p);
        }
        integrate_one(p, timestep);
        update_bounds(p);
    }

    // 3b) Velocity-threshold rest with sustain counter. A particle locks
    //     only after rest_sustain_ticks consecutive ticks below the velocity
    //     threshold — that way a bouncing ball doesn't freeze at its apex
    //     (where v == 0 for one frame). Any frame above the threshold resets
    //     the counter. Lock = px snapped to cx; skipped by gravity /
    //     integration / spring movement / own collision response until the
    //     two-stage wake gate fires.
    if (params.cell_rest_enabled && params.rest_velocity_threshold > 0.0f) {
        const float thr2 = params.rest_velocity_threshold *
                            params.rest_velocity_threshold;
        const int sustain = params.rest_sustain_ticks > 0
                            ? params.rest_sustain_ticks : 1;
        for (auto& p : particles_) {
            if (!p.enable_forces) continue;
            if (p.at_rest) continue;  // already locked
            const float vx = p.cx - p.px;
            const float vy = p.cy - p.py;
            const float v2 = vx*vx + vy*vy;
            if (v2 < thr2) {
                if (p.rest_ticks < 255) ++p.rest_ticks;
                if (static_cast<int>(p.rest_ticks) >= sustain) {
                    p.at_rest = true;
                    p.px = p.cx;
                    p.py = p.cy;
                }
            } else {
                p.rest_ticks = 0;
            }
        }
    }

    // 3c) [Removed] The old post-integration wake pass is gone — integration
    //     now skips at_rest particles entirely, so cx-px stays 0 and the
    //     check could never fire. Wake decisions live in the collision
    //     response (phase 2) and the fickle velocity tear (phase 1b).

    // 3d) Body identification + rest propagation. Recompute the spring-graph
    //     connected components every body_recompute_interval_ticks; in the
    //     years between recomputes, body_id values stay stable (spring tears
    //     are tolerated — purge happens below).
    if (params.body_rest_propagation_enabled) {
        if (body_recompute_countdown_ <= 0) {
            recompute_bodies();
            body_recompute_countdown_ = params.body_recompute_interval_ticks > 0
                                         ? params.body_recompute_interval_ticks
                                         : 30;
        } else {
            --body_recompute_countdown_;
        }
        if (body_count_ > 0) {
            std::vector<int> rest_count(static_cast<std::size_t>(body_count_) + 1, 0);
            for (const auto& p : particles_) {
                if (p.at_rest && p.body_id > 0 && p.body_id <= body_count_) {
                    ++rest_count[p.body_id];
                }
            }
            for (auto& p : particles_) {
                if (p.at_rest || p.body_id == 0 || p.body_id > body_count_) continue;
                if (!p.enable_forces) continue;
                const int total = body_particle_count_[p.body_id];
                if (total < 2) continue;
                const float frac =
                    static_cast<float>(rest_count[p.body_id]) /
                    static_cast<float>(total);
                if (frac >= params.body_rest_fraction) {
                    p.at_rest = true;
                    p.px = p.cx;
                    p.py = p.cy;
                }
            }
        }
    }

    // 3e) End-of-tick spring purge — erases springs flagged with the "really
    //     erase me" sentinel (set by tear_spring when bond memory is off).
    springs_.erase(
        std::remove_if(springs_.begin(), springs_.end(),
            [](const Spring& s) {
                return !s.enabled && s.tear_threshold_override < 0.0f;
            }),
        springs_.end());

    const auto t_end = clock::now();

    last_timings_.springs_ms    = ms(t_springs    - t_start).count();
    last_timings_.collisions_ms = ms(t_collisions - t_springs).count();
    last_timings_.integrate_ms  = ms(t_end        - t_collisions).count();
}

} // namespace ekchous::softbody
