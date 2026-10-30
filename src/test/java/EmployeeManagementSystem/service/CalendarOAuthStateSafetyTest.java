package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CalendarOAuthStateSafetyTest {
 CalendarOAuthStateRepository rows;CalendarSettings settings;CalendarOAuthStates service;CalendarOAuthState state;String raw;
 @BeforeEach void setup(){rows=mock(CalendarOAuthStateRepository.class);settings=mock(CalendarSettings.class);service=new CalendarOAuthStates(rows,settings);raw=IntegrationCrypto.randomToken();String key=Base64.getEncoder().encodeToString(new byte[32]);when(settings.key()).thenReturn(key);state=new CalendarOAuthState();state.stateHash=IntegrationCrypto.sha256(raw);state.ownerId="EMP_TEST";state.provider="google";state.expiresAt=OffsetDateTime.now().plusMinutes(10);state.encryptedVerifier=IntegrationCrypto.encrypt("verifier",key,"EMP_TEST:google:pkce");when(rows.lock(state.stateHash)).thenReturn(Optional.of(state));}
 @Test void stateIsSingleUse(){assertEquals("verifier",service.consume("EMP_TEST","google",raw));assertTrue(state.consumed);assertThrows(ResponseStatusException.class,()->service.consume("EMP_TEST","google",raw));}
 @Test void anotherEmployeeCannotCompleteConsent(){assertThrows(ResponseStatusException.class,()->service.consume("OTHER","google",raw));assertFalse(state.consumed);}
 @Test void anotherProviderCannotUseState(){assertThrows(ResponseStatusException.class,()->service.consume("EMP_TEST","microsoft",raw));}
 @Test void expiryBlocksCompletion(){state.expiresAt=OffsetDateTime.now().minusSeconds(1);assertThrows(ResponseStatusException.class,()->service.consume("EMP_TEST","google",raw));}
}
