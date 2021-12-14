package de.geolykt.starloader.launcher.components;

import java.awt.GridBagLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.geolykt.sleadn.ExtensionDescriptor;
import de.geolykt.sleadn.SleadnClient;
import de.geolykt.starloader.launcher.Launcher;
import de.geolykt.starloader.launcher.LauncherConfiguration;
import de.geolykt.starloader.launcher.Utils;

/**
 * A simple browser for extensions making use of SlEADN via {@link SleadnClient}.
 */
@SuppressWarnings("serial") // Superclass not serializable across java versions
public class BrowseExtensionsTab extends JSplitPane implements StarloaderTab {

    SleadnClient client;
    @Nullable
    Optional<List<ExtensionDescriptor>> extensions;
    private boolean fetchedExtensions = false;
    HttpClient httpClient;
    @NotNull
    LauncherConfiguration preferences;

    public BrowseExtensionsTab(@NotNull LauncherConfiguration prefs) {
        super(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(new JPanel()), new JScrollPane(new JPanel()));
        this.preferences = prefs;
        this.setResizeWeight(0.5D);
    }

    private void initList(JFrame parent) {
        JPanel extensionList = (JPanel) getTopComponent();

        Optional<List<ExtensionDescriptor>> extensions = this.extensions;
        if (extensions == null) {
            throw new IllegalStateException("Extensions not yet fetched.");
        }
        if (extensions.isEmpty()) {
            extensionList.add(new JLabel("Unable to fetch extensions."));
            return;
        }

        int nameWidth = 0;
        int descriptionWidth = 0;
        int versionWidth = 0;
        int row = 1;
        GridBagLayout layout = new GridBagLayout();
        extensionList.setLayout(layout);

        GridBagConstraints nameHeaderConstraint = new GridBagConstraints();
        nameHeaderConstraint.gridx = 0;
        nameHeaderConstraint.gridy = 0;
        nameHeaderConstraint.ipadx = 10;
        nameHeaderConstraint.ipady = 5;
        nameHeaderConstraint.anchor = GridBagConstraints.WEST;
        nameHeaderConstraint.fill = GridBagConstraints.HORIZONTAL;
        GridBagConstraints descriptionHeaderConstraint = new GridBagConstraints();
        descriptionHeaderConstraint.gridx = 1;
        descriptionHeaderConstraint.gridy = 0;
        descriptionHeaderConstraint.ipadx = 10;
        descriptionHeaderConstraint.ipady = 5;
        descriptionHeaderConstraint.anchor = GridBagConstraints.WEST;
        descriptionHeaderConstraint.fill = GridBagConstraints.HORIZONTAL;
        GridBagConstraints versionHeaderConstraint = new GridBagConstraints();
        versionHeaderConstraint.gridx = 2;
        versionHeaderConstraint.gridy = 0;
        versionHeaderConstraint.ipadx = 10;
        versionHeaderConstraint.ipady = 5;
        versionHeaderConstraint.anchor = GridBagConstraints.WEST;
        versionHeaderConstraint.fill = GridBagConstraints.HORIZONTAL;
        JLabel nameHeader = new JLabel("Name");
        JLabel descriptionHeader = new JLabel("Description");
        JLabel versionHeader = new JLabel("Latest version");
        nameHeader.setBackground(Color.GRAY);
        descriptionHeader.setBackground(Color.GRAY);
        versionHeader.setBackground(Color.GRAY);
        nameHeader.setOpaque(true);
        descriptionHeader.setOpaque(true);
        versionHeader.setOpaque(true);
        extensionList.add(nameHeader, nameHeaderConstraint);
        extensionList.add(descriptionHeader, descriptionHeaderConstraint);
        extensionList.add(versionHeader, versionHeaderConstraint);

        for (ExtensionDescriptor desc : extensions.get()) {
            JLabel name = new JLabel(desc.name);
            JLabel description = new JLabel(desc.description);
            JLabel latestVersion = new JLabel(desc.latestVersion);
            MouseClickListener listener = new MouseClickListener(new ExtensionSelector(parent, row - 1, this));
            name.addMouseListener(listener);
            description.addMouseListener(listener);
            latestVersion.addMouseListener(listener);
            if (name.getPreferredSize().width > nameWidth) {
                nameWidth = name.getPreferredSize().width;
                layout.columnWidths = new int[] {nameWidth, descriptionWidth, versionWidth};
            }
            if (description.getPreferredSize().width > descriptionWidth) {
                descriptionWidth = description.getPreferredSize().width;
                layout.columnWidths = new int[] {nameWidth, descriptionWidth, versionWidth};
            }
            if (latestVersion.getPreferredSize().width > versionWidth) {
                versionWidth = latestVersion.getPreferredSize().width;
                layout.columnWidths = new int[] {nameWidth, descriptionWidth, versionWidth};
            }
            description.setBackground(Color.LIGHT_GRAY);
            description.setOpaque(true); // Otherwise the background color is not drawn as it is transparent
            GridBagConstraints nameConstraint = new GridBagConstraints();
            nameConstraint.gridx = 0;
            nameConstraint.gridy = row;
            nameConstraint.ipadx = 10;
            nameConstraint.ipady = 5;
            nameConstraint.anchor = GridBagConstraints.WEST;
            nameConstraint.fill = GridBagConstraints.HORIZONTAL;
            GridBagConstraints descriptionConstraint = new GridBagConstraints();
            descriptionConstraint.gridx = 1;
            descriptionConstraint.gridy = row;
            descriptionConstraint.weightx = 1;
            descriptionConstraint.ipadx = 10;
            descriptionConstraint.ipady = 5;
            descriptionConstraint.anchor = GridBagConstraints.WEST;
            descriptionConstraint.fill = GridBagConstraints.HORIZONTAL;
            GridBagConstraints versionConstraint = new GridBagConstraints();
            versionConstraint.gridx = 2;
            versionConstraint.gridy = row;
            versionConstraint.ipady = 5;
            versionConstraint.anchor = GridBagConstraints.WEST;
            versionConstraint.fill = GridBagConstraints.HORIZONTAL;
            extensionList.add(name, nameConstraint);
            extensionList.add(description, descriptionConstraint);
            extensionList.add(latestVersion, versionConstraint);
            row++;
        }
        GridBagConstraints fillerConstraints = new GridBagConstraints();
        fillerConstraints.weighty = 1;
        fillerConstraints.gridy = row;
        extensionList.add(new JLabel(), fillerConstraints);
    }

