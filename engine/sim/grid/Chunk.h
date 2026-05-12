#pragma once

// Single-chunk pixel grid for the Day-One falling-sand demo.
//
// Production layout (per docs/data-layout.md) is a 16-byte packed SSBO with
// element_id, state, integration_mode, mutation, body_membership_flags,
// purity, temperature, charge, damage_accum, decay_progress, bonds,
// grain_orientation, compound_classification_slot, body_id (24-bit), flags.
//
// For Day-One we model just the subset the falling-sand demo needs:
//   - element_id, state, charge (=0 in demo), velocity (only for in_flux)
//
// The full 16-byte struct is documented here so future passes can extend
// without changing the file layout.

#include "engine/core/Types.h"
#include <array>
#include <cstring>

namespace ekchous::sim {

constexpr int kChunkSize = 64;
constexpr int kChunkPixelCount = kChunkSize * kChunkSize;

// 16-byte packed pixel slot, matching docs/data-layout.md exactly.
struct PixelSlot {
    core::u8  element_id;                  // 0
    core::u8  state_flags;                 // 1: state(2) | integration_mode(1) | mutation(1) | body_membership_flags(4)
    core::u8  purity;                      // 2
    core::i16 temperature_q12_4;           // 3..4
    core::u16 charge;                      // 5..6
    core::u8  damage_accum;                // 7
    core::u8  decay_progress;              // 8
    core::u8  bonds_and_grain;             // 9: bonds(5 low bits) | grain(3 high bits) — simplified
    core::u8  compound_slot;               // 10
    core::u16 body_id_low;                 // 11..12
    core::u8  body_id_high;                // 13
    core::u8  flags;                       // 14
    core::u8  _pad;                        // 15

    constexpr core::PixelState state() const noexcept {
        return static_cast<core::PixelState>(state_flags & 0b11);
    }
    void set_state(core::PixelState s) noexcept {
        state_flags = (state_flags & ~0b11) | static_cast<core::u8>(s);
    }
};
static_assert(sizeof(PixelSlot) == 16, "PixelSlot must be exactly 16 bytes per data-layout.md");

class Chunk {
public:
    Chunk();

    void clear();

    PixelSlot& at(int x, int y) noexcept {
        return pixels_[y * kChunkSize + x];
    }
    const PixelSlot& at(int x, int y) const noexcept {
        return pixels_[y * kChunkSize + x];
    }

    bool in_bounds(int x, int y) const noexcept {
        return x >= 0 && x < kChunkSize && y >= 0 && y < kChunkSize;
    }

    // Raw bytes for golden hashing.
    const core::u8* data() const noexcept {
        return reinterpret_cast<const core::u8*>(pixels_.data());
    }
    std::size_t byte_count() const noexcept {
        return sizeof(PixelSlot) * pixels_.size();
    }

private:
    std::array<PixelSlot, kChunkPixelCount> pixels_;
};

} // namespace ekchous::sim
