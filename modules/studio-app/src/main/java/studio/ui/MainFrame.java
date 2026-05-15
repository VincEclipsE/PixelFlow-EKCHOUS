package studio.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import studio.graph.NodeFactoryRegistry;
import studio.headless.HeadlessSmoke;
import studio.save.PflowReader;
import studio.save.ToolsLibrary;

/**
 * Top-level Studio window. M3 v1 layout:
 *
 * <pre>
 *  ┌───────────────────────────────────────────────────────────┐
 *  │ File  Help                                                │
 *  ├───────────┬─────────────────────────────┬────────────────┤
 *  │  Palette  │       Live preview (GL)     │  Parameters    │
 *  │ (typeId   │                             │  (auto-gen     │
 *  │  list)    │                             │   widgets for  │
 *  │           │                             │   selected     │
 *  │           │                             │   node)        │
 *  └───────────┴─────────────────────────────┴────────────────┘
 * </pre>
 *
 * <p>Node-editor canvas, tool composition, and Save-as-Tool wizard are
 * deferred to M3.x. M3 v1 proves the shell, the embedded GL preview, and
 * the descriptor-driven parameter panel.
 */
public final class MainFrame extends JFrame {

    private final ToolPalette palette;
    private final NodeEditorPanel editor;
    private final GLPreviewPanel preview;
    private final ParameterPanel parameters;
    private final StudioModel model;
    private final NodeFactoryRegistry registry;
    private final ToolsLibrary toolsLibrary;
    private final StatusBar statusBar;
    private final RecentProjects recent = new RecentProjects();
    private JMenu recentMenu;

    public MainFrame() {
        super("PixelFlow Studio");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1480, 880);
        setLocationByPlatform(true);

        this.registry = HeadlessSmoke.defaultRegistry();
        this.toolsLibrary = new ToolsLibrary(Paths.get("tools"), registry);
        this.toolsLibrary.rescan();

        PflowReader reader = new PflowReader(registry);
        this.model = new StudioModel(reader);

