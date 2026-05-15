package studio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Slim message strip at the bottom of {@link MainFrame}. Use it to surface
 * connect errors (type mismatch, cycle), save outcomes, and "X tools
 * loaded" toasts without popping a modal.
 *
 * <p>Messages are colored by severity and auto-clear after a few seconds
 * (info: 4 s, error: 8 s). Calling {@link #info(String)} or
 * {@link #error(String)} is safe from any thread; the update is marshalled
 * to the EDT.
 */
public final class StatusBar extends JPanel {

    public enum Severity { INFO, ERROR }

    private final JLabel label = new JLabel(" ");
    private Timer clearTimer;

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        label.setForeground(new Color(180, 180, 190));
        add(label, BorderLayout.WEST);
    }

    public void info(String msg)  { show(msg, Severity.INFO,  4_000); }
    public void error(String msg) { show(msg, Severity.ERROR, 8_000); }

    private void show(String msg, Severity sev, int ttlMillis) {
        Runnable apply = () -> {
            label.setText(msg);
            label.setForeground(sev == Severity.ERROR
                    ? new Color(255, 110, 110)
                    : new Color(180, 200, 220));
            if (clearTimer != null) clearTimer.stop();
            clearTimer = new Timer(ttlMillis, e -> {
                label.setText(" ");
                label.setForeground(new Color(180, 180, 190));
            });
            clearTimer.setRepeats(false);
            clearTimer.start();
        };
        if (SwingUtilities.isEventDispatchThread()) apply.run();
        else SwingUtilities.invokeLater(apply);
    }
}
