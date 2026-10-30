package EmployeeManagementSystem.entity;

import EmployeeManagementSystem.enums.InvoiceStatue;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

@Entity
@Data
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigInteger projectId;
    private LocalDate invoice_date;
    private BigDecimal amount;
    private InvoiceStatue status;
}
