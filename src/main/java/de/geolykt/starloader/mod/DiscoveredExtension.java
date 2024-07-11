package de.geolykt.starloader.mod;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stianloader.picoresolve.exclusion.Exclusion;
import org.stianloader.picoresolve.exclusion.ExclusionContainer;
import org.stianloader.picoresolve.exclusion.ExclusionContainer.ExclusionMode;
import org.stianloader.picoresolve.version.VersionRange;

import net.minestom.server.extras.selfmodification.MinestomExtensionClassLoader;

import de.geolykt.starloader.launcher.Utils;
import de.geolykt.starloader.util.JavaInterop;

public final class DiscoveredExtension {

    public static final class ExternalDependencies {
        @NotNull
        private final List<ExternalDependencyArtifact> artifacts = new ArrayList<>();
        @NotNull
        private final List<ExternalRepository> repositories = new ArrayList<>();

        public ExternalDependencies(@NotNull List<ExternalRepository> repositories, @NotNull List<ExternalDependencyArtifact> artifacts) {
            this.repositories.addAll(repositories);
            this.artifacts.addAll(artifacts);
        }

        @NotNull
        public List<ExternalDependencyArtifact> getArtifacts() {
            return this.artifacts;
        }

        @NotNull
        public Collection<ExternalRepository> getRepositories() {
            return this.repositories;
        }
    }

    public static final class ExternalDependencyArtifact {
        @NotNull
        private final String artifact;
        @Nullable
        private final String classifier;
        @NotNull
        private final ExclusionContainer<Exclusion> exclusions;
        @NotNull
        private final String extension;
        @NotNull
        private final String group;
        @NotNull
        private final VersionRange version;

        public ExternalDependencyArtifact(@NotNull String group, @NotNull String artifact, @NotNull VersionRange version,
                @Nullable String classifier, @NotNull String extension, @NotNull ExclusionContainer<Exclusion> exclusions) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
            this.classifier = classifier;
            this.extension = extension;
            this.exclusions = exclusions;
        }

        @NotNull
        public String getArtifact() {
            return this.artifact;
        }

        @Nullable
        public String getClassifier() {
            return this.classifier;
        }

        @NotNull
        public ExclusionContainer<Exclusion> getExclusions() {
            return this.exclusions;
        }

        @NotNull
        public String getExtension() {
            return this.extension;
        }

        @NotNull
        public String getGroup() {
            return this.group;
        }

