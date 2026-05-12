#include "engine/determinism/GoldenHash.h"

#define XXH_INLINE_ALL
#include <xxhash.h>

#include <fstream>
#include <iomanip>

namespace ekchous::determinism {

core::u64 hash_bytes(const void* data, std::size_t bytes) noexcept {
    return XXH3_64bits(data, bytes);
}

int GoldenLog::compare(const GoldenLog& a, const GoldenLog& b) noexcept {
    const std::size_t n = std::min(a.hashes_.size(), b.hashes_.size());
    for (std::size_t i = 0; i < n; ++i) {
        if (a.hashes_[i] != b.hashes_[i]) {
            return static_cast<int>(i);
        }
    }
    if (a.hashes_.size() != b.hashes_.size()) {
        return static_cast<int>(n);
    }
    return -1;
}

void GoldenLog::save(const std::string& path) const {
    std::ofstream f(path);
    f << std::hex << std::setfill('0');
    for (std::size_t i = 0; i < hashes_.size(); ++i) {
        f << std::setw(16) << hashes_[i] << '\n';
    }
}

} // namespace ekchous::determinism
