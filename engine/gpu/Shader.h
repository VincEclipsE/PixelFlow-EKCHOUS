#pragma once

// OpenGL 4.3 compute / graphics shader wrapper.
// Port target: src/com/thomasdiewald/pixelflow/java/dwgl/DwGLShader.java
//
// Day-One: method signatures complete; bodies are TODO_PIXELFLOW_PORT stubs.
// The Day-One falling-sand demo runs on CPU; GPU compute shader wiring is
// Day-N+1 work that ports the dwgl shader management patterns.

#include "engine/core/Types.h"
#include <string>
#include <vector>
#include <optional>

namespace ekchous::gpu {

enum class ShaderStage : core::u8 {
    Compute,
    Vertex,
    Fragment,
};

class Shader {
public:
    Shader() = default;
    ~Shader();

    // Compile a single-stage shader from source.
    // Returns empty optional on compile error (logged via spdlog).
    static std::optional<Shader> compile(ShaderStage stage, const std::string& source);

    // Compile a vertex+fragment pair into a linked program.
    static std::optional<Shader> compile_graphics(const std::string& vert_source,
                                                  const std::string& frag_source);

    // Bind for use.
    void bind() const;
    void unbind() const;

    // Compute dispatch (only valid for compute shaders).
    void dispatch(core::u32 groups_x, core::u32 groups_y, core::u32 groups_z) const;
    void dispatch_indirect(core::u32 indirect_buffer_offset) const;

    // Uniforms.
    void set_int(const std::string& name, int v) const;
    void set_uint(const std::string& name, core::u32 v) const;
    void set_float(const std::string& name, float v) const;

    bool valid() const noexcept { return program_id_ != 0; }
    core::u32 program_id() const noexcept { return program_id_; }

private:
    core::u32 program_id_ = 0;
};

} // namespace ekchous::gpu
