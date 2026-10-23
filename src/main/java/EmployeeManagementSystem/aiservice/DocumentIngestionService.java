package EmployeeManagementSystem.aiservice;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentIngestionService {

    private final DataLoader dataLoader;
    private final DataTransformer dataTransformer;
    private final VectorStore vectorStore;

    public DocumentIngestionService(
            DataLoader dataLoader,
            DataTransformer dataTransformer,
            VectorStore vectorStore) {
        this.dataLoader = dataLoader;
        this.dataTransformer = dataTransformer;
        this.vectorStore = vectorStore;
    }

    public int ingestPdf() {
        List<Document> documents = dataLoader.loadDocumentsFromPdf();
        List<Document> chunks = dataTransformer.transform(documents);
        vectorStore.add(chunks);
        return chunks.size();
    }
}
