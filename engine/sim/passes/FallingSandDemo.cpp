#include "engine/sim/passes/FallingSandDemo.h"
#include "engine/core/Logger.h"
#include <algorithm>

namespace ekchous::sim {

namespace {

// Deterministic 32-bit integer hash (xorshift-mix). Identical inputs always
// produce the same byte — preserves the determinism contract while giving
// each particle its own colour identity.
core::u8 jitter_hash(core::u64 world_seed, core::u32 a, core::u32 b) noexcept {
    core::u64 h = world_seed * 0x9E3779B97F4A7C15ULL;
    h ^= core::u64(a) * 0xBF58476D1CE4E5B9ULL;
    h ^= core::u64(b) * 0x94D049BB133111EBULL;
    h ^= h >> 30;
    h *= 0xBF58476D1CE4E5B9ULL;
    h ^= h >> 27;
    h *= 0x94D049BB133111EBULL;
    h ^= h >> 31;
    return static_cast<core::u8>(h & 0xFFu);
}

constexpr core::u8 kFlagFuzzy    = 0x01;
constexpr core::u8 kFlagAtRest   = 0x02;
constexpr core::u8 kFlagAnchored = 0x04;

} // namespace

FallingSandDemo::FallingSandDemo(core::u64 world_seed, int chunk_size) noexcept
    : world_seed_(world_seed),
      rng_(world_seed),
      chunk_(chunk_size),
      cell_to_pid_(static_cast<std::size_t>(chunk_size) * chunk_size, -1) {}

void FallingSandDemo::reset() {
    chunk_.clear();
    particles_.clear();
    bonds_.clear();
    next_particle_id_ = 0;
    at_rest_count_ = 0;
    const std::size_t cells = static_cast<std::size_t>(chunk_.size()) * chunk_.size();
    cell_to_pid_.assign(cells, -1);
    inject_scenario();
    pass_stamp_to_grid();
    rebuild_hash_buffer();
}

void FallingSandDemo::inject_scenario() {
    // Static stone floor: anchored particles at y in [0, floor_h). Adjacent
    // stone particles bond to each other (4-neighbour) so the floor is one
    // rigid cluster from the start.
    const int N = chunk_.size();
    const int floor_h = floor_rows();
    for (int y = 0; y < floor_h; ++y) {
        for (int x = 0; x < N; ++x) {
            const std::size_t idx = spawn_particle_at(x, y, kElementStone, kFlagAnchored | kFlagAtRest);
            particles_[idx].residency_ticks = static_cast<core::u8>(rest_threshold_ticks_);
        }
    }
    ++at_rest_count_;  // counter is a UI hint; close-enough approximation
    at_rest_count_ = particles_.size();

    // Form bonds between 4-neighbour stone particles. Build a quick (x,y)→idx
    // lookup from the spawned floor row.
    const auto lookup = [&](int x, int y) -> int {
        if (x < 0 || x >= N || y < 0 || y >= floor_h) return -1;
        // Particles spawned in row-major order, so idx = y*N + x.
        return y * N + x;
    };
    const core::u8 stone_strength = static_cast<core::u8>(bond_strength_stone_);
    for (int y = 0; y < floor_h; ++y) {
        for (int x = 0; x < N; ++x) {
            const int me = lookup(x, y);
            if (me < 0) continue;
            // Right and up neighbours (avoid duplicating).
            const int right = lookup(x + 1, y);
            const int up    = lookup(x, y + 1);
            if (right >= 0) {
                Bond b;
                b.a_id = particles_[me].particle_id;
                b.b_id = particles_[right].particle_id;
                if (b.a_id > b.b_id) std::swap(b.a_id, b.b_id);
                b.strength_q8 = stone_strength;
                b._pad0 = b._pad1 = b._pad2 = 0;
                bonds_.push_back(b);
            }
            if (up >= 0) {
                Bond b;
                b.a_id = particles_[me].particle_id;
                b.b_id = particles_[up].particle_id;
                if (b.a_id > b.b_id) std::swap(b.a_id, b.b_id);
                b.strength_q8 = stone_strength;
                b._pad0 = b._pad1 = b._pad2 = 0;
                bonds_.push_back(b);
            }
        }
    }
}

std::size_t FallingSandDemo::spawn_particle_at(int x, int y, core::u8 element_id,
                                                core::u8 flags) noexcept {
    Particle p;
    p.pos = {determinism::Q16_16::from_int(static_cast<core::i32>(x)),
             determinism::Q16_16::from_int(static_cast<core::i32>(y))};
    p.vel = {determinism::Q8_8::from_int(0), determinism::Q8_8::from_int(0)};
    p.particle_id = next_particle_id_++;
    p.last_cell_x = static_cast<core::i16>(x);
    p.last_cell_y = static_cast<core::i16>(y);
    p.element_id = element_id;
    p.color_jitter = jitter_hash(world_seed_, p.particle_id, 0);
    p.flags = flags;
    p.residency_ticks = 0;
    particles_.push_back(p);
    return particles_.size() - 1;
}

void FallingSandDemo::paint_cell(int x, int y, core::u8 element_id, bool fuzzy) {
    if (!chunk_.in_bounds(x, y)) return;

    if (element_id == kElementVacuum) {
        // Erase any particle at this integer cell, plus its incident bonds.
        const auto erase_idx_iter = std::remove_if(
            particles_.begin(), particles_.end(),
            [x, y](const Particle& p) {
                return p.pos.x.to_int() == x && p.pos.y.to_int() == y;
            });
        // Collect ids being erased so we can prune bonds.
        std::vector<core::u32> erased_ids;
        for (auto it = erase_idx_iter; it != particles_.end(); ++it) {
            erased_ids.push_back(it->particle_id);
        }
        particles_.erase(erase_idx_iter, particles_.end());
        if (!erased_ids.empty()) {
            bonds_.erase(
                std::remove_if(bonds_.begin(), bonds_.end(),
                    [&](const Bond& b) {
                        for (core::u32 id : erased_ids) {
                            if (b.a_id == id || b.b_id == id) return true;
                        }
                        return false;
                    }),
                bonds_.end());
        }
        return;
    }

    // Idempotent: if a particle already occupies this cell, do nothing. This
    // is what prevents the "infinite tower" bug when LMB is held over a single
    // cell — without this every render frame would spawn another particle.
    for (const auto& q : particles_) {
        if (q.pos.x.to_int() == x && q.pos.y.to_int() == y) return;
    }

    const core::u8 flags = static_cast<core::u8>(fuzzy ? kFlagFuzzy : 0);
    const std::size_t new_idx = spawn_particle_at(x, y, element_id, flags);

    // Form bonds with any 4-neighbour particles of compatible element. v1
    // starter: same-element only. Sand/water valence is 0 so this is a no-op
    // for them; stone gets adjacency bonds.
    const int my_valence = element_valence(element_id);
    if (my_valence > 0) {
        const int dxs[4] = { 1, -1, 0, 0 };
        const int dys[4] = { 0,  0, 1, -1 };
        int my_bond_count = 0;
        const core::u32 my_id = particles_[new_idx].particle_id;
        // Tally existing bonds for the new particle (should be 0).
        for (const auto& b : bonds_) {
            if (b.a_id == my_id || b.b_id == my_id) ++my_bond_count;
        }
        for (int d = 0; d < 4 && my_bond_count < my_valence; ++d) {
            const int nx = x + dxs[d];
            const int ny = y + dys[d];
            for (std::size_t i = 0; i < particles_.size(); ++i) {
                if (i == new_idx) continue;
                const auto& np = particles_[i];
                if (np.element_id != element_id) continue;
                if (np.flags & kFlagAnchored) continue;  // never bond to anchored
                if (np.pos.x.to_int() != nx || np.pos.y.to_int() != ny) continue;
                // Check neighbour's free valence.
                int n_bonds = 0;
                for (const auto& b : bonds_) {
                    if (b.a_id == np.particle_id || b.b_id == np.particle_id) ++n_bonds;
                }
                if (n_bonds >= my_valence) break;
                // Add bond.
                Bond b;
                b.a_id = my_id;
                b.b_id = np.particle_id;
                if (b.a_id > b.b_id) std::swap(b.a_id, b.b_id);
                b.strength_q8 = static_cast<core::u8>(element_bond_strength_q8(element_id));
                b._pad0 = b._pad1 = b._pad2 = 0;
                bonds_.push_back(b);
                ++my_bond_count;
                break;
            }
        }
    }
}

void FallingSandDemo::pass_emit(core::u64 tick_index) {
    const int N = chunk_.size();
    const int kEmitPerTick = emits_per_tick();
    const int kEmitMaxX = N - 2;
    for (int i = 0; i < kEmitPerTick; ++i) {
        core::u32 x = rng_.u32_below(tick_index, 0xC0DE0000u + i, kEmitMaxX);
        const int xi = static_cast<int>(x);
        const int yi = N - 1;
        // Skip if a particle already sits at the chosen cell (prevents
        // duplicates from accumulating when the top row is congested).
        bool occupied = false;
        for (const auto& q : particles_) {
            if (q.pos.x.to_int() == xi && q.pos.y.to_int() == yi) {
                occupied = true;
                break;
            }
        }
        if (occupied) continue;
        spawn_particle_at(xi, yi, kElementSand, 0);
    }
}

void FallingSandDemo::pass_integrate(core::u64 tick_index) {
    (void)tick_index;
    const determinism::Q8_8 gravity{gravity_raw_};
    const core::i16 vmin = static_cast<core::i16>(-terminal_vel_raw_);
    const core::i16 vmax = terminal_vel_raw_;
    for (auto& p : particles_) {
        if (p.flags & kFlagAnchored) continue;
        if (p.flags & kFlagAtRest)   continue;
        p.vel.y = p.vel.y - gravity;
        if (p.vel.y.raw < vmin) p.vel.y = determinism::Q8_8{vmin};
        if (p.vel.y.raw > vmax) p.vel.y = determinism::Q8_8{vmax};
    }
}

int FallingSandDemo::uf_find(std::vector<int>& parent, int i) const noexcept {
    while (parent[i] != i) {
        parent[i] = parent[parent[i]];  // path halving
        i = parent[i];
    }
    return i;
}

void FallingSandDemo::uf_union(std::vector<int>& parent, int a, int b) const noexcept {
    const int ra = uf_find(parent, a);
    const int rb = uf_find(parent, b);
    if (ra == rb) return;
    // Union by lower index for determinism.
    if (ra < rb) parent[rb] = ra;
    else         parent[ra] = rb;
}

void FallingSandDemo::pass_bond_break() {
    // Build particle_id → index lookup (bonds reference ids, not indices).
    // Sort particles by id first so lookup can be binary-search-style.
    std::sort(particles_.begin(), particles_.end(),
              [](const Particle& a, const Particle& b) {
                  return a.particle_id < b.particle_id;
              });
    const auto find_idx_by_id = [&](core::u32 id) -> int {
        auto it = std::lower_bound(particles_.begin(), particles_.end(), id,
            [](const Particle& p, core::u32 v) { return p.particle_id < v; });
        if (it == particles_.end() || it->particle_id != id) return -1;
        return static_cast<int>(std::distance(particles_.begin(), it));
    };

    // Walk bonds, removing those whose endpoints have |Δv| > strength.
    bonds_.erase(
        std::remove_if(bonds_.begin(), bonds_.end(),
            [&](const Bond& b) {
                const int ai = find_idx_by_id(b.a_id);
                const int bi = find_idx_by_id(b.b_id);
                if (ai < 0 || bi < 0) return true;  // endpoint gone → bond gone
                const int dvx = static_cast<int>(particles_[ai].vel.x.raw) -
                                static_cast<int>(particles_[bi].vel.x.raw);
                const int dvy = static_cast<int>(particles_[ai].vel.y.raw) -
                                static_cast<int>(particles_[bi].vel.y.raw);
                const core::i64 mag2 = static_cast<core::i64>(dvx) * dvx +
                                       static_cast<core::i64>(dvy) * dvy;
                const core::i64 thresh = static_cast<core::i64>(b.strength_q8) * b.strength_q8;
                return mag2 > thresh;
            }),
        bonds_.end());
}

void FallingSandDemo::pass_cluster_merge() {
    // Union-find on bonds. Then for each cluster, compute mass-weighted
    // velocity and assign back to every member. Anchored particles stay at
    // vel=0 and force their cluster's velocity to 0 too (they pin clusters
    // they're part of, which is how the floor anchors anything resting on it
    // if it ever forms a bond — sand/water won't, but it's a safety net).
    const std::size_t n = particles_.size();
    if (n == 0) return;
    std::vector<int> parent(n);
    for (std::size_t i = 0; i < n; ++i) parent[i] = static_cast<int>(i);

    // Build id→index lookup (particles are sorted by id from pass_bond_break).
    const auto find_idx_by_id = [&](core::u32 id) -> int {
        auto it = std::lower_bound(particles_.begin(), particles_.end(), id,
            [](const Particle& p, core::u32 v) { return p.particle_id < v; });
        if (it == particles_.end() || it->particle_id != id) return -1;
        return static_cast<int>(std::distance(particles_.begin(), it));
    };

    for (const auto& b : bonds_) {
        const int ai = find_idx_by_id(b.a_id);
        const int bi = find_idx_by_id(b.b_id);
        if (ai < 0 || bi < 0) continue;
        uf_union(parent, ai, bi);
    }

    // For each root, accumulate total momentum + mass. Anchored membership
    // flag flips the cluster's vel to 0.
    std::vector<core::i64> sum_px(n, 0);
    std::vector<core::i64> sum_py(n, 0);
    std::vector<core::i64> sum_m(n, 0);
    std::vector<char> has_anchor(n, 0);
    for (std::size_t i = 0; i < n; ++i) {
        const int r = uf_find(parent, static_cast<int>(i));
        const core::i32 m = pixel_mass_q88(particles_[i].element_id);
        sum_px[r] += static_cast<core::i64>(particles_[i].vel.x.raw) * m;
        sum_py[r] += static_cast<core::i64>(particles_[i].vel.y.raw) * m;
        sum_m[r] += m;
        if (particles_[i].flags & kFlagAnchored) has_anchor[r] = 1;
    }

    for (std::size_t i = 0; i < n; ++i) {
        if (particles_[i].flags & kFlagAnchored) continue;  // anchored stays at vel=0
        const int r = uf_find(parent, static_cast<int>(i));
        if (has_anchor[r]) {
            particles_[i].vel = {determinism::Q8_8::from_int(0),
                                 determinism::Q8_8::from_int(0)};
            continue;
        }
        if (sum_m[r] == 0) continue;
        const core::i64 avg_vx = sum_px[r] / sum_m[r];
        const core::i64 avg_vy = sum_py[r] / sum_m[r];
        particles_[i].vel.x.raw = static_cast<core::i16>(avg_vx);
        particles_[i].vel.y.raw = static_cast<core::i16>(avg_vy);
    }
}

void FallingSandDemo::pass_bond_form() {
    // Scan 4-neighbour pairs of same-element particles with free valence.
    // Determinism: iterate particles in id order (already sorted), and for
    // each, scan its 4 neighbours in fixed (right, up, left, down) order.
    //
    // Spatial index: cell_to_pid_ is rebuilt from current positions before
    // this pass so we can O(1) look up neighbours.
    const int N = chunk_.size();
    std::fill(cell_to_pid_.begin(), cell_to_pid_.end(), -1);
    for (std::size_t i = 0; i < particles_.size(); ++i) {
        const int x = particles_[i].pos.x.to_int();
        const int y = particles_[i].pos.y.to_int();
        if (x >= 0 && x < N && y >= 0 && y < N) {
            cell_to_pid_[static_cast<std::size_t>(y) * N + x] = static_cast<int>(i);
        }
    }

    const int dxs[4] = { 1, 0, -1, 0 };
    const int dys[4] = { 0, 1,  0, -1 };
    for (std::size_t i = 0; i < particles_.size(); ++i) {
        const core::u8 my_el = particles_[i].element_id;
        const int my_valence = element_valence(my_el);
        if (my_valence <= 0) continue;

        const core::u32 my_id = particles_[i].particle_id;
        int my_bond_count = 0;
        for (const auto& b : bonds_) {
            if (b.a_id == my_id || b.b_id == my_id) ++my_bond_count;
        }
        if (my_bond_count >= my_valence) continue;

        const int x = particles_[i].pos.x.to_int();
        const int y = particles_[i].pos.y.to_int();
        for (int d = 0; d < 4 && my_bond_count < my_valence; ++d) {
            const int nx = x + dxs[d];
            const int ny = y + dys[d];
            if (nx < 0 || nx >= N || ny < 0 || ny >= N) continue;
            const int ni = cell_to_pid_[static_cast<std::size_t>(ny) * N + nx];
            if (ni < 0) continue;
            if (particles_[ni].element_id != my_el) continue;
            if (particles_[ni].flags & kFlagAnchored) continue;  // never bond to anchored
            const core::u32 n_id = particles_[ni].particle_id;
            // Skip if bond already exists.
            bool already_bonded = false;
            core::u32 lo = my_id < n_id ? my_id : n_id;
            core::u32 hi = my_id < n_id ? n_id : my_id;
            for (const auto& b : bonds_) {
                if (b.a_id == lo && b.b_id == hi) { already_bonded = true; break; }
            }
            if (already_bonded) continue;
            // Check neighbour's free valence.
            int n_bond_count = 0;
            for (const auto& b : bonds_) {
                if (b.a_id == n_id || b.b_id == n_id) ++n_bond_count;
            }
            if (n_bond_count >= my_valence) continue;
            Bond b;
            b.a_id = lo;
            b.b_id = hi;
            b.strength_q8 = static_cast<core::u8>(element_bond_strength_q8(my_el));
            b._pad0 = b._pad1 = b._pad2 = 0;
            bonds_.push_back(b);
            ++my_bond_count;
        }
    }
}

void FallingSandDemo::pass_move() {
    // Deterministic move pass. Particles are already sorted by particle_id
    // (pass_bond_break sorted them). Iterate in that order. Each particle's
    // motion is decomposed into integer cell steps along y then x; each step
    // checks the per-tick claim grid for collisions.
    const int N = chunk_.size();

    // Rebuild the claim grid from current positions.
    std::fill(cell_to_pid_.begin(), cell_to_pid_.end(), -1);
    for (std::size_t i = 0; i < particles_.size(); ++i) {
        const int x = particles_[i].pos.x.to_int();
        const int y = particles_[i].pos.y.to_int();
        if (x >= 0 && x < N && y >= 0 && y < N) {
            cell_to_pid_[static_cast<std::size_t>(y) * N + x] = static_cast<int>(i);
        }
    }
    const auto cell_idx = [N](int x, int y) -> std::size_t {
        return static_cast<std::size_t>(y) * N + x;
    };

    for (std::size_t pi = 0; pi < particles_.size(); ++pi) {
        auto& p = particles_[pi];
        if (p.flags & kFlagAnchored) continue;
        if (p.flags & kFlagAtRest)   continue;

        const int cur_x = p.pos.x.to_int();
        const int cur_y = p.pos.y.to_int();

        // Compute next pos in q16.16, then convert to integer cell.
        const core::i32 next_x_q16 = p.pos.x.raw + (static_cast<core::i32>(p.vel.x.raw) << 8);
        core::i32 next_y_q16 = p.pos.y.raw + (static_cast<core::i32>(p.vel.y.raw) << 8);

        int next_cx = next_x_q16 >> 16;
        int next_cy = next_y_q16 >> 16;

        // Force at least 1 cell of fall if vel.y is non-zero negative — matches
        // the legacy "painted sand starts falling on tick 1" behaviour even
        // before fractional pos accumulates a full cell.
        if (p.vel.y.raw < 0 && next_cy == cur_y) {
            next_cy = cur_y - 1;
        }

        // Step y first (gravity dominates). Cell-by-cell so we can collide.
        int landed_y = cur_y;
        if (next_cy != cur_y) {
            const int step_dir = next_cy < cur_y ? -1 : +1;
            const int steps = next_cy < cur_y ? cur_y - next_cy : next_cy - cur_y;
            for (int s = 0; s < steps; ++s) {
                const int try_y = landed_y + step_dir;
                if (try_y < 0 || try_y >= N) break;
                const int occupant = cell_to_pid_[cell_idx(cur_x, try_y)];
                if (occupant >= 0 && occupant != static_cast<int>(pi)) break;
                cell_to_pid_[cell_idx(cur_x, landed_y)] = -1;
                cell_to_pid_[cell_idx(cur_x, try_y)] = static_cast<int>(pi);
                landed_y = try_y;
            }
            if (landed_y != next_cy) {
                // Collided. Rebound or zero depending on the three-zone rule.
                const int my_friction = static_cast<int>(pixel_friction_q88(p.element_id));
                const int my_mass = static_cast<int>(pixel_mass_q88(p.element_id));
                const int abs_vy = p.vel.y.raw < 0 ? -p.vel.y.raw : p.vel.y.raw;
                if (abs_vy <= my_friction) {
                    p.vel.y = determinism::Q8_8::from_int(0);
                } else if (abs_vy <= my_friction + my_mass) {
                    const int ry = -static_cast<int>(p.vel.y.raw) * rebound_percent_q8_ / 256;
                    p.vel.y.raw = static_cast<core::i16>(ry);
                } else {
                    p.vel.y = determinism::Q8_8::from_int(0);
                }
                // Snap q16.16 pos.y so accumulated fractional doesn't push
                // through the blocker next tick.
                next_y_q16 = static_cast<core::i32>(landed_y) << 16;
            }
        }

        // Step x. Same shape, on the row at landed_y.
        int landed_x = cur_x;
        if (next_cx != cur_x) {
            const int step_dir = next_cx < cur_x ? -1 : +1;
            const int steps = next_cx < cur_x ? cur_x - next_cx : next_cx - cur_x;
            for (int s = 0; s < steps; ++s) {
                const int try_x = landed_x + step_dir;
                if (try_x < 0 || try_x >= N) break;
                const int occupant = cell_to_pid_[cell_idx(try_x, landed_y)];
                if (occupant >= 0 && occupant != static_cast<int>(pi)) break;
                cell_to_pid_[cell_idx(landed_x, landed_y)] = -1;
                cell_to_pid_[cell_idx(try_x, landed_y)] = static_cast<int>(pi);
                landed_x = try_x;
            }
            if (landed_x != next_cx) {
                const int my_friction = static_cast<int>(pixel_friction_q88(p.element_id));
                const int my_mass = static_cast<int>(pixel_mass_q88(p.element_id));
                const int abs_vx = p.vel.x.raw < 0 ? -p.vel.x.raw : p.vel.x.raw;
                if (abs_vx <= my_friction) {
                    p.vel.x = determinism::Q8_8::from_int(0);
                } else if (abs_vx <= my_friction + my_mass) {
                    const int rx = -static_cast<int>(p.vel.x.raw) * rebound_percent_q8_ / 256;
                    p.vel.x.raw = static_cast<core::i16>(rx);
                } else {
                    p.vel.x = determinism::Q8_8::from_int(0);
                }
            }
        }

        // Commit pos. If the y axis collided we already snapped next_y_q16.
        // For axes that didn't collide we keep the fractional q16.16 motion,
        // so sub-cell accumulation works correctly.
        if (landed_x == next_cx) {
            p.pos.x.raw = next_x_q16;
        } else {
            p.pos.x.raw = static_cast<core::i32>(landed_x) << 16;
        }
        if (landed_y == next_cy) {
            p.pos.y.raw = next_y_q16;
        } else {
            p.pos.y.raw = static_cast<core::i32>(landed_y) << 16;
        }
    }
}

void FallingSandDemo::pass_residency_rest() {
    at_rest_count_ = 0;
    for (auto& p : particles_) {
        const int cx = p.pos.x.to_int();
        const int cy = p.pos.y.to_int();
        if (p.flags & kFlagAnchored) {
            ++at_rest_count_;
            continue;
        }
        if (cx == static_cast<int>(p.last_cell_x) && cy == static_cast<int>(p.last_cell_y)) {
            if (p.residency_ticks < 255) ++p.residency_ticks;
        } else {
            p.residency_ticks = 0;
            p.flags &= static_cast<core::u8>(~kFlagAtRest);
            p.last_cell_x = static_cast<core::i16>(cx);
            p.last_cell_y = static_cast<core::i16>(cy);
        }
        if (static_cast<int>(p.residency_ticks) >= rest_threshold_ticks_) {
            p.flags |= kFlagAtRest;
            p.vel = {determinism::Q8_8::from_int(0), determinism::Q8_8::from_int(0)};
            ++at_rest_count_;
        }
    }
}

void FallingSandDemo::pass_stamp_to_grid() {
    // Clear chunk, then stamp each particle's center cell. Diagonal radius
    // bleed (the visual "fuzzy" pass) lives in the renderer; here we just
    // mark the cells particles occupy so the renderer + the next tick's
    // collision queries have a consistent spatial index.
    //
    // Iterate in particle_id order (particles_ is sorted) so multiple
    // particles claiming the same cell deterministically resolve to the
    // higher-id particle.
    const int N = chunk_.size();
    for (int y = 0; y < N; ++y) {
        for (int x = 0; x < N; ++x) {
            auto& s = chunk_.at(x, y);
            s.element_id = kElementVacuum;
            s.set_state(core::PixelState::Inert);
            s.body_id_low = 0;
            s.body_id_high = 0;
            s.color_jitter = 0;
            s.flags = 0;
        }
    }
    for (const auto& p : particles_) {
        const int x = p.pos.x.to_int();
        const int y = p.pos.y.to_int();
        if (x < 0 || x >= N || y < 0 || y >= N) continue;
        auto& s = chunk_.at(x, y);
        s.element_id = p.element_id;
        s.set_state((p.flags & kFlagAtRest) ? core::PixelState::Inert : core::PixelState::InFlux);
        s.color_jitter = p.color_jitter;
        s.flags = p.flags;
    }
}

void FallingSandDemo::tick(core::u64 tick_index) {
    if (auto_emit_) pass_emit(tick_index);
    pass_integrate(tick_index);
    pass_bond_break();
    pass_cluster_merge();
    // pass_bond_form is intentionally NOT run every tick: in v1 starter,
    // bonds form only at paint time + inject_scenario. Per-tick scanning
    // would let any stone falling near the floor fuse to it, which we
    // don't want. Kept in the API for future cohesion experiments.
    pass_move();
    pass_residency_rest();
    pass_stamp_to_grid();
    rebuild_hash_buffer();
}

core::i32 FallingSandDemo::pixel_mass_q88(core::u8 element_id) const noexcept {
    switch (element_id) {
        case kElementStone: return mass_stone_;
        case kElementSand:  return mass_sand_;
        case kElementWater: return mass_water_;
        default:            return 0;
    }
}

core::i32 FallingSandDemo::pixel_friction_q88(core::u8 element_id) const noexcept {
    switch (element_id) {
        case kElementStone: return friction_stone_;
        case kElementSand:  return friction_sand_;
        case kElementWater: return friction_water_;
        default:            return 0;
    }
}

int FallingSandDemo::element_valence(core::u8 element_id) const noexcept {
    switch (element_id) {
        case kElementStone: return bond_valence_stone_;
        default:            return 0;  // sand/water/vacuum: no cohesion
    }
}

int FallingSandDemo::element_bond_strength_q8(core::u8 element_id) const noexcept {
    switch (element_id) {
        case kElementStone: return bond_strength_stone_;
        default:            return 0;
    }
}

int FallingSandDemo::element_probe_radius(core::u8 element_id) const noexcept {
    switch (element_id) {
        case kElementStone: return probe_radius_stone_;
        case kElementSand:  return probe_radius_sand_;
        case kElementWater: return probe_radius_water_;
        default:            return 1;
    }
}

void FallingSandDemo::rebuild_hash_buffer() {
    // Particles sorted by particle_id, bonds sorted by (a_id, b_id).
    std::sort(particles_.begin(), particles_.end(),
              [](const Particle& a, const Particle& b) {
                  return a.particle_id < b.particle_id;
              });
    std::sort(bonds_.begin(), bonds_.end(),
              [](const Bond& a, const Bond& b) {
                  if (a.a_id != b.a_id) return a.a_id < b.a_id;
                  return a.b_id < b.b_id;
              });

    hash_buffer_.clear();
    const std::size_t pbytes = particles_.size() * sizeof(Particle);
    const std::size_t bbytes = bonds_.size() * sizeof(Bond);
    hash_buffer_.reserve(pbytes + bbytes);
    if (!particles_.empty()) {
        const core::u8* p = reinterpret_cast<const core::u8*>(particles_.data());
        hash_buffer_.insert(hash_buffer_.end(), p, p + pbytes);
    }
    if (!bonds_.empty()) {
        const core::u8* b = reinterpret_cast<const core::u8*>(bonds_.data());
        hash_buffer_.insert(hash_buffer_.end(), b, b + bbytes);
    }
}

} // namespace ekchous::sim
