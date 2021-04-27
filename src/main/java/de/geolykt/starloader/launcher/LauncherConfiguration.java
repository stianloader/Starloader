package de.geolykt.starloader.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

import de.geolykt.starloader.mod.DiscoveredExtensionList;
import de.geolykt.starloader.mod.ExtensionPrototype;

/**
 * Object that stores the configuration of the launcher.
 */
public final class LauncherConfiguration {

    private File storageLoc;
    private String galimulatorFile;
    private boolean extensionSupport;
    private boolean patchSupport;
    private File extensionsFolder;
    private File patchesFolder;
    private DiscoveredExtensionList extensions;
    private JSONObject extensionsObject;

    LauncherConfiguration(File configLoc) throws IOException {
        storageLoc = configLoc;
        load();
    }

    /**
     * Sets the file where the configuration will be saved to.
     *
     * @param file The file to save the configuration to
     */
    public void setStorageFile(File file) {
        storageLoc = file;
    }

    /**
     * Obtains the file where the configuration will be saved to
     *
     * @return The file
     */
    public File getStorageFile() {
        return storageLoc;
    }

    public void load() throws IOException {
        if (!storageLoc.exists()) {
            // Set defaults
            galimulatorFile = "./jar/galimulator-desktop.jar";
            extensionSupport = true;
            patchSupport = false; // TODO make "true" the default, when it is a useable setting
            extensionsFolder = new File("extensions/");
            patchesFolder = new File("patches/");
            extensions = new DiscoveredExtensionList(extensionsFolder);
            extensionsObject = new JSONObject();
            extensionsObject.put("enabled", new JSONArray());
            return;
        }
        try (FileInputStream fis = new FileInputStream(storageLoc)) {
            String data = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObj = new JSONObject(data);
            galimulatorFile = jsonObj.getString("target-jar");
            extensionSupport = jsonObj.getBoolean("do-extensions");
            patchSupport = jsonObj.getBoolean("do-patches");
            extensionsFolder = new File(jsonObj.getString("folder-extensions"));
            patchesFolder = new File(jsonObj.getString("folder-patches"));
            extensionsObject = jsonObj.getJSONObject("extensions");
            extensions = new DiscoveredExtensionList(extensionsFolder);
            JSONArray arr = extensionsObject.getJSONArray("enabled");
            for (Object enabledExtension : arr) {
                String[] entry = enabledExtension.toString().split("@");
                if (entry.length == 2) {
                    for (ExtensionPrototype prototype : extensions.getPrototypes(entry[0])) {
                        if (prototype.version.equals(entry[1])) {
                            prototype.enabled = true;
                        }
                    }
                }
            }
        }
    }

    public void save() throws IOException {
        JSONObject object = new JSONObject();
        object.put("target-jar", galimulatorFile);
        object.put("do-extensions", extensionSupport);
        object.put("do-patches", patchSupport);
        object.put("folder-extensions", extensionsFolder.getAbsolutePath());
        object.put("folder-patches", patchesFolder.getAbsolutePath());

        JSONArray arr = extensionsObject.getJSONArray("enabled");
        HashSet<String> s = new HashSet<>();
        arr.forEach(e -> s.add(e.toString()));
        for (ExtensionPrototype prototype : extensions.getPrototypes()) {
            if (prototype.enabled) {
                s.add(String.format("%s@%s", prototype.name, prototype.version));
            } else {
                s.remove(String.format("%s@%s", prototype.name, prototype.version));
            }
        }
        JSONArray nArr = new JSONArray();
        s.forEach(nArr::put);
        extensionsObject.put("enabled", nArr);
        object.put("extensions", extensionsObject);

        try (FileOutputStream fos = new FileOutputStream(storageLoc)) {
            fos.write(object.toString(4).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Sets the jar that should be loaded when the "Play" button is pressed
     *
     * @param selected The java archive file to load
     */
    public void setTargetJar(File selected) {
        galimulatorFile = selected.getAbsolutePath();
    }

    public File getTargetJar() {
        return new File(galimulatorFile);
    }

    public boolean hasExtensionsEnabled() {
        return extensionSupport;
    }

    /**
     * Whether or not the applications should run with extension (Mixin) support.
     * Extensions make use of a custom classloader.
     *
     * @param enabled The enable state
     */
    public void setExtensionsEnabled(boolean enabled) {
        extensionSupport = enabled;
    }

    public boolean hasPatchesEnabled() {
        return patchSupport;
    }

    public void setPatchesEnabled(boolean enabled) {
        patchSupport = enabled;
    }

    public void setPatchesFolder(File folder) {
        patchesFolder = folder;
    }

    public void setExtensionsFolder(File folder) {
        extensionsFolder = folder;
    }

    public File getPatchesFolder() {
        return patchesFolder;
    }

    public File getExtensionsFolder() {
        return extensionsFolder;
    }

    public void setExtensionList(DiscoveredExtensionList extList) {
        extensions = extList;
    }

    public DiscoveredExtensionList getExtensionList() {
        if (extensions != null && extensions.getFolder().equals(getExtensionsFolder())) {
            return extensions; // List does not need updating
        }
        extensions = new DiscoveredExtensionList(extensionsFolder);
        JSONArray arr = extensionsObject.getJSONArray("enabled");
        for (Object enabledExtension : arr) {
            String[] entry = enabledExtension.toString().split("@");
            if (entry.length == 2) {
                for (ExtensionPrototype prototype : extensions.getPrototypes(entry[0])) {
                    if (prototype.version.equals(entry[1])) {
                        prototype.enabled = true;
                    }
                }
            }
        }
        return extensions;
    }
}
