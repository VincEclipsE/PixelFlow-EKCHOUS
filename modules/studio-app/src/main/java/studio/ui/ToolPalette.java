package studio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import studio.graph.NodeFactoryRegistry;
import studio.nodes.builtin.GraphOutputNode;
import studio.nodes.filter.BloomNode;
import studio.nodes.fluid.FluidNode;
import studio.save.ToolsLibrary;

/**
 * Tool palette. Lists registered node typeIds with a search box at the top;
 * the user can drag an entry onto the {@link NodeEditorPanel} to add a new
 * node to the current graph. After {@link #reload()} the list re-syncs with
 * the registry + tools library (used after a Save-as-Tool or a hot-reload).
 */
public final class ToolPalette extends JPanel {

    public static final DataFlavor TYPE_ID_FLAVOR = new DataFlavor(String.class, "studio/typeId");

    /** Sentinel prefix on entries that act as visual section headers. */
    private static final String HEADER_PREFIX = "##HDR##";

    private final NodeFactoryRegistry registry;
    private final ToolsLibrary toolsLibrary;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JTextField search = new JTextField();
    private final List<String> allIds = new ArrayList<>();

    public ToolPalette(NodeFactoryRegistry registry, ToolsLibrary toolsLibrary) {
        super(new BorderLayout());
        this.registry = registry;
        this.toolsLibrary = toolsLibrary;
        setBorder(BorderFactory.createTitledBorder("Tools"));

        search.setToolTipText("Filter tools…");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        add(search, BorderLayout.NORTH);

        JList<String> list = new JList<>(model);
        list.setVisibleRowCount(20);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setDragEnabled(true);
        list.setDropMode(DropMode.ON);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> jlist, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String s = value == null ? "" : value.toString();
                boolean isHeader = s.startsWith(HEADER_PREFIX);
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        jlist, s, index, isSelected && !isHeader, cellHasFocus && !isHeader);
                if (isHeader) {
                    label.setText(s.substring(HEADER_PREFIX.length()));
                    label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1f));
                    label.setForeground(new Color(150, 160, 180));
                    label.setBackground(new Color(35, 35, 42));
                    label.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
                }
                return label;
            }
        });
        list.addListSelectionListener(e -> {
            String v = list.getSelectedValue();
            if (v != null && v.startsWith(HEADER_PREFIX)) list.clearSelection();
        });
        list.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override protected Transferable createTransferable(JComponent c) {
                @SuppressWarnings("unchecked")
                JList<String> source = (JList<String>) c;
                String value = source.getSelectedValue();
                if (value == null || value.startsWith(HEADER_PREFIX)) return null;
                return new StringSelection(value);
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

        JLabel hint = new JLabel("Drag a tool onto the canvas");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(hint, BorderLayout.SOUTH);

        reload();
    }

    /** Refresh the displayed list from the registry + tools library. */
    public void reload() {
        allIds.clear();
        TreeSet<String> ids = new TreeSet<>();
        for (String id : builtinTypeIds()) {
            if (registry.has(id)) ids.add(id);
        }
        if (toolsLibrary != null) {
            ids.addAll(toolsLibrary.registeredTypeIds());
        }
        allIds.addAll(ids);
        applyFilter();
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        // Bucket by category, preserving the natural sorted-by-id ordering inside each bucket.
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) buckets.put(cat, new ArrayList<>());
        buckets.put("Other", new ArrayList<>());
        for (String id : allIds) {
            if (!q.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(q)) continue;
            String cat = categoryOf(id);
            buckets.computeIfAbsent(cat, k -> new ArrayList<>()).add(id);
        }
        for (var entry : buckets.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            model.addElement(HEADER_PREFIX + entry.getKey());
            for (String id : entry.getValue()) model.addElement(id);
        }
    }

    private static final List<String> CATEGORY_ORDER = List.of(
            "Input", "Fluid", "Filter", "Image", "Flow field", "Anti-aliasing",
            "Built-in", "Sample tools", "User tools");

    private static String categoryOf(String typeId) {
        if (typeId.startsWith("studio.input"))   return "Input";
        if (typeId.startsWith("pf.fluid"))       return "Fluid";
        if (typeId.startsWith("pf.filter"))      return "Filter";
        if (typeId.startsWith("pf.image"))       return "Image";
        if (typeId.startsWith("pf.flowfield"))   return "Flow field";
        if (typeId.startsWith("pf.aa"))          return "Anti-aliasing";
        if (typeId.startsWith("studio.builtin")) return "Built-in";
        if (typeId.startsWith("sample:"))        return "Sample tools";
        if (typeId.startsWith("user:"))          return "User tools";
        return "Other";
    }

    private static List<String> builtinTypeIds() {
        List<String> ids = new ArrayList<>();
        ids.add(FluidNode.TYPE_ID);
        ids.add(BloomNode.TYPE_ID);
        ids.add(studio.nodes.filter.GaussianBlurNode.TYPE_ID);
        ids.add(studio.nodes.filter.SobelNode.TYPE_ID);
        ids.add(studio.nodes.filter.LuminanceNode.TYPE_ID);
        ids.add(studio.nodes.filter.BoxBlurNode.TYPE_ID);
        ids.add(studio.nodes.filter.GammaNode.TYPE_ID);
        ids.add(studio.nodes.filter.LuminanceThresholdNode.TYPE_ID);
        ids.add(studio.nodes.filter.DifferenceNode.TYPE_ID);
        ids.add(studio.nodes.filter.MultiplyNode.TYPE_ID);
        ids.add(studio.nodes.filter.MedianNode.TYPE_ID);
        ids.add(studio.nodes.filter.BinomialBlurNode.TYPE_ID);
        ids.add(studio.nodes.filter.LiquidFxNode.TYPE_ID);
        ids.add(studio.nodes.image.OpticalFlowNode.TYPE_ID);
        ids.add(studio.nodes.filter.LaplaceNode.TYPE_ID);
        ids.add(studio.nodes.filter.ClampNode.TYPE_ID);
        ids.add(studio.nodes.filter.BilateralFilterNode.TYPE_ID);
        ids.add(studio.nodes.filter.DistanceTransformNode.TYPE_ID);
        ids.add(studio.nodes.image.HarrisCornerNode.TYPE_ID);
        ids.add(studio.nodes.filter.LevelsNode.TYPE_ID);
        ids.add(studio.nodes.aa.SmaaNode.TYPE_ID);
        ids.add(studio.nodes.aa.FxaaNode.TYPE_ID);
        ids.add(studio.nodes.flowfield.FlowFieldNode.TYPE_ID);
        ids.add(studio.nodes.flowfield.FlowFieldParticlesNode.TYPE_ID);
        ids.add(studio.nodes.input.MouseNode.TYPE_ID);
        ids.add(GraphOutputNode.TYPE_ID);
        ids.add(studio.nodes.builtin.GraphInputNode.TYPE_ID);
        ids.add(studio.nodes.builtin.NoteNode.TYPE_ID);
        ids.add(studio.nodes.builtin.GroupBoxNode.TYPE_ID);
        return ids;
    }
}
