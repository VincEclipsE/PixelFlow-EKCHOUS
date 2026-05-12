#include "engine/gpu/Texture.h"
#include "engine/core/Logger.h"

namespace ekchous::gpu {

Texture::~Texture() {
    if (id_ != 0) {
        LOG_TRACE("Texture::~Texture: id={} {}x{} (GL deletion stubbed)", id_, width_, height_);
    }
}

Texture Texture::create_2d(core::u32 w, core::u32 h, TextureFormat fmt) {
    TODO_PIXELFLOW_PORT("dwgl/DwGLTexture.java#resize()");
    Texture t;
    t.width_ = w;
    t.height_ = h;
    t.fmt_ = fmt;
    return t;
}

void Texture::bind_unit(core::u32 unit) const {
    TODO_PIXELFLOW_PORT("glBindTextureUnit");
    (void)unit;
}

void Texture::bind_image(core::u32 unit, bool read, bool write) const {
    TODO_PIXELFLOW_PORT("glBindImageTexture (compute shader image binding)");
    (void)unit; (void)read; (void)write;
}

void Texture::upload(const void* data) const {
    TODO_PIXELFLOW_PORT("glTextureSubImage2D");
    (void)data;
}

} // namespace ekchous::gpu
