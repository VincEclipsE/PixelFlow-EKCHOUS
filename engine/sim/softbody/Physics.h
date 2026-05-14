#pragma once

// Top-level softbody simulation orchestrator. Ported from PixelFlow's
// DwPhysics. Three-phase update per step:
//   1. Springs: iterative position-correction (iterations_springs× per step)
//   2. Collisions: iterative position-correction (iterations_collisions× per step)
//   3. Verlet integration: apply gravity, integrate position
//
// All math is float — PixelFlow's reference behaviour intentionally
// includes float rounding effects. No determinism guarantees.

#include "engine/sim/softbody/AABB.h"
#include "engine/sim/softbody/BoundingDisk.h"
#include "engine/sim/softbody/CollisionGrid.h"
#include "engine/sim/softbody/ForceGenerators.h"
#include "engine/sim/softbody/Particle.h"
#include "engine/sim/softbody/Spring.h"
#include <vector>

namespace ekchous::softbody {

struct StaticDiskRec {
    BoundingDisk disk;
    float damp = 1.0f;
};

struct StaticBoxRec {
    AABB aabb;
    float damp = 1.0f;
};

struct StaticLineRec {
    float ax = 0.0f, ay = 0.0f;
    float bx = 0.0f, by = 0.0f;
    float thickness = 4.0f;
    float damp = 1.0f;
};

// Per-phase wall-clock timings, stamped at the end of each Physics::update.
// Useful for tuning iteration counts and identifying hotspots.
struct PhaseTimings {
    double springs_ms    = 0.0;
    double collisions_ms = 0.0;
    double integrate_ms  = 0.0;
};

struct PhysicsParams {
    int   iterations_springs    = 4;
    int   iterations_collisions = 4;
    float gravity_x = 0.0f;
    float gravity_y = 0.6f;  // px / step², 60-step canonical timestep
    // bounds [xmin, ymin, xmax, ymax] in world coords.
    float bounds_xmin = 0.0f;
    float bounds_ymin = 0.0f;
    float bounds_xmax = 800.0f;
    float bounds_ymax = 600.0f;
    // Spring tearing: any spring whose |force| exceeds this threshold at the
    // end of the spring-iteration phase is disabled (enabled = false) for the
    // rest of the run. 0 disables tearing entirely.
    float spring_tear_threshold = 0.0f;

    // EKCHOUS layer 2 — cell-residency rest. After integration, a particle
    // whose integer cell (cx / rest_cell_size, cy / rest_cell_size) has
    // stayed the same for rest_threshold_ticks consecutive ticks is marked
    // at_rest and its implicit velocity is zeroed. External forces can
    // still wake it (cell change → counter reset → at_rest cleared).
    bool  cell_rest_enabled     = false;
    int   rest_threshold_ticks  = 30;
    float rest_cell_size        = 10.0f;
};

class Physics {
public:
    Physics() = default;
    explicit Physics(PhysicsParams initial_params) : params(initial_params) {}

    void reset();

    // Advance the simulation by one tick.
    void update(float timestep);

    // Particle / spring management. Returns the index of the new element.
    // add_spring uses the current particle positions to compute rest length.
    int add_particle(float x, float y, float radius);
    int add_particle(const Particle& p);
    int add_spring(int a_idx, int b_idx, SpringType type = SpringType::Struct);
    int add_spring(int a_idx, int b_idx, float rest_length,
                   SpringType type = SpringType::Struct);

    // A fresh collision group id. Particles in the same group skip
    // pairwise collision with each other (used to wire a single soft body
    // together — its internal particles don't self-collide).
    int next_collision_group() noexcept { return next_collision_group_++; }

    std::vector<Particle>& particles() noexcept { return particles_; }
    const std::vector<Particle>& particles() const noexcept { return particles_; }
    std::vector<Spring>& springs() noexcept { return springs_; }
    const std::vector<Spring>& springs() const noexcept { return springs_; }

