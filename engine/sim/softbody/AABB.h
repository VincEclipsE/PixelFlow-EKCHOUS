#pragma once

// 2D axis-aligned bounding box. Direct port of PixelFlow's DwAABB_2D.
//
// Used both as a spatial primitive (for static obstacles, culling, etc.)
// and as a building block in the obstacle collision routines.

#include <limits>

namespace ekchous::softbody {

struct AABB {
    float min_x = 0.0f;
    float min_y = 0.0f;
    float max_x = 0.0f;
    float max_y = 0.0f;

    constexpr AABB() noexcept = default;
    constexpr AABB(float minx, float miny, float maxx, float maxy) noexcept
        : min_x(minx), min_y(miny), max_x(maxx), max_y(maxy) {}

    void reset() noexcept {
        min_x = min_y = +std::numeric_limits<float>::max();
        max_x = max_y = -std::numeric_limits<float>::max();
    }

    void expand_to(float x, float y) noexcept {
        if (x < min_x) min_x = x;
        if (x > max_x) max_x = x;
        if (y < min_y) min_y = y;
        if (y > max_y) max_y = y;
    }

    bool contains(float x, float y) const noexcept {
        return x >= min_x && x <= max_x && y >= min_y && y <= max_y;
    }

    bool overlaps(const AABB& o) const noexcept {
        return !(max_x < o.min_x || min_x > o.max_x ||
                 max_y < o.min_y || min_y > o.max_y);
    }

    float width()    const noexcept { return max_x - min_x; }
    float height()   const noexcept { return max_y - min_y; }
    float center_x() const noexcept { return 0.5f * (min_x + max_x); }
    float center_y() const noexcept { return 0.5f * (min_y + max_y); }
};

} // namespace ekchous::softbody
