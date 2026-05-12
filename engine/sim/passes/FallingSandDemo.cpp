#include "engine/sim/passes/FallingSandDemo.h"
#include "engine/core/Logger.h"
#include <algorithm>

namespace ekchous::sim {

FallingSandDemo::FallingSandDemo(core::u64 world_seed) noexcept
    : world_seed_(world_seed), rng_(world_seed) {}

void FallingSandDemo::reset() {
    chunk_.clear();
    in_flux_.clear();
    next_particle_id_ = 0;
    settled_count_ = 0;
    inject_scenario();
    rebuild_hash_buffer();
}

void FallingSandDemo::inject_scenario() {
    // Static stone floor at the bottom 4 rows. Pass 14 rigid path = these
    // pixels never move; they are body_id=1 with integration_mode=RIGID.
    for (int y = 0; y < 4; ++y) {
        for (int x = 0; x < kChunkSize; ++x) {
            auto& slot = chunk_.at(x, y);
            slot.element_id = kElementStone;
            slot.set_state(core::PixelState::Inert);
            slot.body_id_low = 1;
        }
    }
}

void FallingSandDemo::pass_emit_new_particles(core::u64 tick_index) {
    // Emit up to 4 sand particles per tick from the top of the chunk, in a
    // stable left-to-right pattern derived from the seeded RNG.
    // Determinism: the RNG output depends only on (world_seed, tick_index, slot_id).
    constexpr int kEmitPerTick = 4;
    constexpr int kEmitMaxX = kChunkSize - 2;
    for (int i = 0; i < kEmitPerTick; ++i) {
        core::u32 x = rng_.u32_below(tick_index, 0xC0DE0000u + i, kEmitMaxX);
        InFluxParticle p;
        p.pos = {determinism::Q16_16::from_int(static_cast<core::i32>(x)),
                 determinism::Q16_16::from_int(static_cast<core::i32>(kChunkSize - 1))};
        p.vel = {determinism::Q8_8::from_int(0), determinism::Q8_8::from_int(0)};
        p.element_id = kElementSand;
        p.rest_counter = 0;
        p.flags = 0;
        p._pad = 0;
        p.particle_id = next_particle_id_++;
        in_flux_.push_back(p);
    }
}

void FallingSandDemo::pass_in_flux_integrate(core::u64 tick_index) {
    (void)tick_index;
    // Apply gravity (downward = -y) and integrate position.
    for (auto& p : in_flux_) {
        // velocity += gravity
        p.vel.y = p.vel.y - kGravityPerTick;
        // position += velocity
        p.pos = determinism::step(p.pos, p.vel);

        // Rest detection: |v|² below threshold accumulates rest_counter.
        const core::i64 mag2 = determinism::vel_mag2(p.vel);
        if (mag2 < kRestVelMag2Threshold) {
            if (p.rest_counter < 255) ++p.rest_counter;
        } else {
            p.rest_counter = 0;
        }
    }
}

void FallingSandDemo::pass_collision_resolve(core::u64 tick_index) {
    (void)tick_index;

    // Sort particles by stable (target_cell, particle_id) before resolving.
    // This is the propose-then-resolve pattern (docs/determinism.md § atomic discipline).
    // For the simple Day-One demo, we just check each particle's target cell
    // against the chunk grid in a stable order (sorted by particle_id).
    std::sort(in_flux_.begin(), in_flux_.end(),
              [](const InFluxParticle& a, const InFluxParticle& b) {
                  // Stable order: by particle_id (unique).
                  return a.particle_id < b.particle_id;
              });

    std::vector<InFluxParticle> survivors;
    survivors.reserve(in_flux_.size());

    for (auto& p : in_flux_) {
        const int ix = p.pos.x.to_int();
        const int iy = p.pos.y.to_int();

        // Out-of-bounds drop — below the floor or off sides.
        if (iy < 0) continue;
        if (!chunk_.in_bounds(ix, iy)) {
            survivors.push_back(p);
            continue;
        }

        // If the target cell is occupied (stone floor or another settled grain),
        // try to settle one cell above. If THAT is also occupied, the particle is
        // squashed against the floor — push it back up if possible.
        auto& target = chunk_.at(ix, iy);
        if (target.element_id != kElementVacuum) {
            // Settle directly above the obstacle if possible.
            const int sy = iy + 1;
            if (sy < kChunkSize && chunk_.at(ix, sy).element_id == kElementVacuum) {
                auto& above = chunk_.at(ix, sy);
                above.element_id = p.element_id;
                above.set_state(core::PixelState::Inert);
                ++settled_count_;
                continue;
            }
            // Try lateral spill (deterministic: left, then right) so piles spread out.
            if (ix > 0 && chunk_.at(ix - 1, iy).element_id == kElementVacuum) {
                auto& left = chunk_.at(ix - 1, iy);
                left.element_id = p.element_id;
                left.set_state(core::PixelState::Inert);
                ++settled_count_;
                continue;
            }
            if (ix < kChunkSize - 1 && chunk_.at(ix + 1, iy).element_id == kElementVacuum) {
                auto& right = chunk_.at(ix + 1, iy);
                right.element_id = p.element_id;
                right.set_state(core::PixelState::Inert);
                ++settled_count_;
                continue;
            }
            // No room — drop the particle.
            continue;
        }

        // Target cell is vacuum. If the particle has been below rest threshold
        // for K=8 ticks AND there's a solid 4-neighbour, snap it to the grid.
        bool has_solid_neighbour = false;
        if (iy > 0 && chunk_.at(ix, iy - 1).element_id != kElementVacuum) has_solid_neighbour = true;
        if (!has_solid_neighbour && ix > 0 && chunk_.at(ix - 1, iy).element_id != kElementVacuum) has_solid_neighbour = true;
        if (!has_solid_neighbour && ix < kChunkSize - 1 && chunk_.at(ix + 1, iy).element_id != kElementVacuum) has_solid_neighbour = true;

        if (p.rest_counter >= kRestThresholdTicks && has_solid_neighbour) {
            target.element_id = p.element_id;
            target.set_state(core::PixelState::Inert);
            ++settled_count_;
            continue;
        }

        // Particle stays in flux.
        survivors.push_back(p);
    }
    in_flux_ = std::move(survivors);
}

void FallingSandDemo::tick(core::u64 tick_index) {
    pass_emit_new_particles(tick_index);
    pass_in_flux_integrate(tick_index);
    pass_collision_resolve(tick_index);
    rebuild_hash_buffer();
}

void FallingSandDemo::rebuild_hash_buffer() {
    // Order: chunk pixels, then in_flux particles sorted by particle_id.
    // Sort here ensures the hash is order-independent of insertion patterns.
    std::sort(in_flux_.begin(), in_flux_.end(),
              [](const InFluxParticle& a, const InFluxParticle& b) {
                  return a.particle_id < b.particle_id;
              });

    hash_buffer_.clear();
    hash_buffer_.reserve(chunk_.byte_count() + in_flux_.size() * sizeof(InFluxParticle));
    hash_buffer_.insert(hash_buffer_.end(),
                        chunk_.data(),
                        chunk_.data() + chunk_.byte_count());
    if (!in_flux_.empty()) {
        const core::u8* p = reinterpret_cast<const core::u8*>(in_flux_.data());
        hash_buffer_.insert(hash_buffer_.end(),
                            p,
                            p + in_flux_.size() * sizeof(InFluxParticle));
    }
}

} // namespace ekchous::sim
