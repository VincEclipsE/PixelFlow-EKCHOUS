#include "engine/sim/softbody/CollisionGrid.h"
#include <algorithm>
#include <cmath>
#include <limits>

namespace ekchous::softbody {

void CollisionGrid::resize(int gx, int gy, int ppll_size) {
    const std::size_t head_cells = static_cast<std::size_t>(gx) * gy;
    if (head_cells > head_.size()) {
        head_.resize(head_cells);
    }
    if (static_cast<std::size_t>(ppll_size) > next_.size()) {
        const std::size_t new_len = static_cast<std::size_t>(ppll_size * 1.2f);
        next_.resize(new_len);
        data_.resize(new_len);
    }
    std::fill(head_.begin(), head_.begin() + head_cells, 0);
    head_ptr_ = 1;
    grid_x_ = gx;
    grid_y_ = gy;
}

void CollisionGrid::compute_bounds(const std::vector<Particle>& particles) {
    float xmin = +std::numeric_limits<float>::max();
    float ymin = +std::numeric_limits<float>::max();
    float xmax = -std::numeric_limits<float>::max();
    float ymax = -std::numeric_limits<float>::max();

    float r_sum = 0.0f;
    for (const auto& p : particles) {
        const float r = p.rad_collision;
        if (p.cx - r < xmin) xmin = p.cx - r;
        if (p.cx + r > xmax) xmax = p.cx + r;
        if (p.cy - r < ymin) ymin = p.cy - r;
        if (p.cy + r > ymax) ymax = p.cy + r;
        r_sum += r;
    }
    bounds_[0] = xmin;
    bounds_[1] = ymin;
    bounds_[2] = xmax;
    bounds_[3] = ymax;
    if (!particles.empty()) {
        cell_size_ = (r_sum * 2.0f) / static_cast<float>(particles.size());
        if (cell_size_ < 1.0f) cell_size_ = 1.0f;
    } else {
        cell_size_ = 10.0f;
    }
}

void CollisionGrid::insert(int particle_idx, const Particle& p) {
    const float pr = p.rad_collision;
    const float px = p.cx - bounds_[0];
    const float py = p.cy - bounds_[1];

    int xmin = static_cast<int>((px - pr) / cell_size_);
    int xmax = static_cast<int>((px + pr) / cell_size_);
    int ymin = static_cast<int>((py - pr) / cell_size_);
    int ymax = static_cast<int>((py + pr) / cell_size_);

    if (xmin < 0) xmin = 0;
    if (ymin < 0) ymin = 0;
    if (xmax > grid_x_ - 1) xmax = grid_x_ - 1;
    if (ymax > grid_y_ - 1) ymax = grid_y_ - 1;

    const int count = (xmax - xmin + 1) * (ymax - ymin + 1);
    if (head_ptr_ + count > static_cast<int>(next_.size())) {
        // Defer: caller will detect overflow and resize.
        head_ptr_ += count;
        return;
    }

    for (int y = ymin; y <= ymax; ++y) {
        for (int x = xmin; x <= xmax; ++x) {
            const int gid = y * grid_x_ + x;
            const int slot = head_ptr_++;
            const int old_head = head_[gid];
            head_[gid] = slot;
            next_[slot] = old_head;
            data_[slot] = particle_idx;
        }
    }
}

void CollisionGrid::collide_pair(int self_idx, int other_idx,
                                  std::vector<Particle>& particles) {
    Particle& self  = particles[self_idx];
    Particle& other = particles[other_idx];

    if (!self.enable_collisions) return;
    if (other.collision_group == self.collision_group) return;
    if (self_idx == other_idx) return;
    if (other.collision_ptr_idx == self_idx) return;

    other.collision_ptr_idx = self_idx;

    const float dx        = other.cx - self.cx;
    const float dy        = other.cy - self.cy;
    const float dd_sq     = dx*dx + dy*dy;
    const float dd_min    = other.rad_collision + self.rad_collision;
    const float dd_min_sq = dd_min * dd_min;

    if (dd_sq < dd_min_sq) {
        const float self_mass_factor = 2.0f * other.mass / (self.mass + other.mass);
        const float damp = self.damp ? self.damp->collision : 1.0f;
        const float force = (dd_min_sq / (dd_sq + dd_min_sq) - 0.5f) * damp;

        self.collision_x -= dx * force * self_mass_factor;
        self.collision_y -= dy * force * self_mass_factor;
        ++self.collision_count;
    }
}

void CollisionGrid::solve_collision(int particle_idx, std::vector<Particle>& particles) {
    const Particle& p = particles[particle_idx];
    const float pr = p.rad_collision;
    const float px = p.cx - bounds_[0];
    const float py = p.cy - bounds_[1];

    int xmin = static_cast<int>((px - pr) / cell_size_);
    int xmax = static_cast<int>((px + pr) / cell_size_);
    int ymin = static_cast<int>((py - pr) / cell_size_);
    int ymax = static_cast<int>((py + pr) / cell_size_);

    if (xmin < 0) xmin = 0;
    if (ymin < 0) ymin = 0;
    if (xmax > grid_x_ - 1) xmax = grid_x_ - 1;
    if (ymax > grid_y_ - 1) ymax = grid_y_ - 1;

    for (int y = ymin; y <= ymax; ++y) {
        for (int x = xmin; x <= xmax; ++x) {
            const int gid = y * grid_x_ + x;
            int slot = head_[gid];
            while (slot > 0) {
                const int other_idx = data_[slot];
                collide_pair(particle_idx, other_idx, particles);
                slot = next_[slot];
            }
        }
    }
}

void CollisionGrid::update_collisions(std::vector<Particle>& particles) {
    if (particles.empty()) return;

    // 0) Reset per-particle collision_ptr for this sweep.
    for (auto& p : particles) p.collision_ptr_idx = -1;

    // 1) Compute bounds + cell size from current positions.
    compute_bounds(particles);

    const float span_x = bounds_[2] - bounds_[0];
    const float span_y = bounds_[3] - bounds_[1];
    int gx = static_cast<int>(std::ceil(span_x / cell_size_)) + 1;
    int gy = static_cast<int>(std::ceil(span_y / cell_size_)) + 1;
    if (gx < 1) gx = 1;
    if (gy < 1) gy = 1;
    int ppll_len = static_cast<int>(particles.size()) * 4 + 1;

    // 2) Resize storage and (re)build the PPLL.
    resize(gx, gy, ppll_len);
    for (std::size_t i = 0; i < particles.size(); ++i) {
        insert(static_cast<int>(i), particles[i]);
    }
    if (head_ptr_ > static_cast<int>(next_.size())) {
        resize(gx, gy, head_ptr_);
        for (std::size_t i = 0; i < particles.size(); ++i) {
            insert(static_cast<int>(i), particles[i]);
        }
    }

    // 3) Solve every particle's collisions.
    for (std::size_t i = 0; i < particles.size(); ++i) {
        solve_collision(static_cast<int>(i), particles);
    }
}

} // namespace ekchous::softbody
