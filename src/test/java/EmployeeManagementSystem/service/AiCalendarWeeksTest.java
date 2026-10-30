package EmployeeManagementSystem.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class AiCalendarWeeksTest {
 @Test void weeksAreMondayToSundayWithoutOverlap() {
  LocalDate saturday=LocalDate.of(2026,10,17);
  assertEquals(LocalDate.of(2026,10,12),AiCalendarWeeks.weekStart(saturday));
  assertEquals(LocalDate.of(2026,10,18),AiCalendarWeeks.weekEnd(saturday));
  LocalDate previous=AiCalendarWeeks.weekStart(saturday).minusWeeks(1);
  assertEquals(LocalDate.of(2026,10,5),previous);
  assertEquals(LocalDate.of(2026,10,11),previous.plusDays(6));
 }
}
