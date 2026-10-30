package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.EmployeeDocument;
import EmployeeManagementSystem.repository.EmployeeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EmployeeDocumentStorageTest {
    @TempDir Path root;
    @Test void storedMetadataCannotReadOutsideDocumentRoot() throws Exception {
        var service=new EmployeeDocumentService(mock(EmployeeDocumentRepository.class),root.toString());
        var document=new EmployeeDocument(); document.setStoredFilename("../outside.txt");
        assertThrows(IllegalArgumentException.class, () -> service.bytes(document));
    }
    @Test void validStoredDocumentCanBeRead() throws Exception {
        Files.writeString(root.resolve("test.pdf"),"document bytes");
        var service=new EmployeeDocumentService(mock(EmployeeDocumentRepository.class),root.toString());
        var document=new EmployeeDocument(); document.setStoredFilename("test.pdf");
        assertArrayEquals("document bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8),service.bytes(document));
    }
}
