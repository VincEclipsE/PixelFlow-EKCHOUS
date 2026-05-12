#include "engine/gpu/Shader.h"
#include "engine/core/Logger.h"

namespace ekchous::gpu {

Shader::~Shader() {
    // TODO_PIXELFLOW_PORT("dwgl/DwGLShader.java#release()"):
    //   glDeleteProgram(program_id_);
    if (program_id_ != 0) {
        LOG_TRACE("Shader::~Shader: program_id={} (GL deletion stubbed)", program_id_);
    }
}

std::optional<Shader> Shader::compile(ShaderStage stage, const std::string& source) {
    TODO_PIXELFLOW_PORT("dwgl/DwGLShader.java#compile()");
    (void)stage;
    (void)source;
    // Real impl: glCreateShader, glShaderSource, glCompileShader, glCreateProgram, glAttachShader, glLinkProgram.
    // Day-One returns empty so callers detect the stub.
    return std::nullopt;
}

std::optional<Shader> Shader::compile_graphics(const std::string& vert_source,
                                                const std::string& frag_source) {
    TODO_PIXELFLOW_PORT("dwgl/DwGLShader.java#compileGraphics()");
    (void)vert_source;
    (void)frag_source;
    return std::nullopt;
}

void Shader::bind() const {
    TODO_PIXELFLOW_PORT("dwgl/DwGLShader.java#begin()");
}

void Shader::unbind() const {
    TODO_PIXELFLOW_PORT("dwgl/DwGLShader.java#end()");
}

void Shader::dispatch(core::u32 gx, core::u32 gy, core::u32 gz) const {
    TODO_PIXELFLOW_PORT("dwgl/DwGLShader.java#beginDispatch() (extend for compute)");
    (void)gx; (void)gy; (void)gz;
}

void Shader::dispatch_indirect(core::u32 offset) const {
    TODO_PIXELFLOW_PORT("glDispatchComputeIndirect");
    (void)offset;
}

void Shader::set_int(const std::string& n, int v) const   { (void)n; (void)v; }
void Shader::set_uint(const std::string& n, core::u32 v) const { (void)n; (void)v; }
void Shader::set_float(const std::string& n, float v) const{ (void)n; (void)v; }

} // namespace ekchous::gpu
