package de.geolykt.starloader.mod;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public abstract class Extension {
    // Set by reflection
    private ExtensionDescription description;
    // Set by reflection
    private Logger logger;

    protected Extension() {

    }

    public void preInitialize() {

    }

    public void initialize() {

    }

    public void postInitialize() {

    }

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
    public void preTerminate() {

    }

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
    public void terminate() {

    }

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
    public void postTerminate() {

    }

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
    public void unload() {

    }

    @NotNull
    public ExtensionDescription getDescription() {
        return description;
    }

    @NotNull
    public Logger getLogger() {
        return logger;
    }


    public static class ExtensionDescription {
        private final String name;
        private final String version;
        private final List<String> authors;
        private final List<String> dependents = new ArrayList<>();
        private final DiscoveredExtension origin;

        ExtensionDescription(@NotNull String name, @NotNull String version, @NotNull List<String> authors, @NotNull DiscoveredExtension origin) {
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
        DiscoveredExtension getOrigin() {
            return origin;
        }
    }
}
