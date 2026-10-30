package Employee_Management_System;

import EmployeeManagementSystem.EmployeeManagementSystemApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="HRMS_RUN_INTEGRATION_TESTS", matches="true")
@SpringBootTest(classes = EmployeeManagementSystemApplication.class)
class EmployeeManagementSystemApplicationTests {

	@Test
	void contextLoads() {
	}
}