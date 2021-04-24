package de.geolykt.starloader.launcher.components;

import java.awt.GridLayout;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;

import de.geolykt.starloader.launcher.LauncherConfiguration;

@SuppressWarnings("serial") // Superclass not serializable across java versions
public class ExtensionsTab extends JPanel implements StarloaderTab {

    protected final LauncherConfiguration cfg;

    public ExtensionsTab(LauncherConfiguration config) {
        cfg = config;
        GridLayout layout = new GridLayout(0, 1);
        this.setLayout(layout);
    }

    @Override
    public void onClose(JFrame parent) {
        parent.remove(this);
        try {
            cfg.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
