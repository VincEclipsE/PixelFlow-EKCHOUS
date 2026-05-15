package studio.ui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import studio.graph.NodeFactoryRegistry;
import studio.nodes.builtin.GraphOutputNode;
import studio.nodes.filter.BloomNode;
import studio.nodes.fluid.FluidNode;

/**
 * v1 tool palette — informational only. Shows the list of registered node
 * type ids so users know what's available; double-click and drag-to-canvas
 * land in M3.x once the node-editor canvas exists.
 */
public final class ToolPalette extends JPanel {

    public ToolPalette(NodeFactoryRegistry registry) {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Tools"));

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String id : knownTypeIds()) {
            if (registry.has(id)) model.addElement(id);
        }

        JList<String> list = new JList<>(model);
        list.setVisibleRowCount(20);
        add(new JScrollPane(list), BorderLayout.CENTER);

        JLabel hint = new JLabel("Tools palette (v1 read-only)");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(hint, BorderLayout.SOUTH);
    }

    /** Curated order for the v1 palette. */
    private static List<String> knownTypeIds() {
        List<String> ids = new ArrayList<>();
        ids.add(FluidNode.TYPE_ID);
        ids.add(BloomNode.TYPE_ID);
        ids.add(GraphOutputNode.TYPE_ID);
        Collections.sort(ids);
        return ids;
    }
}