        this.editor = new NodeEditorPanel(registry);
        this.preview = new GLPreviewPanel(model);
        this.parameters = new ParameterPanel(model);
        this.palette = new ToolPalette(registry, toolsLibrary);
        this.statusBar = new StatusBar();
        this.editor.setStatusBar(statusBar);
        this.preview.setStatusBar(statusBar);

        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor, preview);
        center.setResizeWeight(0.55);
        center.setDividerLocation(480);

        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, parameters);
        right.setResizeWeight(1.0);
        right.setDividerLocation(1060);

        JSplitPane root = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palette, right);
        root.setResizeWeight(0.0);
        root.setDividerLocation(220);

        setLayout(new BorderLayout());
        add(root, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setJMenuBar(buildMenuBar());

        model.addProjectLoadedListener(loaded -> {
            preview.attachRuntime(loaded);
            editor.attachGraph(loaded);
            parameters.attachGraph(loaded);
            preview.setGraphStats(loaded.graph.nodes().size(), loaded.graph.edges().size());
            statusBar.info("Loaded " + loaded.graph.nodes().size() + " nodes, "
                    + loaded.graph.edges().size() + " edges from "
                    + (model.currentPath() != null ? model.currentPath().getFileName() : "memory"));
        });

        editor.addSelectionListener(n -> parameters.setActiveNode(n));

        // Hot-reload: watch tools/ for .pftool changes, refresh the palette.
        toolsLibrary.startWatcher(() -> SwingUtilities.invokeLater(() -> {
            palette.reload();
            statusBar.info("Tools library reloaded: " + toolsLibrary.registeredTypeIds().size() + " tool(s)");
        }));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                preview.shutdown();
                toolsLibrary.stopWatcher();
            }
        });

        setMinimumSize(new Dimension(900, 600));
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem open = new JMenuItem("Open project…");
        open.setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        open.addActionListener(e -> chooseProject());
        fileMenu.add(open);

        recentMenu = new JMenu("Open Recent");
        fileMenu.add(recentMenu);
        rebuildRecentMenu();

        JMenuItem reload = new JMenuItem("Reload");
        reload.setAccelerator(KeyStroke.getKeyStroke("F5"));
        reload.addActionListener(e -> model.reload());
        fileMenu.add(reload);

        fileMenu.addSeparator();
        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        save.addActionListener(e -> saveProject());
        fileMenu.add(save);

        JMenuItem saveAs = new JMenuItem("Save As…");
        saveAs.setAccelerator(KeyStroke.getKeyStroke("ctrl shift S"));
        saveAs.addActionListener(e -> saveProjectAs());
        fileMenu.add(saveAs);

        JMenuItem saveAsTool = new JMenuItem("Save as Tool…");
        saveAsTool.setAccelerator(KeyStroke.getKeyStroke("ctrl T"));
        saveAsTool.addActionListener(e -> {
            String id = SaveAsToolDialog.run(this, model, toolsLibrary);
            if (id != null) {
                palette.reload();
                statusBar.info("Saved tool '" + id + "' — added to palette");
            }
        });
        fileMenu.add(saveAsTool);

        fileMenu.addSeparator();
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        fileMenu.add(exit);

        bar.add(fileMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(
                this,
                "PixelFlow Studio — M3 v1\n\nGraph runtime running live in an embedded JOGL canvas.\n"
                        + "Edit parameters on the right; changes propagate to the next sim frame.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(about);
        bar.add(helpMenu);

        return bar;
    }

    private void chooseProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PixelFlow project (*.pflow)", "pflow"));
        chooser.setCurrentDirectory(new File("starters"));
        int rval = chooser.showOpenDialog(this);
        if (rval == JFileChooser.APPROVE_OPTION) {
            Path p = chooser.getSelectedFile().toPath();
            openProject(p.toString());
        }
    }

    public void openProject(String path) {
        SwingUtilities.invokeLater(() -> {
            try {
                Path p = Paths.get(path);
                model.loadProject(p);
                setTitle("PixelFlow Studio — " + path);
                recent.add(p);
                rebuildRecentMenu();
            } catch (IOException ex) {
                statusBar.error("Open failed: " + ex.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Failed to load " + path + ":\n" + ex.getMessage(),
                        "Open error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void rebuildRecentMenu() {
        if (recentMenu == null) return;
        recentMenu.removeAll();
        var entries = recent.entries();
        if (entries.isEmpty()) {
            JMenuItem empty = new JMenuItem("(empty)");
            empty.setEnabled(false);
            recentMenu.add(empty);
            return;
        }
        for (Path p : entries) {
            String label = p.getFileName() == null ? p.toString() : p.getFileName().toString();
            JMenuItem item = new JMenuItem(label + "   " + p.getParent());
            item.addActionListener(e -> openProject(p.toString()));
            recentMenu.add(item);
        }
    }

    private void saveProject() {
        if (model.currentPath() == null) { saveProjectAs(); return; }
        try {
            model.save();
            setTitle("PixelFlow Studio — " + model.currentPath());
            statusBar.info("Saved " + model.currentPath().getFileName());
        } catch (IOException ex) {
            statusBar.error("Save failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to save: " + ex.getMessage(),
                    "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveProjectAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PixelFlow project (*.pflow)", "pflow"));
        if (model.currentPath() != null) {
            chooser.setSelectedFile(model.currentPath().toFile());
        } else {
            chooser.setCurrentDirectory(new File("."));
            chooser.setSelectedFile(new File("untitled.pflow"));
        }
        int rval = chooser.showSaveDialog(this);
        if (rval != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".pflow")) {
            f = new File(f.getParentFile(), f.getName() + ".pflow");
        }
        try {
            model.saveAs(f.toPath());
            setTitle("PixelFlow Studio — " + f.toPath());
            statusBar.info("Saved " + f.toPath().getFileName());
            recent.add(f.toPath());
            rebuildRecentMenu();
        } catch (IOException ex) {
            statusBar.error("Save failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to save: " + ex.getMessage(),
                    "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
