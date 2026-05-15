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
import javax.swing.JLabel;
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
    private final PreviewToolbar previewToolbar;
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
        this.previewToolbar = new PreviewToolbar(preview, statusBar);
        this.editor.setStatusBar(statusBar);
        this.preview.setStatusBar(statusBar);
        this.editor.setOnMutate(parameters::refresh);
        this.parameters.setOnLabelChange(() -> { editor.repaint(); model.markDirty(); });
        this.editor.undoStack().setOnMutate(model::markDirty);
        this.editor.setOnSpaceToggle(previewToolbar::togglePlay);
        this.model.setDirtyListener(this::refreshTitle);

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
        add(previewToolbar, BorderLayout.NORTH);
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

        editor.addSelectionListener(n -> {
            parameters.setActiveNode(n);
            preview.setThumbnailTarget(n, parameters::setThumbnail);
        });

        // Hot-reload: watch tools/ for .pftool changes, refresh the palette.
        toolsLibrary.startWatcher(() -> SwingUtilities.invokeLater(() -> {
            palette.reload();
            statusBar.info("Tools library reloaded: " + toolsLibrary.registeredTypeIds().size() + " tool(s)");
        }));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (!confirmCloseIfDirty()) return;
                preview.shutdown();
                toolsLibrary.stopWatcher();
                dispose();
            }
        });
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

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

        JMenuItem exportPng = new JMenuItem("Export PNG…");
        exportPng.setAccelerator(KeyStroke.getKeyStroke("ctrl shift P"));
        exportPng.addActionListener(e -> exportPng());
        fileMenu.add(exportPng);

        fileMenu.addSeparator();

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

        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke("ctrl Z"));
        undoItem.addActionListener(e -> editor.undo());
        editMenu.add(undoItem);

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke("ctrl Y"));
        redoItem.addActionListener(e -> editor.redo());
        editMenu.add(redoItem);

        editMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                undoItem.setEnabled(editor.canUndo());
                redoItem.setEnabled(editor.canRedo());
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });

        editMenu.addSeparator();

        JMenuItem duplicate = new JMenuItem("Duplicate Selected");
        duplicate.addActionListener(e -> editor.duplicateSelected());
        editMenu.add(duplicate);

        JMenuItem mute = new JMenuItem("Mute / Unmute Selected");
        mute.addActionListener(e -> editor.toggleMuteSelected());
        editMenu.add(mute);

        JMenuItem copy = new JMenuItem("Copy Selected");
        copy.addActionListener(e -> editor.copySelectedToClipboard());
        editMenu.add(copy);

        JMenuItem paste = new JMenuItem("Paste");
        paste.addActionListener(e -> editor.pasteFromClipboard());
        editMenu.add(paste);

        JMenuItem cut = new JMenuItem("Cut Selected");
        cut.addActionListener(e -> editor.cutSelected());
        editMenu.add(cut);

        JMenuItem delete = new JMenuItem("Delete Selected");
        delete.addActionListener(e -> editor.deleteSelected());
        editMenu.add(delete);

        editMenu.addSeparator();
        JLabel hint = new JLabel("  Shortcuts: see Help > Keybindings  ");
        hint.setEnabled(false);
        editMenu.add(hint);

        bar.add(editMenu);

        JMenu viewMenu = new JMenu("View");
        JMenuItem resetLayout = new JMenuItem("Reset Layout");
        resetLayout.setAccelerator(KeyStroke.getKeyStroke("ctrl L"));
        resetLayout.addActionListener(e -> {
            editor.resetLayout();
            statusBar.info("Layout reset");
        });
        viewMenu.add(resetLayout);

        JMenuItem frameAll = new JMenuItem("Frame All");
        frameAll.setAccelerator(KeyStroke.getKeyStroke("F"));
        frameAll.addActionListener(e -> editor.frameAll());
        viewMenu.add(frameAll);

        javax.swing.JCheckBoxMenuItem snap = new javax.swing.JCheckBoxMenuItem("Snap to Grid");
        snap.setSelected(editor.isSnapToGrid());
        snap.addActionListener(e -> editor.setSnapToGrid(snap.isSelected()));
        viewMenu.add(snap);
        bar.add(viewMenu);

        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem rescanTools = new JMenuItem("Reload Tools Library");
        rescanTools.addActionListener(e -> {
            toolsLibrary.rescan();
            palette.reload();
            statusBar.info("Tools library reloaded: " + toolsLibrary.registeredTypeIds().size() + " tool(s)");
        });
        toolsMenu.add(rescanTools);

        JMenuItem openToolsFolder = new JMenuItem("Open Tools Folder…");
        openToolsFolder.addActionListener(e -> {
            try {
                Path root = toolsLibrary.root().toAbsolutePath();
                if (!java.nio.file.Files.isDirectory(root)) java.nio.file.Files.createDirectories(root);
                java.awt.Desktop.getDesktop().open(root.toFile());
            } catch (Exception ex) {
                statusBar.error("Open folder failed: " + ex.getMessage());
            }
        });
        toolsMenu.add(openToolsFolder);
        bar.add(toolsMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem keys = new JMenuItem("Keybindings");
        keys.setAccelerator(KeyStroke.getKeyStroke("F1"));
        keys.addActionListener(e -> JOptionPane.showMessageDialog(
                this,
                """
                Canvas
                  Left-drag node       Move node
                  Middle-drag          Pan canvas
                  Mouse-wheel          Zoom
                  Home                 Reset pan/zoom
                  F                    Frame all (zoom to fit)
                  F9                   Toggle minimap
                  Right-click edge     Disconnect
                  Drag output→input    Create edge

                Selection
                  Click node           Select (replaces selection)
                  Shift+click          Add/remove from selection
                  Left-drag empty      Marquee select
                  Ctrl+A               Select all
                  Shift+A              Quick-add tool (fuzzy palette)
                  Delete               Remove selected
                  M                    Mute / unmute selected
                  Ctrl+D               Duplicate selected
                  Ctrl+C / Ctrl+V      Copy / paste primary (system clipboard)
                  Ctrl+X               Cut selected

                Editing
                  Ctrl+Z               Undo
                  Ctrl+Y / Ctrl+Shift+Z  Redo

                Preview
                  Space                Pause / resume simulation

                File
                  Ctrl+O   Open       Ctrl+S   Save
                  Ctrl+Shift+S Save As
                  Ctrl+T   Save as Tool   F5  Reload
                  Ctrl+Shift+P Export PNG of preview
                """,
                "Keybindings", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(keys);
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(
                this,
                "PixelFlow Studio — M3.5\n\nCompose PixelFlow GPU primitives into reusable tools.\n"
                        + "Drag from the palette, wire ports by drag, save compositions as new tools.",
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
                refreshTitle();
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

    /** Update the title bar to include the current path + a trailing * when dirty. */
    private void refreshTitle() {
        String base = "PixelFlow Studio";
        Path p = model.currentPath();
        String stem = p != null ? p.getFileName().toString() : "(unsaved)";
        setTitle(base + " — " + stem + (model.isDirty() ? " *" : ""));
    }

    /** Prompt to save when dirty; returns true if the close should proceed. */
    private boolean confirmCloseIfDirty() {
        if (!model.isDirty()) return true;
        int rval = JOptionPane.showConfirmDialog(
                this,
                "Discard unsaved changes?",
                "Close",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return rval == JOptionPane.OK_OPTION;
    }

    private void exportPng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
        String stem = model.currentPath() != null
                ? model.currentPath().getFileName().toString().replaceFirst("\\.pflow$", "")
                : "frame";
        chooser.setSelectedFile(new File(stem + ".png"));
        int rval = chooser.showSaveDialog(this);
        if (rval != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) {
            f = new File(f.getParentFile(), f.getName() + ".png");
        }
        final File target = f;
        preview.captureNextFrameAsPng(f.toPath(), err -> {
            if (err == null) statusBar.info("Exported " + target.getName());
            else statusBar.error("Export failed: " + err.getMessage());
        });
    }

    private void saveProject() {
        if (model.currentPath() == null) { saveProjectAs(); return; }
        try {
            model.save(editor.exportLayout());
            refreshTitle();
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
            model.saveAs(f.toPath(), editor.exportLayout());
            refreshTitle();
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
