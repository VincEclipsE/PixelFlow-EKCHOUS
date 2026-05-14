#pragma once

// EKCHOUS particle physics layer 3 — auto-bond formation.
//
// Each call scans the live particle set, finds pairs of compatible atoms
// within their combined bond radius, and forms a Struct spring when:
//   - both elements have valence > 0
//   - both have bond_count < valence
//   - the pair isn't already bonded
//
// Bond stiffness is the average of the two elements' bond_strength.
// Bond rest length is set to the current pair distance — atoms snap
// together gently rather than yanking into the rest length.
//
// Existing spring tearing (Physics::params.spring_tear_threshold) handles
// the "bonds break under velocity change" rule, so all we need here is
// formation.
//
// O(N · k) via spatial-hash buckets keyed by the max element bond_radius.
// Element compatibility is currently "all-with-all" — adding a per-pair
// compatibility matrix is a follow-up.

#include "engine/sim/atoms/AtomElement.h"
#include "engine/sim/softbody/Physics.h"
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace ekchous::atoms {

inline int try_auto_bonds(softbody::Physics& physics, float radius_scale = 1.0f) {
    auto& parts   = physics.particles();
    auto& springs = physics.springs();
    if (parts.empty() || radius_scale <= 0.0f) return 0;

    const int n = static_cast<int>(parts.size());

    // Bond count + bonded-pair set in one pass over springs.
    std::vector<int> bond_count(n, 0);
    std::unordered_set<long long> bonded;
    bonded.reserve(springs.size() * 2);
    auto pair_key = [](int a, int b) -> long long {
        const long long lo = a < b ? a : b;
        const long long hi = a < b ? b : a;
        return lo * 1'000'000LL + hi;
    };
    for (const auto& s : springs) {
        if (!s.enabled) continue;
        if (s.a_idx >= 0 && s.a_idx < n) ++bond_count[s.a_idx];
        if (s.b_idx >= 0 && s.b_idx < n) ++bond_count[s.b_idx];
        bonded.insert(pair_key(s.a_idx, s.b_idx));
    }

    // Bucket cell size — slightly larger than the largest possible bond.
    float max_r = 0.0f;
    for (int i = 0; i < kNumElements; ++i) {
        if (kElements[i].bond_radius > max_r) max_r = kElements[i].bond_radius;
    }
    max_r *= radius_scale;
    if (max_r <= 0.0f) return 0;
    const float cell = max_r * 1.1f;

    auto cell_key = [](int cx, int cy) -> long long {
        return (long long)(cy + 100000) * 200000LL + (cx + 100000);
    };
    std::unordered_map<long long, std::vector<int>> buckets;
    buckets.reserve(static_cast<std::size_t>(n));
    for (int i = 0; i < n; ++i) {
        const int cx = static_cast<int>(parts[i].cx / cell);
        const int cy = static_cast<int>(parts[i].cy / cell);
        buckets[cell_key(cx, cy)].push_back(i);
    }

    int formed = 0;

    for (int i = 0; i < n; ++i) {
        const auto& pa   = parts[i];
        const auto& a_el = element(pa.element_id);
        if (a_el.valence == 0) continue;
        if (bond_count[i] >= a_el.valence) continue;

        const int cx = static_cast<int>(pa.cx / cell);
        const int cy = static_cast<int>(pa.cy / cell);

        for (int dy = -1; dy <= 1 && bond_count[i] < a_el.valence; ++dy) {
            for (int dx = -1; dx <= 1 && bond_count[i] < a_el.valence; ++dx) {
                auto it = buckets.find(cell_key(cx + dx, cy + dy));
                if (it == buckets.end()) continue;
                for (int j : it->second) {
                    if (bond_count[i] >= a_el.valence) break;
                    if (j <= i) continue;  // process each ordered pair once

                    const auto& pb   = parts[j];
                    const auto& b_el = element(pb.element_id);
                    if (b_el.valence == 0) continue;
                    if (bond_count[j] >= b_el.valence) continue;
                    if (bonded.count(pair_key(i, j))) continue;

                    const float ddx = pb.cx - pa.cx;
                    const float ddy = pb.cy - pa.cy;
                    const float d2 = ddx * ddx + ddy * ddy;
                    const float bond_r =
                        0.5f * (a_el.bond_radius + b_el.bond_radius) * radius_scale;
                    if (d2 > bond_r * bond_r) continue;

                    const float strength =
                        0.5f * (a_el.bond_strength + b_el.bond_strength);
                    const int s_idx =
                        physics.add_spring(i, j, softbody::SpringType::Struct);
                    auto& s = physics.springs()[s_idx];
                    s.damp_inc = strength;
                    s.damp_dec = strength;
                    ++bond_count[i];
                    ++bond_count[j];
                    bonded.insert(pair_key(i, j));
                    ++formed;
                }
            }
        }
    }
    return formed;
}

// try_fickle_bonds was removed when fickle-spring proxy gave way to a
// real per-particle friction coefficient. The proxy bond is gone; the
// friction value lives on ParticleTemplate and is applied during the
// collision response phase in Physics.

} // namespace ekchous::atoms
