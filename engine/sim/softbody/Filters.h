#pragma once

// CPU image filters operating on row-major 2D float grids. Ports of
// PixelFlow's imageprocessing/filter/{GaussianBlur, BoxBlur, Threshold}
// (which are GLSL on PixelFlow's side; we run the equivalent math on the
// CPU since our grids are small).
//
// All functions operate in-place where possible; gaussian_blur uses a
// single temporary buffer for the separable 1D passes.

#include <algorithm>
#include <cmath>
#include <vector>

namespace ekchous::softbody {

// Build a normalized 1D Gaussian kernel of length 2*radius+1.
// sigma defaults to max(radius / 2.5, 0.5) — PixelFlow's GaussianBlur uses
// a similar relationship.
inline std::vector<float> gaussian_kernel_1d(int radius, float sigma = 0.0f) {
    if (radius < 0) radius = 0;
    if (sigma <= 0.0f) {
        sigma = static_cast<float>(radius) / 2.5f;
        if (sigma < 0.5f) sigma = 0.5f;
    }
    const float two_sigma2 = 2.0f * sigma * sigma;
    std::vector<float> k(static_cast<std::size_t>(2 * radius + 1));
    float sum = 0.0f;
    for (int i = -radius; i <= radius; ++i) {
        const float w = std::exp(-static_cast<float>(i * i) / two_sigma2);
        k[static_cast<std::size_t>(i + radius)] = w;
        sum += w;
    }
    if (sum > 0.0f) {
        for (auto& w : k) w /= sum;
    }
    return k;
}

// Separable Gaussian blur on a row-major NxM float grid. Border samples
// clamp to the nearest in-bounds cell.
inline void gaussian_blur(std::vector<float>& data, int width, int height,
                          int radius) {
    if (radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    const auto kernel = gaussian_kernel_1d(radius);
    std::vector<float> tmp(data.size(), 0.0f);

    // Horizontal pass: data → tmp.
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            for (int i = -radius; i <= radius; ++i) {
                int xi = x + i;
                if (xi < 0)         xi = 0;
                else if (xi >= width) xi = width - 1;
                sum += data[static_cast<std::size_t>(y) * width + xi]
                     * kernel[static_cast<std::size_t>(i + radius)];
            }
            tmp[static_cast<std::size_t>(y) * width + x] = sum;
        }
    }
    // Vertical pass: tmp → data.
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            for (int i = -radius; i <= radius; ++i) {
                int yi = y + i;
                if (yi < 0)          yi = 0;
                else if (yi >= height) yi = height - 1;
                sum += tmp[static_cast<std::size_t>(yi) * width + x]
                     * kernel[static_cast<std::size_t>(i + radius)];
            }
            data[static_cast<std::size_t>(y) * width + x] = sum;
        }
    }
}

// Separable box blur — cheaper than Gaussian, slightly less smooth.
// Three successive box blurs approximate a Gaussian (binomial-blur trick).
inline void box_blur(std::vector<float>& data, int width, int height,
                     int radius) {
    if (radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> tmp(data.size(), 0.0f);
    const float scale = 1.0f / static_cast<float>(2 * radius + 1);

    // Horizontal pass.
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            for (int i = -radius; i <= radius; ++i) {
                int xi = x + i;
                if (xi < 0)         xi = 0;
                else if (xi >= width) xi = width - 1;
                sum += data[static_cast<std::size_t>(y) * width + xi];
            }
            tmp[static_cast<std::size_t>(y) * width + x] = sum * scale;
        }
    }
    // Vertical pass.
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            for (int i = -radius; i <= radius; ++i) {
                int yi = y + i;
                if (yi < 0)          yi = 0;
                else if (yi >= height) yi = height - 1;
                sum += tmp[static_cast<std::size_t>(yi) * width + x];
            }
            data[static_cast<std::size_t>(y) * width + x] = sum * scale;
        }
    }
}

// Clip every cell to [lo, hi]. PixelFlow's Threshold filter has a similar
// shape; this is the simplest variant.
inline void threshold(std::vector<float>& data, float lo, float hi) {
    if (lo > hi) std::swap(lo, hi);
    for (auto& v : data) {
        if (v < lo)      v = lo;
        else if (v > hi) v = hi;
    }
}

