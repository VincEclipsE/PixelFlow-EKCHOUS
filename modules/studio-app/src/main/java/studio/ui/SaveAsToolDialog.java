package studio.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import studio.save.PftoolWriter;
import studio.save.ToolsLibrary;

/**
 * v1 Save-as-Tool dialog — a single-screen form. The plan's 5-step wizard
 * (scope / ports / param exposure / metadata / review) will refine this
 * in M3.4; for now we expose every output and every parameter automatically.
 */
public final class SaveAsToolDialog extends JDialog {

    private final JTextField nameField = new JTextField(24);
    private final JTextField categoryField = new JTextField(24);
    private final JTextField typeIdField = new JTextField(24);
    private final JTextArea descriptionField = new JTextArea(4, 24);
    private boolean accepted;

    public SaveAsToolDialog(java.awt.Frame parent, String suggestedName) {
        super(parent, "Save as Tool…", true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 8);

        nameField.setText(suggestedName != null ? suggestedName : "");
        categoryField.setText("My Tools");
        typeIdField.setText("user:" + slug(nameField.getText()));
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);

        nameField.addCaretListener(e -> {
            if (typeIdField.getText().startsWith("user:")
                    || typeIdField.getText().isBlank()) {
                typeIdField.setText("user:" + slug(nameField.getText()));
            }
        });

        addRow(form, c, "Name",         nameField);
        addRow(form, c, "Category",     categoryField);
        addRow(form, c, "Type ID",      typeIdField);
        addRow(form, c, "Description",  new JScrollPane(descriptionField));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton(new AbstractAction("Cancel") {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });
        JButton save = new JButton(new AbstractAction("Save") {
            @Override public void actionPerformed(ActionEvent e) {
                if (validateForm()) { accepted = true; dispose(); }
            }
        });
        getRootPane().setDefaultButton(save);
        buttons.add(cancel);
        buttons.add(save);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(440, getHeight()));
        setLocationRelativeTo(parent);
    }

    private boolean validateForm() {
        if (nameField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Name is required", "Missing", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (typeIdField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Type ID is required", "Missing", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void addRow(JPanel form, GridBagConstraints c, String labelText, java.awt.Component field) {
        c.gridx = 0; c.weightx = 0;
        form.add(new JLabel(labelText), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
    }

    public boolean accepted() { return accepted; }
    public String name()        { return nameField.getText().trim(); }
    public String category()    { return categoryField.getText().trim(); }
    public String typeId()      { return typeIdField.getText().trim(); }
    public String description() { return descriptionField.getText().trim(); }

    /**
     * Convenience: show the dialog, and on accept, write the .pftool file
     * into the library's root and rescan so the palette picks it up.
     *
     * @return the typeId of the saved tool, or {@code null} if cancelled / failed
     */
    public static String run(java.awt.Frame parent, StudioModel model, ToolsLibrary library) {
        if (model.current() == null) {
            JOptionPane.showMessageDialog(parent, "No project loaded", "Save as Tool",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        SaveAsToolDialog dlg = new SaveAsToolDialog(parent,
                model.currentPath() != null
                        ? model.currentPath().getFileName().toString().replaceFirst("\\.pflow$", "")
                        : "Untitled");
        dlg.setVisible(true);
        if (!dlg.accepted()) return null;

        Path out = library.root().resolve(slug(dlg.name()) + ".pftool");
        try {
            Files.createDirectories(library.root());
            PftoolWriter.write(out, dlg.typeId(), dlg.name(), dlg.category(),
                    dlg.description(), model.current().graph);
            library.rescan();
            return dlg.typeId();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Failed to save tool:\n" + ex.getMessage(),
                    "Save as Tool", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private static String slug(String s) {
        if (s == null) return "untitled";
        String out = s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return out.isEmpty() ? "untitled" : out;
    }
}
