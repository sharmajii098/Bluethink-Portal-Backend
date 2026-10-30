package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.AiKnowledgeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** Optional private Qdrant index. MySQL remains authoritative for text, publication and ACLs. */
@Service
public class AiQdrantVectorStore {
    public static final int DIMENSIONS = 512;
    private final Environment env;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public AiQdrantVectorStore(Environment env) { this.env = env; }

    @jakarta.annotation.PostConstruct public void validate() {
        String backend = env.getProperty("bluethink.ai.vector-store", "mysql").toLowerCase(Locale.ROOT);
        if (!Set.of("mysql", "qdrant").contains(backend))
            throw new IllegalStateException("AI vector store must be mysql or qdrant");
        if ("qdrant".equals(backend)) { collection(); endpoint("/healthz"); }
    }

    public boolean enabled() {
        return "qdrant".equalsIgnoreCase(env.getProperty("bluethink.ai.vector-store", "mysql"));
    }

    public String configuredBackend() { return enabled() ? "qdrant" : "mysql"; }

    public String collection() {
        String value = env.getProperty("bluethink.ai.qdrant.collection", "bluethink_knowledge_v1");
        if (!value.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalStateException("Invalid Qdrant collection name");
        return value;
    }

    private URI endpoint(String suffix) {
        URI base = URI.create(env.getProperty("bluethink.ai.qdrant.url", "http://127.0.0.1:6333"));
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) ||
                base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null ||
                base.getFragment() != null || (base.getPath() != null && !base.getPath().isEmpty() && !"/".equals(base.getPath())))
            throw new IllegalStateException("Qdrant URL must be an HTTP(S) origin without path or credentials");
        return URI.create(base.toString().replaceAll("/+$", "") + suffix);
    }

    private JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint(path))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
            String key = env.getProperty("bluethink.ai.qdrant.api-key", "");
            if (!key.isBlank()) request.header("api-key", key);
            if (body != null) request.header("Content-Type", "application/json");
            byte[] payload = body == null ? new byte[0] : json.writeValueAsBytes(body);
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(payload));
            // Stream and cap bytes before allocating: ofByteArray() would buffer an
            // unbounded response before the previous size check could run.
            HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new IllegalStateException("Qdrant " + method + " request failed (HTTP " + response.statusCode() + ")");
                byte[] boundedBody = stream.readNBytes(2_000_001);
                if (boundedBody.length > 2_000_000) throw new IllegalStateException("Qdrant response too large");
                return json.readTree(boundedBody);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant request interrupted", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Qdrant unavailable or returned an invalid response", e);
        }
    }

    public void ensureCollection() {
        if (!enabled()) return;
        String path = "/collections/" + collection();
        // GET collection first: never overwrite an existing collection configuration.
        try {
            JsonNode details = send("GET", path, null);
            int size = details.path("result").path("config").path("params").path("vectors").path("size").asInt(-1);
            String distance = details.path("result").path("config").path("params").path("vectors").path("distance").asText("");
            if (size != DIMENSIONS || !"Cosine".equalsIgnoreCase(distance))
                throw new IllegalStateException("Qdrant collection requires 512-dimensional cosine vectors");
        } catch (IllegalStateException e) {
            if (e.getMessage() == null || !e.getMessage().contains("HTTP 404")) throw e;
            send("PUT", path, Map.of("vectors", Map.of("size", DIMENSIONS, "distance", "Cosine")));
        }
    }

    /** Regenerate this derived collection from MySQL; does not modify MySQL or other collections. */
    public void resetIndex() {
        if (!enabled()) return;
        String path = "/collections/" + collection();
        try { send("DELETE", path, null); }
        catch (IllegalStateException e) {
            if (e.getMessage() == null || !e.getMessage().contains("HTTP 404")) throw e;
        }
        send("PUT", path, Map.of("vectors", Map.of("size", DIMENSIONS, "distance", "Cosine")));
    }

    public void upsert(List<AiKnowledgeChunk> rows) {
        if (!enabled() || rows.isEmpty()) return;
        ensureCollection();
        List<Map<String, Object>> points = new ArrayList<>();
        for (AiKnowledgeChunk row : rows) {
            try {
                double[] vector = json.readValue(row.embedding, double[].class);
                if (!validVector(vector)) throw new IllegalStateException("Invalid document embedding for Qdrant");
                points.add(Map.of("id", row.id, "vector", vector,
                        "payload", Map.of("source_id", row.sourceId, "embedding_model", row.embeddingModel,
                                "roles", roles(row.allowedRoles))));
            } catch (IOException e) { throw new IllegalStateException("Invalid stored embedding", e); }
        }
        send("PUT", "/collections/" + collection() + "/points?wait=true", Map.of("points", points));
    }

    /** Return candidate IDs only. ALWAYS re-authorize every ID against the current MySQL records. */
    public List<String> queryIds(double[] query, String role, String model) {
        if (!enabled()) return List.of();
        if (!validVector(query) || !Set.of("EMPLOYEE", "MANAGER", "HR", "ADMIN").contains(role))
            throw new IllegalArgumentException("Invalid authorized vector query");
        ensureCollection();
        Map<String, Object> filters = Map.of("must", List.of(
                Map.of("key", "roles", "match", Map.of("value", role)),
                Map.of("key", "embedding_model", "match", Map.of("value", model))));
        JsonNode data = send("POST", "/collections/" + collection() + "/points/query",
                Map.of("query", query, "limit", 100, "with_payload", false, "with_vector", false, "filter", filters));
        List<String> ids = new ArrayList<>();
        for (JsonNode item : data.path("result").path("points")) {
            String id = item.path("id").asText("");
            if (id.matches("[0-9a-fA-F-]{36}")) ids.add(id);
        }
        return List.copyOf(ids);
    }

    public void deleteSource(String sourceId) {
        if (!enabled()) return;
        ensureCollection();
        send("POST", "/collections/" + collection() + "/points/delete?wait=true", Map.of(
                "filter", Map.of("must", List.of(Map.of("key", "source_id", "match", Map.of("value", sourceId))))));
    }

    static List<String> roles(String stored) {
        if (stored == null) return List.of();
        return Arrays.stream(stored.split("\\|"))
                .filter(s -> Set.of("EMPLOYEE", "MANAGER", "HR", "ADMIN").contains(s)).distinct().toList();
    }

    static boolean validVector(double[] values) {
        if (values == null || values.length != DIMENSIONS) return false;
        double norm = 0;
        for (double value : values) { if (!Double.isFinite(value)) return false; norm += value * value; }
        return norm > 0 && Double.isFinite(norm);
    }
}
