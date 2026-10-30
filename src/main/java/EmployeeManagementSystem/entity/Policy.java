package EmployeeManagementSystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
@Data
public class Policy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    // A policy can be a full multi-page handbook, not a VARCHAR(255).
    // Existing installations must run the included SQL migration too.
    @Column(name = "description", columnDefinition = "LONGTEXT")
    @Size(max = 100000, message = "Policy content must be 100,000 characters or fewer.")
    private String description;
    private String category;
    private String documentPath;
    private LocalDate effectiveDate;
}
