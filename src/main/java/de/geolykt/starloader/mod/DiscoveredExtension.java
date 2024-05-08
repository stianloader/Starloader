package de.geolykt.starloader.mod;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stianloader.micromixin.transform.internal.util.Objects;

import net.minestom.server.extras.selfmodification.MinestomExtensionClassLoader;

import de.geolykt.starloader.mod.DiscoveredExtension.ExternalDependencies.Repository;
import de.geolykt.starloader.util.JavaInterop;

public final class DiscoveredExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveredExtension.class);

    public static final String NAME_REGEX = "[A-Za-z][_A-Za-z0-9\\-]+";
    private String name;
    private String entrypoint;
    private String version;
    private String mixinConfig;
    private String[] authors;
    private String[] codeModifiers;
    private String[] dependencies;
    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    private String accessWidener;
    private String reversibleAccessSetter;
    private ExternalDependencies externalDependencies;
    public transient List<URL> files = new LinkedList<>();
    @NotNull
    private transient LoadStatus loadStatus = LoadStatus.LOAD_SUCCESS;
    @Internal
    public transient MinestomExtensionClassLoader loader;

    @SuppressWarnings("null")
    @NotNull
    public String getName() {
        return name;
    }

    @SuppressWarnings("null")
    @NotNull
    public String getEntrypoint() {
        return entrypoint;
    }

    @SuppressWarnings("null")
    @NotNull
    public String getVersion() {
        return version;
    }

    @SuppressWarnings("null")
    @NotNull
    public String getMixinConfig() {
        return mixinConfig;
    }

    @SuppressWarnings("null")
    @NotNull
    public String[] getAuthors() {
        return authors;
    }

    @SuppressWarnings("null")
    @NotNull
    public String[] getCodeModifiers() {
        return codeModifiers;
    }

    @SuppressWarnings("null")
    @NotNull
    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public String getAccessWidener() {
        return accessWidener;
    }

    /**
     * Obtains the reversible access setter file required by this extension.
     * Returns an empty string if not set.
     *
     * @return The path to the reversible access setter file relative to the {@link #files} of this extension.
     * @since 4.0.0
     */
    @SuppressWarnings("null")
    @NotNull
    public String getReversibleAccessSetter() {
        return this.reversibleAccessSetter;
    }

    @SuppressWarnings("null")
    @NotNull
    public String[] getDependencies() {
        return dependencies;
    }

    @SuppressWarnings("null")
    @NotNull
    public ExternalDependencies getExternalDependencies() {
        return externalDependencies;
    }

    void setLoadStatus(@NotNull LoadStatus loadStatus) {
        this.loadStatus = loadStatus;
        if (loadStatus != LoadStatus.LOAD_SUCCESS && this.loader != null) {
            try {
                this.loader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.loader = null;
        }
    }

    @NotNull
    public LoadStatus getLoadStatus() {
        return loadStatus;
    }

    public static void verifyIntegrity(@NotNull DiscoveredExtension extension) {
        if (extension.name == null) {
            StringBuilder fileList = new StringBuilder();
            for (URL f : extension.files) {
                fileList.append(f.toExternalForm()).append(", ");
            }
            LOGGER.error("Extension with no name. (at {}})", fileList);
            LOGGER.error("Extension at ({}) will not be loaded.", fileList);
            extension.loadStatus = DiscoveredExtension.LoadStatus.INVALID_NAME;

            // To ensure @NotNull: name = INVALID_NAME
            extension.name = extension.loadStatus.name();
            return;
        }
        if (!extension.name.matches(NAME_REGEX)) {
            LOGGER.error("Extension '{}' specified an invalid name.", extension.name);
            LOGGER.error("Extension '{}' will not be loaded.", extension.name);
            extension.loadStatus = DiscoveredExtension.LoadStatus.INVALID_NAME;

            // To ensure @NotNull: name = INVALID_NAME
            extension.name = extension.loadStatus.name();
            return;
        }
        if (extension.entrypoint == null) {
            LOGGER.error("Extension '{}' did not specify an entry point (via 'entrypoint').", extension.name);
            LOGGER.error("Extension '{}' will not be loaded.", extension.name);
            extension.loadStatus = DiscoveredExtension.LoadStatus.NO_ENTRYPOINT;

            // To ensure @NotNull: entrypoint = NO_ENTRYPOINT
            extension.entrypoint = extension.loadStatus.name();
            return;
        }
        // Handle defaults
        // If we reach this code, then the extension will most likely be loaded:
        if (extension.version == null) {
            LOGGER.warn("Extension '{}' did not specify a version.", extension.name);
            LOGGER.warn("Extension '{}' will continue to load but should specify a plugin version.", extension.name);
            extension.version = "Unspecified";
        }
        if (extension.mixinConfig == null) {
            extension.mixinConfig = "";
        }
        if (extension.accessWidener == null) {
            extension.accessWidener = "";
        } else {
            LOGGER.warn("Extension '{}' specified an access widener, however access wideners are scheduled for removal and will not work in SLL 5.0. Use reversible access setters (via the 'reversibleAccessSetter' field) instead.", extension.name);
        }
        if (extension.reversibleAccessSetter == null) {
            extension.reversibleAccessSetter = "";
        }
        if (extension.authors == null) {
            extension.authors = new String[0];
        }
        if (extension.codeModifiers == null) {
            extension.codeModifiers = new String[0];
        }
        // No dependencies were specified
        if (extension.dependencies == null) {
            extension.dependencies = new String[0];
        }
        // No external dependencies were specified;
        if (extension.externalDependencies == null) {
            extension.externalDependencies = new ExternalDependencies();
        }
    }

    public enum LoadStatus {
        LOAD_SUCCESS("Actually, it did not fail. This message should not have been printed."),
        MISSING_DEPENDENCIES("Missing dependencies, check your logs."),
        INVALID_NAME("Invalid name."),
        NO_ENTRYPOINT("No entrypoint specified."),
        FAILED_TO_SETUP_CLASSLOADER("Extension classloader could not be setup."),
        LOAD_FAILED("Load failed. See logs for more information.");

        @NotNull
        private final String message;

        LoadStatus(@NotNull String message) {
            this.message = message;
        }

        @NotNull
        public String getMessage() {
            return message;
        }
    }

    public static final class ExternalDependencies {
        Repository[] repositories = new Repository[0];
        String[] artifacts = new String[0];

        static class Repository {
            @NotNull
            String name = "";
            @NotNull
            String url = "";

            @Deprecated
            @ScheduledForRemoval(inVersion = "5.0.0")
            Repository() {
                // No-args constructor (only there for legacy reasons)
            }

            public Repository(String name, String url) {
                this.name = Objects.requireNonNull(name, "name may not be null");
                this.url = Objects.requireNonNull(url, "url may not be null");
            }
        }
    }

    @NotNull
    public static DiscoveredExtension fromJSON(@NotNull InputStream in) throws IOException {
        try {
            JSONObject json = new JSONObject(JavaInterop.readAllBytes(in));
            DiscoveredExtension extension = new DiscoveredExtension();
            extension.name = json.optString("name", null);
            extension.accessWidener = json.optString("accessWidener", null);
            extension.mixinConfig = json.optString("mixinConfig", null);
            extension.reversibleAccessSetter = json.optString("reversibleAccessSetter", null);
            extension.entrypoint = json.optString("entrypoint", null);
            extension.version = json.optString("version", null);
            extension.authors = json.optJSONArray("authors", new JSONArray()).toList().toArray(new String[0]);
            extension.codeModifiers = json.optJSONArray("codeModifiers", new JSONArray()).toList().toArray(new String[0]);
            extension.dependencies = json.optJSONArray("dependencies", new JSONArray()).toList().toArray(new String[0]);

            if (json.has("externalDependencies")) {
                JSONObject externalDepsJson = json.getJSONObject("externalDependencies");
                List<Repository> repositories = new ArrayList<>();
                JSONArray repositoriesJson = externalDepsJson.getJSONArray("repositories");
                for (Object repo : repositoriesJson) {
                    if (!(repo instanceof JSONObject)) {
                        throw new IOException("Malformed element in repositories: Expected JSONObject, got " + repo.getClass());
                    }

                    JSONObject repoJson = (JSONObject) repo;
                    repositories.add(new Repository(repoJson.getString("name"), repoJson.getString("url")));
                }

                extension.externalDependencies = new ExternalDependencies();
                extension.externalDependencies.repositories = repositories.toArray(new Repository[0]);
                extension.externalDependencies.artifacts = externalDepsJson.optJSONArray("artifacts", new JSONArray()).toList().toArray(new String[0]);
            }

            return extension;
        } catch (JSONException e) {
            throw new IOException("The provided json is invalid and thus cannot be parsed as a extension descriptor.", e);
        }
    }
}
