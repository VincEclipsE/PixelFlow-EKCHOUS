#pragma once

// Particle-first falling-sand demo (post-big-bang rewrite).
//
// Every pixel is a Particle living in continuous q16.16 space. The chunk
// grid is a per-tick spatial index built by stamping each particle's
// radius footprint; it carries no state of its own. Static stone (the
// floor) is just anchored particles bonded into a rigid cluster.
//
// Per-tick pipeline:
//   1. emit            — spawn new sand at the top (deterministic)
//   2. integrate       — gravity, terminal-velocity cap (skips anchored)
//   3. bond_break      — for each bond, break if |Δv| > strength
//   4. cluster_merge   — union-find on bonds; cluster vel = mass-
//                        weighted average, assigned back to all members
//   5. bond_form       — adjacent compatible particles with free
//                        valence (sand=0, water=0, stone=4) get bonded
//   6. move            — deterministic claim grid; cell-by-cell stepping
//                        in particle_id order; downward probe blocks fall
//   7. residency_rest  — cell-residency counter (per particle); at
//                        threshold, vel→0, at_rest flag set
//   8. stamp_to_grid   — radius footprint into chunk (renderer + next
//                        tick's spatial index)
//   9. rebuild_hash    — particles (sorted by id) + bonds (sorted)
//
// Integer math throughout. q16.16 positions, q8.8 velocities. Float is
// banned in the sim path. All randomness flows through SeededRng over
// (world_seed, tick, identifier).

#include "engine/core/Types.h"
#include "engine/determinism/FixedPoint.h"
#include "engine/determinism/SeededRng.h"
#include "engine/sim/grid/Chunk.h"
#include <vector>
#include <cstdint>

namespace ekchous::sim {

// One simulated particle (24 bytes — matches previous InFluxParticle so the
// memory profile is unchanged).
//
// flags bits:
//   bit 0  fuzzy        — soft-radius render hint
//   bit 1  at_rest      — vel pinned to 0 by residency rest condition
//   bit 2  anchored     — never integrates (static floor, manual pins)
struct Particle {
    determinism::Vec2Q16_16 pos;     // 0..7
    determinism::Vec2Q8_8   vel;     // 8..11
    core::u32 particle_id;           // 12..15 — stable, monotonically allocated
    core::i16 last_cell_x;           // 16..17
    core::i16 last_cell_y;           // 18..19
    core::u8  element_id;            // 20
    core::u8  color_jitter;          // 21
    core::u8  flags;                 // 22
    core::u8  residency_ticks;       // 23
};
static_assert(sizeof(Particle) == 24, "Particle must be 24 bytes");

// A bond between two particles, stored with endpoint ids sorted (a_id < b_id)
// so the (a_id, b_id) pair is canonical. strength_q8 is the |Δv| break
// threshold in raw q8.8 velocity units.
struct Bond {
    core::u32 a_id;        // 0..3
    core::u32 b_id;        // 4..7
    core::u8  strength_q8; // 8
    core::u8  _pad0;       // 9
    core::u8  _pad1;       // 10
    core::u8  _pad2;       // 11
};
static_assert(sizeof(Bond) == 12, "Bond must be 12 bytes");

class FallingSandDemo {
public:
    explicit FallingSandDemo(core::u64 world_seed,
                             int chunk_size = kDefaultChunkSize) noexcept;

    void reset();
    void tick(core::u64 tick_index);

    // Serialized state bytes for the golden hash: particles sorted by
    // particle_id, then bonds sorted by (a_id, b_id). The chunk grid is
    // NOT hashed — it's a derived spatial index.
    const std::vector<core::u8>& grid_bytes() const noexcept { return hash_buffer_; }

    std::size_t particle_count() const noexcept { return particles_.size(); }
    std::size_t bond_count() const noexcept { return bonds_.size(); }
    std::size_t at_rest_count() const noexcept { return at_rest_count_; }