// Summed area table (integral image) — port of imageprocessing/filter/
// SummedAreaTable.java. Each cell of `sat` holds the sum of all `src`
// cells at-or-above-and-at-or-left-of it. Once built, any axis-aligned
// box sum is O(1) via sat_box_sum, so a box blur becomes O(N) total.
inline void compute_sat(std::vector<float>& sat, const std::vector<float>& src,
                        int width, int height) {
    if (width <= 0 || height <= 0) return;
    sat.assign(src.size(), 0.0f);
    for (int y = 0; y < height; ++y) {
        float row_sum = 0.0f;
        for (int x = 0; x < width; ++x) {
            row_sum += src[static_cast<std::size_t>(y) * width + x];
            const float above = (y > 0)
                ? sat[static_cast<std::size_t>(y - 1) * width + x]
                : 0.0f;
            sat[static_cast<std::size_t>(y) * width + x] = row_sum + above;
        }
    }
}

inline float sat_box_sum(const std::vector<float>& sat, int width, int height,
                         int x0, int y0, int x1, int y1) noexcept {
    if (x0 < 0)        x0 = 0;
    if (y0 < 0)        y0 = 0;
    if (x1 >= width)   x1 = width - 1;
    if (y1 >= height)  y1 = height - 1;
    if (x0 > x1 || y0 > y1) return 0.0f;
    float total = sat[static_cast<std::size_t>(y1) * width + x1];
    if (x0 > 0) total -= sat[static_cast<std::size_t>(y1) * width + (x0 - 1)];
    if (y0 > 0) total -= sat[static_cast<std::size_t>(y0 - 1) * width + x1];
    if (x0 > 0 && y0 > 0)
        total += sat[static_cast<std::size_t>(y0 - 1) * width + (x0 - 1)];
    return total;
}

// Box blur backed by a summed area table: O(N) total (one pass to build
// SAT, one pass to compute averages). Much faster than the naive
// box_blur for large radii.
inline void sat_box_blur(std::vector<float>& data, int width, int height,
                         int radius) {
    if (radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> sat;
    compute_sat(sat, data, width, height);
    std::vector<float> out(data.size(), 0.0f);
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const int x0 = x - radius;
            const int y0 = y - radius;
            const int x1 = x + radius;
            const int y1 = y + radius;
            const float sum = sat_box_sum(sat, width, height, x0, y0, x1, y1);
            const int eff_x0 = x0 < 0 ? 0 : x0;
            const int eff_y0 = y0 < 0 ? 0 : y0;
            const int eff_x1 = x1 >= width  ? width - 1  : x1;
            const int eff_y1 = y1 >= height ? height - 1 : y1;
            const float area = static_cast<float>((eff_x1 - eff_x0 + 1) *
                                                   (eff_y1 - eff_y0 + 1));
            out[static_cast<std::size_t>(y) * width + x] = sum / area;
        }
    }
    data = std::move(out);
}

// Multiply every cell by a scalar — port of imageprocessing/filter/
// Multiply.java's scalar mode.
inline void multiply_scalar(std::vector<float>& data, float k) noexcept {
    for (auto& v : data) v *= k;
}

// Multiply-add: v ← v * a + b. Port of imageprocessing/filter/Mad.java.
// Cheap primitive for linear remaps (e.g. shift+scale density into a
// chosen range before rendering).
inline void mad(std::vector<float>& data, float a, float b) noexcept {
    for (auto& v : data) v = v * a + b;
}

// Gamma correction — port of imageprocessing/filter/Gamma.java. Raises
// each positive cell to the 1/gamma power. gamma > 1 brightens midtones;
// gamma < 1 darkens them.
inline void gamma_correct(std::vector<float>& data, float gamma) noexcept {
    if (gamma <= 0.0f) return;
    const float inv = 1.0f / gamma;
    for (auto& v : data) {
        if (v > 0.0f) v = std::pow(v, inv);
    }
}

