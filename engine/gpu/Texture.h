#pragma once

// OpenGL texture wrapper.
// Port target: src/com/thomasdiewald/pixelflow/java/dwgl/DwGLTexture.java

#include "engine/core/Types.h"

namespace ekchous::gpu {

enum class TextureFormat : core::u8 {
    R8,
    R16F,
    R32UI,
    RGBA8,
    RGBA32UI,
};

class Texture {
public:
    Texture() = default;
    ~Texture();

    // Move-only: a Texture owns a GL name and must not be duplicated by copy.
    Texture(const Texture&) = delete;
    Texture& operator=(const Texture&) = delete;
    Texture(Texture&& o) noexcept;
    Texture& operator=(Texture&& o) noexcept;

    static Texture create_2d(core::u32 width, core::u32 height, TextureFormat fmt);

    void bind_unit(core::u32 unit) const;
    void bind_image(core::u32 unit, bool read, bool write) const;
    void upload(const void* data) const;

    core::u32 id() const noexcept { return id_; }
    core::u32 width() const noexcept { return width_; }
    core::u32 height() const noexcept { return height_; }

private:
    core::u32 id_ = 0;
    core::u32 width_ = 0;
    core::u32 height_ = 0;
    TextureFormat fmt_ = TextureFormat::RGBA8;
};

} // namespace ekchous::gpu
