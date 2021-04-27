package de.geolykt.starloader.launcher.components;

import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import de.geolykt.starloader.launcher.DigestCalculationRunnable;
import de.geolykt.starloader.launcher.Launcher;
import de.geolykt.starloader.launcher.LauncherConfiguration;
import de.geolykt.starloader.launcher.Utils;

@SuppressWarnings("serial") // Superclass not serializable across java versions
public class ConfigurationTab extends JPanel implements StarloaderTab {

    protected final LauncherConfiguration cfg;

    protected final JLabel headerLabel;
    protected final JLabel headerSeperator = new JLabel();
    protected final JLabel fileChooserDesc;
    protected final JLabel filever;
    protected final JButton fileChooserButton;
    protected final JCheckBox allowExtensions;
    protected final JCheckBox allowPatches;
    protected final JLabel patchesFolderDesc;
    protected final JButton patchesFolderButton;
    protected final JLabel extensionsFolderDesc;
    protected final JButton extensionsFolderButton;

    protected final JFrame superparent;
    protected DigestCalculationRunnable digester;

    protected JFileChooser fileChooserGali;
    protected JFileChooser fileChooserExtensions;
    protected JFileChooser fileChooserPatches;

    private Timer versionTimer = null;
    public ConfigurationTab(LauncherConfiguration config, JFrame superparent) {
        super();
        cfg = config;
        this.superparent = superparent;
        GridLayout layout = new GridLayout(0, 1);
        this.setLayout(layout);
        headerLabel = new JLabel("Configuration menu", SwingConstants.CENTER);
        fileChooserDesc = new JLabel(String.format("Galimulator jar file (currently %s)", cfg.getTargetJar().getPath()));
        filever = new JLabel("Version: Calculating...");
        digester = new DigestCalculationRunnable(cfg.getTargetJar());
        versionTimer = new Timer(1, evt -> {
            setVersion(digester.getDirectly());
            stopTimer(versionTimer);
            digester = null;
        });
        digester.setRunAfter(() -> versionTimer.start());
        Launcher.MAIN_TASK_QUEUE.add(digester);
        fileChooserButton = new JButton("Choose");
        fileChooserButton.addMouseListener(new MouseClickListener(this::showJarFileChooser));
        allowExtensions = new JCheckBox("Enable extension support", cfg.hasExtensionsEnabled());
        allowPatches = new JCheckBox("Enable patch support", cfg.hasPatchesEnabled());
        patchesFolderDesc = new JLabel("Patches folder: " + cfg.getPatchesFolder().getPath());
        patchesFolderButton = new JButton("Change folder");
        patchesFolderButton.addMouseListener(new MouseClickListener(this::showPatchesFC));
        extensionsFolderDesc = new JLabel("Extensions folder: " + cfg.getExtensionsFolder().getPath());
        extensionsFolderButton = new JButton("Change folder");
        extensionsFolderButton.addMouseListener(new MouseClickListener(this::showExtensionsFC));
        add(headerLabel);
        add(headerSeperator);
        add(fileChooserDesc);
        add(filever);
        add(fileChooserButton);
        add(allowExtensions);
        add(allowPatches);
        add(patchesFolderDesc);
        add(patchesFolderButton);
        add(extensionsFolderDesc);
        add(extensionsFolderButton);
    }

    /**
     * Stops a timer, nothing more.
     * This is a workaround to how the java compiler works and validates method calls.
     *
     * @param timer The timer to stop.
     */
    private static final void stopTimer(Timer timer) {
        timer.stop();
    }

    public void showJarFileChooser() {
        if (fileChooserGali == null) {
            fileChooserGali = new JFileChooser(Utils.getGameDir(Utils.STEAM_GALIMULATOR_APPNAME));
            FileFilter filter = new FileNameExtensionFilter("Java Archives", "jar");
            fileChooserGali.setFileFilter(filter);
            fileChooserGali.addChoosableFileFilter(filter);
            fileChooserGali.setVisible(true);
        }
        if (fileChooserGali.showOpenDialog(getParent()) == JFileChooser.APPROVE_OPTION) {
            acceptJarFileChooser();
        }
    }

    public void showPatchesFC() {
        if (fileChooserPatches == null) {
            fileChooserPatches = new JFileChooser(Utils.getCurrentDir());
            fileChooserPatches.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooserPatches.setVisible(true);
        }
        if (fileChooserPatches.showOpenDialog(getParent()) == JFileChooser.APPROVE_OPTION) {
            File patchesFolder = fileChooserPatches.getSelectedFile();
            if (!patchesFolder.isDirectory()) {
                patchesFolder = fileChooserPatches.getCurrentDirectory();
            }
            cfg.setPatchesFolder(patchesFolder);
            try {
                cfg.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
            patchesFolderDesc.setText("Patches folder: " + patchesFolder.getPath());
        }
    }

    public void showExtensionsFC() {
        if (fileChooserExtensions == null) {
            fileChooserExtensions = new JFileChooser(Utils.getCurrentDir());
            fileChooserExtensions.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooserExtensions.setVisible(true);
        }
        if (fileChooserExtensions.showOpenDialog(getParent()) == JFileChooser.APPROVE_OPTION) {
            File extensionsFolder = fileChooserExtensions.getSelectedFile();
            if (!extensionsFolder.isDirectory()) {
                extensionsFolder = fileChooserExtensions.getCurrentDirectory();
            }
            cfg.setExtensionsFolder(extensionsFolder);
            try {
                cfg.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
            extensionsFolderDesc.setText("Extensions folder: " + extensionsFolder.getPath());
        }
    }

    public JFrame getSuperparent() {
        return superparent;
    }

    public void acceptJarFileChooser() {
        File selected = fileChooserGali.getSelectedFile();
        // TODO perform verification of the file
        cfg.setTargetJar(selected);
        fileChooserDesc.setText(String.format("Galimulator jar file (currently %s)", cfg.getTargetJar().getPath()));
        if (digester != null) {
            digester.cancel(true);
        }
        digester = new DigestCalculationRunnable(cfg.getTargetJar());
        digester.setRunAfter(() -> versionTimer.start());
        Launcher.MAIN_TASK_QUEUE.add(digester);
        if (superparent.getPreferredSize().getWidth() > superparent.getWidth()) {
            superparent.pack();
        }
    }

    @Override
    public void onClose(JFrame parent) {
        parent.remove(this);
        cfg.setExtensionsEnabled(allowExtensions.getModel().isSelected());
        cfg.setPatchesEnabled(allowPatches.getModel().isSelected());
        try {
            cfg.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setVersion(String version) {
        filever.setText("Version: " + version);
    }
}
