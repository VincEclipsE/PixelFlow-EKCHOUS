#pragma once

// Spatial-hash broadphase for particle-particle collisions. Ported from
// PixelFlow's DwCollisionGrid (per-pixel-linked-list / PPLL layout).
//
// Each particle is inserted into every cell its (rad_collision)-AABB
// overlaps. During solve, each particle walks the buckets it overlaps
// and pairwise-processes every other particle stored there. The
// collision_ptr_idx field on Particle deduplicates pairs that share
// multiple buckets.
//
// The grid auto-fits its bounds + cell size from the current particle
// set at the start of each updateCollisions().

#include "engine/sim/softbody/Particle.h"
#include <vector>

namespace ekchous::softbody {

class CollisionGrid {
public:
    CollisionGrid() = default;

    // Resolve all pairwise collisions. Mutates Particle.collision_x/y/count
    // and Particle.collision_ptr_idx on the affected particles. Caller is
    // expected to apply collision_x/y as a position correction after this.
    void update_collisions(std::vector<Particle>& particles);

private:
    void compute_bounds(const std::vector<Particle>& particles);
    void resize(int gx, int gy, int ppll_size);
    void insert(int particle_idx, const Particle& p);
    void solve_collision(int particle_idx, std::vector<Particle>& particles);
    void collide_pair(int self_idx, int other_idx, std::vector<Particle>& particles);

    // Auto-fit bounds in [xmin, ymin, xmax, ymax] world coords.
    float bounds_[4] = {0, 0, 0, 0};
    float cell_size_ = 10.0f;
    int   grid_x_    = 0;
    int   grid_y_    = 0;

    // Per-pixel-linked-list storage. HEAD[gid] is the head of a singly-
    // linked list of slot indices; NEXT[slot] is the next slot in the
    // list; DATA[slot] is the particle index. head_ptr_ is the next free
    // slot (starts at 1; slot 0 is the sentinel = "end of list").
    int               head_ptr_ = 1;
    std::vector<int>  head_;
    std::vector<int>  next_;
    std::vector<int>  data_;
};

} // namespace ekchous::softbody
