package studio.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JPanel;

import studio.graph.Edge;
import studio.graph.InputPort;
import studio.graph.Node;
import studio.graph.NodeFactoryRegistry;
import studio.graph.OutputPort;
import studio.save.PflowReader;

/**
 * Java2D node-editor canvas. v1 is view-with-drag: auto-lays-out the loaded
 * graph in topological order, lets the user reposition nodes with the
 * mouse, pan with middle-drag, zoom with the wheel, and click to select a
 * node (selection drives the parameter panel).
 *
 * <p>Edge creation/deletion is deferred to M3.2; the graph topology is
 * read-only from this panel for now.
 */
public final class NodeEditorPanel extends JPanel {

    // Visual constants
    private static final int NODE_WIDTH   = 180;
    private static final int NODE_HEADER  = 22;
    private static final int ROW_HEIGHT   = 18;
    private static final int PORT_RADIUS  = 6;
    private static final int LAYOUT_X_GAP = 80;
    private static final int LAYOUT_Y_GAP = 30;
    private static final int LAYOUT_ORIGIN_X = 80;
    private static final int LAYOUT_ORIGIN_Y = 80;

    private final Map<Node, Layout> layouts = new IdentityHashMap<>();
    private final List<Consumer<Node>> selectionListeners = new ArrayList<>();

    private PflowReader.Result current;
    private Node selected;                                          // primary (drives param panel)
    private final java.util.Set<Node> secondary = new java.util.LinkedHashSet<>(); // additional selected nodes

    // Viewport transform
    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;

    // Drag state
    private Node draggingNode;
    private Point2D dragNodeOffset;
    private Point dragPanStart;
    private double dragPanStartX, dragPanStartY;
    private OutputPort<?> wireFromPort;     // start of an in-progress edge drag
    private Point2D wireDragCurrent;        // current mouse position in graph space

    // Marquee
    private Point2D marqueeStart;           // world-space anchor; null when not marqueeing
    private Point2D marqueeCurrent;
    // Drag-move tracking for multi-select
    private final java.util.Map<Node, int[]> dragStartPositions = new java.util.IdentityHashMap<>();

    private final NodeFactoryRegistry registry;
    private final studio.graph.UndoStack undo = new studio.graph.UndoStack();
    private final ParamEditTracker paramTracker = new ParamEditTracker(undo);
    private StatusBar statusBar;

    public void setStatusBar(StatusBar bar) { this.statusBar = bar; }

    public boolean canUndo() { return undo.canUndo(); }
    public boolean canRedo() { return undo.canRedo(); }

    private Runnable onMutate;
    public void setOnMutate(Runnable r) { this.onMutate = r; }
    private void fireOnMutate() { if (onMutate != null) onMutate.run(); }

    public void undo() {
        var c = undo.undo();
        if (c != null) {
            if (statusBar != null) statusBar.info("Undid: " + c.description());
            repaint();
            fireOnMutate();
        }
    }

    public void redo() {
        var c = undo.redo();
        if (c != null) {
            if (statusBar != null) statusBar.info("Redid: " + c.description());
            repaint();
            fireOnMutate();
        }
    }

