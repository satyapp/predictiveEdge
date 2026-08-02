package org.predictiveedge.platform.eventing.contract;

/** Major changes are breaking; minor changes must remain backward compatible. */
public record SchemaVersion(int major, int minor) {
    public SchemaVersion {
        if (major < 1) {
            throw new IllegalArgumentException("Schema major version must be positive");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("Schema minor version cannot be negative");
        }
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
