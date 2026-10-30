// EmployeeReportServiceImpl.java
package EmployeeManagementSystem.service;

import EmployeeManagementSystem.dto.EmployeeReportDTO;
import EmployeeManagementSystem.entity.Department;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.EmployeeProfile;
import EmployeeManagementSystem.entity.Project;
import EmployeeManagementSystem.entity.SalaryStructure;
import EmployeeManagementSystem.enums.ProjectStatus;
import EmployeeManagementSystem.repository.DepartmentRepository;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.repository.ProjectRepository;
import EmployeeManagementSystem.repository.AdminSalaryStructureRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmployeeReportServiceImpl implements EmployeeReportService {

    @Autowired
    private EmployeeProfileRepository employeeProfileRepository;

    @Autowired
    private AdminSalaryStructureRepo adminSalaryStructureRepo;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Override
    public Page<EmployeeReportDTO> getEmployeeReports(String keyword, Long departmentId,
                                                      String status, Pageable pageable) {
        Page<EmployeeProfile> profilePage = employeeProfileRepository.searchEmployeeProfiles(
                keyword, departmentId, status, pageable);

        List<EmployeeReportDTO> dtos = profilePage.getContent().stream()
                .map(this::convertToEmployeeReportDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, profilePage.getTotalElements());
    }

    @Override
    public EmployeeReportDTO convertToEmployeeReportDTO(EmployeeProfile profile) {
        Employee employee = profile.getEmployee();
        Department department = employee != null ? employee.getDepartment() : null;

        String departmentName = department != null ? department.getDepartmentName() : null;
        String designation = profile.getDesignation();
        String photo = profile.getPhoto();
        String userId = profile.getUserId();
        String profileId = profile.getId().toString();
        String employeeStatus = profile.getStatus();

        // Get Salary Structure directly from profile
        SalaryStructure salaryStructure = profile.getSalaryStructure();
        BigDecimal basicSalary = salaryStructure != null ? salaryStructure.getBasicSalary() : BigDecimal.ZERO;
        BigDecimal netSalary = calculateNetSalary(salaryStructure);

        // Get current active project directly from profile
        String currentProject = getCurrentActiveProject(profile);

        return new EmployeeReportDTO(
                employee != null ? employee.getId() : null,
                userId,
                profile.getFullName(),
                profile.getEmail(),
                profile.getPhoneNumber(),
                departmentName,
                designation,
                currentProject,
                basicSalary,
                netSalary,
                netSalary.compareTo(BigDecimal.ZERO) > 0 ? "PAID" : "UNPAID",
                employeeStatus,
                photo,
                profileId
        );
    }

    private BigDecimal calculateNetSalary(SalaryStructure salaryStructure) {
        if (salaryStructure == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalEarnings = salaryStructure.getBasicSalary()
                .add(salaryStructure.getHra())
                .add(salaryStructure.getConveyance())
                .add(salaryStructure.getMedicalAllowance())
                .add(salaryStructure.getSpecialAllowance())
                .add(salaryStructure.getOtherAllowance());

        BigDecimal totalDeductions = salaryStructure.getPf()
                .add(salaryStructure.getEsi())
                .add(salaryStructure.getProfessionalTax())
                .add(salaryStructure.getTds())
                .add(salaryStructure.getLoanDeduction());

        return totalEarnings.subtract(totalDeductions);
    }

    @Override
    public String getCurrentActiveProject(EmployeeProfile profile) {
        List<Project> projects = profile.getProjects();

        if (projects == null || projects.isEmpty()) {
            return "No Active Project";
        }

        return projects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE ||
                        p.getStatus() == ProjectStatus.IN_PROGRESS)
                .findFirst()
                .map(Project::getProjectName)
                .orElse("No Active Project");
    }

    @Override
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Override
    public EmployeeReportDTO getEmployeeDetail(Long profileId) {

        EmployeeProfile profile = employeeProfileRepository.findByIdWithAllDetails(profileId)
                .orElseThrow(() -> new RuntimeException("Employee profile not found with id: " + profileId));

        return convertToEmployeeReportDTO(profile);
    }
}