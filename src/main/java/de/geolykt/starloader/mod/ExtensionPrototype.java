package de.geolykt.starloader.mod;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import de.geolykt.starloader.launcher.IDELauncher;

/**
 * A prototype of an extension. This class is different to the DiscoveredExtension class
 * as it is only used within the launcher to explain whether a jar should be loaded or not.
 */
public class ExtensionPrototype {
    @Nullable
    @UnmodifiableView
    private final Map<String, String> definedProperties;
    public boolean enabled;
    public final List<URL> originURLs;

    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public ExtensionPrototype(List<URL> originURLs) {
        this(originURLs, false, null);
    }

    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public ExtensionPrototype(List<URL> originURLs, boolean enabled) {
        this(originURLs, enabled, null);
    }

    @ApiStatus.AvailableSince("4.0.0-a20240711")
    @Contract(pure = true)
    public ExtensionPrototype(List<URL> originURLs, boolean enabled, @Nullable Map<String, String> properties) {
        this.originURLs = originURLs;
        this.enabled = enabled;
        if (properties != null) {
            properties = Collections.unmodifiableMap(properties);
        }
        this.definedProperties = properties;
    }

    /**
     * Obtains the placeholder properties applicable to this extension prototype.
     * When parsing the extension.json file of the mod backed by this {@link ExtensionPrototype},
     * placeholders are expanded using the key-value pairs supplied by this method.
     * If this method returns <code>null</code>, this step shall not be performed.
     *
     * <p>Placeholders have the pattern of '<code>${propertyKey}</code>'. When found,
     * the placeholder will be replaced with the corresponding value declared by the
     * map returned by this method. If no value is present in the returned map, then
     * the placeholder is skipped and a warning may get logged hinting at the absence
     * of the key.
     *
     * <p>This process is required as not doing so would mean that when using
     * these placeholders in the extension.json files of mods will not get applied
     * when launching from the IDE, which can result in crashes during the dependency
     * resolution process.
     *
     * <p><b>Warning</b>: When launching the mod outside the IDE, this feature is <b>not</b> present
     * as it will only be set by the {@link IDELauncher}.
     *
     * @return A map mapping the placeholder keys to their respective values, or <code>null</code>
     * if the process should be skipped.
     * @since 4.0.0-a20240711
     */
    @Nullable
    @ApiStatus.AvailableSince("4.0.0-a20240711")
    @Contract(pure = true)
    @UnmodifiableView
    public Map<String, String> getDefinedProperties() {
        return this.definedProperties;
    }

    @Override
    public String toString() {
        return "ExtensionPrototype[URLs=" + this.originURLs + "]";
    }
}
