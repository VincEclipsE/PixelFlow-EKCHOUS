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

    public static final String DEFAULT_PROJECT = "starters/fluid-bloom.pflow";

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
            frame.openProject(DEFAULT_PROJECT);
        });
    }

    private StudioApp() {}
}
