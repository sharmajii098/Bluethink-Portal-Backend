package EmployeeManagementSystem.aiservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private static final String NO_CONTEXT_MESSAGE = "This query is not in my database.";
    private static final String SYSTEM_INSTRUCTIONS = """
            You answer employee questions about the leave policy.
            Answer using only the policy excerpts provided in the system context.
            Answer in the same language and writing style as the user's question.
            For English questions, answer in English. For Hinglish questions written in Latin
            script, answer naturally in Hinglish using Latin script.
            If the excerpts do not contain the answer, say so in the user's language.
            Be concise and do not infer rules that are not stated in the policy.

            Policy excerpts:
            %s
            """;

    private final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatServiceImpl(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @Override
    public String getResponse(String userQuery) {
        List<Document> documents = retrieveDocuments(userQuery);
        if (documents.isEmpty()) {
            return noContextMessage(userQuery);
        }

        return chatClient.prompt()
                .system(SYSTEM_INSTRUCTIONS.formatted(formatContext(documents)))
                .user(userQuery)
                .call()
                .content();
    }

    private List<Document> retrieveDocuments(String userQuery) {
        long retrievalStarted = System.nanoTime();
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuery)
                        .topK(6)
                        .similarityThreshold(0.3)
                        .build()
        );
        logger.info("Retrieved {} policy chunks in {} ms",
                documents.size(), elapsedMillis(retrievalStarted));
        return documents;
    }

    private String formatContext(List<Document> documents) {
        return documents.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((first, next) -> first + "\n\n---\n\n" + next)
                .orElse("");
    }

    private String noContextMessage(String userQuery) {
        return userQuery.matches("(?is).*\\b(kya|kaise|ka|ki|ke|hai|hain|ho|mujhe|mera|meri|mere|chhutti|chutti|batao|btao|kitni|kab|milti|milegi)\\b.*")
                ? "Is query ka answer mere database mein nahi hai."
                : NO_CONTEXT_MESSAGE;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
