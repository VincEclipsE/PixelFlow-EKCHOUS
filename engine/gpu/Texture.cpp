#include "engine/gpu/Texture.h"
#include "engine/core/Logger.h"

#include <glad/gl.h>
#include <utility>

namespace ekchous::gpu {

namespace {
struct GlTextureFormat {
    GLenum internal_format;
    GLenum upload_format;
    GLenum upload_type;
};

GlTextureFormat gl_format_for(TextureFormat fmt) noexcept {
    switch (fmt) {
        case TextureFormat::R8:       return {GL_R8,       GL_RED,          GL_UNSIGNED_BYTE};
        case TextureFormat::R16F:     return {GL_R16F,     GL_RED,          GL_HALF_FLOAT};
        case TextureFormat::R32UI:    return {GL_R32UI,    GL_RED_INTEGER,  GL_UNSIGNED_INT};
        case TextureFormat::RGBA8:    return {GL_RGBA8,    GL_RGBA,         GL_UNSIGNED_BYTE};
        case TextureFormat::RGBA32UI: return {GL_RGBA32UI, GL_RGBA_INTEGER, GL_UNSIGNED_INT};
    }
    return {GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE};
}
} // namespace

Texture::~Texture() {
    if (id_ != 0) {
        const GLuint name = id_;
        glDeleteTextures(1, &name);
    }
}

Texture::Texture(Texture&& o) noexcept
    : id_(o.id_), width_(o.width_), height_(o.height_), fmt_(o.fmt_) {
    o.id_ = 0;
    o.width_ = 0;
    o.height_ = 0;
}

Texture& Texture::operator=(Texture&& o) noexcept {
    if (this != &o) {
        if (id_ != 0) {
            const GLuint name = id_;
            glDeleteTextures(1, &name);
        }
        id_ = o.id_;
        width_ = o.width_;
        height_ = o.height_;
        fmt_ = o.fmt_;
        o.id_ = 0;
        o.width_ = 0;
        o.height_ = 0;
    }
    return *this;
}

Texture Texture::create_2d(core::u32 w, core::u32 h, TextureFormat fmt) {
    Texture t;
    t.width_ = w;
    t.height_ = h;
    t.fmt_ = fmt;

    // Non-DSA path so the engine stays on the locked GL 4.3 core baseline
    // (DSA — glCreateTextures / glTextureStorage2D — is GL 4.5+).
    GLuint name = 0;
    glGenTextures(1, &name);
    if (name == 0) {
        LOG_ERROR("glGenTextures returned 0 — driver out of names or invalid context");
        return t;
    }
    glBindTexture(GL_TEXTURE_2D, name);

    const auto glf = gl_format_for(fmt);
    glTexStorage2D(GL_TEXTURE_2D, 1, glf.internal_format,
                   static_cast<GLsizei>(w), static_cast<GLsizei>(h));
    // Nearest filtering preserves the 16-bit pixel-art aesthetic — no blurring on zoom.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,     GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);

    t.id_ = name;
    return t;
}

void Texture::bind_unit(core::u32 unit) const {
    if (id_ == 0) return;
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(GL_TEXTURE_2D, id_);
}

void Texture::bind_image(core::u32 unit, bool read, bool write) const {
    if (id_ == 0) return;
    GLenum access = GL_READ_ONLY;
    if (read && write)      access = GL_READ_WRITE;
    else if (write)         access = GL_WRITE_ONLY;
    const auto glf = gl_format_for(fmt_);
    glBindImageTexture(static_cast<GLuint>(unit), id_, 0, GL_FALSE, 0, access, glf.internal_format);
}

void Texture::upload(const void* data) const {
    if (id_ == 0 || data == nullptr) return;
    const auto glf = gl_format_for(fmt_);
    glBindTexture(GL_TEXTURE_2D, id_);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                    static_cast<GLsizei>(width_),
                    static_cast<GLsizei>(height_),
                    glf.upload_format, glf.upload_type, data);
}

} // namespace ekchous::gpu
