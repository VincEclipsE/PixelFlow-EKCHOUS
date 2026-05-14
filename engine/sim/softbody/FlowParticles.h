#pragma once

// Lightweight field-advected particles. Spiritual port of PixelFlow's
// DwFlowFieldParticles minus the GPU compute path.
//
// These are NOT softbody particles — no springs, no pairwise collision,
// no mass/Verlet integration. Each tick they sample the FlowField at
// their position, add to their velocity, integrate, and decay. Cheap
// enough to spawn thousands.

#include "engine/sim/softbody/FlowField.h"
#include "engine/sim/softbody/Fluid2D.h"
#include <algorithm>
#include <cstdint>
#include <vector>

namespace ekchous::softbody {

struct FlowParticle {
    static constexpr int kTrailLen = 8;

    float x = 0, y = 0;
    float vx = 0, vy = 0;
    float lifetime = 1.0f;   // 1.0 at birth → 0 at death
    bool  alive = false;

    // Trail ring buffer. Populated by the system only when record_trails is
    // true; otherwise these stay at 0 and skip the per-tick write cost.
    float trail_xs[kTrailLen] = {0};
    float trail_ys[kTrailLen] = {0};
    int   trail_head  = 0;  // next write index
    int   trail_count = 0;  // valid entries (0 .. kTrailLen)
};

// A spawn source: emits N particles per tick at (x, y) with the given
// velocity jitter. Multiple emitters can coexist in one FlowParticleSystem;
// the engine renders them as crosshair icons so the user knows where they
// are.
struct FlowEmitter {
    float x = 0.0f;
    float y = 0.0f;
    int   per_frame = 8;
    float vx_jitter = 0.6f;
    float vy_jitter = 0.6f;
    bool  enabled = true;
};

class FlowParticleSystem {
public:
    FlowParticleSystem() = default;
    explicit FlowParticleSystem(std::uint32_t seed) : rng_state_(seed ? seed : 1) {}

    void clear() noexcept { particles_.clear(); }

    // Emit `count` particles centred on (x, y). Each particle gets random
    // initial velocity uniformly in [-vx_jitter, +vx_jitter] × [-vy_jitter,
    // +vy_jitter]. Cap respects max_count.
    void emit(float x, float y, int count,
              float vx_jitter, float vy_jitter,
              int max_count) {
        for (int i = 0; i < count; ++i) {
            if (static_cast<int>(particles_.size()) >= max_count) return;
            FlowParticle p;
            p.x = x;
            p.y = y;
            p.vx = (rand_unit() * 2.0f - 1.0f) * vx_jitter;
            p.vy = (rand_unit() * 2.0f - 1.0f) * vy_jitter;
            p.lifetime = 1.0f;
            p.alive = true;
            particles_.push_back(p);
        }
    }

    // Advance every particle one tick. If `fluid` is non-null, fluid velocity
    // is sampled and added to the per-particle velocity nudge (scaled by
    // fluid_strength) on top of the field sample. canvas_w/canvas_h are
    // required for the fluid sampling coordinate mapping; they're ignored
    // when fluid is null. If `record_trails` is true, each tick pushes the
    // particle's current (x, y) into its trail ring buffer.
    void update(const FlowField& field, float timestep, float damping,
                float lifetime_decay,
                float bounds_xmin, float bounds_ymin,
                float bounds_xmax, float bounds_ymax,
                const Fluid2D* fluid = nullptr, float fluid_strength = 0.0f,
                float canvas_w = 0.0f, float canvas_h = 0.0f,
                bool record_trails = false) {
        for (auto& p : particles_) {
            if (!p.alive) continue;
            if (record_trails) {
                p.trail_xs[p.trail_head] = p.x;
                p.trail_ys[p.trail_head] = p.y;
                p.trail_head = (p.trail_head + 1) % FlowParticle::kTrailLen;
                if (p.trail_count < FlowParticle::kTrailLen) ++p.trail_count;
            }
            float fx, fy;
            field.sample(p.x, p.y, fx, fy);
            if (fluid && fluid_strength != 0.0f) {
                float fu, fv;
                fluid->sample_velocity(p.x, p.y, canvas_w, canvas_h, fu, fv);
                fx += fu * fluid_strength;
                fy += fv * fluid_strength;
            }
            p.vx = (p.vx + fx) * damping;
            p.vy = (p.vy + fy) * damping;
            p.x += p.vx * timestep;
            p.y += p.vy * timestep;
            p.lifetime -= lifetime_decay * timestep;
            if (p.lifetime <= 0.0f ||
                p.x < bounds_xmin || p.x > bounds_xmax ||
                p.y < bounds_ymin || p.y > bounds_ymax) {
                p.alive = false;
            }
        }
        particles_.erase(
            std::remove_if(particles_.begin(), particles_.end(),
                [](const FlowParticle& p) { return !p.alive; }),
            particles_.end());
    }

    const std::vector<FlowParticle>& particles() const noexcept { return particles_; }
    std::size_t particle_count() const noexcept { return particles_.size(); }

    // Emitter management. Engine owns a single global max_count cap.
    int add_emitter(const FlowEmitter& e) {
        emitters_.push_back(e);
        return static_cast<int>(emitters_.size()) - 1;
    }
    void remove_emitter(int idx) {
        if (idx >= 0 && idx < static_cast<int>(emitters_.size())) {
            emitters_.erase(emitters_.begin() + idx);
        }
    }
    void clear_emitters() { emitters_.clear(); }
    std::vector<FlowEmitter>& emitters() noexcept { return emitters_; }
    const std::vector<FlowEmitter>& emitters() const noexcept { return emitters_; }

    // One-call helper: emit from every enabled emitter at its configured
    // rate. `max_count` is the global cap that applies across all emitters.
    void emit_from_emitters(int max_count) {
        for (const auto& e : emitters_) {
            if (!e.enabled || e.per_frame <= 0) continue;
            emit(e.x, e.y, e.per_frame, e.vx_jitter, e.vy_jitter, max_count);
        }
    }

private:
    // Tiny xorshift32 — keeps the dependency surface tiny and reproducible.
    float rand_unit() noexcept {
        std::uint32_t x = rng_state_;
        x ^= x << 13;
        x ^= x >> 17;
        x ^= x << 5;
        rng_state_ = x;
        return static_cast<float>(x & 0x00FFFFFFu) / static_cast<float>(0x01000000u);
    }

    std::vector<FlowParticle> particles_;
    std::vector<FlowEmitter>  emitters_;
    std::uint32_t rng_state_ = 0xDEADBEEFu;
};

} // namespace ekchous::softbody