    // Engine.cpp back-compat aliases.
    std::size_t in_flux_count() const noexcept { return particles_.size(); }
    std::size_t settled_count() const noexcept { return at_rest_count_; }

    const Chunk& chunk() const noexcept { return chunk_; }
    const std::vector<Particle>& particles() const noexcept { return particles_; }
    const std::vector<Particle>& in_flux() const noexcept { return particles_; }
    const std::vector<Bond>& bonds() const noexcept { return bonds_; }

    void paint_cell(int x, int y, core::u8 element_id, bool fuzzy = false);

    void set_auto_emit(bool on) noexcept { auto_emit_ = on; }
    bool auto_emit() const noexcept { return auto_emit_; }

    static constexpr core::u8 element_vacuum() noexcept { return kElementVacuum; }
    static constexpr core::u8 element_stone()  noexcept { return kElementStone; }
    static constexpr core::u8 element_water()  noexcept { return kElementWater; }
    static constexpr core::u8 element_sand()   noexcept { return kElementSand; }

    void set_mass_stone(int v) noexcept   { mass_stone_   = v < 0 ? 0 : v; }
    void set_mass_sand(int v) noexcept    { mass_sand_    = v < 0 ? 0 : v; }
    void set_mass_water(int v) noexcept   { mass_water_   = v < 0 ? 0 : v; }
    void set_friction_stone(int v) noexcept { friction_stone_ = v < 0 ? 0 : v; }
    void set_friction_sand(int v) noexcept  { friction_sand_  = v < 0 ? 0 : v; }
    void set_friction_water(int v) noexcept { friction_water_ = v < 0 ? 0 : v; }
    int mass_stone() const noexcept   { return mass_stone_; }
    int mass_sand() const noexcept    { return mass_sand_; }
    int mass_water() const noexcept   { return mass_water_; }
    int friction_stone() const noexcept { return friction_stone_; }
    int friction_sand() const noexcept  { return friction_sand_; }
    int friction_water() const noexcept { return friction_water_; }

    void set_rebound_percent_q8(int v) noexcept {
        rebound_percent_q8_ = v < 0 ? 0 : (v > 256 ? 256 : v);
    }
    int rebound_percent_q8() const noexcept { return rebound_percent_q8_; }

    void set_gravity_per_tick_raw(core::i16 raw) noexcept { gravity_raw_ = raw; }
    void set_terminal_velocity_raw(core::i16 raw) noexcept { terminal_vel_raw_ = raw; }
    void set_rest_threshold_ticks(int t) noexcept { rest_threshold_ticks_ = t < 0 ? 0 : t; }

    core::i16 gravity_per_tick_raw() const noexcept { return gravity_raw_; }
    core::i16 terminal_velocity_raw() const noexcept { return terminal_vel_raw_; }
    int rest_threshold_ticks() const noexcept { return rest_threshold_ticks_; }

    // Per-element radius (cells) used by the downward support probe AND the
    // visual stamp footprint. Diagonals contribute half-weight on the stamp.
    void set_probe_radius_stone(int r) noexcept { probe_radius_stone_ = r < 1 ? 1 : r; }
    void set_probe_radius_sand(int r) noexcept  { probe_radius_sand_  = r < 1 ? 1 : r; }
    void set_probe_radius_water(int r) noexcept { probe_radius_water_ = r < 1 ? 1 : r; }
    int probe_radius_stone() const noexcept { return probe_radius_stone_; }
    int probe_radius_sand()  const noexcept { return probe_radius_sand_; }
    int probe_radius_water() const noexcept { return probe_radius_water_; }

    // Bond starter tunables: per-element max valence and base strength_q8.
    // Sand/water default to 0 valence (no cohesion); stone defaults to 4.
    void set_bond_valence_stone(int v) noexcept { bond_valence_stone_ = v < 0 ? 0 : (v > 8 ? 8 : v); }
    void set_bond_strength_stone(int v) noexcept { bond_strength_stone_ = v < 0 ? 0 : (v > 255 ? 255 : v); }
    int bond_valence_stone() const noexcept { return bond_valence_stone_; }
    int bond_strength_stone() const noexcept { return bond_strength_stone_; }

