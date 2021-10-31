package de.geolykt.starloader.mod;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public abstract class Extension {
    // Set by reflection
    // FIXME issue: null within the constructor
    private ExtensionDescription description;
    // Set by reflection
    // FIXME issue: null within the constructor
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
    public final ExtensionDescription getDescription() {
        ExtensionDescription description = this.description;
        if (description == null) {
            throw new IllegalStateException("You cannot call getDescription() in the constructor. Sorry.");
        }
        return description;
    }

    @NotNull
    public final Logger getLogger() {
        Logger logger = this.logger;
        if (logger == null) {
            throw new IllegalStateException("You cannot call getLogger() in the constructor. Sorry.");
        }
        return logger;
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
