package studio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import studio.graph.Node;
import studio.graph.Parameter;
import studio.graph.PortTypes;
import studio.save.PflowReader;

/**
 * Right-side panel that auto-generates widgets for the parameters of the
 * currently selected node. v1 selects via a combo box at the top because
 * there's no graph-editor canvas yet.
 */
public final class ParameterPanel extends JPanel {

    private final JLabel typeLabel = new JLabel(" ");
    private final JTextField nameField = new JTextField();
    private final JPanel headerWrap = new JPanel(new BorderLayout(4, 2));
    private final JLabel thumbnail = new JLabel();
    private final JPanel body = new JPanel();
    private Runnable onLabelChange;

    @SuppressWarnings("unused")
    private final StudioModel model;
    private Node active;

    public ParameterPanel(StudioModel model) {
        super(new BorderLayout());
        this.model = model;
        setBorder(BorderFactory.createTitledBorder("Parameters"));

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(body);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        nameField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        nameField.setToolTipText("Rename this node");
        nameField.addActionListener(e -> applyLabel());
        nameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { applyLabel(); }
        });
        typeLabel.setForeground(new Color(140, 140, 150));
        typeLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));

        thumbnail.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnail.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        thumbnail.setPreferredSize(new java.awt.Dimension(160, 0));

        headerWrap.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        headerWrap.add(nameField, BorderLayout.NORTH);
        headerWrap.add(typeLabel, BorderLayout.CENTER);
        headerWrap.add(thumbnail, BorderLayout.SOUTH);
        add(headerWrap, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** Display a live thumbnail image at the top of the panel (null clears it). */
    public void setThumbnail(java.awt.image.BufferedImage img) {
        if (img == null) {
            thumbnail.setIcon(null);
            thumbnail.setPreferredSize(new java.awt.Dimension(160, 0));
        } else {
            thumbnail.setIcon(new javax.swing.ImageIcon(img));
            thumbnail.setPreferredSize(new java.awt.Dimension(img.getWidth(), img.getHeight() + 4));
        }
        headerWrap.revalidate();
        headerWrap.repaint();
    }

    /** Optional callback fired after the user renames the active node via the panel. */
    public void setOnLabelChange(Runnable r) { this.onLabelChange = r; }

    private void applyLabel() {
        if (active == null) return;
        String text = nameField.getText().trim();
        if (text.isEmpty() || text.equals(active.label())) return;
        active.setLabel(text);
        if (onLabelChange != null) onLabelChange.run();
    }

    public void attachGraph(PflowReader.Result loaded) {
        active = null;
        for (Node n : loaded.graph.nodes()) {
            if (!n.parameters().isEmpty()) { active = n; break; }
        }
        rebuildBody();
    }

    /** Update the active node (driven by node-editor selection). */
    public void setActiveNode(Node n) {
        if (n == active) return;
        active = n;
        rebuildBody();
    }

    /** Re-render the widgets — call after a param change happens outside the panel (e.g. undo/redo). */
    public void refresh() { rebuildBody(); }

    private void rebuildBody() {
        body.removeAll();
        if (active != null) {
            nameField.setText(active.label());
            nameField.setEnabled(true);
            typeLabel.setText(active.typeId());
            for (Parameter<?> p : active.parameters()) {
                body.add(buildRow(p));
                body.add(Box.createVerticalStrut(2));
            }
        } else {
            nameField.setText("");
            nameField.setEnabled(false);
            typeLabel.setText("(no node selected)");
        }
        body.add(Box.createVerticalGlue());
        body.revalidate();
        body.repaint();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private JComponent buildRow(Parameter<?> p) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JLabel label = new JLabel(p.label);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setPreferredSize(new java.awt.Dimension(160, 22));
        if (p.description != null && !p.description.isBlank()) {
            label.setToolTipText(p.description);
        }
        row.add(label, BorderLayout.WEST);

        // Reset-to-default button (east). Skip for unsupported types.
        Parameter rawP = p;
        javax.swing.JButton reset = new javax.swing.JButton("↺");
        reset.setMargin(new java.awt.Insets(0, 4, 0, 4));
        reset.setToolTipText("Reset to default" + (p.defaultValue == null ? "" : " (" + describeDefault(p) + ")"));
        reset.setFocusable(false);
        reset.addActionListener(e -> {
            if (p.defaultValue != null) rawP.set(p.defaultValue);
            rebuildBody();
        });
        row.add(reset, BorderLayout.EAST);

        if (p.type == PortTypes.SCALAR) {
            row.add(buildFloatWidget((Parameter<Float>) p), BorderLayout.CENTER);
        } else if (p.type == PortTypes.INT) {
            row.add(buildIntWidget((Parameter<Integer>) p), BorderLayout.CENTER);
        } else if (p.type == PortTypes.BOOL) {
            row.add(buildBoolWidget((Parameter<Boolean>) p), BorderLayout.CENTER);
        } else if (p.type == PortTypes.VEC2 || p.type == PortTypes.VEC3) {
            row.add(buildVecWidget((Parameter<float[]>) p, p.type == PortTypes.VEC3 ? 3 : 2), BorderLayout.CENTER);
        } else if (p.type == PortTypes.VEC4) {
            if (p.uiHint == Parameter.UiHint.COLOR_RGBA) {
                row.add(buildColorWidget((Parameter<float[]>) p), BorderLayout.CENTER);
            } else {
                row.add(buildVecWidget((Parameter<float[]>) p, 4), BorderLayout.CENTER);
            }
        } else if (p.type == PortTypes.STRING) {
            row.add(buildStringWidget((Parameter<String>) p), BorderLayout.CENTER);
        } else {
            JLabel placeholder = new JLabel("(unsupported: " + p.type.id + ")");
            placeholder.setForeground(Color.LIGHT_GRAY);
            row.add(placeholder, BorderLayout.CENTER);
        }
        return row;
    }

    private JComponent buildFloatWidget(Parameter<Float> p) {
        float lo = p.min != null ? p.min : 0f;
        float hi = p.max != null ? p.max : Math.max(1f, p.get() * 2f);
        int slots = 1000;
        JSlider slider = new JSlider(0, slots);
        slider.setValue(toSlider(p.get(), lo, hi, slots));

        NumberFormat fmt = NumberFormat.getNumberInstance();
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(4);
        JFormattedTextField text = new JFormattedTextField(fmt);
        text.setColumns(6);
        text.setValue(p.get());

        slider.addChangeListener(e -> {
            float v = fromSlider(slider.getValue(), lo, hi, slots);
            if (!Float.valueOf(v).equals(p.get())) {
                p.set(v);
                text.setValue(v);
            }
        });
        text.addPropertyChangeListener("value", e -> {
            if (text.getValue() instanceof Number n) {
                float v = clamp(n.floatValue(), lo, hi);
                if (!Float.valueOf(v).equals(p.get())) {
                    p.set(v);
                    slider.setValue(toSlider(v, lo, hi, slots));
                }
            }
        });

        JPanel wrap = new JPanel(new BorderLayout(4, 0));
        wrap.add(slider, BorderLayout.CENTER);
        wrap.add(text, BorderLayout.EAST);
        return wrap;
    }

    private JComponent buildIntWidget(Parameter<Integer> p) {
        int lo = p.min != null ? p.min : 0;
        int hi = p.max != null ? p.max : Math.max(100, p.get() * 2);
        SpinnerNumberModel snm = new SpinnerNumberModel(p.get().intValue(), lo, hi, 1);
        JSpinner spinner = new JSpinner(snm);
        spinner.addChangeListener(e -> {
            int v = (Integer) spinner.getValue();
            if (!Integer.valueOf(v).equals(p.get())) p.set(v);
        });
        return spinner;
    }

    private JComponent buildBoolWidget(Parameter<Boolean> p) {
        JCheckBox cb = new JCheckBox();
        cb.setSelected(Boolean.TRUE.equals(p.get()));
        cb.addItemListener(e -> p.set(cb.isSelected()));
        return cb;
    }

    private JComponent buildVecWidget(Parameter<float[]> p, int n) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        float[] current = p.get() != null ? p.get() : new float[n];
        JTextField[] fields = new JTextField[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            JTextField tf = new JTextField(Float.toString(current[idx]), 5);
            fields[i] = tf;
            tf.addActionListener(new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    pushVec(p, fields);
                }
            });
            row.add(tf);
            if (i < n - 1) row.add(Box.createHorizontalStrut(3));
        }
        return row;
    }

    private void pushVec(Parameter<float[]> p, JTextField[] fields) {
        float[] out = new float[fields.length];
        for (int i = 0; i < fields.length; i++) {
            try { out[i] = Float.parseFloat(fields[i].getText()); }
            catch (NumberFormatException ignored) { out[i] = 0f; }
        }
        p.set(out);
    }

    private static String describeDefault(Parameter<?> p) {
        Object v = p.defaultValue;
        if (v == null) return "null";
        if (v instanceof float[] arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(arr[i]);
            }
            return sb.append("]").toString();
        }
        return v.toString();
    }

    private JComponent buildStringWidget(Parameter<String> p) {
        JTextField tf = new JTextField(p.get() == null ? "" : p.get(), 20);
        tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void push() { p.set(tf.getText()); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { push(); }
        });
        return tf;
    }

    private JComponent buildColorWidget(Parameter<float[]> p) {
        JPanel swatch = new JPanel();
        float[] c = p.get();
        Color initial = new Color(c[0], c[1], c[2], c.length > 3 ? c[3] : 1f);
        swatch.setBackground(initial);
        swatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        swatch.setPreferredSize(new java.awt.Dimension(48, 22));
        swatch.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                Color chosen = JColorChooser.showDialog(swatch, "Pick color", swatch.getBackground());
                if (chosen == null) return;
                float[] out = chosen.getComponents(null);
                p.set(out);
                swatch.setBackground(chosen);
            }
        });
        return swatch;
    }

    /* ------------------------------ helpers ------------------------------ */

    private static int toSlider(float v, float lo, float hi, int slots) {
        if (hi <= lo) return 0;
        return Math.max(0, Math.min(slots, Math.round(((v - lo) / (hi - lo)) * slots)));
    }
    private static float fromSlider(int slider, float lo, float hi, int slots) {
        return lo + (slider / (float) slots) * (hi - lo);
    }
    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

}