        @NotNull
        public VersionRange getVersion() {
            return this.version;
        }
    }

    public static class ExternalRepository {
        private final boolean mirrorable;
        private final boolean mirrorOnly;
        @NotNull
        private final String name;
        @NotNull
        private final String url;

        public ExternalRepository(@NotNull String name, @NotNull String url, boolean mirrorable, boolean mirrorOnly) {
            this.name = Objects.requireNonNull(name, "name may not be null");
            this.url = Objects.requireNonNull(url, "url may not be null");
            this.mirrorable = mirrorable;
            this.mirrorOnly = mirrorOnly;
        }

        @NotNull
        public String getName() {
            return this.name;
        }

        @NotNull
        public String getUrl() {
            return this.url;
        }

        public boolean isMirrorable() {
            return this.mirrorable;
        }

        public boolean isMirrorOnly() {
            return this.mirrorOnly;
        }
    }

    public enum LoadStatus {
        FAILED_TO_SETUP_CLASSLOADER("Extension classloader could not be setup."),
        INVALID_NAME("Invalid name."),
        LOAD_FAILED("Load failed. See logs for more information."),
        LOAD_SUCCESS("Actually, it did not fail. This message should not have been printed."),
        MISSING_DEPENDENCIES("Missing dependencies, check your logs."),
        NO_ENTRYPOINT("No entrypoint specified.");

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

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveredExtension.class);
    public static final String NAME_REGEX = "[A-Za-z][_A-Za-z0-9\\-]+";

    @NotNull
    @ApiStatus.AvailableSince("4.0.0-a20240711")
    public static DiscoveredExtension fromJSON(@NotNull InputStream in, @NotNull ExtensionPrototype prototype) throws IOException {
        try {
            String readInput = new String(JavaInterop.readAllBytes(in), StandardCharsets.UTF_8);
            Map<String, String> properties = prototype.getDefinedProperties();
            if (properties != null) {
                readInput = Utils.applyPlaceholders(prototype, readInput, 0, properties);
            }

            JSONObject json = new JSONObject(readInput);
            DiscoveredExtension extension = new DiscoveredExtension(prototype);
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
                List<ExternalRepository> repositories = new ArrayList<>();
                JSONArray repositoriesJson = externalDepsJson.getJSONArray("repositories");
                for (Object repo : repositoriesJson) {
                    if (!(repo instanceof JSONObject)) {
                        throw new IOException("Malformed element in repositories: Expected JSONObject, got " + repo.getClass());
                    }

                    JSONObject repoJson = (JSONObject) repo;
                    boolean mirrorable = repoJson.optBoolean("mirrorable", true);
                    boolean mirrorOnly = repoJson.optBoolean("mirrorOnly", false);
                    String name = repoJson.optString("name", null);
                    String url = repoJson.optString("url", null);

                    if (name == null) {
                        throw new IOException("Malformed element in repositories: 'name' is null");
                    }
                    if (url == null) {
                        throw new IOException("Malformed element in repositories: 'url' is null");
                    }

                    ExternalRepository repository = new ExternalRepository(name, url, mirrorable, mirrorOnly);
                    repositories.add(repository);
                }

                List<ExternalDependencyArtifact> artifacts = new ArrayList<>();

                JSONArray artifactsJson = externalDepsJson.optJSONArray("artifacts");
                if (artifactsJson != null) {
                    int i = artifactsJson.length();
                    while (i-- != 0) {
                        Object o = artifactsJson.get(i);
                        if (o instanceof JSONObject) {
                            JSONObject artifactJSON = (JSONObject) o;
                            String group = artifactJSON.getString("group");
                            String artifact = artifactJSON.getString("artifact");
                            String version = artifactJSON.getString("version");
                            String classifier = artifactJSON.optString("classifier", null);
                            String gavceExtension = artifactJSON.optString("extension", "jar");

                            if (!gavceExtension.equals("jar")) {
                                LOGGER.warn("Unsupported GAVCE extension: '{}'. Beware.", gavceExtension);
                            }

                            assert group != null;
                            assert artifact != null;
                            assert version != null;

                            ExclusionContainer<Exclusion> exclusions = new ExclusionContainer<>(ExclusionMode.ANY);

                            JSONArray exclusionsJSON = artifactJSON.optJSONArray("exclusions");
                            if (exclusionsJSON != null) {
                                int j = exclusionsJSON.length();
                                while (j-- != 0) {
                                    String exclusion = exclusionsJSON.getString(j);
                                    String exclusionGroup = exclusion.split(":")[0];
                                    String exclusionArtifact = exclusion.split(":")[1];
                                    exclusions.addChild(new Exclusion(exclusionGroup, exclusionArtifact));
                                }
                            }

                            artifacts.add(0, new ExternalDependencyArtifact(group, artifact, VersionRange.parse(version), classifier, gavceExtension, exclusions));
                        } else if (o instanceof String) {
                            @NotNull String[] gavceSplits = ((String) o).split(":");
                            if (gavceSplits.length != 3 && gavceSplits.length != 4) {
                                throw new IOException("Illegal GAV string (examples: 'org.example:artifact:1.0', 'org.example:artifact:1.3:remapped'): " + o);
                            }
                            String group = gavceSplits[0];
                            String artifact = gavceSplits[1];
                            VersionRange version = VersionRange.parse(gavceSplits[2]);
                            String classifier = null;
                            if (gavceSplits.length >= 4) {
                                classifier = gavceSplits[3];
                            }
                            artifacts.add(0, new ExternalDependencyArtifact(group, artifact, version, classifier, "jar", ExclusionContainer.empty()));
                        } else {
                            throw new IOException("Malformed element in artifacts: Expected JSONObject or String, instead got " + o.getClass().getName());
                        }
                    }
                }

                extension.externalDependencies = new ExternalDependencies(repositories, artifacts);
            }

            return extension;
        } catch (JSONException e) {
            throw new IOException("The provided json is invalid and thus cannot be parsed as a extension descriptor.", e);
        }
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
            extension.externalDependencies = new ExternalDependencies(Collections.emptyList(), Collections.emptyList());
        }
    }

    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    private String accessWidener;
    private String[] authors;
    private String[] codeModifiers;
    private String[] dependencies;
    private String entrypoint;
    private ExternalDependencies externalDependencies;
    public transient List<URL> files = new LinkedList<>();
    @Internal
    public transient MinestomExtensionClassLoader loader;
    @NotNull
    private transient LoadStatus loadStatus = LoadStatus.LOAD_SUCCESS;
    private String mixinConfig;
    private String name;
    private String reversibleAccessSetter;

    @NotNull
    @ApiStatus.AvailableSince("4.0.0-a20240711")
    private final ExtensionPrototype sourcePrototype;

    private String version;

    @ApiStatus.AvailableSince("4.0.0-a20240711")
    @Contract(pure = true)
    private DiscoveredExtension(@NotNull ExtensionPrototype prototype) {
        this.sourcePrototype = prototype;
    }

    @SuppressWarnings("null")
    @NotNull
    @Deprecated
    @ScheduledForRemoval(inVersion = "5.0.0")
    public String getAccessWidener() {
        return this.accessWidener;
    }

    @SuppressWarnings("null")
    @NotNull
    public String[] getAuthors() {
        return this.authors;
    }

    @SuppressWarnings("null")
    @NotNull
    public String[] getCodeModifiers() {
        return this.codeModifiers;
    }

    @SuppressWarnings("null")
    @NotNull
    public String[] getDependencies() {
        return this.dependencies;
    }

    @SuppressWarnings("null")
    @NotNull
    public String getEntrypoint() {
        return this.entrypoint;
    }

    @SuppressWarnings("null")
    @NotNull
    public ExternalDependencies getExternalDependencies() {
        return this.externalDependencies;
    }

    @NotNull
    public LoadStatus getLoadStatus() {
        return this.loadStatus;
    }

    @SuppressWarnings("null")
    @NotNull
    public String getMixinConfig() {
        return this.mixinConfig;
    }

    @SuppressWarnings("null")
    @NotNull
    public String getName() {
        return this.name;
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

    @NotNull
    @ApiStatus.AvailableSince("4.0.0-a20240711")
    @Contract(pure = true)
    public ExtensionPrototype getSourcePrototype() {
        return this.sourcePrototype;
    }

    @SuppressWarnings("null")
    @NotNull
    public String getVersion() {
        return this.version;
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
}
