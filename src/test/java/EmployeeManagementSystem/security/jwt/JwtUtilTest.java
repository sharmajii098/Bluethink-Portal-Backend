package EmployeeManagementSystem.security.jwt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JwtUtilTest {
    @Test void repeatedLoginsHaveDistinctRevocableTokens() {
        var jwt=new JwtUtil("this-is-a-test-only-signing-key-of-at-least-32-bytes",3600000);
        String first=jwt.generateToken("EMP1","EMPLOYEE");
        String second=jwt.generateToken("EMP1","EMPLOYEE");
        assertNotEquals(first,second);
        assertTrue(jwt.validateToken(first,"EMP1")); assertTrue(jwt.validateToken(second,"EMP1"));
    }
}
