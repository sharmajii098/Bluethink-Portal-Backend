package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeofenceDecisionTest {
    @Test void classifiesOnlyUnambiguousLocations() {
        assertEquals(GeofenceDecision.Result.WFO, GeofenceDecision.classify(15, 10, 100));
        assertEquals(GeofenceDecision.Result.WFH, GeofenceDecision.classify(250, 25, 100));
        assertEquals(GeofenceDecision.Result.RETRY, GeofenceDecision.classify(100, 20, 100));
        assertEquals(GeofenceDecision.Result.RETRY, GeofenceDecision.classify(80, 30, 100));
        assertEquals(GeofenceDecision.Result.RETRY, GeofenceDecision.classify(120, 30, 100));
        assertEquals(GeofenceDecision.Result.WFO, GeofenceDecision.classify(100, 0, 100));
    }
    @Test void rejectsBadMeasurements() {
        assertThrows(IllegalArgumentException.class, () -> GeofenceDecision.classify(Double.NaN, 15, 100));
        assertThrows(IllegalArgumentException.class, () -> GeofenceDecision.classify(0, Double.POSITIVE_INFINITY, 100));
        assertThrows(IllegalArgumentException.class, () -> GeofenceDecision.classify(0, 151, 100));
        assertThrows(IllegalArgumentException.class, () -> GeofenceDecision.classify(-2, 15, 100));
        assertThrows(IllegalArgumentException.class, () -> GeofenceDecision.classify(20, 10, 0));
    }
}
