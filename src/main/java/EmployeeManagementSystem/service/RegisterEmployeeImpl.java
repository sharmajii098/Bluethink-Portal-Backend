package EmployeeManagementSystem.service;

import EmployeeManagementSystem.dto.LoginRequest;
import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RegisterEmployeeImpl implements RegisterEmployeeService {
    private final RegisterEmployeeRepository repository;
    //private final EmployeeRepository repository1;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;

    public String registerEmployee(RegisterEmployee employee) {
        String nextUserId = generateNextEmployeeId();
        employee.setUserId(nextUserId);
        String rawPassword=generateRandomPassword(8);
        employee.setPassword(passwordEncoder.encode(rawPassword));
        repository.save(employee);
        return rawPassword;
    }

    public String generateNextEmployeeId() {

        return repository.findFirstByUserIdIsNotNullOrderByIdDesc()
                .map(lastEmp -> {
                    String lastUserId = lastEmp.getUserId();
                    int lastNumber = Integer.parseInt(lastUserId.replace("EMP", ""));
                    return String.format("EMP%04d", lastNumber + 1);
                })
                .orElse("EMP0001");
    }

    private String generateRandomPassword(int length){
        String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random=new SecureRandom();
        StringBuilder sb=new StringBuilder();
        for (int i=0;i<length;i++){
            int index=random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
    public void sendOtpProcessing(String email) {
        RegisterEmployee employee = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Employee with this email not found"));

        String otp = String.format("%06d", new java.util.Random().nextInt(999999));

        employee.setOtp(otp);
        employee.setOtpExpiryTime(java.time.LocalDateTime.now().plusMinutes(5));
        repository.save(employee);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Password Reset OTP - Employee Management System");
        message.setText("Dear Employee, Your OTP for password reset is: " + otp + "\nValid for 5 minutes only.");

        mailSender.send(message);
    }
    public void verifyOtpAndChangePassword(String email, String otp, String newPassword) {
        RegisterEmployee employee = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (employee.getOtp() == null || !employee.getOtp().equals(otp)) {
            throw new RuntimeException("Invalid OTP! Please try again.");
        }

        if (employee.getOtpExpiryTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP has expired! Request a new one.");
        }
        employee.setPassword(passwordEncoder.encode(newPassword));
        //employee.setOtp(null);
        //employee.setOtpExpiryTime(null);

        repository.save(employee);
    }
    public String login(LoginRequest request) {

        RegisterEmployee employee =
                repository.findByUserId(request.getUserId());

        if (employee == null) {
            throw new RuntimeException("Invalid username");
        }

        if (passwordEncoder.matches(
                request.getPassword(),
                employee.getPassword())) {

            return jwtUtil.generateToken(
                    employee.getUserId(),
                    employee.getRole()
            );
        }

        throw new RuntimeException("Invalid password");
    }
    @Override
    public RegisterEmployee save(RegisterEmployee employee) {
        return repository.save(employee);
    }

    public RegisterEmployee getEmployeeById(String employeeId){
        return repository.findByUserId(employeeId);
    }
    @Override
    public List<RegisterEmployee> getUpcomingBirthdays() {
        return repository.findUpcomingBirthdays();
    }
}
