package io.github.topdanya33200_netizen.reporthook.model;

/**
 * Where the reported player was standing when the report was filed.
 */
public record ReportLocation(String world, double x, double y, double z) {

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }
}
