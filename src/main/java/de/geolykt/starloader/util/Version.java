package de.geolykt.starloader.util;

public class Version implements Comparable<Version> {

    public final int major;
    public final int minor;
    public final int patch;
    public final String target;
    public final String notes;
    public final Stabillity stabillity;

    public Version(int major, int minor, int patch) {
        this(major, minor, patch, "universal", null, Stabillity.SNAPSHOT);
    }

    public Version(int major, int minor, int patch, Stabillity stabillity) {
        this(major, minor, patch, "universal", null, stabillity);
    }

    public Version(int major, int minor, int patch, String target, Stabillity stabillity) {
        this(major, minor, patch, target, null, stabillity);
    }

    public Version(int major, int minor, int patch, String target, String notes, Stabillity stabillity) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.target = target;
        this.notes = notes;
        this.stabillity = stabillity;
    }

    @Override
    public String toString() {
        if (notes == null) {
            return String.format("%d.%d-%s.%d-%s", major, minor, stabillity.toString(), patch, target);
        } else {
            return String.format("%d.%d-%s.%d-%s (%s)", major, minor, stabillity.toString(), patch, target, notes);
        }
    }

    @Override
    public int compareTo(Version obj) {
        if (obj == null) {
            throw new NullPointerException();
        }

        if (major != obj.major) {
            return major - obj.major;
        }
        if (minor != obj.minor) {
            return minor - obj.minor;
        }
        if (patch != obj.patch) {
            return patch - obj.patch;
        }
        int returnCode = target.compareTo(obj.target);
        if (returnCode != 0) {
            return returnCode;
        }
        if (notes == null) {
            return obj.notes == null ? 0 : -1;
        }
        return notes.compareTo(obj.notes);
    }

    public static enum Stabillity {
        STABLE,
        BETA,
        ALPHA,
        SNAPSHOT;

        // Has to be done since Enum#compareTo already exists
        int compareStabillity(Stabillity compared) {
            return this.ordinal() - compared.ordinal();
        }
    }
}
