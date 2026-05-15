package studio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Shift+A floating "quick-add" panel: a single-line filter at the top, a
 * ranked list of node typeIds below, Enter to insert at the canvas
 * cursor, Esc to dismiss.
 *
 * <p>Built as a JDialog instead of a Window so it inherits the frame's
 * z-order; positioned centered over the parent window.
 */
public final class QuickAddOverlay extends JDialog {

    private final JTextField filter = new JTextField();
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final List<String> allIds;
    private final Consumer<String> onPick;

    public QuickAddOverlay(java.awt.Frame owner, List<String> allIds, Consumer<String> onPick) {
        super(owner, "Add tool…", true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setUndecorated(true);
        this.allIds = allIds;
        this.onPick = onPick;

        filter.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 96), 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild(); }
        });
        filter.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int c = e.getKeyCode();
                if (c == KeyEvent.VK_ESCAPE) { dispose(); }
                else if (c == KeyEvent.VK_ENTER) { commit(); }
                else if (c == KeyEvent.VK_DOWN) {
                    int i = Math.min(list.getModel().getSize() - 1, list.getSelectedIndex() + 1);
                    list.setSelectedIndex(i); list.ensureIndexIsVisible(i);
                } else if (c == KeyEvent.VK_UP) {
                    int i = Math.max(0, list.getSelectedIndex() - 1);
                    list.setSelectedIndex(i); list.ensureIndexIsVisible(i);
                }
            }
        });
        list.setVisibleRowCount(10);
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() >= 2) commit();
            }
        });

        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 140), 1));
        root.add(filter, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        setContentPane(root);

        rebuild();
        setSize(new Dimension(360, 320));
        setLocationRelativeTo(owner);
    }

    private void rebuild() {
        String q = filter.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (String id : allIds) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(id);
            }
        }
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void commit() {
        String pick = list.getSelectedValue();
        if (pick == null && !model.isEmpty()) pick = model.firstElement();
        if (pick != null && onPick != null) onPick.accept(pick);
        dispose();
    }

    /** Pop the overlay over {@code parent}; focuses the filter field. */
    public static void show(java.awt.Frame parent, List<String> allIds, Consumer<String> onPick) {
        QuickAddOverlay dlg = new QuickAddOverlay(parent, allIds, onPick);
        SwingUtilities.invokeLater(() -> dlg.filter.requestFocusInWindow());
        dlg.setVisible(true);
    }
}