    // Geometric static obstacles. Lifecycle is owned by Physics: cleared on
    // reset(); resolved each collision iteration via position correction.
    int add_static_disk(float cx, float cy, float radius, float damp = 1.0f) {
        static_disks_.push_back(StaticDiskRec{BoundingDisk{cx, cy, radius}, damp});
        return static_cast<int>(static_disks_.size()) - 1;
    }
    int add_static_box(float min_x, float min_y, float max_x, float max_y,
                       float damp = 1.0f) {
        static_boxes_.push_back(StaticBoxRec{AABB{min_x, min_y, max_x, max_y}, damp});
        return static_cast<int>(static_boxes_.size()) - 1;
    }
    int add_static_line(float ax, float ay, float bx, float by,
                        float thickness, float damp = 1.0f) {
        static_lines_.push_back(StaticLineRec{ax, ay, bx, by, thickness, damp});
        return static_cast<int>(static_lines_.size()) - 1;
    }
    void clear_static_obstacles() noexcept {
        static_disks_.clear();
        static_boxes_.clear();
        static_lines_.clear();
    }
    const std::vector<StaticDiskRec>& static_disks() const noexcept { return static_disks_; }
    const std::vector<StaticBoxRec>&  static_boxes() const noexcept { return static_boxes_; }
    const std::vector<StaticLineRec>& static_lines() const noexcept { return static_lines_; }

    // Force generators placed in the world. Each is invoked per non-pinned
    // particle during the integrate phase, alongside gravity.
    int add_point_gravity(const PointGravity& g) {
        point_gravity_.push_back(g);
        return static_cast<int>(point_gravity_.size()) - 1;
    }
    int add_drag_field(const DragField& f) {
        drag_field_.push_back(f);
        return static_cast<int>(drag_field_.size()) - 1;
    }
    void clear_force_generators() noexcept {
        point_gravity_.clear();
        drag_field_.clear();
    }
    std::vector<PointGravity>& point_gravities() noexcept { return point_gravity_; }
    std::vector<DragField>&    drag_fields() noexcept    { return drag_field_; }
    const std::vector<PointGravity>& point_gravities() const noexcept { return point_gravity_; }
    const std::vector<DragField>&    drag_fields() const noexcept    { return drag_field_; }

    const PhaseTimings& last_timings() const noexcept { return last_timings_; }

    // Post-tearing cleanup — erase springs flagged enabled=false. Indices
    // into particles_ are unaffected.
    void purge_torn_springs() {
        springs_.erase(
            std::remove_if(springs_.begin(), springs_.end(),
                [](const Spring& s) { return !s.enabled; }),
            springs_.end());
    }

    // Erase a particle. Springs touching it are removed; springs at higher
    // indices have their endpoint ids decremented so the remaining graph
    // stays valid. Returns true if the particle existed.
    bool remove_particle(int idx) {
        if (idx < 0 || idx >= static_cast<int>(particles_.size())) return false;
        particles_.erase(particles_.begin() + idx);
        springs_.erase(
            std::remove_if(springs_.begin(), springs_.end(),
                [idx](const Spring& s) {
                    return s.a_idx == idx || s.b_idx == idx;
                }),
            springs_.end());
        for (auto& s : springs_) {
            if (s.a_idx > idx) --s.a_idx;
            if (s.b_idx > idx) --s.b_idx;
        }
        return true;
    }

    // Snap every active spring's rest length to its current pa-pb distance.
    // Effectively "memorizes" the current pose so the soft body settles to
    // it instead of its build-time shape.
    void recompute_rest_lengths() {
        for (auto& s : springs_) {
            if (!s.enabled) continue;
            s.compute_rest_from_positions(particles_);
        }
    }

    PhysicsParams params{};
    DampingParams particle_damp{};  // referenced by each particle via .damp = &this

private:
    void integrate_one(Particle& p, float timestep) const;
    void update_bounds(Particle& p) const;

    std::vector<Particle>      particles_;
    std::vector<Spring>        springs_;
    CollisionGrid              grid_;
    int                        next_collision_group_ = 1;
    std::vector<StaticDiskRec> static_disks_;
    std::vector<StaticBoxRec>  static_boxes_;
    std::vector<StaticLineRec> static_lines_;
    std::vector<PointGravity>  point_gravity_;
    std::vector<DragField>     drag_field_;
    PhaseTimings               last_timings_{};
};

} // namespace ekchous::softbody
