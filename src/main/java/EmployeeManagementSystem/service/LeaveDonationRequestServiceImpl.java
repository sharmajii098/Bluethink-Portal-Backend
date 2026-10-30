package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.LeaveDonationRequest;
import EmployeeManagementSystem.entity.LeaveBalance;
import EmployeeManagementSystem.entity.LeaveType;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.LeaveDonationRequestRepository;
import EmployeeManagementSystem.repository.LeaveBalanceRepository;
import EmployeeManagementSystem.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LeaveDonationRequestServiceImpl implements LeaveDonationRequestService {
    private static final Set<String> NON_TRANSFERABLE_CODES = Set.of("PROJECT_OFF", "LEAVE_WITHOUT_PAY", "LWP", "SHORT_LEAVE");

    private final LeaveDonationRequestRepository repo;
    private final EmployeeRepository employeeRepo;
    private final LeaveBalanceRepository leaveBalanceRepo;
    private final LeaveTypeRepository leaveTypeRepo;

    public LeaveDonationRequestServiceImpl(LeaveDonationRequestRepository repo,
                                           EmployeeRepository employeeRepo,
                                           LeaveBalanceRepository leaveBalanceRepo,
                                           LeaveTypeRepository leaveTypeRepo) {
        this.repo = repo;
        this.employeeRepo = employeeRepo;
        this.leaveBalanceRepo = leaveBalanceRepo;
        this.leaveTypeRepo = leaveTypeRepo;
    }

    @Override
    public List<LeaveDonationRequest> my(String u) {
        return repo.findByDonorUserIdOrRecipientUserIdOrderByCreatedAtDesc(u, u);
    }

    @Override
    public List<LeaveDonationRequest> all() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public LeaveDonationRequest create(LeaveDonationRequest x, String donor, String donorName, String recipientName) {
        if (x.getRecipientUserId() == null || x.getRecipientUserId().isBlank()) {
            throw new IllegalArgumentException("Recipient employee is required.");
        }
        if (x.getDays() == null || x.getDays() < 1 || x.getDays() > 365) {
            throw new IllegalArgumentException("Donation days must be between 1 and 365.");
        }
        if (x.getReason() == null || x.getReason().isBlank()) {
            throw new IllegalArgumentException("Reason is required.");
        }

        Employee donorEmployee = resolveEmployee(donor)
                .orElseThrow(() -> new IllegalArgumentException("Donor employee record was not found."));
        Employee recipientEmployee = resolveEmployee(x.getRecipientUserId())
                .orElseThrow(() -> new IllegalArgumentException("Recipient employee was not found."));
        if (Objects.equals(donorEmployee.getId(), recipientEmployee.getId())) {
            throw new IllegalArgumentException("You cannot donate leave to yourself.");
        }

        LeaveType leaveType = resolveLeaveType(x.getLeaveType());
        validateTransferable(leaveType);
        int year = LocalDate.now().getYear();
        double available = leaveBalanceRepo.findByEmployeeIdAndLeaveTypeIdAndYear(
                        donorEmployee.getId(), leaveType.getId(), year)
                .map(b -> Optional.ofNullable(b.getBalance()).orElse(0.0))
                // This mirrors the regular leave-approval behavior: if a current-year
                // bucket has not been materialized yet, the configured default is the
                // employee's starting entitlement for validation purposes.
                .orElse(leaveType.getDefaultDays() == null ? 0.0 : leaveType.getDefaultDays().doubleValue());
        if (available < x.getDays()) {
            throw new IllegalArgumentException("Insufficient " + leaveType.getName() + " balance. Available: " + available + " day(s).");
        }

        if (repo.existsByDonorUserIdAndRecipientUserIdAndStatusIn(
                donor, x.getRecipientUserId(), List.of("SUBMITTED", "UNDER_REVIEW"))) {
            throw new IllegalArgumentException("An active donation request already exists for this recipient.");
        }

        x.setId(null);
        x.setRequestNumber("LVD-" + System.currentTimeMillis());
        x.setDonorUserId(donor);
        x.setDonorName(donorName);
        x.setRecipientName(recipientName);
        // Store the canonical leave code so the approval transaction always maps to
        // the same balance bucket even if the display name changes later.
        x.setLeaveType(leaveType.getCode());
        x.setStatus("SUBMITTED");
        x.setTransferApplied(false);
        x.setTransferYear(null);
        x.setTransferredAt(null);
        return repo.save(x);
    }

    @Override
    @Transactional
    public LeaveDonationRequest review(Long id, String s, String c, String r) {
        LeaveDonationRequest x = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Leave donation request not found."));
        if (!Set.of("UNDER_REVIEW", "APPROVED", "REJECTED", "CANCELLED").contains(s)) {
            throw new IllegalArgumentException("Invalid status.");
        }
        if ("APPROVED".equalsIgnoreCase(x.getStatus()) && !"APPROVED".equalsIgnoreCase(s)) {
            throw new IllegalArgumentException("An approved leave donation cannot be changed after the balance transfer.");
        }

        if ("APPROVED".equalsIgnoreCase(s)) {
            applyTransferIfNeeded(x);
        }

        x.setStatus(s);
        x.setReviewerUserId(r);
        x.setReviewerComment(c);
        x.setReviewedAt(LocalDateTime.now());
        return repo.save(x);
    }

    /**
     * Repairs approvals created by older builds where the request status changed
     * to APPROVED but no leave-balance transfer was performed.
     */
    @Override
    @Transactional
    public void reconcileApprovedTransfers() {
        for (LeaveDonationRequest request : repo.findAllByOrderByCreatedAtDesc()) {
            if (!"APPROVED".equalsIgnoreCase(request.getStatus()) || Boolean.TRUE.equals(request.getTransferApplied())) {
                continue;
            }
            try {
                applyTransferIfNeeded(request);
                repo.save(request);
                System.out.println("Reconciled an approved leave donation");
            } catch (RuntimeException ex) {
                // Do not stop application startup because one historical request is
                // inconsistent. Keep it unapplied so HR can correct the source data.
                System.err.println("Unable to reconcile an approved leave donation (type=" + ex.getClass().getSimpleName() + ")");
            }
        }
    }

    private void applyTransferIfNeeded(LeaveDonationRequest x) {
        if (Boolean.TRUE.equals(x.getTransferApplied())) return;

        Employee donor = resolveEmployee(x.getDonorUserId())
                .orElseThrow(() -> new IllegalStateException("Donor employee record was not found."));
        Employee recipient = resolveEmployee(x.getRecipientUserId())
                .orElseThrow(() -> new IllegalStateException("Recipient employee record was not found."));
        if (Objects.equals(donor.getId(), recipient.getId())) {
            throw new IllegalStateException("Donor and recipient cannot be the same employee.");
        }

        LeaveType leaveType = resolveLeaveType(x.getLeaveType());
        validateTransferable(leaveType);
        int year = x.getTransferYear() == null ? LocalDate.now().getYear() : x.getTransferYear();
        double days = x.getDays() == null ? 0.0 : x.getDays().doubleValue();
        if (days <= 0) throw new IllegalStateException("Donation days must be greater than zero.");

        LeaveBalance donorBalance = leaveBalanceRepo.findByEmployeeIdAndLeaveTypeIdAndYear(
                        donor.getId(), leaveType.getId(), year)
                .orElseGet(() -> {
                    LeaveBalance created = new LeaveBalance();
                    created.setEmployee(donor);
                    created.setLeaveType(leaveType);
                    created.setYear(year);
                    created.setBalance(leaveType.getDefaultDays() == null ? 0.0 : leaveType.getDefaultDays().doubleValue());
                    return leaveBalanceRepo.save(created);
                });

        double donorAvailable = donorBalance.getBalance() == null ? 0.0 : donorBalance.getBalance();
        if (donorAvailable < days) {
            throw new IllegalStateException("Insufficient donor leave balance at approval time. Available: " + donorAvailable + " day(s).");
        }

        LeaveBalance recipientBalance = leaveBalanceRepo.findByEmployeeIdAndLeaveTypeIdAndYear(
                        recipient.getId(), leaveType.getId(), year)
                .orElseGet(() -> {
                    LeaveBalance created = new LeaveBalance();
                    created.setEmployee(recipient);
                    created.setLeaveType(leaveType);
                    created.setYear(year);
                    // A donation creates only the donated balance. Annual/default
                    // entitlement remains a separate HR leave-policy operation.
                    created.setBalance(0.0);
                    return leaveBalanceRepo.save(created);
                });

        donorBalance.setBalance(donorAvailable - days);
        recipientBalance.setBalance((recipientBalance.getBalance() == null ? 0.0 : recipientBalance.getBalance()) + days);
        leaveBalanceRepo.save(donorBalance);
        leaveBalanceRepo.save(recipientBalance);

        x.setTransferApplied(true);
        x.setTransferYear(year);
        x.setTransferredAt(LocalDateTime.now());
    }

    private Optional<Employee> resolveEmployee(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        Optional<Employee> employee = employeeRepo.findByRegisterEmployee_UserId(userId);
        if (employee.isPresent()) return employee;
        return employeeRepo.findByEmployeeProfile_UserId(userId);
    }

    private LeaveType resolveLeaveType(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) throw new IllegalArgumentException("Leave type is required.");
        Optional<LeaveType> byCode = leaveTypeRepo.findByCode(value.toUpperCase(Locale.ENGLISH));
        if (byCode.isPresent()) return byCode.get();
        Optional<LeaveType> byName = leaveTypeRepo.findByName(value);
        if (byName.isPresent()) return byName.get();
        String display = Arrays.stream(value.toLowerCase(Locale.ENGLISH).split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(value);
        return leaveTypeRepo.findByName(display)
                .orElseThrow(() -> new IllegalArgumentException("Leave type is not configured: " + value));
    }

    private void validateTransferable(LeaveType leaveType) {
        String code = leaveType.getCode() == null ? "" : leaveType.getCode().trim().toUpperCase(Locale.ENGLISH);
        if (Boolean.FALSE.equals(leaveType.getIsActive())) {
            throw new IllegalArgumentException("Leave type is inactive: " + leaveType.getName());
        }
        if (NON_TRANSFERABLE_CODES.contains(code)) {
            throw new IllegalArgumentException(leaveType.getName() + " cannot be donated or transferred.");
        }
    }
}