// Hard cutoff: zero anything below `cutoff`, leave the rest untouched.
// Useful for "show only bright regions" effects.
inline void cutoff_below(std::vector<float>& data, float cutoff) {
    for (auto& v : data) {
        if (v < cutoff) v = 0.0f;
    }
}

// Bloom — port of PixelFlow's imageprocessing/filter/Bloom.java. Extract
// brightness above `bright_threshold`, blur it, add back into data scaled
// by `intensity`. Composes existing gaussian_blur + threshold filters.
inline void bloom(std::vector<float>& data, int width, int height,
                  int blur_radius, float bright_threshold, float intensity) {
    if (intensity <= 0.0f || blur_radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> bright(data.size(), 0.0f);
    for (std::size_t i = 0; i < data.size(); ++i) {
        if (data[i] > bright_threshold) {
            bright[i] = data[i] - bright_threshold;
        }
    }
    gaussian_blur(bright, width, height, blur_radius);
    for (std::size_t i = 0; i < data.size(); ++i) {
        data[i] += bright[i] * intensity;
    }
}

// Sobel edge-magnitude filter. 3x3 Gx + Gy kernels; output is sqrt(Gx² +
// Gy²). Replaces data with its edge-magnitude image.
inline void sobel_magnitude(std::vector<float>& data, int width, int height) {
    if (width < 3 || height < 3) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> out(data.size(), 0.0f);
    auto at = [&](int x, int y) -> float {
        if (x < 0)        x = 0;
        else if (x >= width) x = width - 1;
        if (y < 0)        y = 0;
        else if (y >= height) y = height - 1;
        return data[static_cast<std::size_t>(y) * width + x];
    };
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const float a = at(x - 1, y - 1);
            const float b = at(x,     y - 1);
            const float c = at(x + 1, y - 1);
            const float d = at(x - 1, y);
            const float f = at(x + 1, y);
            const float g = at(x - 1, y + 1);
            const float h = at(x,     y + 1);
            const float i = at(x + 1, y + 1);
            const float gx = (c + 2.0f * f + i) - (a + 2.0f * d + g);
            const float gy = (g + 2.0f * h + i) - (a + 2.0f * b + c);
            out[static_cast<std::size_t>(y) * width + x] = std::sqrt(gx * gx + gy * gy);
        }
    }
    data = std::move(out);
}

// Max filter — port of imageprocessing/filter/MinMaxLocal.java's max
// mode. For each cell, write the maximum value in its (2r+1)² window.
// Morphological dilation when applied to a binary mask; "local brightness
// spread" on grayscale.
inline void max_filter(std::vector<float>& data, int width, int height, int radius) {
    if (radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> out(data.size(), 0.0f);
    auto at = [&](int x, int y) -> float {
        if (x < 0)        x = 0;
        else if (x >= width) x = width - 1;
        if (y < 0)        y = 0;
        else if (y >= height) y = height - 1;
        return data[static_cast<std::size_t>(y) * width + x];
    };
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float m = at(x, y);
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    const float v = at(x + dx, y + dy);
                    if (v > m) m = v;
                }
            }
            out[static_cast<std::size_t>(y) * width + x] = m;
        }
    }
    data = std::move(out);
}

// Min filter — companion to max_filter. Each cell becomes the minimum in
// its (2r+1)² window. Morphological erosion / "local darkness shrink".
inline void min_filter(std::vector<float>& data, int width, int height, int radius) {
    if (radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> out(data.size(), 0.0f);
    auto at = [&](int x, int y) -> float {
        if (x < 0)        x = 0;
        else if (x >= width) x = width - 1;
        if (y < 0)        y = 0;
        else if (y >= height) y = height - 1;
        return data[static_cast<std::size_t>(y) * width + x];
    };
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float m = at(x, y);
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    const float v = at(x + dx, y + dy);
                    if (v < m) m = v;
                }
            }
            out[static_cast<std::size_t>(y) * width + x] = m;
        }
    }
    data = std::move(out);
}