    // Removed APIs kept as no-op stubs so Engine.cpp still links during the
    // migration. Water spread and fuzzy bleed move to renderer concerns.
    void set_water_spread(int) noexcept {}
    int water_spread() const noexcept { return 1; }
    void set_fuzzy_pixel_radius(int) noexcept {}
    int fuzzy_pixel_radius() const noexcept { return 1; }

private:
    // Spawn anchored stone particles along y=0..floor_h-1 forming a static
    // floor. Stone particles bond on adjacency.
    void inject_scenario();

    // Per-tick passes.
    void pass_emit(core::u64 tick_index);
    void pass_integrate(core::u64 tick_index);
    void pass_bond_break();
    void pass_cluster_merge();
    void pass_bond_form();
    void pass_move();
    void pass_residency_rest();
    void pass_stamp_to_grid();
    void rebuild_hash_buffer();

    // Element identifiers.
    static constexpr core::u8 kElementVacuum = 0;
    static constexpr core::u8 kElementStone  = 1;
    static constexpr core::u8 kElementWater  = 2;
    static constexpr core::u8 kElementSand   = 4;

    static constexpr core::i16 kDefaultGravityRaw         = 30;
    static constexpr core::i16 kDefaultTerminalVelocityRaw = 256;
    static constexpr int       kDefaultRestThresholdTicks  = 4;

    int floor_rows() const noexcept {
        const int n = chunk_.size();
        return (n / 16) < 2 ? 2 : (n / 16);
    }
    int emits_per_tick() const noexcept {
        const int n = chunk_.size();
        return (n / 16) < 1 ? 1 : (n / 16);
    }

    core::i32 pixel_mass_q88(core::u8 element_id) const noexcept;
    core::i32 pixel_friction_q88(core::u8 element_id) const noexcept;
    int element_valence(core::u8 element_id) const noexcept;
    int element_bond_strength_q8(core::u8 element_id) const noexcept;
    int element_probe_radius(core::u8 element_id) const noexcept;

    // Spawn a particle with element/jitter/flags at integer cell (x, y),
    // assigning a fresh particle_id. Returns the new particle's index in
    // particles_. Caller must verify the cell is free.
    std::size_t spawn_particle_at(int x, int y, core::u8 element_id,
                                  core::u8 flags) noexcept;

    // Union-find helpers used by cluster_merge.
    int  uf_find(std::vector<int>& parent, int i) const noexcept;
    void uf_union(std::vector<int>& parent, int a, int b) const noexcept;

    core::u64 world_seed_;
    determinism::SeededRng rng_;
    Chunk chunk_;
    std::vector<Particle> particles_;
    std::vector<Bond> bonds_;
    std::vector<core::u8> hash_buffer_;
    core::u32 next_particle_id_ = 0;
    std::size_t at_rest_count_ = 0;
    bool auto_emit_ = true;

    // Per-tick spatial index: cell → particle index (or -1). Rebuilt at the
    // start of pass_move from current particle positions.
    std::vector<int> cell_to_pid_;

    core::i16 gravity_raw_           = kDefaultGravityRaw;
    core::i16 terminal_vel_raw_      = kDefaultTerminalVelocityRaw;
    int       rest_threshold_ticks_  = kDefaultRestThresholdTicks;

    int       mass_stone_      = 280;
    int       mass_sand_       = 300;
    int       mass_water_      = 64;
    int       friction_stone_  = 250;
    int       friction_sand_   = 50;
    int       friction_water_  = 10;
    int       rebound_percent_q8_ = 128;

    int       probe_radius_stone_  = 1;
    int       probe_radius_sand_   = 1;
    int       probe_radius_water_  = 1;

    int       bond_valence_stone_  = 4;
    int       bond_strength_stone_ = 200;
};

} // namespace ekchous::sim
