#pragma once

// xxHash3-based per-tick golden-state hashing for determinism verification.
// See docs/determinism.md § "Test strategy".

#include "engine/core/Types.h"
#include <cstddef>
#include <vector>
#include <string>

namespace ekchous::determinism {

// Hash an arbitrary byte buffer to a 64-bit value (xxHash3).
core::u64 hash_bytes(const void* data, std::size_t bytes) noexcept;

// A per-tick log of golden hashes; used by the --replay-test gate.
class GoldenLog {
public:
    GoldenLog() = default;

    // Append the hash for the current tick.
    void record(core::u64 tick_hash) {
        hashes_.push_back(tick_hash);
    }

    // Compare two logs for bit-identical equality.
    // Returns -1 if equal; otherwise the tick index where they first diverge.
    static int compare(const GoldenLog& a, const GoldenLog& b) noexcept;

    const std::vector<core::u64>& hashes() const noexcept { return hashes_; }
    std::size_t size() const noexcept { return hashes_.size(); }

    // Persist to a file (one hex hash per line). Mostly for diagnostic dumps.
    void save(const std::string& path) const;

private:
    std::vector<core::u64> hashes_;
};

} // namespace ekchous::determinism
