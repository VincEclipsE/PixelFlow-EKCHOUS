package studio.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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
    private Node selected;

    // Viewport transform
    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;

    // Drag state
    private Node draggingNode;
    private Point2D dragNodeOffset;
    private Point dragPanStart;
    private double dragPanStartX, dragPanStartY;

    public NodeEditorPanel() {
        setBackground(new Color(20, 20, 24));
        setOpaque(true);
        setPreferredSize(new Dimension(800, 600));

        MouseHandler m = new MouseHandler();
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    public void attachGraph(PflowReader.Result loaded) {
        this.current = loaded;
        this.selected = null;
        autoLayout(loaded);
        repaint();
    }

    public void addSelectionListener(Consumer<Node> listener) { selectionListeners.add(listener); }

    public Node selectedNode() { return selected; }

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
        int rows = Math.max(n.inputs().size(), n.outputs().size());
        return NODE_HEADER + Math.max(2, rows) * ROW_HEIGHT + 8;
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

        g.setTransform(original);
        g.dispose();
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
        Layout L = layoutOf(n);
        int height = nodeHeight(n);
        RoundRectangle2D body = new RoundRectangle2D.Double(L.x, L.y, NODE_WIDTH, height, 8, 8);

        // Selection halo
        if (n == selected) {
            g.setColor(new Color(255, 196, 64, 180));
            g.setStroke(new BasicStroke(3f));
            g.draw(new RoundRectangle2D.Double(L.x - 2, L.y - 2, NODE_WIDTH + 4, height + 4, 10, 10));
        }

        // Body
        g.setColor(new Color(52, 52, 64));
        g.fill(body);
        g.setColor(new Color(80, 80, 96));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(body);

        // Header
        RoundRectangle2D header = new RoundRectangle2D.Double(L.x, L.y, NODE_WIDTH, NODE_HEADER, 8, 8);
        g.setColor(headerColor(n));
        g.fill(header);
        g.setColor(new Color(220, 220, 230));
        g.drawString(n.label(), L.x + 8, L.y + 15);

        // Inputs (left)
        List<InputPort<?>> inputs = n.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            int py = L.y + NODE_HEADER + ROW_HEIGHT / 2 + i * ROW_HEIGHT;
            drawPort(g, L.x, py, false);
            g.setColor(new Color(180, 180, 190));
            g.drawString(inputs.get(i).name, L.x + 12, py + 4);
        }

        // Outputs (right)
        List<OutputPort<?>> outputs = n.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            int py = L.y + NODE_HEADER + ROW_HEIGHT / 2 + i * ROW_HEIGHT;
            drawPort(g, L.x + NODE_WIDTH, py, true);
            g.setColor(new Color(180, 180, 190));
            String label = outputs.get(i).name;
            int sw = g.getFontMetrics().stringWidth(label);
            g.drawString(label, L.x + NODE_WIDTH - 12 - sw, py + 4);
        }
    }

    private Color headerColor(Node n) {
        // Category-coded by typeId prefix
        if (n.typeId().startsWith("pf.fluid"))  return new Color(64, 110, 160);
        if (n.typeId().startsWith("pf.filter")) return new Color(140, 90, 60);
        if (n.typeId().startsWith("pf.flow"))   return new Color(70, 130, 110);
        if (n.typeId().startsWith("studio.builtin")) return new Color(90, 90, 110);
        return new Color(80, 80, 100);
    }

    private void drawPort(Graphics2D g, int cx, int cy, boolean output) {
        g.setColor(new Color(120, 200, 200));
        g.fill(new Ellipse2D.Double(cx - PORT_RADIUS, cy - PORT_RADIUS,
                PORT_RADIUS * 2, PORT_RADIUS * 2));
        g.setColor(new Color(60, 120, 120));
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
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(180, 200, 220, 200));
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
            if (world.getX() >= L.x && world.getX() <= L.x + NODE_WIDTH
                    && world.getY() >= L.y && world.getY() <= L.y + h) {
                return n;
            }
        }
        return null;
    }

    private Point2D screenToWorld(Point p) {
        return new Point2D.Double((p.x - panX) / zoom, (p.y - panY) / zoom);
    }

    private void setSelection(Node n) {
        if (n == selected) return;
        selected = n;
        for (Consumer<Node> l : selectionListeners) l.accept(n);
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
            Node hit = hitTest(e.getPoint());
            setSelection(hit);
            if (hit != null && e.getButton() == MouseEvent.BUTTON1) {
                draggingNode = hit;
                Point2D world = screenToWorld(e.getPoint());
                Layout L = layoutOf(hit);
                dragNodeOffset = new Point2D.Double(world.getX() - L.x, world.getY() - L.y);
            }
        }

        @Override public void mouseReleased(MouseEvent e) {
            draggingNode = null;
            dragPanStart = null;
        }

        @Override public void mouseDragged(MouseEvent e) {
            if (draggingNode != null) {
                Point2D world = screenToWorld(e.getPoint());
                Layout L = layoutOf(draggingNode);
                L.x = (int) Math.round(world.getX() - dragNodeOffset.getX());
                L.y = (int) Math.round(world.getY() - dragNodeOffset.getY());
                repaint();
            } else if (dragPanStart != null) {
                panX = dragPanStartX + (e.getX() - dragPanStart.x);
                panY = dragPanStartY + (e.getY() - dragPanStart.y);
                repaint();
            }
        }

        // Plain MouseAdapter.mouseMoved is unused here.
        @Override public void mouseMoved(MouseEvent e) {}

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
