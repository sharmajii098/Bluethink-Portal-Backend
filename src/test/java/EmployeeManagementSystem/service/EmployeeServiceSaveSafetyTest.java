package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Attendance;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.Salary;
import EmployeeManagementSystem.messaging.kafka.EmployeeEvent;
import EmployeeManagementSystem.messaging.kafka.EmployeeProducer;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeServiceSaveSafetyTest {
    @Test
    void persistsEmployeeWithNullSalaryAndAttendanceLists() {
        EmployeeRepository repo = mock(EmployeeRepository.class);
        EmployeeProducer producer = mock(EmployeeProducer.class);
        EmployeeProfileRepository profiles = mock(EmployeeProfileRepository.class);
        EmployeeServiceImpl service = new EmployeeServiceImpl(repo, producer, profiles);
        Employee input = new Employee();
        input.setSalaries(null);
        input.setAttendanceList(null);
        input.setFirstName("Test");
        input.setLastName("Employee");
        input.setEmail("synthetic@example.invalid");
        when(repo.save(input)).thenAnswer(invocation -> {
            input.setId(42L);
            return input;
        });

        Employee persisted = service.saveEmployee(input);
        assertSame(input, persisted);
        assertEquals(Long.valueOf(42L), persisted.getId());
        verify(repo).save(input);
        org.mockito.ArgumentCaptor<EmployeeEvent> event = org.mockito.ArgumentCaptor.forClass(EmployeeEvent.class);
        verify(producer).sendEmployeeCreatedEvent(event.capture());
        assertEquals("Test Employee", event.getValue().getFullName());
    }

    @Test
    void associatesChildRecordsBeforeSaving() {
        EmployeeRepository repo = mock(EmployeeRepository.class);
        EmployeeProducer producer = mock(EmployeeProducer.class);
        EmployeeServiceImpl service = new EmployeeServiceImpl(repo, producer, mock(EmployeeProfileRepository.class));
        Employee input = new Employee();
        Salary salary = new Salary();
        Attendance attendance = new Attendance();
        input.setSalaries(List.of(salary));
        input.setAttendanceList(List.of(attendance));
        when(repo.save(input)).thenReturn(input);

        service.saveEmployee(input);
        assertSame(input, salary.getEmployee());
        assertSame(input, attendance.getEmployee());
        verify(repo).save(input);
    }

    @Test
    void onlyPublishesLegacyEmployeeEventAfterCommit() {
        EmployeeRepository repo = mock(EmployeeRepository.class);
        EmployeeProducer producer = mock(EmployeeProducer.class);
        EmployeeServiceImpl service = new EmployeeServiceImpl(repo, producer, mock(EmployeeProfileRepository.class));
        Employee input = new Employee();
        input.setEmail("synthetic@example.invalid");
        when(repo.save(input)).thenAnswer(invocation -> {
            input.setId(43L);
            return input;
        });
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.saveEmployee(input);
            verify(producer, never()).sendEmployeeCreatedEvent(any(EmployeeEvent.class));
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.forEach(TransactionSynchronization::afterCommit);
            verify(producer).sendEmployeeCreatedEvent(any(EmployeeEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
