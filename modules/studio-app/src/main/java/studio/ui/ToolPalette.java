package studio.ui;

import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
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
        list.setDragEnabled(true);
        list.setDropMode(DropMode.ON);
        list.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override protected Transferable createTransferable(JComponent c) {
                @SuppressWarnings("unchecked")
                JList<String> source = (JList<String>) c;
                String value = source.getSelectedValue();
                return value == null ? null : new StringSelection(value);
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
        for (String id : allIds) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(id);
            }
        }
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
        ids.add(studio.nodes.aa.FxaaNode.TYPE_ID);
        ids.add(studio.nodes.flowfield.FlowFieldNode.TYPE_ID);
        ids.add(GraphOutputNode.TYPE_ID);
        ids.add(studio.nodes.builtin.GraphInputNode.TYPE_ID);
        ids.add(studio.nodes.builtin.NoteNode.TYPE_ID);
        ids.add(studio.nodes.builtin.GroupBoxNode.TYPE_ID);
        return ids;
    }
}
