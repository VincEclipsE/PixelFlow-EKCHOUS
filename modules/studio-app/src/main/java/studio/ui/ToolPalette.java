package studio.ui;

import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.TransferHandler;

import studio.graph.NodeFactoryRegistry;
import studio.nodes.builtin.GraphOutputNode;
import studio.nodes.filter.BloomNode;
import studio.nodes.fluid.FluidNode;

/**
 * Tool palette. Lists registered node typeIds; the user can drag an entry
 * onto the {@link NodeEditorPanel} to add a new node to the current graph.
 */
public final class ToolPalette extends JPanel {

    /** Custom data flavor used to identify palette drags. */
    public static final DataFlavor TYPE_ID_FLAVOR = new DataFlavor(String.class, "studio/typeId");

    public ToolPalette(NodeFactoryRegistry registry) {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Tools"));

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String id : knownTypeIds()) {
            if (registry.has(id)) model.addElement(id);
        }

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
    }

    private static List<String> knownTypeIds() {
        List<String> ids = new ArrayList<>();
        ids.add(FluidNode.TYPE_ID);
        ids.add(BloomNode.TYPE_ID);
        ids.add(GraphOutputNode.TYPE_ID);
        Collections.sort(ids);
        return ids;
    }
}
