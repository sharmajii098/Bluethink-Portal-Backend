package EmployeeManagementSystem.entity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SalaryStructureBuilderDefaultsTest {

    @Test
    void allSalaryComponentsDefaultToZero() {
        SalaryStructure salary = SalaryStructure.builder().build();

        assertAll(
            () -> assertEquals(BigDecimal.ZERO, salary.getBasicSalary()),
            () -> assertEquals(BigDecimal.ZERO, salary.getHra()),
            () -> assertEquals(BigDecimal.ZERO, salary.getConveyance()),
            () -> assertEquals(BigDecimal.ZERO, salary.getMedicalAllowance()),
            () -> assertEquals(BigDecimal.ZERO, salary.getSpecialAllowance()),
            () -> assertEquals(BigDecimal.ZERO, salary.getOtherAllowance()),
            () -> assertEquals(BigDecimal.ZERO, salary.getPf()),
            () -> assertEquals(BigDecimal.ZERO, salary.getEsi()),
            () -> assertEquals(BigDecimal.ZERO, salary.getProfessionalTax()),
            () -> assertEquals(BigDecimal.ZERO, salary.getTds()),
            () -> assertEquals(BigDecimal.ZERO, salary.getLoanDeduction())
        );
    }

    @Test
    void explicitSalaryAmountsArePreserved() {
        BigDecimal basic = new BigDecimal("50000.00");
        BigDecimal hra = new BigDecimal("15000.00");

        SalaryStructure salary = SalaryStructure.builder()
            .basicSalary(basic)
            .hra(hra)
            .build();

        assertEquals(basic, salary.getBasicSalary());
        assertEquals(hra, salary.getHra());
        assertEquals(BigDecimal.ZERO, salary.getPf());
        assertEquals(BigDecimal.ZERO, salary.getTds());
    }
}