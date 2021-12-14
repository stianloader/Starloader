package de.geolykt.sleadn;

import java.util.List;

public final class VersionDescriptor {

    public final String versionId;
    public final String url;
    public final List<DependencyDescriptor> dependencies;

    public VersionDescriptor(String versionName, String location, List<DependencyDescriptor> dependencies) {
        this.versionId = versionName;
        this.url = location;
        this.dependencies = dependencies;
    }
}
