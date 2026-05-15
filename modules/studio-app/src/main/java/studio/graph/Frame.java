package studio.graph;

import com.jogamp.opengl.GL2ES2;
import com.thomasdiewald.pixelflow.java.DwPixelFlow;

/**
 * Per-frame execution context passed to {@link Node#evaluate(Frame)}. Carries
 * timing, the GL handle, the PixelFlow context, and the bus that holds
 * upstream output values.
 */
public interface Frame {

    GL2ES2 gl();

    DwPixelFlow pixelFlow();

    GraphContext context();

    /** Seconds since the runtime started. */
    double timeSeconds();

    /** Frame index, monotonically increasing from 0. */
    long frameIndex();

    /** Seconds since the previous frame (or 0 for frame 0). */
    double deltaSeconds();

    /** Resolve an input port's incoming value. Returns null if the input is unconnected. */
    <T> T read(InputPort<T> port);

    /** Publish a value on an output port for downstream consumers. */
    <T> void publish(OutputPort<T> port, T value);
}
