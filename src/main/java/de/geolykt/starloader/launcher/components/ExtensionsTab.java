package de.geolykt.starloader.launcher.components;

import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import de.geolykt.starloader.launcher.LauncherConfiguration;
import de.geolykt.starloader.mod.DiscoveredExtensionList;
import de.geolykt.starloader.mod.ExtensionPrototype;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstraints;

@SuppressWarnings("serial") // Superclass not serializable across java versions
public class ExtensionsTab extends JPanel implements StarloaderTab {

    protected final LauncherConfiguration cfg;
    protected DiscoveredExtensionList extList = null;
    protected List<Component> right = null;
    protected List<Component> center = null;
    protected List<Component> left = null;
    protected TableLayout layout = null;

    public ExtensionsTab(LauncherConfiguration config) {
        cfg = config;
        layout = new TableLayout();
        setLayout(layout);
    }

    @Override
    public void onClose(JFrame parent) {
        for (int i = 1; i < right.size(); i++) {
            Component comp = right.get(i);
            if (comp instanceof JCheckBox) {
                extList.getPrototypes().get(i - 1).enabled = ((JCheckBox) comp).getModel().isSelected();
            }
        }
        parent.remove(this);
        try {
            cfg.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen(JFrame parent) {
        if (extList != null && extList.getFolder().equals(cfg.getExtensionsFolder())) {
            return; // List does not need updating
        }
        // Update list
        if (right != null) {
            right.forEach(this::remove);
            center.forEach(this::remove);
            left.forEach(this::remove);
            right.clear();
            center.clear();
            left.clear();
        } else {
            right = new ArrayList<>();
            center = new ArrayList<>();
            left = new ArrayList<>();
        }
        right.add(new JLabel(""));
        add(right.get(0), new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.LEFT, TableLayoutConstraints.CENTER));
        center.add(new JLabel("Name"));
        add(center.get(0), new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.LEFT, TableLayoutConstraints.CENTER));
        left.add(new JLabel("Version"));
        add(left.get(0), new TableLayoutConstraints(2, 0, 2, 0, TableLayoutConstraints.LEFT, TableLayoutConstraints.CENTER));
        extList = cfg.getExtensionList();
        int i = 1;
        for (ExtensionPrototype prototype : extList.getPrototypes()) {
            JCheckBox checkbox = new JCheckBox((String) null, prototype.enabled);
            JLabel name = new JLabel(prototype.name);
            JLabel version = new JLabel(prototype.version);
            right.add(checkbox);
            center.add(name);
            left.add(version);
            add(checkbox, new TableLayoutConstraints(0, i, 0, i, TableLayoutConstraints.LEFT, TableLayoutConstraints.CENTER));
            add(name, new TableLayoutConstraints(1, i, 1, i, TableLayoutConstraints.LEFT, TableLayoutConstraints.CENTER));
            add(version, new TableLayoutConstraints(2, i, 2, i, TableLayoutConstraints.LEFT, TableLayoutConstraints.CENTER));
            i++;
        }
        // Update layout
        double checkboxWidth = 0.0;
        double nameLen = 0.0;
        double versionLen = 0.0;
        double[] heights = new double[right.size()];
        for (i = 0; i < right.size(); i++) {
            Dimension checkbox = right.get(i).getPreferredSize();
            Dimension name = center.get(i).getPreferredSize();
            Dimension version = left.get(i).getPreferredSize();
            checkboxWidth = Math.max(checkbox.width * 1.5, checkboxWidth);
            nameLen = Math.max(name.width * 1.5, nameLen);
            versionLen = Math.max(version.width * 1.5, versionLen);
            heights[i] = Math.max(name.height, Math.max(version.height, checkbox.height)) * 1.5;
        }
        layout.setColumn(new double[] {checkboxWidth, nameLen, versionLen});
        layout.setRow(heights);
        layout.layoutContainer(this);
        repaint();
    }
}