// Bilateral filter — port of imageprocessing/filter/BilateralFilter.java.
// Edge-preserving smoothing: each cell is averaged with its neighbours
// weighted by Gaussian(spatial distance) × Gaussian(intensity difference).
// Pixels across a strong intensity step get tiny weights so the edge
// survives. Quadratic in radius — keep `radius` ≤ 4.
inline void bilateral_filter(std::vector<float>& data, int width, int height,
                              int radius, float sigma_space, float sigma_range) {
    if (radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> out(data.size(), 0.0f);
    auto at = [&](int x, int y) -> float {
        if (x < 0)        x = 0;
        else if (x >= width) x = width - 1;
        if (y < 0)        y = 0;
        else if (y >= height) y = height - 1;
        return data[static_cast<std::size_t>(y) * width + x];
    };
    const float two_ss = 2.0f * sigma_space * sigma_space;
    const float two_sr = 2.0f * sigma_range * sigma_range;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const float centre = data[static_cast<std::size_t>(y) * width + x];
            float sum  = 0.0f;
            float wsum = 0.0f;
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    const float v = at(x + dx, y + dy);
                    const float ws = std::exp(
                        -static_cast<float>(dx * dx + dy * dy) / two_ss);
                    const float dv = v - centre;
                    const float wr = std::exp(-(dv * dv) / two_sr);
                    const float w  = ws * wr;
                    sum  += v * w;
                    wsum += w;
                }
            }
            out[static_cast<std::size_t>(y) * width + x] =
                wsum > 0.0f ? sum / wsum : centre;
        }
    }
    data = std::move(out);
}

// Find global min and max in a grid. Port of imageprocessing/filter/
// MinMaxGlobal.java (scalar variant).
inline void minmax_global(const std::vector<float>& data,
                          float& out_min, float& out_max) noexcept {
    if (data.empty()) {
        out_min = out_max = 0.0f;
        return;
    }
    out_min = data.front();
    out_max = data.front();
    for (const float v : data) {
        if (v < out_min) out_min = v;
        if (v > out_max) out_max = v;
    }
}

// Normalize cells to [0, 1] using the grid's global range. Useful for
// making sobel/distance-transform output renderable.
inline void normalize_global(std::vector<float>& data) noexcept {
    float lo, hi;
    minmax_global(data, lo, hi);
    const float range = hi - lo;
    if (range <= 0.0f) {
        for (auto& v : data) v = 0.0f;
        return;
    }
    const float inv = 1.0f / range;
    for (auto& v : data) v = (v - lo) * inv;
}

// 2× decimation by averaging 2×2 blocks. Width/height clamp to even.
inline void downsample_2x(const std::vector<float>& src, int sw, int sh,
                          std::vector<float>& dst, int& dw, int& dh) {
    dw = sw / 2;
    dh = sh / 2;
    if (dw < 1 || dh < 1) {
        dst = src;
        dw = sw;
        dh = sh;
        return;
    }
    dst.assign(static_cast<std::size_t>(dw) * dh, 0.0f);
    for (int y = 0; y < dh; ++y) {
        for (int x = 0; x < dw; ++x) {
            const int sx = x * 2;
            const int sy = y * 2;
            float s = src[static_cast<std::size_t>(sy) * sw + sx];
            s     += src[static_cast<std::size_t>(sy) * sw + sx + 1];
            s     += src[static_cast<std::size_t>(sy + 1) * sw + sx];
            s     += src[static_cast<std::size_t>(sy + 1) * sw + sx + 1];
            dst[static_cast<std::size_t>(y) * dw + x] = s * 0.25f;
        }
    }
}

