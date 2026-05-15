package studio.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import studio.graph.Graph;
import studio.graph.Node;
import studio.graph.Parameter;
import studio.save.Exposure;
import studio.save.PftoolWriter;
import studio.save.ToolsLibrary;

/**
 * Save-as-Tool dialog. Two tabs:
 * <ol>
 *   <li><b>Metadata</b> — name, category, typeId, description.</li>
 *   <li><b>Expose</b> — checklist of every inner parameter. Toggle which
 *       ones become outer knobs on the saved compound; per-row alias
 *       override defaults to {@code <nodeLabel>.<paramName>}.</li>
 * </ol>
 *
 * <p>GraphInput / GraphOutput boundary nodes are always exposed
 * automatically (they are the compound's external face).
 */
public final class SaveAsToolDialog extends JDialog {

    private final JTextField nameField = new JTextField(24);
    private final JTextField categoryField = new JTextField(24);
    private final JTextField typeIdField = new JTextField(24);
    private final JTextArea descriptionField = new JTextArea(4, 24);

    private final List<ParamRow> paramRows = new ArrayList<>();
    private boolean accepted;
    private final Graph graph;

    public SaveAsToolDialog(java.awt.Frame parent, String suggestedName, Graph graph) {
        super(parent, "Save as Tool…", true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.graph = graph;

        nameField.setText(suggestedName != null ? suggestedName : "");
        categoryField.setText("My Tools");
        typeIdField.setText("user:" + slug(nameField.getText()));
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);

        nameField.addCaretListener(e -> {
            if (typeIdField.getText().startsWith("user:") || typeIdField.getText().isBlank()) {
                typeIdField.setText("user:" + slug(nameField.getText()));
            }
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Metadata", buildMetadataTab());
        tabs.addTab("Expose " + countExposable() + " params", buildExposureTab());

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
        add(tabs, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(560, 460));
        setLocationRelativeTo(parent);
    }

    private int countExposable() {
        if (graph == null) return 0;
        return Exposure.enumerateParams(graph).size();
    }

    private JPanel buildMetadataTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 8);

        addRow(form, c, "Name",         nameField);
        addRow(form, c, "Category",     categoryField);
        addRow(form, c, "Type ID",      typeIdField);
        addRow(form, c, "Description",  new JScrollPane(descriptionField));
        return form;
    }

    private JPanel buildExposureTab() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel rows = new JPanel();
        rows.setLayout(new javax.swing.BoxLayout(rows, javax.swing.BoxLayout.Y_AXIS));

        // Header row
        rows.add(headerRow());

        if (graph != null) {
            for (Node n : graph.nodes()) {
                String tid = n.typeId();
                if (tid.equals(studio.nodes.builtin.GraphInputNode.TYPE_ID)) continue;
                if (tid.equals(studio.nodes.builtin.GraphOutputNode.TYPE_ID)) continue;
                for (Parameter<?> p : n.parameters()) {
                    ParamRow row = new ParamRow(n, p);
                    paramRows.add(row);
                    rows.add(row.component);
                }
            }
        }

        JScrollPane scroll = new JScrollPane(rows);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        pane.add(scroll, BorderLayout.CENTER);

        // Footer with "Select All / None" buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton all = new JButton(new AbstractAction("Select all") {
            @Override public void actionPerformed(ActionEvent e) {
                for (ParamRow r : paramRows) r.cb.setSelected(true);
            }
        });
        JButton none = new JButton(new AbstractAction("Select none") {
            @Override public void actionPerformed(ActionEvent e) {
                for (ParamRow r : paramRows) r.cb.setSelected(false);
            }
        });
        footer.add(all);
        footer.add(none);
        pane.add(footer, BorderLayout.SOUTH);

        return pane;
    }

    private JPanel headerRow() {
        JPanel header = new JPanel();
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.X_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 8, 6));
        header.add(sized(new JLabel(""), 22));
        header.add(sized(boldLabel("Node"), 130));
        header.add(sized(boldLabel("Param"), 110));
        header.add(sized(boldLabel("Type"), 64));
        header.add(sized(boldLabel("Outer alias"), 200));
        return header;
    }

    private static JLabel boldLabel(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD));
        return l;
    }

    private static Component sized(Component c, int w) {
        Dimension d = new Dimension(w, 24);
        c.setPreferredSize(d);
        c.setMaximumSize(new Dimension(w, 24));
        c.setMinimumSize(d);
        return c;
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

    private void addRow(JPanel form, GridBagConstraints c, String labelText, Component field) {
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

    /** Build the Exposure spec selected in the dialog. */
    public Exposure exposure() {
        Exposure e = new Exposure();
        if (graph != null) {
            // Always expose every boundary node (the user adds these on purpose).
            for (Node n : graph.nodes()) {
                String tid = n.typeId();
                if (tid.equals(studio.nodes.builtin.GraphInputNode.TYPE_ID)) {
                    e.includedInputs.add(n.id().value);
                } else if (tid.equals(studio.nodes.builtin.GraphOutputNode.TYPE_ID)) {
                    e.includedOutputs.add(n.id().value);
                }
            }
        }
        for (ParamRow r : paramRows) {
            if (!r.cb.isSelected()) continue;
            Exposure.ParamKey key = new Exposure.ParamKey(r.node.id().value, r.param.name);
            e.includedParams.add(key);
            String alias = r.aliasField.getText().trim();
            String fallback = r.node.label() + "." + r.param.name;
            if (!alias.isBlank() && !alias.equals(fallback)) {
                e.aliases.put(key, alias);
            }
        }
        return e;
    }

    /**
     * Show the dialog. On accept, write the .pftool file into the library's
     * root and rescan so the palette picks it up.
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
                        : "Untitled",
                model.current().graph);
        dlg.setVisible(true);
        if (!dlg.accepted()) return null;

        Path out = library.root().resolve(slug(dlg.name()) + ".pftool");
        try {
            Files.createDirectories(library.root());
            PftoolWriter.write(out, dlg.typeId(), dlg.name(), dlg.category(),
                    dlg.description(), model.current().graph, dlg.exposure());
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

    /** Holds the checkbox + alias field for one param. */
    private static final class ParamRow {
        final Node node;
        final Parameter<?> param;
        final JCheckBox cb;
        final JTextField aliasField;
        final JPanel component;

        ParamRow(Node node, Parameter<?> param) {
            this.node = node;
            this.param = param;
            this.cb = new JCheckBox();
            this.cb.setSelected(true);
            this.aliasField = new JTextField(node.label() + "." + param.name);

            component = new JPanel();
            component.setLayout(new javax.swing.BoxLayout(component, javax.swing.BoxLayout.X_AXIS));
            component.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            component.add(sized(cb, 22));
            component.add(sized(label(node.label(), false), 130));
            component.add(sized(label(param.name, false), 110));
            component.add(sized(label(param.type.id, false), 64));
            component.add(sized(aliasField, 200));
            component.add(Box.createHorizontalGlue());
        }

        private static JLabel label(String s, boolean bold) {
            JLabel l = new JLabel(s);
            l.setHorizontalAlignment(SwingConstants.LEFT);
            if (bold) l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD));
            return l;
        }
    }
}
