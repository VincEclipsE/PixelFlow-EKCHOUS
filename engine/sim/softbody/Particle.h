#pragma once

// Verlet particle (2D). Ported from PixelFlow's DwParticle2D — same state,
// same integration, same collision math. No EKCHOUS-specific concerns
// (no element ids, no chunks, no determinism contract).
//
// Verlet integration stores current + previous position; velocity is
// implicit (cx - px). Acceleration accumulates over a tick and is reset
// at the end of updatePosition.

#include "engine/core/Types.h"
#include <cmath>

namespace ekchous::softbody {

struct DampingParams {
    float bounds    = 1.0f;
    float collision = 1.0f;
    float velocity  = 1.0f;
};

struct Particle {
    int idx = 0;
    int collision_group = 0;

    float cx = 0, cy = 0;     // current position
    float px = 0, py = 0;     // previous position
    float ax = 0, ay = 0;     // accumulated acceleration this step

    float rad           = 1.0f;
    float rad_collision = 1.0f;
    float mass          = 1.0f;

    bool enable_collisions = true;
    bool enable_springs    = true;
    bool enable_forces     = true;

    // Pointer (non-owning) to a shared damping config. nullptr → identity.
    const DampingParams* damp = nullptr;

    // Per-step collision scratch (mutated by Physics).
    float collision_x = 0, collision_y = 0;
    int   collision_count = 0;
    int   collision_ptr_idx = -1;  // last `self` index that processed this particle this sweep

    // Extensibility slot: arbitrary u32 the user can stuff with tags, atom
    // types, charge codes, indices into their own table, etc. Not touched
    // by the physics core; preserved by SceneIO; readable by the renderer.
    core::u32 user_data = 0;

    // EKCHOUS atomic element id — index into atoms::kElements. Used by the
    // renderer for per-element coloring and (later) by the auto-bond pass
    // to look up valence + bond strength. Not touched by softbody physics.
    core::u8  element_id = 0;

    // EKCHOUS template id — index into Engine::templates_. drop_atom stamps
    // this so per-template tool linkages can be looked up each tick.
    core::u8  template_id = 0;

    // Cell-residency rest tracking (EKCHOUS layer 2).
    //   last_cell_x/y: integer cell the particle ended the previous tick in
    //   rest_ticks:    consecutive ticks the particle has stayed in that cell
    //   at_rest:       set when rest_ticks ≥ Physics::params.rest_threshold_ticks
    // Physics writes these only when params.cell_rest_enabled is true.
    core::i16 last_cell_x = -1;
    core::i16 last_cell_y = -1;
    core::u8  rest_ticks  = 0;
    bool      at_rest     = false;

    Particle() = default;
    Particle(int idx_, float x, float y, float radius)
        : idx(idx_),
          collision_group(idx_),
          cx(x), cy(y), px(x), py(y),
          rad(radius < 0.1f ? 0.1f : radius),
          rad_collision(radius < 0.1f ? 0.1f : radius) {}

    void set_position(float x, float y) noexcept {
        cx = px = x;
        cy = py = y;
    }
    void set_radius(float r) noexcept {
        rad = r < 0.1f ? 0.1f : r;
        rad_collision = rad;
    }
    void set_radius_collision(float r) noexcept {
        rad_collision = r < 0.1f ? 0.1f : r;
    }
    void set_mass(float m) noexcept { mass = m; }

    float velocity_x() const noexcept { return cx - px; }
    float velocity_y() const noexcept { return cy - py; }
    float velocity() const noexcept {
        const float vx = cx - px;
        const float vy = cy - py;
        return std::sqrt(vx*vx + vy*vy);
    }

    // Smoothly move this particle toward (target_x, target_y) by a fraction
    // of the remaining distance. Used for mouse-drag (matches PixelFlow's
    // DwParticle2D.moveTo so the drag feel is familiar).
    void move_to(float target_x, float target_y, float damping) noexcept {
        px = cx;
        py = cy;
        cx += (target_x - cx) * damping;
        cy += (target_y - cy) * damping;
    }

    // Accumulate a force into this tick's acceleration. Force is divided by
    // mass per DwParticle2D.addForce so heavier particles respond less to
    // the same impulse. Reset to 0 at the end of updatePosition.
    void add_force(float fx, float fy) noexcept {
        if (mass <= 0.0f) return;
        ax += fx / mass;
        ay += fy / mass;
    }
};

} // namespace ekchous::softbody
