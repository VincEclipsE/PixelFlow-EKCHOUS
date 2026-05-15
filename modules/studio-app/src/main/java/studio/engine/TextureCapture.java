package studio.engine;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;

/**
 * Helpers for reading the pixels of a GL-backed {@link RenderTarget} into a
 * {@link BufferedImage} or writing it directly to a PNG file. Used by the
 * headless smoke gate and by the studio's File &gt; Export PNG action.
 *
 * <p>Caller is responsible for invoking these from the GL thread with a
 * current context.
 */
public final class TextureCapture {

    private TextureCapture() {}

    public static BufferedImage readToImage(GL2ES2 gl, RenderTarget rt) {
        int w = rt.getWidth();
        int h = rt.getHeight();
        int textureId = rt.getGLTextureId();
        if (textureId == 0 || w <= 0 || h <= 0) return null;

        gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        gl.getGL().getGL3().glGetTexImage(GL.GL_TEXTURE_2D, 0,
                GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buf);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        // GL textures are bottom-row-first; flip to top-row-first for image IO.
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            int srcRow = (h - 1 - y) * w;
            for (int x = 0; x < w; x++) {
                int i = (srcRow + x) * 4;
                int r = buf.get(i)     & 0xFF;
                int g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
                int a = buf.get(i + 3) & 0xFF;
                argb[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        img.setRGB(0, 0, w, h, argb, 0, w);
        return img;
    }

    public static void writePng(GL2ES2 gl, RenderTarget rt, Path outPath) throws IOException {
        BufferedImage img = readToImage(gl, rt);
        if (img == null) throw new IOException("RenderTarget is not sampleable");
        if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());
        ImageIO.write(img, "png", outPath.toFile());
    }
}
