package de.geolykt.starloader.launcher.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.Popup;
import javax.swing.SwingUtilities;

import de.geolykt.sleadn.DependencyDescriptor;
import de.geolykt.sleadn.ExtensionDescriptor;
import de.geolykt.sleadn.VersionDescriptor;
import de.geolykt.starloader.launcher.Launcher;
import de.geolykt.starloader.launcher.Utils;

final class ExtensionSelector implements Runnable {

    private final int index;
    private final JFrame parent;
    BrowseExtensionsTab tab;

    ExtensionSelector(JFrame parent, int extensionIndex, BrowseExtensionsTab tab) {
        this.parent = parent;
        this.index = extensionIndex;
        this.tab = tab;
    }

    private void displayDescriptor(Optional<ExtensionDescriptor> descriptor, String extensionId, JPanel extensionDetailsPanel) {
        if (descriptor.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                extensionDetailsPanel.removeAll();
                JLabel fetchingLabel = new JLabel("Could not fetch extension metadata for " + extensionId);
                extensionDetailsPanel.add(fetchingLabel);
                parent.paintComponents(parent.getGraphics());
            });
            return;
        } else {
            ExtensionDescriptor desc = descriptor.get();
            SwingUtilities.invokeLater(() -> {
                extensionDetailsPanel.removeAll();
                if (!(extensionDetailsPanel.getLayout() instanceof GridBagLayout)) {
                    extensionDetailsPanel.setLayout(new GridBagLayout());
                }
                GridBagConstraints titleConstraints = new GridBagConstraints();
                titleConstraints.gridwidth = 2;
                extensionDetailsPanel.add(new JLabel(desc.name), titleConstraints);
                GridBagConstraints keyDescriptionConstraints = new GridBagConstraints();
                keyDescriptionConstraints.gridy = 1;
                keyDescriptionConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints keyLicenseConstraints = new GridBagConstraints();
                keyLicenseConstraints.gridy = 2;
                keyLicenseConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints keyAuthorsConstraints = new GridBagConstraints();
                keyAuthorsConstraints.gridy = 3;
                keyAuthorsConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints keyIdentifierConstraints = new GridBagConstraints();
                keyIdentifierConstraints.gridy = 4;
                keyIdentifierConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints valueDescriptionConstraints = new GridBagConstraints();
                valueDescriptionConstraints.gridy = 1;
                valueDescriptionConstraints.gridx = 1;
                valueDescriptionConstraints.weightx = 1;
                valueDescriptionConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints valueLicenseConstraints = new GridBagConstraints();
                valueLicenseConstraints.gridy = 2;
                valueLicenseConstraints.gridx = 1;
                valueLicenseConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints valueAuthorsConstraints = new GridBagConstraints();
                valueAuthorsConstraints.gridy = 3;
                valueAuthorsConstraints.gridx = 1;
                valueAuthorsConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints valueIdentifierConstraints = new GridBagConstraints();
                valueIdentifierConstraints.gridy = 4;
                valueIdentifierConstraints.gridx = 1;
                valueIdentifierConstraints.anchor = GridBagConstraints.WEST;
                GridBagConstraints versionTableHeaderConstraints = new GridBagConstraints();
                versionTableHeaderConstraints.gridwidth = 2;
                versionTableHeaderConstraints.gridy = 5;
                versionTableHeaderConstraints.ipady = 5;
                versionTableHeaderConstraints.fill = GridBagConstraints.BOTH;
                versionTableHeaderConstraints.anchor = GridBagConstraints.WEST;
                extensionDetailsPanel.add(new JLabel("Description: "), keyDescriptionConstraints);
                extensionDetailsPanel.add(new JLabel("License: "), keyLicenseConstraints);
                extensionDetailsPanel.add(new JLabel("Authors: "), keyAuthorsConstraints);
                extensionDetailsPanel.add(new JLabel("Identifier: "), keyIdentifierConstraints);
                extensionDetailsPanel.add(new JLabel(desc.description), valueDescriptionConstraints);
                extensionDetailsPanel.add(new JLabel(desc.license), valueLicenseConstraints);
                extensionDetailsPanel.add(new JLabel(Arrays.toString(desc.authors.toArray())), valueAuthorsConstraints);
                extensionDetailsPanel.add(new JLabel(desc.identifier), valueIdentifierConstraints);
                extensionDetailsPanel.add(new JSeparator(JSeparator.HORIZONTAL), versionTableHeaderConstraints);
                GridBagConstraints versionTableConstraints = new GridBagConstraints();
                versionTableConstraints.gridwidth = 2;
                versionTableConstraints.gridy = 6;
                versionTableConstraints.ipady = 10;
                versionTableConstraints.fill = GridBagConstraints.BOTH;
                versionTableConstraints.anchor = GridBagConstraints.WEST;
                extensionDetailsPanel.add(getVersionTable(desc), versionTableConstraints);
                GridBagConstraints padConstraints = new GridBagConstraints();
                padConstraints.gridy = 7;
                padConstraints.weighty = 1;
                extensionDetailsPanel.add(new JLabel(), padConstraints);
                parent.paintComponents(parent.getGraphics());
            });
        }
    }

    private Component getVersionTable(ExtensionDescriptor desc) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        List<VersionDescriptor> versions = desc.versions.get();
        {
            GridBagConstraints nameHeaderConstraint = new GridBagConstraints();
            nameHeaderConstraint.gridx = 0;
            nameHeaderConstraint.gridy = 0;
            nameHeaderConstraint.ipadx = 10;
            nameHeaderConstraint.ipady = 5;
            nameHeaderConstraint.anchor = GridBagConstraints.WEST;
            nameHeaderConstraint.fill = GridBagConstraints.HORIZONTAL;
            GridBagConstraints urlButtonConstraint = new GridBagConstraints();
            urlButtonConstraint.gridx = 1;
            urlButtonConstraint.gridy = 0;
            urlButtonConstraint.ipadx = 10;
            urlButtonConstraint.ipady = 5;
            urlButtonConstraint.anchor = GridBagConstraints.WEST;
            urlButtonConstraint.fill = GridBagConstraints.HORIZONTAL;
            GridBagConstraints buttonHeaderConstraint = new GridBagConstraints();
            buttonHeaderConstraint.gridx = 2;
            buttonHeaderConstraint.gridy = 0;
            buttonHeaderConstraint.ipadx = 10;
            buttonHeaderConstraint.ipady = 5;
            buttonHeaderConstraint.anchor = GridBagConstraints.WEST;
            buttonHeaderConstraint.fill = GridBagConstraints.HORIZONTAL;
            JLabel versionLabel = new JLabel("Version");
            versionLabel.setOpaque(true);
            versionLabel.setBackground(Color.GRAY);
            JLabel urlLabel = new JLabel("URL");
            urlLabel.setOpaque(true);
            urlLabel.setBackground(Color.GRAY);
            JLabel installHeader = new JLabel(" ");
            installHeader.setOpaque(true);
            installHeader.setBackground(Color.GRAY);
            panel.add(versionLabel, nameHeaderConstraint);
            panel.add(urlLabel, urlButtonConstraint);
            panel.add(installHeader, buttonHeaderConstraint);
        }
        int row = 1;
        for (VersionDescriptor verDesc : versions) {
            GridBagConstraints versionNameConstraints = new GridBagConstraints();
            versionNameConstraints.anchor = GridBagConstraints.WEST;
            versionNameConstraints.gridy = row;
            versionNameConstraints.gridx = 0;
            versionNameConstraints.ipadx = 10;
            GridBagConstraints versionUrlConstraints = new GridBagConstraints();
            versionUrlConstraints.anchor = GridBagConstraints.WEST;
            versionUrlConstraints.gridy = row;
            versionUrlConstraints.gridx = 1;
            versionUrlConstraints.ipadx = 10;
            versionUrlConstraints.weightx = 1;
            versionUrlConstraints.fill = GridBagConstraints.BOTH;
            GridBagConstraints installButtonConstraints = new GridBagConstraints();
            installButtonConstraints.anchor = GridBagConstraints.WEST;
            installButtonConstraints.gridy = row;
            installButtonConstraints.gridx = 2;
            installButtonConstraints.ipadx = 10;
            JLabel urlLabel = new JLabel(verDesc.url);
            urlLabel.setBackground(Color.LIGHT_GRAY);
            urlLabel.setOpaque(true);
            urlLabel.addMouseListener(new MouseClickListener(() -> {
                try {
                    // Linux apparently does not implement Desktop#browse correctly. xdg-open is our only saviour.
                    Runtime.getRuntime().exec(new String[] {"xdg-open", verDesc.url});
                } catch (Exception e1) {
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(URI.create(verDesc.url));
                        } catch (IllegalArgumentException | IOException ignored) {
                            // We can absorb these exceptions; the user probably doesn't even know about this functionality
                        }
                    }
                }
            }));
            panel.add(new JLabel(verDesc.versionId), versionNameConstraints);
            panel.add(urlLabel, versionUrlConstraints);
            JButton button = new JButton("Install");
            button.addMouseListener(new MouseClickListener(() -> {
                installExtension(verDesc);
            }));
            panel.add(button, installButtonConstraints);
            row++;
        }
        return panel;
    }

    private void informDependencies(VersionDescriptor verDesc) {
        JPanel popupPanel = new JPanel();
        popupPanel.setLayout(new BoxLayout(popupPanel, BoxLayout.Y_AXIS));

        Optional<List<ExtensionDescriptor>> extensions = tab.extensions;
        if (extensions == null) {
            throw new IllegalStateException();
        }

        popupPanel.add(new JLabel(extensions.get().get(index).name + " version " + verDesc.versionId + " requires: "));
        for (DependencyDescriptor dependency : verDesc.dependencies) {
            if (dependency.hardDepend) {
                popupPanel.add(new JLabel(" - " + dependency.dependencyId + " version " + dependency.dependencyVersion));
            }
        }

        popupPanel.add(new JSeparator());
        popupPanel.add(new JLabel(extensions.get().get(index).name + " version " + verDesc.versionId + " recommends: "));
        for (DependencyDescriptor dependency : verDesc.dependencies) {
            if (!dependency.hardDepend) {
                popupPanel.add(new JLabel(" - " + dependency.dependencyId + " version " + dependency.dependencyVersion));
            }
        }

        JLabel warnLabel = new JLabel("Make sure to have these dependencies installed!");
        warnLabel.setForeground(new Color(128, 0, 0));
        popupPanel.add(warnLabel);

        JButton popupButton = new JButton("Ok");
        popupPanel.add(popupButton);
        Popup popup = Utils.createPopup(parent, popupPanel, true);
        popupButton.addMouseListener(new MouseClickListener(popup::hide));
        popup.show();
    }

    private void installExtension(VersionDescriptor verDesc) {
        JPanel popupPanel = new JPanel();
        JLabel popupText = new JLabel();
        JButton popupButton = new JButton("Ok");
        popupPanel.add(popupText);
        popupPanel.add(popupButton);
        URI uri;
        try {
            uri = URI.create(verDesc.url);
            if (uri.getScheme() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            popupText.setText("Failed to create URI from version descriptor. This usually indicates a broken repository. Contact the repository maintainers about this.");
            Popup popup = Utils.createPopup(parent, popupPanel, true);
            popupButton.addMouseListener(new MouseClickListener(popup::hide));
            popup.show();
            return;
        }
        HttpClient httpClient = tab.httpClient;
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        CompletableFuture<?> requestFuture = httpClient.sendAsync(request, BodyHandlers.ofInputStream()).thenAccept(response -> {
            System.out.println("Status code: " + response.statusCode());
            // Derived from https://stackoverflow.com/a/63582938 by VGR
            Optional<String> disposition = response.headers().firstValue("Content-Disposition");
            if (disposition.isPresent()) {
                int filenameIndex = disposition.get().indexOf("filename=");
                if (filenameIndex >= 0) {
                    String filename = disposition.get().substring(filenameIndex + 9);
                    if (filename.startsWith("\"")) {
                        // filename is everything inside double-quotes.
                        int endIndex = filename.indexOf('"', 1);
                        filename = filename.substring(1, endIndex);
                    } else {
                        // filename is unquoted and goes until ';' or end of string.
                        int endIndex = filename.indexOf(';');
                        if (endIndex > 0) {
                            filename = filename.substring(0, endIndex);
                        }
                    }
                    try (FileOutputStream fos = new FileOutputStream(new File(tab.preferences.getExtensionsFolder(), filename))) {
                        response.body().transferTo(fos);
                    } catch (IOException e) {
                        Utils.reportError(parent, e);
                    }
                    return;
                }
            }
            Optional<List<ExtensionDescriptor>> extensions = tab.extensions;
            if (extensions == null) {
                throw new IllegalStateException();
            }
            try (FileOutputStream fos = new FileOutputStream(new File(tab.preferences.getExtensionsFolder(), extensions.get().get(index).name + "-" + verDesc.versionId + ".jar"))) {
                response.body().transferTo(fos);
            } catch (IOException e) {
                Utils.reportError(parent, e);
            }
        });
        popupText.setText("Downloading the extension. This may take a while, the popup will automatically close when it is done.");
        Popup popup = Utils.createPopup(parent, popupPanel, true);
        popupButton.addMouseListener(new MouseClickListener(popup::hide));
        popup.show();
        Launcher.MAIN_TASK_QUEUE.add(() -> {
            if (joinSafely(requestFuture)) {
                SwingUtilities.invokeLater(() -> {
                    informDependencies(verDesc);
                });
            }
            popup.hide();
        });
    }

    private boolean joinSafely(CompletableFuture<?> future) {
        try {
            future.join();
            return true;
        } catch (Throwable e) {
            Utils.reportError(parent, e);
            return false;
        }
    }

    @Override
    public void run() {
        Optional<List<ExtensionDescriptor>> extensions = tab.extensions;
        if (extensions == null) {
            throw new IllegalStateException("Extensions not yet fetched.");
        }
        String extensionId = extensions.get().get(index).identifier;
        JPanel extensionDetailsPanel = (JPanel) tab.getBottomComponent();
        SwingUtilities.invokeLater(() -> {
            extensionDetailsPanel.removeAll();
            extensionDetailsPanel.add(new JLabel("Fecthing extension metadata for " + extensionId));
            parent.paintComponents(parent.getGraphics());
        });
        CompletableFuture<Void> f = tab.client.getExtensionMetadata(extensionId).thenAccept(descriptor -> {
            displayDescriptor(descriptor, extensionId, extensionDetailsPanel);
        });
        // FIXME fix issues concerning connectivity
        Launcher.MAIN_TASK_QUEUE.add(() -> joinSafely(f));
    }
}