    public NodeEditorPanel(NodeFactoryRegistry registry) {
        this.registry = registry;
        setBackground(new Color(20, 20, 24));
        setOpaque(true);
        setPreferredSize(new Dimension(800, 600));
        setFocusable(true);

        MouseHandler m = new MouseHandler();
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_F9) {
                    showMinimap = !showMinimap;
                    repaint();
                    return;
                }
                if (code == KeyEvent.VK_HOME) {
                    panX = 0; panY = 0; zoom = 1.0;
                    repaint();
                    return;
                }
                if (code == KeyEvent.VK_F) {
                    frameAll();
                    return;
                }
                if (e.isControlDown() && code == KeyEvent.VK_Z && !e.isShiftDown()) {
                    undo();
                    return;
                }
                if ((e.isControlDown() && code == KeyEvent.VK_Y)
                        || (e.isControlDown() && e.isShiftDown() && code == KeyEvent.VK_Z)) {
                    redo();
                    return;
                }
                if (e.isControlDown() && code == KeyEvent.VK_V) {
                    pasteFromClipboard();
                    return;
                }
                if (e.isControlDown() && code == KeyEvent.VK_A) {
                    if (current != null) setMultiSelection(current.graph.nodes());
                    return;
                }
                if (selected == null || current == null) return;
                if (code == KeyEvent.VK_DELETE) {
                    deleteSelected();
                } else if (e.isControlDown() && code == KeyEvent.VK_D) {
                    duplicateSelected();
                } else if (e.isControlDown() && code == KeyEvent.VK_C) {
                    copySelectedToClipboard();
                } else if (e.isControlDown() && code == KeyEvent.VK_X) {
                    cutSelected();
                } else if (code == KeyEvent.VK_M) {
                    toggleMuteSelected();
                }
            }
        });

        // Accept palette drops to add a new node at the cursor position.
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetListener() {
            @Override public void dragEnter(DropTargetDragEvent e) {}
            @Override public void dragOver(DropTargetDragEvent e) {}
            @Override public void dropActionChanged(DropTargetDragEvent e) {}
            @Override public void dragExit(DropTargetEvent e) {}
            @Override public void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = e.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    String typeId = data == null ? null : data.toString();
                    if (typeId != null && current != null) {
                        Node added = registry.create(typeId);
                        Point2D world = screenToWorld(e.getLocation());
                        int x = (int) Math.round(world.getX() - NODE_WIDTH / 2.0);
                        int y = (int) Math.round(world.getY() - NODE_HEADER / 2.0);
                        doAddNode(added, x, y);
                        setSelection(added);
                    }
                    e.dropComplete(true);
                } catch (Exception ex) {
                    System.err.println("palette drop failed: " + ex.getMessage());
                    e.dropComplete(false);
                }
            }
        });
    }

    public void attachGraph(PflowReader.Result loaded) {
        this.current = loaded;
        this.selected = null;
        this.undo.clear();
        this.paramTracker.clear();
        for (Node n : loaded.graph.nodes()) this.paramTracker.track(n);
        autoLayout(loaded);
        // Apply any saved per-node positions from the .pflow file
        if (loaded.source != null && loaded.source.nodes != null) {
            for (var nj : loaded.source.nodes) {
                if (nj.layout == null || nj.layout.x == null || nj.layout.y == null) continue;
                Node node = loaded.nodesById.get(nj.id);
                if (node != null) layouts.put(node, new Layout(nj.layout.x, nj.layout.y));
            }
        }
        repaint();
    }

    /** Discard saved positions and re-run autoLayout on the current graph. */
    public void resetLayout() {
        if (current == null) return;
        autoLayout(current);
        repaint();
    }

    /** Centre the viewport on the graph's bounding box, zoomed to fit. */
    public void frameAll() {
        if (current == null || current.graph.nodes().isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Node n : current.graph.nodes()) {
            Layout L = layoutOf(n);
            int h = nodeHeight(n);
            minX = Math.min(minX, L.x);
            minY = Math.min(minY, L.y);
            maxX = Math.max(maxX, L.x + NODE_WIDTH);
            maxY = Math.max(maxY, L.y + h);
        }
        int padding = 40;
        double bbW = (maxX - minX) + padding * 2;
        double bbH = (maxY - minY) + padding * 2;
        double sx = getWidth() / bbW;
        double sy = getHeight() / bbH;
        zoom = Math.max(0.2, Math.min(4.0, Math.min(sx, sy)));
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        panX = getWidth() / 2.0 - cx * zoom;
        panY = getHeight() / 2.0 - cy * zoom;
        repaint();
    }

    private static final String CLIPBOARD_MARKER = "studio.node:";
    private static final String CLIPBOARD_MULTI_MARKER = "studio.nodes:";

    public void deleteSelected() {
        if (selected == null || current == null) return;
        java.util.List<Node> targets = allSelected();
        setSelection(null);
        for (Node t : targets) doRemoveNode(t);
    }

    public void toggleMuteSelected() {
        if (selected == null) return;
        java.util.List<Node> targets = allSelected();
        // Toggle relative to the primary's current state so the group ends up consistent.
        final boolean newOn = !selected.isEnabled();
        for (Node n : targets) n.setEnabled(newOn);
        if (statusBar != null) {
            statusBar.info((newOn ? "Enabled " : "Muted ") + targets.size()
                    + (targets.size() == 1 ? " node" : " nodes"));
        }
        final java.util.List<Node> snapshot = new java.util.ArrayList<>(targets);
        undo.push(new studio.graph.UndoStack.Command() {
            @Override public void apply()  { for (Node n : snapshot) n.setEnabled(newOn);  repaint(); }
            @Override public void revert() { for (Node n : snapshot) n.setEnabled(!newOn); repaint(); }
            @Override public String description() {
                return (newOn ? "enable " : "mute ") + snapshot.size()
                        + (snapshot.size() == 1 ? " node" : " nodes");
            }
        });
        repaint();
    }

    public void cutSelected() {
        if (selected == null || current == null) return;
        copySelectedToClipboard();
        java.util.List<Node> targets = allSelected();
        setSelection(null);
        for (Node t : targets) doRemoveNode(t);
    }

    /** Remove + remember edges so undo can restore them. */
    private void doRemoveNode(Node n) {
        final Node node = n;
        final Layout pos = layouts.get(node);
        final int posX = pos != null ? pos.x : 0;
        final int posY = pos != null ? pos.y : 0;
        // Snapshot all edges that touch this node (graph.removeNode will sever them)
        final java.util.List<Edge> severed = new java.util.ArrayList<>();
        for (Edge e : current.graph.edges()) {
            if (e.from.owner == node || e.to.owner == node) severed.add(e);
        }
        // Apply
        current.graph.removeNode(node.id());
        layouts.remove(node);
        if (current.nodesById != null) current.nodesById.remove(node.id().value);
        repaint();

        final java.util.Map<String, studio.graph.Node> nodesByIdRef =
                current.nodesById != null ? current.nodesById : null;
        paramTracker.untrack(node);
        undo.push(new studio.graph.UndoStack.Command() {
            @Override public void apply() {
                current.graph.removeNode(node.id());
                layouts.remove(node);
                if (nodesByIdRef != null) nodesByIdRef.remove(node.id().value);
                paramTracker.untrack(node);
                if (selected == node) setSelection(null);
                repaint();
            }
            @Override public void revert() {
                current.graph.addNode(node);
                if (nodesByIdRef != null) nodesByIdRef.put(node.id().value, node);
                if (pos != null) layouts.put(node, new Layout(posX, posY));
                paramTracker.track(node);
                for (Edge e : severed) {
                    try { current.graph.connect(e.from, e.to); }
                    catch (Exception ignored) { /* skip if endpoints disappeared */ }
                }
                repaint();
            }
            @Override public String description() { return "remove " + node.label(); }
        });
    }

    private void doAddNode(Node n, int x, int y) {
        final Node node = n;
        current.graph.addNode(node);
        if (current.nodesById != null) current.nodesById.put(node.id().value, node);
        layouts.put(node, new Layout(x, y));
        paramTracker.track(node);
        final java.util.Map<String, studio.graph.Node> nodesByIdRef =
                current.nodesById != null ? current.nodesById : null;
        undo.push(new studio.graph.UndoStack.Command() {
            @Override public void apply() {
                current.graph.addNode(node);
                if (nodesByIdRef != null) nodesByIdRef.put(node.id().value, node);
                layouts.put(node, new Layout(x, y));
                paramTracker.track(node);
                repaint();
            }
            @Override public void revert() {
                current.graph.removeNode(node.id());
                if (nodesByIdRef != null) nodesByIdRef.remove(node.id().value);
                layouts.remove(node);
                paramTracker.untrack(node);
                if (selected == node) setSelection(null);
                repaint();
            }
            @Override public String description() { return "add " + node.label(); }
        });
    }

    private void doConnect(OutputPort<?> from, InputPort<?> to) {
        // Replacement edges into the same input port? graph.connect handles that,
        // but for undo we need to remember any displaced edge.
        Edge displaced = null;
        for (Edge e : current.graph.edges()) if (e.to == to) { displaced = e; break; }
        final Edge prev = displaced;
        final Edge created;
        try {
            created = current.graph.connect(from, to);
        } catch (IllegalArgumentException ex) {
            if (statusBar != null) statusBar.error("Connect failed: " + ex.getMessage());
            return;
        }
        if (statusBar != null) {
            statusBar.info("Connected " + from.owner.label() + "." + from.name
                    + " → " + to.owner.label() + "." + to.name);
        }
        undo.push(new studio.graph.UndoStack.Command() {
            @Override public void apply() {
                current.graph.connect(from, to);
                repaint();
            }
            @Override public void revert() {
                current.graph.disconnect(created);
                if (prev != null) {
                    try { current.graph.connect(prev.from, prev.to); }
                    catch (Exception ignored) {}
                }
                repaint();
            }
            @Override public String description() {
                return "connect " + from.owner.label() + "." + from.name
                        + " → " + to.owner.label() + "." + to.name;
            }
        });
    }

    private void doDisconnect(Edge e) {
        final OutputPort<?> from = e.from;
        final InputPort<?>  to   = e.to;
        current.graph.disconnect(e);
        repaint();
        undo.push(new studio.graph.UndoStack.Command() {
            @Override public void apply() {
                Edge edge = null;
                for (Edge cand : current.graph.edges()) if (cand.from == from && cand.to == to) { edge = cand; break; }
                if (edge != null) current.graph.disconnect(edge);
                repaint();
            }
            @Override public void revert() {
                try { current.graph.connect(from, to); }
                catch (Exception ignored) {}
                repaint();
            }
            @Override public String description() {
                return "disconnect " + from.owner.label() + "." + from.name
                        + " → " + to.owner.label() + "." + to.name;
            }
        });
    }

    private void finishMarquee(boolean additive) {
        if (current == null || marqueeStart == null || marqueeCurrent == null) return;
        double mx = Math.min(marqueeStart.getX(), marqueeCurrent.getX());
        double my = Math.min(marqueeStart.getY(), marqueeCurrent.getY());
        double mxe = Math.max(marqueeStart.getX(), marqueeCurrent.getX());
        double mye = Math.max(marqueeStart.getY(), marqueeCurrent.getY());
        if ((mxe - mx) < 4 && (mye - my) < 4) return; // treat as click, not marquee

        java.util.List<Node> picked = new java.util.ArrayList<>();
        for (Node n : current.graph.nodes()) {
            Layout L = layoutOf(n);
            int h = nodeHeight(n), w = nodeWidth(n);
            // intersection test against rect [mx..mxe] x [my..mye]
            if (L.x + w >= mx && L.x <= mxe && L.y + h >= my && L.y <= mye) {
                picked.add(n);
            }
        }
        if (additive) {
            for (Node n : picked) {
                if (selected == null) {
                    selected = n;
                    for (Consumer<Node> l : selectionListeners) l.accept(n);
                } else if (n != selected) {
                    secondary.add(n);
                }
            }
        } else {
            setMultiSelection(picked);
        }
    }

    private void pushGroupMove() {
        if (dragStartPositions.isEmpty()) return;
        // Snapshot the after-positions
        final java.util.Map<Node, int[]> before = new java.util.IdentityHashMap<>(dragStartPositions);
        final java.util.Map<Node, int[]> after = new java.util.IdentityHashMap<>();
        boolean anyMoved = false;
        for (var entry : before.entrySet()) {
            Layout L = layouts.get(entry.getKey());
            if (L == null) continue;
            after.put(entry.getKey(), new int[]{ L.x, L.y });
            int[] start = entry.getValue();
            if (L.x != start[0] || L.y != start[1]) anyMoved = true;
        }
        if (!anyMoved) return;

        undo.push(new studio.graph.UndoStack.Command() {
            @Override public void apply() {
                for (var e : after.entrySet()) {
                    int[] pos = e.getValue();
                    layouts.put(e.getKey(), new Layout(pos[0], pos[1]));
                }
                repaint();
            }
            @Override public void revert() {
                for (var e : before.entrySet()) {
                    int[] pos = e.getValue();
                    layouts.put(e.getKey(), new Layout(pos[0], pos[1]));
                }
                repaint();
            }
            @Override public String description() {
                return after.size() == 1 ? "move " + after.keySet().iterator().next().label()
                        : "move " + after.size() + " nodes";
            }
        });
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    public void copySelectedToClipboard() {
        if (selected == null) return;
        java.util.List<Node> sources = allSelected();
        java.util.List<studio.save.PflowJson.NodeJson> payload = new java.util.ArrayList<>();
        for (Node n : sources) {
            studio.save.PflowJson.NodeJson nj = new studio.save.PflowJson.NodeJson();
            nj.typeId = n.typeId();
            nj.label = n.label();
            if (!n.isEnabled()) nj.enabled = Boolean.FALSE;
            Layout L = layouts.get(n);
            if (L != null) nj.layout = new studio.save.PflowJson.Layout(L.x, L.y);
            for (studio.graph.Parameter<?> p : n.parameters()) {
                Object v = p.get();
                if (v instanceof float[] arr) {
                    java.util.ArrayList<Float> list = new java.util.ArrayList<>(arr.length);
                    for (float f : arr) list.add(f);
                    nj.params.put(p.name, list);
                } else if (v != null) {
                    nj.params.put(p.name, v);
                }
            }
            payload.add(nj);
        }
        try {
            String json = studio.save.JsonCodec.writeString(payload);
            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(
                    CLIPBOARD_MULTI_MARKER + json);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            if (statusBar != null) {
                statusBar.info(sources.size() == 1
                        ? "Copied " + selected.label() + " to clipboard"
                        : "Copied " + sources.size() + " nodes to clipboard");
            }
        } catch (Exception ex) {
            if (statusBar != null) statusBar.error("Copy failed: " + ex.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void pasteFromClipboard() {
        if (current == null) return;
        try {
            Object data = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            if (!(data instanceof String s)) return;
            java.util.List<studio.save.PflowJson.NodeJson> payload = new java.util.ArrayList<>();
            if (s.startsWith(CLIPBOARD_MULTI_MARKER)) {
                studio.save.PflowJson.NodeJson[] arr = studio.save.JsonCodec.readString(
                        s.substring(CLIPBOARD_MULTI_MARKER.length()),
                        studio.save.PflowJson.NodeJson[].class);
                java.util.Collections.addAll(payload, arr);
            } else if (s.startsWith(CLIPBOARD_MARKER)) {
                studio.save.PflowJson.NodeJson nj = studio.save.JsonCodec.readString(
                        s.substring(CLIPBOARD_MARKER.length()),
                        studio.save.PflowJson.NodeJson.class);
                payload.add(nj);
            } else {
                return;
            }
            if (payload.isEmpty()) return;

            // Compute origin relative to the selection's primary so a multi-paste
            // preserves the relative arrangement of the copied nodes.
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            for (var nj : payload) {
                if (nj.layout != null && nj.layout.x != null && nj.layout.y != null) {
                    minX = Math.min(minX, nj.layout.x);
                    minY = Math.min(minY, nj.layout.y);
                }
            }
            if (minX == Integer.MAX_VALUE) { minX = 0; minY = 0; }
            int anchorX = LAYOUT_ORIGIN_X, anchorY = LAYOUT_ORIGIN_Y;
            if (selected != null) {
                Layout L = layoutOf(selected);
                anchorX = L.x + 24;
                anchorY = L.y + 24;
            }
            java.util.List<Node> created = new java.util.ArrayList<>();
            for (var nj : payload) {
                Node node = registry.create(nj.typeId);
                if (nj.label != null) node.setLabel(nj.label);
                if (nj.enabled != null) node.setEnabled(nj.enabled);
                if (nj.params != null) {
                    for (studio.graph.Parameter<?> p : node.parameters()) {
                        Object raw = nj.params.get(p.name);
                        if (raw == null) continue;
                        Object coerced = studio.save.ParamCoercion.coerce(p, raw);
                        if (coerced != null) ((studio.graph.Parameter) p).set(coerced);
                    }
                }
                int x, y;
                if (nj.layout != null && nj.layout.x != null && nj.layout.y != null) {
                    x = anchorX + (nj.layout.x - minX);
                    y = anchorY + (nj.layout.y - minY);
                } else {
                    x = anchorX; y = anchorY;
                }
                doAddNode(node, x, y);
                created.add(node);
            }
            setMultiSelection(created);
            if (statusBar != null) {
                statusBar.info(created.size() == 1
                        ? "Pasted " + created.get(0).label()
                        : "Pasted " + created.size() + " nodes");
            }
        } catch (Exception ex) {
            if (statusBar != null) statusBar.error("Paste failed: " + ex.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void duplicateSelected() {
        if (selected == null || current == null) return;
        java.util.List<Node> sources = allSelected();
        java.util.List<Node> copies = new java.util.ArrayList<>();
        for (Node src : sources) {
            Node copy = registry.create(src.typeId());
            copy.setLabel(src.label() + " copy");
            copy.setEnabled(src.isEnabled());
            var srcParams = src.parameters();
            for (int i = 0; i < srcParams.size(); i++) {
                studio.graph.Parameter sp = srcParams.get(i);
                studio.graph.Parameter cp = (studio.graph.Parameter) copy.parameter(sp.name);
                if (cp != null) cp.set(sp.get());
            }
            Layout L = layoutOf(src);
            doAddNode(copy, L.x + 24, L.y + 24);
            copies.add(copy);
        }
        setMultiSelection(copies);
        if (statusBar != null) {
            statusBar.info(sources.size() == 1
                    ? "Duplicated " + sources.get(0).label()
                    : "Duplicated " + sources.size() + " nodes");
        }
    }

    /** Read-only view of node→(x,y) so the writer can persist layout. */
    public java.util.Map<studio.graph.NodeId, int[]> exportLayout() {
        java.util.LinkedHashMap<studio.graph.NodeId, int[]> out = new java.util.LinkedHashMap<>();
        for (var e : layouts.entrySet()) {
            out.put(e.getKey().id(), new int[]{ e.getValue().x, e.getValue().y });
        }
        return out;
    }

    public void addSelectionListener(Consumer<Node> listener) { selectionListeners.add(listener); }

    public Node selectedNode() { return selected; }

    /** Primary + secondary selection (the union the user thinks of as "selected"). */
    public java.util.List<Node> allSelected() {
        if (selected == null) return java.util.List.of();
        if (secondary.isEmpty()) return java.util.List.of(selected);
        java.util.List<Node> out = new java.util.ArrayList<>(secondary.size() + 1);
        out.add(selected);
        for (Node n : secondary) if (n != selected) out.add(n);
        return out;
    }

    private boolean isInSelection(Node n) {
        return n == selected || secondary.contains(n);
    }

    /* ------------------------------ Layout ------------------------------ */

    /** Auto-position nodes left-to-right by topo depth, stacking peers vertically. */
    private void autoLayout(PflowReader.Result loaded) {
        layouts.clear();
        List<Node> order = loaded.graph.topology();
        Map<Node, Integer> depth = new IdentityHashMap<>();
        for (Node n : order) {
            int d = 0;
            for (var in : n.inputs()) {
                Edge e = loaded.graph.edgeInto(in);
                if (e != null) d = Math.max(d, depth.getOrDefault(e.from.owner, 0) + 1);
            }
            depth.put(n, d);
        }
        Map<Integer, Integer> colCount = new HashMap<>();
        for (Node n : order) {
            int d = depth.get(n);
            int row = colCount.merge(d, 1, Integer::sum) - 1;
            int x = LAYOUT_ORIGIN_X + d * (NODE_WIDTH + LAYOUT_X_GAP);
            int y = LAYOUT_ORIGIN_Y + row * (nodeHeight(n) + LAYOUT_Y_GAP);
            layouts.put(n, new Layout(x, y));
        }
    }

    private int nodeHeight(Node n) {
        if (n.typeId().equals(studio.nodes.builtin.NoteNode.TYPE_ID)) {
            studio.graph.Parameter<?> p = n.parameter("text");
            String text = p == null || p.get() == null ? "" : p.get().toString();
            int lines = Math.max(1, text.split("\\R").length);
            return Math.max(NODE_HEADER, 12 + lines * NOTE_LINE);
        }
        int rows = Math.max(n.inputs().size(), n.outputs().size());
        return NODE_HEADER + Math.max(2, rows) * ROW_HEIGHT + 8;
    }

    private int nodeWidth(Node n) {
        return n.typeId().equals(studio.nodes.builtin.NoteNode.TYPE_ID) ? NOTE_WIDTH : NODE_WIDTH;
    }

    private Layout layoutOf(Node n) {
        return layouts.computeIfAbsent(n, key -> new Layout(LAYOUT_ORIGIN_X, LAYOUT_ORIGIN_Y));
    }

    /* ------------------------------ Painting ------------------------------ */

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (current == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform original = g.getTransform();
        g.translate(panX, panY);
        g.scale(zoom, zoom);

        drawGrid(g);
        for (Edge e : current.graph.edges()) drawEdge(g, e);
        for (Node n : current.graph.nodes()) drawNode(g, n);

        if (marqueeStart != null && marqueeCurrent != null) {
            double mx = Math.min(marqueeStart.getX(), marqueeCurrent.getX());
            double my = Math.min(marqueeStart.getY(), marqueeCurrent.getY());
            double mw = Math.abs(marqueeCurrent.getX() - marqueeStart.getX());
            double mh = Math.abs(marqueeCurrent.getY() - marqueeStart.getY());
            g.setColor(new Color(255, 196, 64, 30));
            g.fillRect((int) mx, (int) my, (int) mw, (int) mh);
            g.setColor(new Color(255, 196, 64, 180));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{ 6f, 4f }, 0f));
            g.drawRect((int) mx, (int) my, (int) mw, (int) mh);
        }

        if (wireFromPort != null && wireDragCurrent != null) {
            Point2D from = outputPortPos(wireFromPort);
            if (from != null) {
                double dx = Math.max(40, (wireDragCurrent.getX() - from.getX()) * 0.5);
                CubicCurve2D pending = new CubicCurve2D.Double(
                        from.getX(), from.getY(),
                        from.getX() + dx, from.getY(),
                        wireDragCurrent.getX() - dx, wireDragCurrent.getY(),
                        wireDragCurrent.getX(), wireDragCurrent.getY());
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{ 8f, 6f }, 0f));
                g.setColor(new Color(255, 196, 64, 220));
                g.draw(pending);
            }
        }

        g.setTransform(original);

        drawMinimap(g);

        g.dispose();
    }

    private static final int MINIMAP_W = 160;
    private static final int MINIMAP_H = 100;
    private static final int MINIMAP_PAD = 10;
    private boolean showMinimap = true;

    private static final int SNAP_GRID = 16;
    private boolean snapToGrid = true;

    public boolean isSnapToGrid() { return snapToGrid; }
    public void setSnapToGrid(boolean on) { this.snapToGrid = on; }

    private void drawMinimap(Graphics2D g) {
        if (!showMinimap || current == null || current.graph.nodes().isEmpty()) return;
        int px = getWidth() - MINIMAP_W - MINIMAP_PAD;
        int py = getHeight() - MINIMAP_H - MINIMAP_PAD;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(px - 4, py - 4, MINIMAP_W + 8, MINIMAP_H + 8, 8, 8);
        g.setColor(new Color(30, 30, 36));
        g.fillRect(px, py, MINIMAP_W, MINIMAP_H);

        // Compute graph bbox in world space
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Node n : current.graph.nodes()) {
            Layout L = layoutOf(n);
            int h = nodeHeight(n);
            minX = Math.min(minX, L.x);
            minY = Math.min(minY, L.y);
            maxX = Math.max(maxX, L.x + NODE_WIDTH);
            maxY = Math.max(maxY, L.y + h);
        }
        int worldW = Math.max(1, maxX - minX);
        int worldH = Math.max(1, maxY - minY);
        double sx = (double) MINIMAP_W / worldW;
        double sy = (double) MINIMAP_H / worldH;
        double s = Math.min(sx, sy) * 0.9;
        double offX = px + (MINIMAP_W - worldW * s) / 2.0 - minX * s;
        double offY = py + (MINIMAP_H - worldH * s) / 2.0 - minY * s;

        for (Node n : current.graph.nodes()) {
            Layout L = layoutOf(n);
            int h = nodeHeight(n);
            int rx = (int) Math.round(offX + L.x * s);
            int ry = (int) Math.round(offY + L.y * s);
            int rw = Math.max(2, (int) Math.round(NODE_WIDTH * s));
            int rh = Math.max(2, (int) Math.round(h * s));
            g.setColor(n == selected ? new Color(255, 196, 64) : new Color(120, 160, 200));
            g.fillRect(rx, ry, rw, rh);
        }

        // Viewport indicator (current pan/zoom view in world space)
        double viewWorldW = getWidth() / zoom;
        double viewWorldH = getHeight() / zoom;
        double viewWorldX = -panX / zoom;
        double viewWorldY = -panY / zoom;
        int vx = (int) Math.round(offX + viewWorldX * s);
        int vy = (int) Math.round(offY + viewWorldY * s);
        int vw = (int) Math.round(viewWorldW * s);
        int vh = (int) Math.round(viewWorldH * s);
        g.setColor(new Color(255, 255, 255, 80));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(vx, vy, vw, vh);
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(40, 40, 48));
        g.setStroke(new BasicStroke(1f));
        int spacing = 32;
        int w = Math.max(getWidth() * 4, 4000);
        int h = Math.max(getHeight() * 4, 4000);
        int x0 = -w / 2;
        int y0 = -h / 2;
        for (int x = x0; x <= w; x += spacing) g.drawLine(x, y0, x, y0 + h);
        for (int y = y0; y <= h; y += spacing) g.drawLine(x0, y, x0 + w, y);
    }

    private void drawNode(Graphics2D g, Node n) {
        if (n.typeId().equals(studio.nodes.builtin.NoteNode.TYPE_ID)) {
            drawNote(g, n);
            return;
        }
        Layout L = layoutOf(n);
        int height = nodeHeight(n);
        boolean disabled = !n.isEnabled();
        RoundRectangle2D body = new RoundRectangle2D.Double(L.x, L.y, NODE_WIDTH, height, 8, 8);

        // Selection halo (primary brighter than secondary)
        if (isInSelection(n)) {
            g.setColor(n == selected ? new Color(255, 196, 64, 200) : new Color(255, 196, 64, 130));
            g.setStroke(new BasicStroke(n == selected ? 3f : 2.2f));
            g.draw(new RoundRectangle2D.Double(L.x - 2, L.y - 2, NODE_WIDTH + 4, height + 4, 10, 10));
        }

        // Body
        g.setColor(disabled ? new Color(40, 40, 48) : new Color(52, 52, 64));
        g.fill(body);
        g.setColor(disabled ? new Color(70, 70, 80) : new Color(80, 80, 96));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(body);

        // Header
        RoundRectangle2D header = new RoundRectangle2D.Double(L.x, L.y, NODE_WIDTH, NODE_HEADER, 8, 8);
        Color hc = headerColor(n);
        g.setColor(disabled
                ? new Color(hc.getRed() / 3, hc.getGreen() / 3, hc.getBlue() / 3)
                : hc);
        g.fill(header);
        g.setColor(disabled ? new Color(130, 130, 140) : new Color(220, 220, 230));
        String headerLabel = (disabled ? "○ " : "") + n.label();
        g.drawString(headerLabel, L.x + 8, L.y + 15);

        // Inputs (left)
        List<InputPort<?>> inputs = n.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            InputPort<?> ip = inputs.get(i);
            int py = L.y + NODE_HEADER + ROW_HEIGHT / 2 + i * ROW_HEIGHT;
            drawPort(g, L.x, py, portColor(ip.type));
            g.setColor(new Color(180, 180, 190));
            g.drawString(ip.name, L.x + 12, py + 4);
        }

        // Outputs (right)
        List<OutputPort<?>> outputs = n.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            OutputPort<?> op = outputs.get(i);
            int py = L.y + NODE_HEADER + ROW_HEIGHT / 2 + i * ROW_HEIGHT;
            drawPort(g, L.x + NODE_WIDTH, py, portColor(op.type));
            g.setColor(new Color(180, 180, 190));
            String label = op.name;
            int sw = g.getFontMetrics().stringWidth(label);
            g.drawString(label, L.x + NODE_WIDTH - 12 - sw, py + 4);
        }
    }

    private static final int NOTE_WIDTH = 220;
    private static final int NOTE_LINE = 16;

    private void drawNote(Graphics2D g, Node n) {
        Layout L = layoutOf(n);
        studio.graph.Parameter<?> p = n.parameter("text");
        String text = p == null || p.get() == null ? "" : p.get().toString();
        String[] lines = text.isEmpty() ? new String[]{"(empty note)"} : text.split("\\R");
        int height = Math.max(NODE_HEADER, 12 + lines.length * NOTE_LINE);
        RoundRectangle2D body = new RoundRectangle2D.Double(L.x, L.y, NOTE_WIDTH, height, 6, 6);

        if (isInSelection(n)) {
            g.setColor(n == selected ? new Color(255, 196, 64, 200) : new Color(255, 196, 64, 130));
            g.setStroke(new BasicStroke(n == selected ? 3f : 2.2f));
            g.draw(new RoundRectangle2D.Double(L.x - 2, L.y - 2, NOTE_WIDTH + 4, height + 4, 8, 8));
        }

        g.setColor(new Color(218, 200, 110));
        g.fill(body);
        g.setColor(new Color(150, 130, 60));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(body);

        g.setColor(new Color(40, 30, 10));
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], L.x + 10, L.y + 18 + i * NOTE_LINE);
        }
    }

    private Color portColor(studio.graph.PortType<?> type) {
        if (type == studio.graph.PortTypes.TEXTURE2D) return new Color(120, 200, 200);
        if (type == studio.graph.PortTypes.SCALAR
         || type == studio.graph.PortTypes.INT)      return new Color(220, 200, 100);
        if (type == studio.graph.PortTypes.BOOL)      return new Color(180, 130, 200);
        if (type == studio.graph.PortTypes.VEC2
         || type == studio.graph.PortTypes.VEC3
         || type == studio.graph.PortTypes.VEC4)     return new Color(120, 160, 220);
        return new Color(160, 160, 170);
    }

    private Color headerColor(Node n) {
        // Category-coded by typeId prefix
        if (n.typeId().startsWith("pf.fluid"))  return new Color(64, 110, 160);
        if (n.typeId().startsWith("pf.filter")) return new Color(140, 90, 60);
        if (n.typeId().startsWith("pf.flow"))   return new Color(70, 130, 110);
        if (n.typeId().startsWith("studio.builtin")) return new Color(90, 90, 110);
        return new Color(80, 80, 100);
    }

    private void drawPort(Graphics2D g, int cx, int cy, Color fill) {
        g.setColor(fill);
        g.fill(new Ellipse2D.Double(cx - PORT_RADIUS, cy - PORT_RADIUS,
                PORT_RADIUS * 2, PORT_RADIUS * 2));
        g.setColor(fill.darker());
        g.draw(new Ellipse2D.Double(cx - PORT_RADIUS, cy - PORT_RADIUS,
                PORT_RADIUS * 2, PORT_RADIUS * 2));
    }

    private void drawEdge(Graphics2D g, Edge e) {
        Point2D from = outputPortPos(e.from);
        Point2D to   = inputPortPos(e.to);
        if (from == null || to == null) return;
        double dx = Math.max(40, (to.getX() - from.getX()) * 0.5);
        CubicCurve2D curve = new CubicCurve2D.Double(
                from.getX(), from.getY(),
                from.getX() + dx, from.getY(),
                to.getX() - dx, to.getY(),
                to.getX(), to.getY());
        boolean connectedToSel =
                (selected != null && (e.from.owner == selected || e.to.owner == selected))
                || (!secondary.isEmpty()
                        && (secondary.contains(e.from.owner) || secondary.contains(e.to.owner)));
        if (connectedToSel) {
            g.setStroke(new BasicStroke(3.2f));
            g.setColor(new Color(255, 196, 64, 220));
        } else {
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(180, 200, 220, 200));
        }
        g.draw(curve);
    }

    private Point2D outputPortPos(OutputPort<?> p) {
        Layout L = layoutOf(p.owner);
        int idx = p.owner.outputs().indexOf(p);
        if (idx < 0) return null;
        int py = L.y + NODE_HEADER + ROW_HEIGHT / 2 + idx * ROW_HEIGHT;
        return new Point2D.Double(L.x + NODE_WIDTH, py);
    }

    private Point2D inputPortPos(InputPort<?> p) {
        Layout L = layoutOf(p.owner);
        int idx = p.owner.inputs().indexOf(p);
        if (idx < 0) return null;
        int py = L.y + NODE_HEADER + ROW_HEIGHT / 2 + idx * ROW_HEIGHT;
        return new Point2D.Double(L.x, py);
    }

    /* ------------------------------ Mouse ------------------------------ */

    private Node hitTest(Point screen) {
        if (current == null) return null;
        Point2D world = screenToWorld(screen);
        // Iterate in reverse so the most recently-added node wins overlaps.
        List<Node> nodes = current.graph.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            Layout L = layoutOf(n);
            int h = nodeHeight(n);
            int w = nodeWidth(n);
            if (world.getX() >= L.x && world.getX() <= L.x + w
                    && world.getY() >= L.y && world.getY() <= L.y + h) {
                return n;
            }
        }
        return null;
    }

    private OutputPort<?> hitOutputPort(Point screen) {
        if (current == null) return null;
        Point2D world = screenToWorld(screen);
        for (Node n : current.graph.nodes()) {
            for (OutputPort<?> p : n.outputs()) {
                Point2D pos = outputPortPos(p);
                if (pos != null && pos.distance(world) <= PORT_RADIUS + 2) return p;
            }
        }
        return null;
    }

    private InputPort<?> hitInputPort(Point screen) {
        if (current == null) return null;
        Point2D world = screenToWorld(screen);
        for (Node n : current.graph.nodes()) {
            for (InputPort<?> p : n.inputs()) {
                Point2D pos = inputPortPos(p);
                if (pos != null && pos.distance(world) <= PORT_RADIUS + 2) return p;
            }
        }
        return null;
    }

    private Edge hitEdge(Point screen) {
        if (current == null) return null;
        Point2D world = screenToWorld(screen);
        Edge best = null;
        double bestDist = 10.0;
        for (Edge e : current.graph.edges()) {
            Point2D from = outputPortPos(e.from);
            Point2D to   = inputPortPos(e.to);
            if (from == null || to == null) continue;
            double dx = Math.max(40, (to.getX() - from.getX()) * 0.5);
            double[] ctrl = {
                    from.getX(), from.getY(),
                    from.getX() + dx, from.getY(),
                    to.getX() - dx, to.getY(),
                    to.getX(), to.getY()
            };
            for (int i = 0; i <= 16; i++) {
                double t = i / 16.0;
                double x = bezier(ctrl[0], ctrl[2], ctrl[4], ctrl[6], t);
                double y = bezier(ctrl[1], ctrl[3], ctrl[5], ctrl[7], t);
                double d = world.distance(x, y);
                if (d < bestDist) { bestDist = d; best = e; }
            }
        }
        return best;
    }

    private static double bezier(double p0, double p1, double p2, double p3, double t) {
        double mt = 1 - t;
        return mt * mt * mt * p0 + 3 * mt * mt * t * p1 + 3 * mt * t * t * p2 + t * t * t * p3;
    }

    private Point2D screenToWorld(Point p) {
        return new Point2D.Double((p.x - panX) / zoom, (p.y - panY) / zoom);
    }

    private void setSelection(Node n) {
        if (n == selected && secondary.isEmpty()) return;
        selected = n;
        secondary.clear();
        for (Consumer<Node> l : selectionListeners) l.accept(n);
        repaint();
    }

    /** Toggle n in/out of the secondary set without touching the primary. */
    private void toggleSecondary(Node n) {
        if (n == null) return;
        if (n == selected) return;
        if (!secondary.add(n)) secondary.remove(n);
        repaint();
    }

    /** Replace the entire selection with the given set; first item becomes primary. */
    public void setMultiSelection(java.util.Collection<Node> nodes) {
        secondary.clear();
        Node first = null;
        for (Node n : nodes) {
            if (first == null) first = n;
            else secondary.add(n);
        }
        selected = first;
        for (Consumer<Node> l : selectionListeners) l.accept(selected);
        repaint();
    }

    private final class MouseHandler extends MouseAdapter implements MouseWheelListener {

        @Override public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (e.getButton() == MouseEvent.BUTTON2) {
                dragPanStart = e.getPoint();
                dragPanStartX = panX;
                dragPanStartY = panY;
                return;
            }
            if (e.getButton() == MouseEvent.BUTTON3) {
                Edge hit = hitEdge(e.getPoint());
                if (hit != null && current != null) {
                    doDisconnect(hit);
                }
                return;
            }
            OutputPort<?> op = hitOutputPort(e.getPoint());
            if (op != null && e.getButton() == MouseEvent.BUTTON1) {
                wireFromPort = op;
                wireDragCurrent = screenToWorld(e.getPoint());
                repaint();
                return;
            }
            Node hit = hitTest(e.getPoint());
            if (e.isShiftDown() && hit != null) {
                // Shift-click adds/removes the node from the secondary set
                // without disturbing the primary, so multi-select grows.
                if (selected == null) {
                    selected = hit;
                    for (Consumer<Node> l : selectionListeners) l.accept(hit);
                } else {
                    toggleSecondary(hit);
                }
                repaint();
            } else if (hit != null) {
                if (!isInSelection(hit)) setSelection(hit);
                else selected = hit; // keep multi-set; promote to primary
            } else {
                // Empty space — start marquee
                if (!e.isShiftDown()) setSelection(null);
                if (e.getButton() == MouseEvent.BUTTON1) {
                    marqueeStart = screenToWorld(e.getPoint());
                    marqueeCurrent = marqueeStart;
                }
            }
            if (hit != null && e.getButton() == MouseEvent.BUTTON1 && !e.isShiftDown()) {
                draggingNode = hit;
                Point2D world = screenToWorld(e.getPoint());
                Layout L = layoutOf(hit);
                dragNodeOffset = new Point2D.Double(world.getX() - L.x, world.getY() - L.y);
                // Snapshot positions of every selected node for group move
                dragStartPositions.clear();
                for (Node n : allSelected()) {
                    Layout p = layouts.get(n);
                    if (p != null) dragStartPositions.put(n, new int[]{ p.x, p.y });
                }
            }
        }

        @Override public void mouseReleased(MouseEvent e) {
            if (wireFromPort != null) {
                InputPort<?> dst = hitInputPort(e.getPoint());
                if (dst != null && current != null) {
                    doConnect(wireFromPort, dst);
                }
                wireFromPort = null;
                wireDragCurrent = null;
                repaint();
            }
            if (marqueeStart != null && marqueeCurrent != null) {
                finishMarquee(e.isShiftDown());
                marqueeStart = null;
                marqueeCurrent = null;
                repaint();
            }
            if (draggingNode != null) {
                // Push a multi-move command covering every selected node that actually moved.
                pushGroupMove();
                dragStartPositions.clear();
            }
            draggingNode = null;
            dragPanStart = null;
        }

        @Override public void mouseDragged(MouseEvent e) {
            if (wireFromPort != null) {
                wireDragCurrent = screenToWorld(e.getPoint());
                repaint();
                return;
            }
            if (marqueeStart != null) {
                marqueeCurrent = screenToWorld(e.getPoint());
                repaint();
                return;
            }
            if (draggingNode != null) {
                Point2D world = screenToWorld(e.getPoint());
                Layout L = layoutOf(draggingNode);
                int nx = (int) Math.round(world.getX() - dragNodeOffset.getX());
                int ny = (int) Math.round(world.getY() - dragNodeOffset.getY());
                if (snapToGrid) {
                    nx = Math.round((float) nx / SNAP_GRID) * SNAP_GRID;
                    ny = Math.round((float) ny / SNAP_GRID) * SNAP_GRID;
                }
                int dx = nx - L.x;
                int dy = ny - L.y;
                // Move every selected node by the same delta
                for (Node n : allSelected()) {
                    Layout p = layouts.get(n);
                    if (p != null) { p.x += dx; p.y += dy; }
                }
                repaint();
            } else if (dragPanStart != null) {
                panX = dragPanStartX + (e.getX() - dragPanStart.x);
                panY = dragPanStartY + (e.getY() - dragPanStart.y);
                repaint();
            }
        }

        @Override public void mouseMoved(MouseEvent e) {
            OutputPort<?> op = hitOutputPort(e.getPoint());
            if (op != null) {
                setToolTipText("output " + op.owner.label() + "." + op.name + " : " + op.type.id);
                return;
            }
            InputPort<?> ip = hitInputPort(e.getPoint());
            if (ip != null) {
                setToolTipText("input " + ip.owner.label() + "." + ip.name + " : " + ip.type.id);
                return;
            }
            Node n = hitTest(e.getPoint());
            if (n != null) {
                setToolTipText(n.label() + "  —  " + n.typeId());
                return;
            }
            setToolTipText(null);
        }

        @Override public void mouseWheelMoved(MouseWheelEvent e) {
            double prevZoom = zoom;
            double factor = e.getPreciseWheelRotation() < 0 ? 1.1 : 1 / 1.1;
            double newZoom = Math.max(0.2, Math.min(4.0, zoom * factor));
            // Keep the cursor anchor stable in world space.
            double wx = (e.getX() - panX) / prevZoom;
            double wy = (e.getY() - panY) / prevZoom;
            zoom = newZoom;
            panX = e.getX() - wx * zoom;
            panY = e.getY() - wy * zoom;
            repaint();
        }
    }

    /** Per-node position in graph space. Mutable so dragging works in place. */
    private static final class Layout {
        int x, y;
        Layout(int x, int y) { this.x = x; this.y = y; }
    }
}
