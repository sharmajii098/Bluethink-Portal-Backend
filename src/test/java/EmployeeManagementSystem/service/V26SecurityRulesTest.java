package EmployeeManagementSystem.service;
import EmployeeManagementSystem.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class V26SecurityRulesTest {
 @Test void fingerprintCannotBeUsedAsOriginalBearer(){String raw="eyJ.private.session";String hash=SessionFingerprint.of(raw);assertEquals(71,hash.length());assertTrue(hash.startsWith("sha256:"));assertFalse(hash.contains(raw));assertEquals(hash,SessionFingerprint.of(raw));assertNotEquals(hash,SessionFingerprint.of(raw+"x"));}
 @Test void perAccountAttemptsAreBounded(){var provider=mock(ObjectProvider.class);var guard=new LoginAttemptGuard(provider,false);for(int i=0;i<10;i++)guard.check("EMP01");assertEquals(429,assertThrows(ResponseStatusException.class,()->guard.check("emp01")).getStatusCode().value());guard.check("EMP02");}
 @Test void missingDistributedProtectionBlocksLogin(){var guard=new LoginAttemptGuard(mock(ObjectProvider.class),true);assertEquals(503,assertThrows(ResponseStatusException.class,()->guard.check("EMP01")).getStatusCode().value());}
 @Test void forgedPdfRejectedBeforeScanner(){var guard=new UploadSafety(true,"localhost",3310){protected String scan(byte[] b){fail("Scanner must not see invalid format");return "OK";}};assertThrows(ResponseStatusException.class,()->guard.validate("fake.pdf","<script>bad</script>".getBytes()));}
 @Test void scannerFailureBlocksProductionUpload(){var guard=new UploadSafety(true,"localhost",3310){protected String scan(byte[] b)throws IOException{throw new IOException();}};assertEquals(503,assertThrows(ResponseStatusException.class,()->guard.validate("policy.txt","policy".getBytes(StandardCharsets.UTF_8))).getStatusCode().value());}
 @Test void malwareVerdictBlocksEvenInDevelopment(){var guard=new UploadSafety(false,"localhost",3310){protected String scan(byte[] b){return "FOUND";}};assertEquals(422,assertThrows(ResponseStatusException.class,()->guard.validate("policy.txt","policy".getBytes())).getStatusCode().value());}
 @Test void verifiedContentDeterminesMime(){var guard=new UploadSafety(true,"localhost",3310){protected String scan(byte[] b){return "OK";}};assertEquals("text/plain",guard.validate("policy.txt","policy".getBytes()));}
 @Test void emailEscapesUserSuppliedMarkup(){String html=EmailTemplates.html("<script>title</script>","Hello <img onerror='x'>",null,null);assertFalse(html.contains("<script>"));assertFalse(html.contains("<img onerror"));assertTrue(html.contains("&lt;img"));assertTrue(html.contains("BlueThink Inc."));}
 @Test void emailRejectsScriptCta(){assertThrows(IllegalArgumentException.class,()->EmailTemplates.html("Setup","Hello","Setup","javascript:alert(1)"));}
}
