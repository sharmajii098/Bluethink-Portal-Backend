package EmployeeManagementSystem.config;

import EmployeeManagementSystem.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${portal.cors.allowed-origins:${portal.frontend-url:http://localhost:5173}}") String origins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                HttpHeaders.CACHE_CONTROL,
                "Last-Event-ID"));
        cors.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.RETRY_AFTER));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/**", "/api/auth/login", "/api/public/invitations/**", "/api/public/password-reset/**", "/api/error",
                    "/favicon.ico", "/error", "/webjars/**","/api/ai/**"
                ).permitAll()

                // All React account/session endpoints except login require a valid JWT.
                .requestMatchers("/api/auth/**").authenticated()

                // Manager team directory must be matched before the admin/HR employee rule.
                .requestMatchers(HttpMethod.GET, "/api/employees/team").hasRole("MANAGER")

                // Match the collection URI explicitly: /employees/** may not match
                // /employees in every Spring PathPattern configuration.
                .requestMatchers("/api/employees", "/api/departments")
                    .hasAnyRole("ADMIN", "HR")
                // Employee/account administration
                .requestMatchers("/api/employees/**", "/api/departments/**")
                    .hasAnyRole("ADMIN", "HR")

                // Attendance
                .requestMatchers(HttpMethod.GET, "/api/attendance")
                    .hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/api/attendance/team")
                    .hasRole("MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/attendance/my")
                    .authenticated()
                .requestMatchers("/api/attendance/punch-in", "/api/attendance/punch-out")
                    .authenticated()

                // Leave
                .requestMatchers(HttpMethod.GET, "/api/leaves")
                    .hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/api/leaves/team")
                    .hasRole("MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/leaves/my", "/api/leaves/context")
                    .authenticated()
                .requestMatchers(HttpMethod.POST, "/api/leaves")
                    .authenticated()
                .requestMatchers("/api/leaves/*/status")
                    .hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers("/api/leaves/*/cancel")
                    .authenticated()

                .requestMatchers(HttpMethod.GET, "/api/leave-balances/my").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/leave-balances/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/employee-hierarchy").hasRole("ADMIN")

                // Leave configuration and holiday calendar
                .requestMatchers(HttpMethod.GET, "/api/leave-types", "/api/leave-types/active", "/api/holidays").hasAnyRole("EMPLOYEE", "MANAGER", "HR", "ADMIN")
                .requestMatchers("/api/leave-types/**", "/api/leave-policies/**", "/api/holidays/**", "/api/leave-balance/**")
                    .hasAnyRole("ADMIN", "HR")

                // Timesheets
                .requestMatchers(HttpMethod.GET, "/api/timesheets")
                    .hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/api/timesheets/team")
                    .hasRole("MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/timesheets/my")
                    .authenticated()
                .requestMatchers(HttpMethod.POST, "/api/timesheets")
                    .hasAnyRole("EMPLOYEE", "MANAGER", "HR")
                .requestMatchers("/api/timesheets/*/status")
                    .hasAnyRole("ADMIN", "HR", "MANAGER")

                // Performance management: role/ownership rules are enforced in the controller.
                .requestMatchers("/api/performance/**").authenticated()
                .requestMatchers("/api/announcements/**", "/api/policies/**").authenticated()

                // Projects
                // Employee self-service documents; ownership and management checks are enforced by the controller.
                .requestMatchers("/api/documents/**").authenticated()
                .requestMatchers("/api/training/**").authenticated()
                .requestMatchers("/api/assets/**").authenticated()
                .requestMatchers("/api/recruitment/**").authenticated()
                .requestMatchers("/api/expenses/**", "/api/travel/**").authenticated()
                .requestMatchers("/api/workforce/**").authenticated()
                .requestMatchers("/api/separations/**").authenticated()
                .requestMatchers("/api/probation-reviews/**").authenticated()
                .requestMatchers("/api/attendance-regularizations/**").authenticated()
                .requestMatchers("/api/letter-requests/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/overtime").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.PATCH, "/api/overtime/*/review").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers("/api/overtime/**").authenticated()
                .requestMatchers("/api/employee-360-feedback/**").authenticated()
                .requestMatchers("/api/referrals/**").authenticated()
                .requestMatchers("/api/emergency-contacts/**").authenticated()
                .requestMatchers("/api/tax-declarations/**").authenticated()
                .requestMatchers("/api/profile-completion/**").authenticated()
                .requestMatchers("/api/shift-swaps/**").authenticated()
                .requestMatchers("/api/transport-requests/**").authenticated()
                .requestMatchers("/api/asset-requests/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/recognitions").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.PATCH, "/api/recognitions/*/review").hasAnyRole("ADMIN", "HR")
                .requestMatchers("/api/recognitions/my", "/api/recognitions").authenticated()
                .requestMatchers("/api/support-tickets/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/projects").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/projects").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.PUT, "/api/projects/*").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.DELETE, "/api/projects/*").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.POST, "/api/projects/*/assign").hasAnyRole("ADMIN", "HR", "MANAGER")

                // Payroll
                .requestMatchers("/api/salaries")
                    .hasAnyRole("ADMIN", "HR")
                .requestMatchers("/api/salaries/my")
                    .authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/salaries/*/payment-status").hasAnyRole("ADMIN", "HR")
                .requestMatchers("/api/salaries/generate/**", "/api/salary-structures/**")
                    .hasRole("HR")
                .requestMatchers("/api/salaries/monthly")
                    .hasAnyRole("ADMIN", "HR")

                // Notifications
                .requestMatchers(HttpMethod.GET, "/api/notifications")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/notifications/stream", "/api/notifications/my")
                    .authenticated()
                .requestMatchers(HttpMethod.GET, "/api/notifications/unread-count")
                    .authenticated()
                .requestMatchers(HttpMethod.GET, "/api/notifications/diagnostics")
                    .authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/notifications/read-all")
                    .authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/notifications/*/read")
                    .authenticated()

                // HR activity/audit feed and detailed activity audit
                .requestMatchers("/api/activity/visible").authenticated()
                .requestMatchers("/api/activity", "/api/activity-feed/**").hasAnyRole("ADMIN", "HR")

                // Reports
                .requestMatchers("/api/reports/**")
                    .hasAnyRole("ADMIN", "HR")

                // Everything else under the REST API requires a valid JWT.
                .requestMatchers("/api/manager-one-on-ones/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/talent-succession/my", "/api/talent-succession/employee/my").authenticated()
                .requestMatchers("/api/talent-succession/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers("/api/employee-suggestions/**").authenticated()
                .requestMatchers("/api/policy-acknowledgements/**").authenticated()
                .requestMatchers("/api/employee-skills/**").authenticated()
                .requestMatchers("/api/**").authenticated()

                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Access denied for this role\"}");
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
