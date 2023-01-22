package de.geolykt.starloader.mod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.util.JarFilter;

public class ExtensionPrototypeList {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionPrototypeList.class);

    private final File extensionFolder;
    private final List<ExtensionPrototype> extensions;
    private final Map<String, List<ExtensionPrototype>> extensionsByName;

    public ExtensionPrototypeList(File sourceFolder) {
        extensionFolder = sourceFolder;
        extensions = new ArrayList<>();
        extensionsByName = new HashMap<>();
        File[] jarFiles = extensionFolder.listFiles(JarFilter.INSTANCE);
        if (jarFiles == null) {
            LOGGER.warn("Unable to list files at {}", extensionFolder);
            return;
        }
        for (File jarFile : jarFiles) {
            try {
                JarFile jar = new JarFile(jarFile);
                ZipEntry entry = jar.getEntry("extension.json");
                if (entry == null) {
                    jar.close();
                    continue;
                }
                InputStream is = jar.getInputStream(entry);
                JSONObject jsonObj = new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                ExtensionPrototype prototype = new ExtensionPrototype(jarFile, jsonObj.getString("name"), jsonObj.optString("version", "unkown"));
                extensions.add(prototype);
                if (extensionsByName.containsKey(prototype.name)) {
                    extensionsByName.get(prototype.name).add(prototype);
                } else {
                    List<ExtensionPrototype> l = new ArrayList<>();
                    l.add(prototype);
                    extensionsByName.put(prototype.name, l);
                }
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

    public void addPrototype(ExtensionPrototype prototype) {
        extensions.add(prototype);
        extensionsByName.compute(prototype.name, (key, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(prototype);
            return list;
        });
    }

    public List<ExtensionPrototype> getPrototypes(String s) {
        List<ExtensionPrototype> l = extensionsByName.get(s);
        if (l == null) {
            return new ArrayList<>();
        }
        return l;
    }

    public File getFolder() {
        return extensionFolder;
    }
}
