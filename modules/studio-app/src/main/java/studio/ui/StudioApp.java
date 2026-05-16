package studio.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;

import com.thomasdiewald.pixelflow.java.dwgl.DwGLError;

/**
 * Entry point for PixelFlow Studio (M3 UI shell). Boots FlatLaf, opens the
 * {@link MainFrame}, and auto-loads the default starter project.
 */
public final class StudioApp {

    public static void main(String[] args) {
        // Silence the known-phantom Fluid.addDensity GL_INVALID_OPERATION from
        // the AMD/JOGL combination (diagnosed in M1, see commit ea6cf64).
        DwGLError.SUPPRESSED_MESSAGE_PREFIXES.add("Fluid.addDensity");

        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            System.err.println("FlatLaf failed to install: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            // Optional CLI arg: a .pflow path; otherwise show the
            // mouse-driven flow-field-particles default scene so the
            // user has something interactive to test against immediately.
            if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
                frame.openProject(args[0]);
            } else {
                frame.openDefaultScene();
            }
        });
    }

    private StudioApp() {}
}
