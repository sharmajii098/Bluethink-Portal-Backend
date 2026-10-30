package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.OfficeLocation;
import EmployeeManagementSystem.repository.OfficeLocationRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GeofenceServiceTest {
 @Test void absentOfficeFailsWithoutInventingOne(){
  var repo=mock(OfficeLocationRepository.class);when(repo.findByOfficeName("Main Office")).thenReturn(Optional.empty());
  assertThrows(IllegalStateException.class,()->new GeofenceService(repo).getOffice());verify(repo,never()).save(any());
 }
 @Test void validatesCoordinatesAndComputesConfiguredOffice(){
  var repo=mock(OfficeLocationRepository.class);when(repo.findByOfficeName("Main Office")).thenReturn(Optional.of(OfficeLocation.builder().latitude(28.61).longitude(77.21).allowedRadius(200.0).build()));
  var service=new GeofenceService(repo);assertTrue(service.isInsideOffice(28.61,77.21));assertThrows(IllegalArgumentException.class,()->service.getDistance(Double.NaN,77.21));assertThrows(IllegalArgumentException.class,()->service.getDistance(91.0,77.21));
 }
}
