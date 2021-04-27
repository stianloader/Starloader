package de.geolykt.starloader.mod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.util.JarFilter;

public class DiscoveredExtensionList {

    public final static Logger LOGGER = LoggerFactory.getLogger(DiscoveredExtensionList.class);

    private final File extensionFolder;
    private final List<ExtensionPrototype> extensions;

    public DiscoveredExtensionList(File sourceFolder) {
        extensionFolder = sourceFolder;
        extensions = new ArrayList<>();
        for (File jarFile : extensionFolder.listFiles(JarFilter.INSTANCE)) {
            try {
                JarFile jar = new JarFile(jarFile);
                ZipEntry entry = jar.getEntry("extension.json");
                if (entry == null) {
                    jar.close();
                    continue;
                }
                InputStream is = jar.getInputStream(entry);
                JSONObject jsonObj = new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                extensions.add(new ExtensionPrototype(jarFile, jsonObj.getString("name"), jsonObj.optString("version", "unkown")));
                is.close();
                jar.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to load potential extension {}: {}", jarFile.getPath(), e);
                continue;
            }
        }
    }

    /**
     * Obtains all the known prototypes for this folder.
     *
     * @return The known prototypes
     */
    public List<ExtensionPrototype> getPrototypes() {
        return extensions;
    }

    public File getFolder() {
        return extensionFolder;
    }
}
