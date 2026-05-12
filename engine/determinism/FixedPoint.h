#pragma once

// Fixed-point integer types used throughout the sim path.
//
// CRITICAL: All sim math goes through these types. Float is BANNED in any
// computation that feeds the next sim tick. See docs/determinism.md for the
// full atomic + IEEE-754 hazard discipline.
//
// Q-format notation: qM.N means M integer bits + N fractional bits.
//   q16.16: 32-bit signed, range ±32768, precision ~15 μpx.
//   q8.8:   16-bit signed, range ±128, precision ~0.004.
//   q12.4:  16-bit signed, range ±4096, precision 0.0625.
//
// Multiplication: (a * b) >> N (where N is the fractional bits of the result).
// Division: (a << N) / b
// Addition / subtraction: ordinary integer ops (associative + commutative).

#include "engine/core/Types.h"
#include <cstdint>
#include <cmath>

namespace ekchous::determinism {

using core::i16;
using core::i32;
using core::i64;
using core::u32;
using core::u64;

constexpr i32 Q16_16_ONE = 1 << 16;
constexpr i32 Q8_8_ONE   = 1 << 8;
constexpr i32 Q12_4_ONE  = 1 << 4;

// q16.16: signed 32-bit, 16 integer + 16 fractional bits.
struct Q16_16 {
    i32 raw;

    constexpr Q16_16() noexcept : raw(0) {}
    constexpr explicit Q16_16(i32 r) noexcept : raw(r) {}

    static constexpr Q16_16 from_int(i32 v) noexcept { return Q16_16{v << 16}; }
    static Q16_16 from_float(float f) noexcept {
        return Q16_16{static_cast<i32>(std::lround(f * float(Q16_16_ONE)))};
    }
    constexpr i32 to_int() const noexcept { return raw >> 16; }
    float to_float() const noexcept { return float(raw) / float(Q16_16_ONE); }

    constexpr Q16_16 operator+(Q16_16 o) const noexcept { return Q16_16{raw + o.raw}; }
    constexpr Q16_16 operator-(Q16_16 o) const noexcept { return Q16_16{raw - o.raw}; }
    constexpr Q16_16 operator-() const noexcept { return Q16_16{-raw}; }
    Q16_16 operator*(Q16_16 o) const noexcept {
        return Q16_16{static_cast<i32>((i64(raw) * i64(o.raw)) >> 16)};
    }
    Q16_16 operator/(Q16_16 o) const noexcept {
        return Q16_16{static_cast<i32>((i64(raw) << 16) / i64(o.raw))};
    }
    constexpr bool operator==(Q16_16 o) const noexcept { return raw == o.raw; }
    constexpr bool operator!=(Q16_16 o) const noexcept { return raw != o.raw; }
    constexpr bool operator<(Q16_16 o)  const noexcept { return raw <  o.raw; }
    constexpr bool operator<=(Q16_16 o) const noexcept { return raw <= o.raw; }
    constexpr bool operator>(Q16_16 o)  const noexcept { return raw >  o.raw; }
    constexpr bool operator>=(Q16_16 o) const noexcept { return raw >= o.raw; }
    Q16_16& operator+=(Q16_16 o) noexcept { raw += o.raw; return *this; }
    Q16_16& operator-=(Q16_16 o) noexcept { raw -= o.raw; return *this; }
};

// q8.8: signed 16-bit, 8 integer + 8 fractional bits. Used for velocity in px/tick.
struct Q8_8 {
    i16 raw;

    constexpr Q8_8() noexcept : raw(0) {}
    constexpr explicit Q8_8(i16 r) noexcept : raw(r) {}

    static constexpr Q8_8 from_int(i16 v) noexcept { return Q8_8{static_cast<i16>(v << 8)}; }
    static Q8_8 from_float(float f) noexcept {
        return Q8_8{static_cast<i16>(std::lround(f * float(Q8_8_ONE)))};
    }
    constexpr i16 to_int() const noexcept { return raw >> 8; }
    float to_float() const noexcept { return float(raw) / float(Q8_8_ONE); }

    constexpr Q8_8 operator+(Q8_8 o) const noexcept { return Q8_8{static_cast<i16>(raw + o.raw)}; }
    constexpr Q8_8 operator-(Q8_8 o) const noexcept { return Q8_8{static_cast<i16>(raw - o.raw)}; }
    constexpr Q8_8 operator-() const noexcept { return Q8_8{static_cast<i16>(-raw)}; }
    Q8_8 operator*(Q8_8 o) const noexcept {
        return Q8_8{static_cast<i16>((i32(raw) * i32(o.raw)) >> 8)};
    }
    Q8_8& operator+=(Q8_8 o) noexcept { raw = static_cast<i16>(raw + o.raw); return *this; }
};

// q12.4: signed 16-bit, 12 integer + 4 fractional bits. Used for temperature in Kelvin.
struct Q12_4 {
    i16 raw;

    constexpr Q12_4() noexcept : raw(0) {}
    constexpr explicit Q12_4(i16 r) noexcept : raw(r) {}

    static constexpr Q12_4 from_int(i16 v) noexcept { return Q12_4{static_cast<i16>(v << 4)}; }
    constexpr i16 to_int() const noexcept { return raw >> 4; }
};

// 2D vector of q16.16 positions.
struct Vec2Q16_16 {
    Q16_16 x, y;
    constexpr Vec2Q16_16() noexcept = default;
    constexpr Vec2Q16_16(Q16_16 x_, Q16_16 y_) noexcept : x(x_), y(y_) {}
    constexpr Vec2Q16_16 operator+(Vec2Q16_16 o) const noexcept { return {x + o.x, y + o.y}; }
    constexpr Vec2Q16_16 operator-(Vec2Q16_16 o) const noexcept { return {x - o.x, y - o.y}; }
    constexpr bool operator==(Vec2Q16_16 o) const noexcept { return x == o.x && y == o.y; }
};

// 2D vector of q8.8 velocities.
struct Vec2Q8_8 {
    Q8_8 x, y;
    constexpr Vec2Q8_8() noexcept = default;
    constexpr Vec2Q8_8(Q8_8 x_, Q8_8 y_) noexcept : x(x_), y(y_) {}
    Vec2Q8_8 operator+(Vec2Q8_8 o) const noexcept { return {x + o.x, y + o.y}; }
};

// Apply a q8.8 velocity to a q16.16 position over one tick (15 Hz → one tick = full velocity step).
// Velocity is px/tick; position is in pixels. Result = pos + vel (with appropriate shift).
inline Vec2Q16_16 step(Vec2Q16_16 pos, Vec2Q8_8 vel) noexcept {
    // q16.16 += q8.8 → shift q8.8 up by 8 to match.
    return {
        Q16_16{pos.x.raw + (i32(vel.x.raw) << 8)},
        Q16_16{pos.y.raw + (i32(vel.y.raw) << 8)}
    };
}

// Magnitude squared in q16.16. Used for rest-detection threshold compare (avoid sqrt).
inline i64 vel_mag2(Vec2Q8_8 v) noexcept {
    return i64(v.x.raw) * i64(v.x.raw) + i64(v.y.raw) * i64(v.y.raw);
}

} // namespace ekchous::determinism
