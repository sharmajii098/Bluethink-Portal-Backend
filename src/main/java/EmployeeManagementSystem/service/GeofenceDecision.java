package EmployeeManagementSystem.service;

/** Treat reported GPS accuracy as an uncertainty radius when classifying work mode.
 * This reduces incorrect WFO/WFH classifications close to the office boundary.
 * Client-provided coordinates are not a cryptographic proof of physical presence. */
public final class GeofenceDecision {
    private GeofenceDecision() {}
    public enum Result { WFO, WFH, RETRY }

    public static Result classify(double distanceMeters, double accuracyMeters, double radiusMeters) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0
                || !Double.isFinite(accuracyMeters) || accuracyMeters < 0 || accuracyMeters > 150
                || !Double.isFinite(radiusMeters) || radiusMeters <= 0 || radiusMeters > 10_000)
            throw new IllegalArgumentException("Invalid geofence measurement");
        // Conservative: WFO only if the entire claimed GPS accuracy circle is within the office.
        if (distanceMeters + accuracyMeters <= radiusMeters) return Result.WFO;
        // WFH only if the entire claimed GPS accuracy circle is outside the office.
        if (Math.max(0, distanceMeters - accuracyMeters) > radiusMeters) return Result.WFH;
        return Result.RETRY;
    }
}
