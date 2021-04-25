package de.geolykt.starloader.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

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
            patchSupport = false; // TODO make "true" the default
            extensionsFolder = new File("extensions/");
            patchesFolder = new File("patches/");
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
        }
    }

    public void save() throws IOException {
        JSONObject object = new JSONObject();
        object.put("target-jar", galimulatorFile);
        object.put("do-extensions", extensionSupport);
        object.put("do-patches", patchSupport);
        object.put("folder-extensions", extensionsFolder.getAbsolutePath());
        object.put("folder-patches", patchesFolder.getAbsolutePath());

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
}
