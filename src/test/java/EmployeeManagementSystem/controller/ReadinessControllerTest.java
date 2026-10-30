package EmployeeManagementSystem.controller;
import javax.sql.DataSource;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ReadinessControllerTest {
 @Test void readinessRequiresStartupCompletionAndMandatoryRedis() throws Exception {
  var db=mock(DataSource.class); var connection=mock(Connection.class);
  when(db.getConnection()).thenReturn(connection); when(connection.isValid(2)).thenReturn(true);
  var availability=mock(ApplicationAvailability.class);
  @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
  var core=new ReadinessController(db,redis,false,availability);
  when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
  assertEquals(503,core.ready().getStatusCode().value()); verifyNoInteractions(db);
  when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
  assertEquals(200,core.ready().getStatusCode().value());
  assertEquals(503,new ReadinessController(db,redis,true,availability).ready().getStatusCode().value());
  when(connection.isValid(2)).thenReturn(false);
  assertEquals(503,core.ready().getStatusCode().value());
 }
}