// Bilinear upsample of `src` (sw × sh) into `dst` (dw × dh).
inline void upsample_bilinear(const std::vector<float>& src, int sw, int sh,
                              std::vector<float>& dst, int dw, int dh) {
    if (dw < 1 || dh < 1 || sw < 2 || sh < 2) {
        dst = src;
        return;
    }
    dst.assign(static_cast<std::size_t>(dw) * dh, 0.0f);
    const float scale_x = static_cast<float>(sw) / static_cast<float>(dw);
    const float scale_y = static_cast<float>(sh) / static_cast<float>(dh);
    for (int y = 0; y < dh; ++y) {
        for (int x = 0; x < dw; ++x) {
            const float fx = (x + 0.5f) * scale_x - 0.5f;
            const float fy = (y + 0.5f) * scale_y - 0.5f;
            int x0 = static_cast<int>(std::floor(fx));
            int y0 = static_cast<int>(std::floor(fy));
            if (x0 < 0)         x0 = 0;
            else if (x0 > sw - 2) x0 = sw - 2;
            if (y0 < 0)         y0 = 0;
            else if (y0 > sh - 2) y0 = sh - 2;
            const float tx = fx - x0;
            const float ty = fy - y0;
            const float v00 = src[static_cast<std::size_t>(y0) * sw + x0];
            const float v10 = src[static_cast<std::size_t>(y0) * sw + x0 + 1];
            const float v01 = src[static_cast<std::size_t>(y0 + 1) * sw + x0];
            const float v11 = src[static_cast<std::size_t>(y0 + 1) * sw + x0 + 1];
            const float top = v00 * (1.0f - tx) + v10 * tx;
            const float bot = v01 * (1.0f - tx) + v11 * tx;
            dst[static_cast<std::size_t>(y) * dw + x] =
                top * (1.0f - ty) + bot * ty;
        }
    }
}

// Depth-of-field-style variable-radius blur. Port of imageprocessing/
// filter/DepthOfField.java in spirit — uses a SAT to make per-cell box
// blurs O(1), and picks each cell's blur radius from its distance to a
// focus point. Cells inside `focus_radius_world` stay sharp; beyond that
// the blur radius scales linearly up to `max_blur_radius`.
//
// `focus_x_world / focus_y_world / focus_radius_world / canvas_w / canvas_h`
// are all in the canvas world coords the engine uses for fluid sources.
inline void depth_of_field(std::vector<float>& data, int w, int h,
                           int max_blur_radius,
                           float focus_x_world, float focus_y_world,
                           float focus_radius_world,
                           float canvas_w, float canvas_h) {
    if (max_blur_radius <= 0 || w <= 0 || h <= 0) return;
    if (data.size() < static_cast<std::size_t>(w) * h) return;
    if (canvas_w <= 0.0f || canvas_h <= 0.0f) return;

    std::vector<float> sat;
    compute_sat(sat, data, w, h);
    std::vector<float> out(data.size(), 0.0f);

    const float focus_cx     = focus_x_world / canvas_w * w;
    const float focus_cy     = focus_y_world / canvas_h * h;
    const float focus_r_cell = focus_radius_world / canvas_w * w;
    const float blur_slope   = 0.2f;  // cells of blur radius per cell of distance

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            const float dx = x - focus_cx;
            const float dy = y - focus_cy;
            const float d  = std::sqrt(dx * dx + dy * dy);
            float blur_rf = (d - focus_r_cell) * blur_slope;
            if (blur_rf < 0.0f)                          blur_rf = 0.0f;
            if (blur_rf > static_cast<float>(max_blur_radius))
                                                          blur_rf = static_cast<float>(max_blur_radius);
            const int br = static_cast<int>(blur_rf);
            if (br <= 0) {
                out[static_cast<std::size_t>(y) * w + x] =
                    data[static_cast<std::size_t>(y) * w + x];
                continue;
            }
            const int x0 = x - br;
            const int y0 = y - br;
            const int x1 = x + br;
            const int y1 = y + br;
            const float sum = sat_box_sum(sat, w, h, x0, y0, x1, y1);
            const int ex0 = x0 < 0 ? 0 : x0;
            const int ey0 = y0 < 0 ? 0 : y0;
            const int ex1 = x1 >= w ? w - 1 : x1;
            const int ey1 = y1 >= h ? h - 1 : y1;
            const float area = static_cast<float>((ex1 - ex0 + 1) * (ey1 - ey0 + 1));
            out[static_cast<std::size_t>(y) * w + x] = sum / area;
        }
    }
    data = std::move(out);
}

