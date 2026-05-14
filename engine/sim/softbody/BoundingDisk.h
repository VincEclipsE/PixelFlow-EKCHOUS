#pragma once

// 2D bounding disk (circle). Direct port of PixelFlow's DwBoundingDisk.
//
// Companion to AABB for circular primitives — used by static disk
// obstacles and (in PixelFlow examples) by sphere-like collision queries.

namespace ekchous::softbody {

struct BoundingDisk {
    float cx     = 0.0f;
    float cy     = 0.0f;
    float radius = 0.0f;

    constexpr BoundingDisk() noexcept = default;
    constexpr BoundingDisk(float x, float y, float r) noexcept
        : cx(x), cy(y), radius(r) {}

    bool contains(float x, float y) const noexcept {
        const float dx = x - cx;
        const float dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    bool overlaps(const BoundingDisk& o) const noexcept {
        const float dx = o.cx - cx;
        const float dy = o.cy - cy;
        const float r  = radius + o.radius;
        return dx * dx + dy * dy <= r * r;
    }
};

} // namespace ekchous::softbody
