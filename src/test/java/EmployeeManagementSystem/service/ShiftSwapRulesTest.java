package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShiftSwapRulesTest {
    @Test void onlyPendingRequestsMayBeReviewed() {
        assertDoesNotThrow(() -> ShiftSwapRules.requirePending("SUBMITTED"));
        assertDoesNotThrow(() -> ShiftSwapRules.requirePending("UNDER_REVIEW"));
        assertThrows(IllegalArgumentException.class, () -> ShiftSwapRules.requirePending("APPROVED"));
        assertThrows(IllegalArgumentException.class, () -> ShiftSwapRules.requirePending("REJECTED"));
    }
    @Test void approvalRequiresTargetConsent() {
        assertDoesNotThrow(() -> ShiftSwapRules.requireConsent("ACCEPTED"));
        assertThrows(IllegalArgumentException.class, () -> ShiftSwapRules.requireConsent("PENDING"));
        assertThrows(IllegalArgumentException.class, () -> ShiftSwapRules.requireConsent("DECLINED"));
        assertThrows(IllegalArgumentException.class, () -> ShiftSwapRules.requireConsent(null));
    }
    @Test void changedShiftsCannotBeApproved() {
        assertDoesNotThrow(() -> ShiftSwapRules.requireUnchanged(10L,10L,20L,20L));
        assertThrows(IllegalArgumentException.class, () -> ShiftSwapRules.requireUnchanged(10L,11L,20L,20L));
        assertThrows(IllegalArgumentException.class, () -> ShiftSwapRules.requireUnchanged(10L,10L,20L,21L));
    }
}
