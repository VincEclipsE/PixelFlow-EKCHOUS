#pragma once

// OpenGL SSBO / UBO / vertex buffer wrapper.
// Port target: src/com/thomasdiewald/pixelflow/java/dwgl/DwGLBuffer.java

#include "engine/core/Types.h"
#include <cstddef>

namespace ekchous::gpu {

enum class BufferKind : core::u8 {
    SSBO,         // Shader Storage Buffer Object
    UBO,          // Uniform Buffer Object
    Vertex,
    Index,
    DispatchIndirect,
};

class Buffer {
public:
    Buffer() = default;
    ~Buffer();

    static Buffer create(BufferKind kind, std::size_t bytes, const void* initial = nullptr);

    void bind() const;
    void bind_indexed(core::u32 binding_point) const;
    void upload(const void* data, std::size_t bytes, std::size_t offset = 0) const;
    void download(void* out, std::size_t bytes, std::size_t offset = 0) const;

    core::u32 id() const noexcept { return id_; }
    std::size_t bytes() const noexcept { return bytes_; }

private:
    core::u32 id_ = 0;
    BufferKind kind_ = BufferKind::SSBO;
    std::size_t bytes_ = 0;
};

} // namespace ekchous::gpu