    @Override
    public void onClose(JFrame parent) {
        parent.remove(this);
    }

    // We use #getTopComponent() and #getBottomComponent() because when I developed this it used to be split horizontally,
    // however due to the pain that was JScrollPane, I had to split it vertically instead.
    @Override
    public JPanel getTopComponent() {
        JScrollPane topPane = (JScrollPane) super.getTopComponent();
        return (JPanel) topPane.getViewport().getView();
    }

    @Override
    public JPanel getBottomComponent() {
        JScrollPane topPane = (JScrollPane) super.getBottomComponent();
        return (JPanel) topPane.getViewport().getView();
    }

    @Override
    public void onOpen(JFrame parent) {
        if (!fetchedExtensions) {
            fetchedExtensions = true;
            JLabel fetchingLabel = new JLabel("Fetching extensions...");
            ((JPanel) getTopComponent()).removeAll();
            ((JPanel) getTopComponent()).add(fetchingLabel);
            if (client == null) {
                HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(Redirect.NORMAL).version(Version.HTTP_2);
                httpClient = builder.build();
                client = new SleadnClient(httpClient, preferences.getExtensionRepository());
            }
            CompletableFuture<Void> f = client.getClientList().thenAccept(extList -> {
                ((JPanel) getTopComponent()).remove(fetchingLabel);
                extensions = extList;
                initList(parent);
                SwingUtilities.invokeLater(() -> {
                    parent.setPreferredSize(parent.getToolkit().getScreenSize()); // Ight, I'm done. Feel free to remove this small hack, if you dare kill your sanity.
                    parent.pack();
                    parent.paintComponents(parent.getGraphics());
                });
            });
            Launcher.MAIN_TASK_QUEUE.add(() -> {
                try {
                    f.join();
                } catch (Throwable e) {
                    Utils.reportError(parent, e);
                    fetchedExtensions = false;
                }
            });
        }
    }
}
