package de.geolykt.starloader.mod;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.geolykt.starloader.Starloader;

public abstract class Extension {
    private final ExtensionDescription description;
    @NotNull
    private final Logger logger;

    @SuppressWarnings("null")
    protected Extension() {
        if (Starloader.getExtensionManager().currentlyLoadedExt != null) {
            this.description = Starloader.getExtensionManager().currentlyLoadedExt;
            Starloader.getExtensionManager().currentlyLoadedExt = null;
        } else {
            throw new IllegalStateException("Unable to obtain extension description from extension manager");
        }
        logger = LoggerFactory.getLogger(this.getClass());
    }

    public void preInitialize() { }

    public void initialize() { }

    public void postInitialize() { }

    /**
     * @deprecated This method is a boilerplate solution, for more info see below
     *
     * WARNING: The implementation of this method should be coded very carefully.
     * The implementation should be thread-safe and avoid deadlocks and should not rely on the existence of other threads.
     * For more info, see {@link Runtime#addShutdownHook(Thread)}.
     * Additionally due to the nature of how extensions unload, it is recommended that are more recommendable
     * method for unloading is used, for example SLAPI offers the ApplicationStopEvent and SignalExtensionTerminationExtension.
     * Other extension libraries might offer similar alternatives that are a lot more safer to use.
     */
    @Deprecated(forRemoval = false)
    public void preTerminate() { }

    /**
     * @deprecated This method is a boilerplate solution, for more info see below
     *
     * WARNING: The implementation of this method should be coded very carefully.
     * The implementation should be thread-safe and avoid deadlocks and should not rely on the existence of other threads.
     * For more info, see {@link Runtime#addShutdownHook(Thread)}.
     * Additionally due to the nature of how extensions unload, it is recommended that are more recommendable
     * method for unloading is used, for example SLAPI offers the ApplicationStopEvent and SignalExtensionTerminationExtension.
     * Other extension libraries might offer similar alternatives that are a lot more safer to use.
     */
    @Deprecated(forRemoval = false)
    public void terminate() { }

    /**
     * @deprecated This method is a boilerplate solution, for more info see below
     *
     * WARNING: The implementation of this method should be coded very carefully.
     * The implementation should be thread-safe and avoid deadlocks and should not rely on the existence of other threads.
     * For more info, see {@link Runtime#addShutdownHook(Thread)}.
     * Additionally due to the nature of how extensions unload, it is recommended that are more recommendable
     * method for unloading is used, for example SLAPI offers the ApplicationStopEvent and SignalExtensionTerminationExtension.
     * Other extension libraries might offer similar alternatives that are a lot more safer to use.
     */
    @Deprecated(forRemoval = false)
    public void postTerminate() { }

    /**
     * @deprecated This method is a boilerplate solution, for more info see below
     *
     * WARNING: The implementation of this method should be coded very carefully.
     * The implementation should be thread-safe and avoid deadlocks and should not rely on the existence of other threads.
     * For more info, see {@link Runtime#addShutdownHook(Thread)}.
     * Additionally due to the nature of how extensions unload, it is recommended that are more recommendable
     * method for unloading is used, for example SLAPI offers the ApplicationStopEvent and SignalExtensionTerminationExtension.
     * Other extension libraries might offer similar alternatives that are a lot more safer to use.
     *
     * After calling this method, the entirety of the Extension is unloaded, which means classes provided by the extension
     * become unusable.
     */
    @Deprecated(forRemoval = false)
    public void unload() { }

    @SuppressWarnings("null")
    @NotNull
    public final ExtensionDescription getDescription() {
        return this.description;
    }

    @NotNull
    public final Logger getLogger() {
        return this.logger;
    }

    public static class ExtensionDescription {

        @NotNull
        private final String name;

        @NotNull
        private final String version;

        @NotNull
        private final List<String> authors;

        @NotNull
        private final List<String> dependents = new ArrayList<>();

        @NotNull
        private final DiscoveredExtension origin;

        public ExtensionDescription(@NotNull String name, @NotNull String version, @NotNull List<String> authors, @NotNull DiscoveredExtension origin) {
            this.name = name;
            this.version = version;
            this.authors = authors;
            this.origin = origin;
        }

        @NotNull
        public String getName() {
            return name;
        }

        @NotNull
        public String getVersion() {
            return version;
        }

        @NotNull
        public List<String> getAuthors() {
            return authors;
        }

        @NotNull
        public List<String> getDependents() {
            return dependents;
        }

        @NotNull
        public DiscoveredExtension getOrigin() {
            return origin;
        }
    }
}
