#include "engine/gpu/Buffer.h"
#include "engine/core/Logger.h"

namespace ekchous::gpu {

Buffer::~Buffer() {
    // TODO_PIXELFLOW_PORT("dwgl/DwGLBuffer.java#release()"): glDeleteBuffers(1, &id_);
    if (id_ != 0) {
        LOG_TRACE("Buffer::~Buffer: id={} bytes={} (GL deletion stubbed)", id_, bytes_);
    }
}

Buffer Buffer::create(BufferKind kind, std::size_t bytes, const void* initial) {
    TODO_PIXELFLOW_PORT("dwgl/DwGLBuffer.java#resize() + glBufferStorage");
    Buffer b;
    b.kind_ = kind;
    b.bytes_ = bytes;
    (void)initial;
    return b;
}

void Buffer::bind() const {
    TODO_PIXELFLOW_PORT("dwgl/DwGLBuffer.java#bind()");
}

void Buffer::bind_indexed(core::u32 binding_point) const {
    TODO_PIXELFLOW_PORT("glBindBufferBase for SSBO/UBO");
    (void)binding_point;
}

void Buffer::upload(const void* data, std::size_t bytes, std::size_t offset) const {
    TODO_PIXELFLOW_PORT("dwgl/DwGLBuffer.java#bufferData() / glBufferSubData");
    (void)data; (void)bytes; (void)offset;
}

void Buffer::download(void* out, std::size_t bytes, std::size_t offset) const {
    TODO_PIXELFLOW_PORT("glGetBufferSubData");
    (void)out; (void)bytes; (void)offset;
}

} // namespace ekchous::gpu
