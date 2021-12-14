package de.geolykt.sleadn;

import java.util.List;
import java.util.Optional;

public final class ExtensionDescriptor {

    public final String name;
    public final String identifier;
    public final String description;
    public final String license;
    public final String latestVersion;
    public final List<String> authors;
    public final List<String> tags;
    public final Optional<List<VersionDescriptor>> versions;

    ExtensionDescriptor(String name, String identifier, String description, String license, String latestVersion, List<String> authors,
            List<String> tags, Optional<List<VersionDescriptor>> versions) {
        this.name = name;
        this.identifier = identifier;
        this.description = description;
        this.license = license;
        this.latestVersion = latestVersion;
        this.authors = authors;
        this.tags = tags;
        this.versions = versions;
    }
}
