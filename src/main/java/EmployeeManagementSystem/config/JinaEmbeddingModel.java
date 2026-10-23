package EmployeeManagementSystem.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class JinaEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final String model;
    private final int dimensions;
    private final String queryTask;
    private final String documentTask;

    public JinaEmbeddingModel(
            RestClient.Builder restClientBuilder,
            @Value("${jina.api.url}") String apiUrl,
            @Value("${jina.api.key}") String apiKey,
            @Value("${jina.embedding.model}") String model,
            @Value("${jina.embedding.dimensions}") int dimensions,
            @Value("${jina.embedding.query-task}") String queryTask,
            @Value("${jina.embedding.document-task}") String documentTask) {
        Assert.hasText(apiKey, "Jina API key must be configured");
        Assert.isTrue(dimensions > 0, "Jina embedding dimensions must be positive");
        this.restClient = restClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
        this.dimensions = dimensions;
        this.queryTask = queryTask;
        this.documentTask = documentTask;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return createEmbeddings(request.getInstructions(), documentTask);
    }

    @Override
    public float[] embed(String text) {
        return createEmbeddings(List.of(text), queryTask).getResult().getOutput();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return createEmbeddings(texts, documentTask).getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }

    @Override
    public float[] embed(Document document) {
        return createEmbeddings(List.of(document.getFormattedContent()), documentTask)
                .getResult()
                .getOutput();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private EmbeddingResponse createEmbeddings(List<String> inputs, String task) {
        Assert.notEmpty(inputs, "At least one input is required for embedding");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", inputs);
        body.put("task", task);
        body.put("dimensions", dimensions);

        JsonNode response = restClient.post()
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.size() != inputs.size()) {
            throw new IllegalStateException("Jina returned an unexpected embeddings response");
        }

        List<Embedding> embeddings = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonNode vector = data.get(i).path("embedding");
            if (!vector.isArray() || vector.size() != dimensions) {
                throw new IllegalStateException(
                        "Jina returned an embedding with an unexpected number of dimensions");
            }

            float[] values = new float[dimensions];
            for (int j = 0; j < dimensions; j++) {
                values[j] = vector.get(j).floatValue();
            }
            int index = data.get(i).path("index").asInt(i);
            embeddings.add(new Embedding(values, index));
        }
        return new EmbeddingResponse(embeddings);
    }
}