// Multi-level Gaussian blur via image pyramid. Downsamples `levels` times,
// blurs at the smallest level, then upsamples back. Much faster than a
// direct large-radius Gaussian for the same visual reach. Port of
// imageprocessing/filter/GaussianBlurPyramid.java.
inline void gaussian_blur_pyramid(std::vector<float>& data, int width, int height,
                                   int blur_radius, int levels) {
    if (levels < 1 || blur_radius <= 0) {
        if (blur_radius > 0) gaussian_blur(data, width, height, blur_radius);
        return;
    }
    std::vector<float> current = data;
    int cw = width;
    int ch = height;
    // Downsample stack.
    std::vector<std::vector<float>> stack;
    std::vector<std::pair<int,int>>  dims;
    stack.push_back(current);
    dims.emplace_back(cw, ch);
    for (int i = 0; i < levels && cw >= 4 && ch >= 4; ++i) {
        std::vector<float> down;
        int dw, dh;
        downsample_2x(stack.back(), cw, ch, down, dw, dh);
        cw = dw;
        ch = dh;
        stack.push_back(std::move(down));
        dims.emplace_back(cw, ch);
    }
    // Blur the smallest level.
    gaussian_blur(stack.back(), cw, ch, blur_radius);
    // Upsample back up through the pyramid.
    for (int lvl = static_cast<int>(stack.size()) - 1; lvl > 0; --lvl) {
        std::vector<float> up;
        const auto [sw, sh] = dims[lvl];
        const auto [dw, dh] = dims[lvl - 1];
        upsample_bilinear(stack[lvl], sw, sh, up, dw, dh);
        stack[lvl - 1] = std::move(up);
    }
    data = std::move(stack.front());
}

// Generic NxN convolution — port of imageprocessing/filter/Convolution.java.
// `kernel` is row-major with `kernel_size` rows AND columns (must be odd).
// Border samples clamp to the nearest in-bounds cell.
inline void convolution(std::vector<float>& data, int width, int height,
                        const std::vector<float>& kernel, int kernel_size) {
    if (kernel_size < 1 || (kernel_size & 1) == 0) return;
    if (width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    if (kernel.size() < static_cast<std::size_t>(kernel_size) * kernel_size) return;
    const int r = kernel_size / 2;
    std::vector<float> out(data.size(), 0.0f);
    auto at = [&](int x, int y) -> float {
        if (x < 0)        x = 0;
        else if (x >= width) x = width - 1;
        if (y < 0)        y = 0;
        else if (y >= height) y = height - 1;
        return data[static_cast<std::size_t>(y) * width + x];
    };
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            for (int ky = 0; ky < kernel_size; ++ky) {
                for (int kx = 0; kx < kernel_size; ++kx) {
                    sum += at(x + kx - r, y + ky - r)
                         * kernel[static_cast<std::size_t>(ky) * kernel_size + kx];
                }
            }
            out[static_cast<std::size_t>(y) * width + x] = sum;
        }
    }
    data = std::move(out);
}

// Difference of Gaussians — port of imageprocessing/filter/DoG.java.
// Runs Gaussian blur twice at small and large radii, then writes the
// difference (small - large). Net effect: band-pass filter that
// highlights features at a particular scale.
inline void difference_of_gaussians(std::vector<float>& data, int width,
                                     int height, int radius_small,
                                     int radius_large) {
    if (width <= 0 || height <= 0) return;
    if (radius_small <= 0 && radius_large <= 0) return;
    if (radius_small >= radius_large) std::swap(radius_small, radius_large);
    std::vector<float> small_blur = data;
    std::vector<float> large_blur = data;
    if (radius_small > 0) gaussian_blur(small_blur, width, height, radius_small);
    if (radius_large > 0) gaussian_blur(large_blur, width, height, radius_large);
    for (std::size_t i = 0; i < data.size(); ++i) {
        data[i] = small_blur[i] - large_blur[i];
    }
}

