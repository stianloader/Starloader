package de.geolykt.starloader.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

import de.geolykt.starloader.mod.ExtensionPrototypeList;
import de.geolykt.starloader.mod.ExtensionPrototype;

/**
 * Object that stores the configuration of the launcher.
 */
public final class LauncherConfiguration {

    private File dataFolder;
    private ExtensionPrototypeList extensions;
    private File extensionsFolder;
    private JSONObject extensionsObject;
    private boolean extensionSupport;
    private File galimulatorFile;
    private File patchesFolder;
    private boolean patchSupport;
    private String sleadnAuthority;
    private File storageLoc;

    LauncherConfiguration(File configLoc) throws IOException {
        storageLoc = configLoc;
        load();
    }

    public File getDataFolder() {
        return this.dataFolder;
    }

    public ExtensionPrototypeList getExtensionList() {
        if (extensions != null && extensions.getFolder().equals(getExtensionsFolder())) {
            return extensions; // List does not need updating
        }
        extensions = new ExtensionPrototypeList(extensionsFolder);
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

    public String getExtensionRepository() {
        return sleadnAuthority;
    }

    public File getExtensionsFolder() {
        return extensionsFolder;
    }

    public File getPatchesFolder() {
        return patchesFolder;
    }

    /**
     * Obtains the file where the configuration will be saved to.
     *
     * @return The file
     */
    public File getStorageFile() {
        return storageLoc;
    }

    public File getTargetJar() {
        return galimulatorFile;
    }

    public boolean hasExtensionsEnabled() {
        return extensionSupport;
    }

    public boolean hasPatchesEnabled() {
        return patchSupport;
    }

    public void load() throws IOException {
        if (!storageLoc.exists()) {
            // Set defaults
            File gameDir = Utils.getGameDir("galimulator");
            galimulatorFile = new File(gameDir, "jar/galimulator-desktop.jar");
            if (!galimulatorFile.exists()) {
                galimulatorFile = new File("jar", "galimulator-desktop.jar");
                dataFolder = new File("data"); // TODO which data folder should be used?
            } else {
                dataFolder = new File(gameDir, "data");
            }
            extensionSupport = true;
            patchSupport = false; // TODO make "true" the default, when it is a usable setting
            extensionsFolder = new File(Utils.getApplicationFolder(), "extensions");
            extensionsFolder.mkdirs();
            patchesFolder = new File(Utils.getApplicationFolder(), "patches");
            extensions = new ExtensionPrototypeList(extensionsFolder);
            extensionsObject = new JSONObject();
            extensionsObject.put("enabled", new JSONArray());
            sleadnAuthority = "https://localhost:26676/";
            return;
        }
        try (FileInputStream fis = new FileInputStream(storageLoc)) {
            String data = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObj = new JSONObject(data);
            galimulatorFile = new File(jsonObj.getString("target-jar"));
            extensionSupport = jsonObj.getBoolean("do-extensions");
            patchSupport = jsonObj.getBoolean("do-patches");
            extensionsFolder = new File(jsonObj.getString("folder-extensions"));
            patchesFolder = new File(jsonObj.getString("folder-patches"));
            dataFolder = new File(jsonObj.getString("folder-data"));
            extensionsObject = jsonObj.getJSONObject("extensions");
            extensions = new ExtensionPrototypeList(extensionsFolder);
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
            sleadnAuthority = jsonObj.optString("sleadn-authority", "https://localhost:26676/");
        }
    }

    public void save() throws IOException {
        JSONObject object = new JSONObject();
        object.put("target-jar", galimulatorFile);
        object.put("do-extensions", extensionSupport);
        object.put("do-patches", patchSupport);
        object.put("folder-extensions", extensionsFolder.getAbsolutePath());
        object.put("folder-patches", patchesFolder.getAbsolutePath());
        object.put("folder-data", dataFolder.getAbsolutePath());

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
        object.put("sleadn-authority", sleadnAuthority);

        try (FileOutputStream fos = new FileOutputStream(storageLoc)) {
            fos.write(object.toString(4).getBytes(StandardCharsets.UTF_8));
        }
    }

    public void setDataFolder(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void setExtensionList(ExtensionPrototypeList extList) {
        extensions = extList;
    }

    public void setExtensionRepository(String authority) {
        this.sleadnAuthority = authority;
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

    public void setExtensionsFolder(File folder) {
        extensionsFolder = folder;
    }

    public void setPatchesEnabled(boolean enabled) {
        patchSupport = enabled;
    }

    public void setPatchesFolder(File folder) {
        patchesFolder = folder;
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
     * Sets the jar that should be loaded when the "Play" button is pressed.
     *
     * @param selected The java archive file to load
     */
    public void setTargetJar(File selected) {
        galimulatorFile = selected;
    }
}
