package studio.ui;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JToolBar;

/**
 * Compact toolbar above the canvas with Play/Pause, Step, and Reset
 * buttons that drive the GL preview's runtime.
 */
public final class PreviewToolbar extends JToolBar {

    private final GLPreviewPanel preview;
    private final StatusBar statusBar;
    private final JButton playPause = new JButton();

    public PreviewToolbar(GLPreviewPanel preview, StatusBar statusBar) {
        super("Preview");
        this.preview = preview;
        this.statusBar = statusBar;
        setFloatable(false);

        playPause.setAction(new AbstractAction("Pause") {
            @Override public void actionPerformed(ActionEvent e) { togglePlay(); }
        });
        playPause.setToolTipText("Pause / resume simulation (Space)");

        JButton step = new JButton(new AbstractAction("Step") {
            @Override public void actionPerformed(ActionEvent e) {
                preview.stepOnce();
                if (statusBar != null) statusBar.info("Stepped 1 frame");
            }
        });
        step.setToolTipText("Advance one frame while paused");

        JButton reset = new JButton(new AbstractAction("Reset") {
            @Override public void actionPerformed(ActionEvent e) {
                preview.resetRuntime();
                if (statusBar != null) statusBar.info("Runtime reset");
            }
        });
        reset.setToolTipText("Recreate the graph runtime (restart from frame 0)");

        add(playPause);
        add(step);
        addSeparator();
        add(reset);
    }

    public void togglePlay() {
        boolean nowPaused = !preview.isPaused();
        preview.setPaused(nowPaused);
        playPause.setText(nowPaused ? "Play" : "Pause");
        if (statusBar != null) statusBar.info(nowPaused ? "Paused" : "Playing");
    }
}