// Median filter — port of imageprocessing/filter/Median.java. Slides a
// (2r+1)² window; the output cell is the median of its window. Robust
// against impulse noise. O(N²·k²·log k) — keep `radius` small.
inline void median_filter(std::vector<float>& data, int width, int height,
                          int radius) {
    if (radius <= 0 || width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> out(data.size(), 0.0f);
    std::vector<float> window;
    const int window_size = (2 * radius + 1) * (2 * radius + 1);
    window.reserve(static_cast<std::size_t>(window_size));
    auto at = [&](int x, int y) -> float {
        if (x < 0)        x = 0;
        else if (x >= width) x = width - 1;
        if (y < 0)        y = 0;
        else if (y >= height) y = height - 1;
        return data[static_cast<std::size_t>(y) * width + x];
    };
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            window.clear();
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    window.push_back(at(x + dx, y + dy));
                }
            }
            std::nth_element(window.begin(),
                             window.begin() + window.size() / 2,
                             window.end());
            out[static_cast<std::size_t>(y) * width + x] = window[window.size() / 2];
        }
    }
    data = std::move(out);
}

// 4-connected Laplace edge filter. Output is |N + S + E + W - 4*centre|,
// the magnitude of the discrete Laplacian. Smaller / faster / less
// directional than Sobel.
inline void laplace(std::vector<float>& data, int width, int height) {
    if (width < 3 || height < 3) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    std::vector<float> out(data.size(), 0.0f);
    auto at = [&](int x, int y) -> float {
        if (x < 0)        x = 0;
        else if (x >= width) x = width - 1;
        if (y < 0)        y = 0;
        else if (y >= height) y = height - 1;
        return data[static_cast<std::size_t>(y) * width + x];
    };
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const float c = at(x,     y);
            const float n = at(x,     y - 1);
            const float s = at(x,     y + 1);
            const float e = at(x + 1, y);
            const float w = at(x - 1, y);
            out[static_cast<std::size_t>(y) * width + x] =
                std::fabs(n + s + e + w - 4.0f * c);
        }
    }
    data = std::move(out);
}

// Distance transform: replace each cell with its distance (in cell units)
// to the nearest "foreground" cell (data > foreground_threshold). Uses the
// classic two-pass Chamfer 3-4 metric — fast and a good Euclidean
// approximation. Result is clamped to `max_dist`.
inline void distance_transform(std::vector<float>& data, int width, int height,
                               float foreground_threshold = 0.0f,
                               float max_dist = 32.0f) {
    if (width <= 0 || height <= 0) return;
    if (data.size() < static_cast<std::size_t>(width) * height) return;
    constexpr float INF = 1.0e30f;
    constexpr float D1 = 3.0f;
    constexpr float D2 = 4.0f;
    std::vector<float> dt(data.size());
    for (std::size_t i = 0; i < data.size(); ++i) {
        dt[i] = data[i] > foreground_threshold ? 0.0f : INF;
    }
    auto at = [&](int x, int y) -> float& {
        return dt[static_cast<std::size_t>(y) * width + x];
    };
    // Forward pass.
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float v = at(x, y);
            if (x > 0)              v = std::min(v, at(x - 1, y    ) + D1);
            if (y > 0)              v = std::min(v, at(x,     y - 1) + D1);
            if (x > 0 && y > 0)     v = std::min(v, at(x - 1, y - 1) + D2);
            if (x + 1 < width && y > 0)
                                    v = std::min(v, at(x + 1, y - 1) + D2);
            at(x, y) = v;
        }
    }
    // Backward pass.
    for (int y = height - 1; y >= 0; --y) {
        for (int x = width - 1; x >= 0; --x) {
            float v = at(x, y);
            if (x + 1 < width)              v = std::min(v, at(x + 1, y    ) + D1);
            if (y + 1 < height)             v = std::min(v, at(x,     y + 1) + D1);
            if (x + 1 < width && y + 1 < height)
                                            v = std::min(v, at(x + 1, y + 1) + D2);
            if (x > 0 && y + 1 < height)    v = std::min(v, at(x - 1, y + 1) + D2);
            at(x, y) = v;
        }
    }
    // Scale Chamfer integer distances back to cell units and clamp.
    for (auto& v : dt) {
        v /= D1;
        if (v > max_dist) v = max_dist;
    }
    data = std::move(dt);
}

} // namespace ekchous::softbody
