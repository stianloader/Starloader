package de.geolykt.sleadn;

public final class DependencyDescriptor {
    public final String dependencyId;
    public final String dependencyVersion;
    public final boolean hardDepend;

    public DependencyDescriptor(String dependencyId, String dependencyVersion, boolean hardDepend) {
        this.dependencyId = dependencyId;
        this.dependencyVersion = dependencyVersion;
        this.hardDepend = hardDepend;
    }
}
