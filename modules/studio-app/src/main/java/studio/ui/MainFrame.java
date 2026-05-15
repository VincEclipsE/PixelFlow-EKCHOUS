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

import studio.headless.HeadlessSmoke;
import studio.save.PflowReader;

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

    public MainFrame() {
        super("PixelFlow Studio");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1480, 880);
        setLocationByPlatform(true);

        PflowReader reader = new PflowReader(HeadlessSmoke.defaultRegistry());
        this.model = new StudioModel(reader);

        this.editor = new NodeEditorPanel(HeadlessSmoke.defaultRegistry());
        this.preview = new GLPreviewPanel(model);
        this.parameters = new ParameterPanel(model);
        this.palette = new ToolPalette(HeadlessSmoke.defaultRegistry());

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

        setJMenuBar(buildMenuBar());

        model.addProjectLoadedListener(loaded -> {
            preview.attachRuntime(loaded);
            editor.attachGraph(loaded);
            parameters.attachGraph(loaded);
        });

        editor.addSelectionListener(n -> parameters.setActiveNode(n));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                preview.shutdown();
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

        JMenuItem reload = new JMenuItem("Reload");
        reload.setAccelerator(KeyStroke.getKeyStroke("F5"));
        reload.addActionListener(e -> model.reload());
        fileMenu.add(reload);

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
                model.loadProject(Paths.get(path));
                setTitle("PixelFlow Studio — " + path);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to load " + path + ":\n" + ex.getMessage(),
                        "Open error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
