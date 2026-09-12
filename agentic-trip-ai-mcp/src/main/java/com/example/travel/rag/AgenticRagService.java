package com.example.travel.rag;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Production-oriented Agentic RAG pipeline used by the LangGraph RAG node.
 *
 * Flow:
 * decide -> query rewrite -> hybrid retrieve (vector + PostgreSQL FTS)
 * -> reciprocal-rank fusion -> LLM rerank -> grounded context compression
 * -> evidence sufficiency check -> optional retry.
 *
 * Real-time facts remain MCP responsibilities; this service is for durable knowledge.
 */
@Service
public class AgenticRagService {

    private static final Logger log = LoggerFactory.getLogger(AgenticRagService.class);
    private static final int MAX_CANDIDATES = 10;
    private static final int MAX_RERANK_INPUT_CHARS = 1100;
    private static final int MAX_CONTEXT_CHARS = 6500;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;
    private final boolean enabled;
    private final int topK;
    private final double similarityThreshold;
    private final int maxIterations;
    private final boolean hybridEnabled;
    private final boolean rerankEnabled;
    private final boolean compressionEnabled;

    public AgenticRagService(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            RoutedLlm routedLlm,
            JsonSupport jsonSupport,
            @Value("${travel.rag.enabled:true}") boolean enabled,
            @Value("${travel.rag.top-k:5}") int topK,
            @Value("${travel.rag.similarity-threshold:0.55}") double similarityThreshold,
            @Value("${travel.rag.max-iterations:2}") int maxIterations,
            @Value("${travel.rag.hybrid.enabled:true}") boolean hybridEnabled,
            @Value("${travel.rag.rerank.enabled:true}") boolean rerankEnabled,
            @Value("${travel.rag.compression.enabled:true}") boolean compressionEnabled) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
        this.enabled = enabled;
        this.topK = Math.max(1, topK);
        this.similarityThreshold = Math.max(0, Math.min(1, similarityThreshold));
        this.maxIterations = Math.max(1, maxIterations);
        this.hybridEnabled = hybridEnabled;
        this.rerankEnabled = rerankEnabled;
        this.compressionEnabled = compressionEnabled;
    }

    public RagResult run(TravelState state) {
        if (!enabled) {
            return RagResult.skipped("disabled");
        }

        Decision decision = decideRetrieval(state);
        if (!decision.retrieve() || decision.query().isBlank()) {
            log.info("Agentic RAG decision=skip reason={} destination={}", decision.reason(), state.destination());
            return RagResult.skipped("llm_decided_no_retrieval");
        }

        List<Document> evidence = List.of();
        Set<String> sources = new LinkedHashSet<>();
        boolean sufficient = false;
        String activeQuery = decision.query().trim();
        int iterations = 0;
        String retrievalMethod = hybridEnabled ? "hybrid_vector_fts" : "vector";
        int candidateCount = 0;
        int rerankedCount = 0;
        String compressedContext = "";

        while (iterations < maxIterations) {
            iterations++;
            Retrieval retrieval = hybridRetrieve(state, decision, activeQuery);
            evidence = retrieval.documents();
            candidateCount = retrieval.candidateCount();
            sources.addAll(sourceNames(evidence));

            if (rerankEnabled && !evidence.isEmpty()) {
                evidence = rerank(state, activeQuery, evidence);
                rerankedCount = evidence.size();
                sources.addAll(sourceNames(evidence));
            }

            compressedContext = compressionEnabled && !evidence.isEmpty()
                    ? compressGroundedContext(state, activeQuery, evidence)
                    : formatEvidence(evidence);

            log.info("Agentic RAG iteration={} query='{}' method={} candidates={} reranked={} contextChars={} sources={}",
                    iterations, activeQuery, retrievalMethod, candidateCount, rerankedCount,
                    compressedContext.length(), sources.size());

            if (evidence.isEmpty()) {
                activeQuery = refineQuery(state, activeQuery, "No relevant documents were found.");
                if (activeQuery.isBlank()) {
                    break;
                }
                continue;
            }

            Evaluation evaluation = evaluateEvidence(state, activeQuery, compressedContext, evidence);
            sufficient = evaluation.sufficient();
            if (sufficient) {
                break;
            }

            String nextQuery = evaluation.nextQuery();
            if (nextQuery.isBlank()) {
                nextQuery = refineQuery(state, activeQuery, evaluation.reason());
            }
            if (nextQuery.isBlank() || nextQuery.equalsIgnoreCase(activeQuery)) {
                break;
            }
            activeQuery = nextQuery.trim();
        }

        String decisionName = sufficient ? "hybrid_reranked_compressed_sufficient"
                : "hybrid_reranked_compressed_best_effort";
        if (!hybridEnabled) {
            decisionName = sufficient ? "vector_reranked_compressed_sufficient" : "vector_best_effort";
        }
        return new RagResult(true, decisionName, activeQuery, compressedContext,
                new ArrayList<>(sources), iterations, sufficient, retrievalMethod,
                candidateCount, rerankedCount, compressedContext.length(),
                evidenceScore(activeQuery, compressedContext, sources),
                decision.destination(), decision.country(), decision.topics());
    }

    private Decision decideRetrieval(TravelState state) {
        String raw = routedLlm.complete(AgentRole.EXTRACT,
                """
                You are the Knowledge Router for a travel-planning agent.
                Decide whether INTERNAL KNOWLEDGE retrieval is needed.
                Use retrieval for durable knowledge such as destination guidance, travel rules,
                packing, culture, safety, planning policies and document-backed facts.
                Do NOT retrieve for live flight availability, hotel prices, live weather, currency rates,
                or airport resolution; those are handled by MCP/tools.
                Return JSON only: {"retrieve":true|false,"query":"concise semantic query","reason":"short reason","destination":"city or destination","country":"country","topics":["culture","food"]}
                """,
                requestContext(state));
        var node = jsonSupport.readTree(raw).orElse(null);
        List<String> topics = new ArrayList<>();
        if (node != null && node.path("topics").isArray()) node.path("topics").forEach(v -> topics.add(v.asString()));
        return new Decision(
                node != null && node.path("retrieve").asBoolean(false),
                node == null ? "" : node.path("query").asString(""),
                node == null ? "" : node.path("reason").asString(""),
                node == null ? state.destination() : node.path("destination").asString(state.destination()),
                node == null ? "" : node.path("country").asString(""),
                topics);
    }

    private Retrieval hybridRetrieve(TravelState state, Decision decision, String query) {
        List<String> queries = multiQueries(state, decision, query);
        Map<String, RankedDocument> merged = new LinkedHashMap<>();
        int rawCandidates = 0;
        int rankBase = 1;

        for (String q : queries) {
            List<Document> vector = vectorSearch(state, decision, q);
            rawCandidates += vector.size();
            addRanked(merged, vector, rankBase++);

            if (hybridEnabled) {
                List<Document> keyword = keywordSearch(state, decision, q, MAX_CANDIDATES);
                rawCandidates += keyword.size();
                addRanked(merged, keyword, rankBase++);
            }
        }

        List<Document> fused = merged.values().stream()
                .sorted(Comparator.comparingDouble(RankedDocument::rrfScore).reversed())
                .limit(MAX_CANDIDATES)
                .map(RankedDocument::document)
                .toList();
        return new Retrieval(fused, Math.max(rawCandidates, merged.size()));
    }

    private List<String> multiQueries(TravelState state, Decision decision, String query) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(query);
        String destination = !TravelState.isBlank(decision.destination()) ? decision.destination() : state.destination();
        if (!TravelState.isBlank(destination)) {
            queries.add(query + " " + destination + " destination travel");
            queries.add(destination + " " + query);
        }
        if (decision.topics() != null && !decision.topics().isEmpty()) {
            queries.add(query + " " + String.join(" ", decision.topics()));
        }
        return queries.stream().limit(3).toList();
    }

    private List<Document> vectorSearch(TravelState state, Decision decision, String query) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK, 8))
                .similarityThreshold(similarityThreshold);
        String filter = destinationFilter(decision);
        if (!filter.isBlank()) {
            builder.filterExpression(filter);
        }
        try {
            List<Document> filtered = vectorStore.similaritySearch(builder.build());
            // A metadata mismatch must never turn into a false "no knowledge" result.
            // If the destination filter yields nothing, retry once without the filter and
            // let RRF + reranking decide relevance.
            if (filtered.isEmpty() && !filter.isBlank()) {
                log.info("RAG vector destination filter returned 0 results; retrying without destination filter destination={} keys={}",
                        decision.destination(), destinationKeys(decision));
                return vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query).topK(Math.max(topK, 8)).similarityThreshold(similarityThreshold).build());
            }
            return filtered;
        } catch (Exception ex) {
            log.warn("Destination metadata vector filter failed; retrying without filter: {}", ex.getMessage());
            return vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query).topK(Math.max(topK, 8)).similarityThreshold(similarityThreshold).build());
        }
    }

    private void addRanked(Map<String, RankedDocument> merged, List<Document> documents, int rankBase) {
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            String key = document.getMetadata().getOrDefault("source", "") + "|" + String.valueOf(document.getText()).hashCode();
            RankedDocument current = merged.get(key);
            double contribution = 1.0 / (60.0 + i + rankBase);
            if (current == null) {
                merged.put(key, new RankedDocument(document, contribution));
            } else {
                current.add(contribution);
            }
        }
    }

    private List<Document> keywordSearch(TravelState state, Decision decision, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String destinationFilter = sqlDestinationFilter(decision);
        String sql = """
                select content, metadata
                from vector_store
                where to_tsvector('simple', content) @@ plainto_tsquery('simple', ?)
                %s
                order by ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) desc
                limit ?
                """.formatted(destinationFilter);
        try {
            List<Object> params = new ArrayList<>();
            params.add(query);
            params.addAll(sqlDestinationParams(decision));
            params.add(query);
            params.add(limit);
            List<Document> filtered = jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> metadata = new LinkedHashMap<>();
                        String rawMetadata = rs.getString("metadata");
                        if (rawMetadata != null) {
                            try {
                                jsonSupport.readTree(rawMetadata).ifPresent(node -> node.properties().forEach(e -> metadata.put(e.getKey(), e.getValue().asString())));
                            } catch (Exception ignored) { }
                        }
                        metadata.putIfAbsent("source", extractSource(rawMetadata));
                        metadata.put("retrieval", "keyword");
                        return new Document(rs.getString("content"), metadata);
                    }, params.toArray());
            if (filtered.isEmpty() && !destinationFilter.isBlank()) {
                log.info("RAG keyword destination filter returned 0 results; retrying without destination filter destination={} keys={}",
                        decision.destination(), destinationKeys(decision));
                String fallbackSql = """
                        select content, metadata
                        from vector_store
                        where to_tsvector('simple', content) @@ plainto_tsquery('simple', ?)
                        order by ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) desc
                        limit ?
                        """;
                return jdbcTemplate.query(fallbackSql,
                        (rs, rowNum) -> {
                            Map<String, Object> metadata = new LinkedHashMap<>();
                            String rawMetadata = rs.getString("metadata");
                            if (rawMetadata != null) {
                                try {
                                    jsonSupport.readTree(rawMetadata).ifPresent(node -> node.properties().forEach(e -> metadata.put(e.getKey(), e.getValue().asString())));
                                } catch (Exception ignored) { }
                            }
                            metadata.putIfAbsent("source", extractSource(rawMetadata));
                            metadata.put("retrieval", "keyword");
                            return new Document(rs.getString("content"), metadata);
                        }, query, query, limit);
            }
            return filtered;
        } catch (Exception ex) {
            log.warn("Hybrid keyword retrieval unavailable; continuing with vector search: {}", ex.getMessage());
            return List.of();
        }
    }

    private String destinationFilter(Decision decision) {
        List<String> keys = destinationKeys(decision);
        // No destination means no metadata restriction. Filtering to global would
        // incorrectly hide destination-specific knowledge from a valid RAG query.
        if (keys.isEmpty()) {
            return "";
        }
        return "destinationKey == 'global' || " + keys.stream()
                .map(key -> "destinationKey == '" + escapeFilter(key) + "'")
                .reduce((a, b) -> a + " || " + b)
                .orElse("");
    }

    private String escapeFilter(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String sqlDestinationFilter(Decision decision) {
        List<String> keys = destinationKeys(decision);
        // No destination means no metadata restriction; search the complete knowledge base.
        if (keys.isEmpty()) {
            return "";
        }
        return "and (metadata ->> 'destinationKey' = 'global' or " + keys.stream()
                .map(key -> "metadata ->> 'destinationKey' = ?")
                .reduce((a, b) -> a + " or " + b)
                .orElse("") + ")";
    }

    private List<Object> sqlDestinationParams(Decision decision) {
        return new ArrayList<>(destinationKeys(decision));
    }

    private List<String> destinationKeys(Decision decision) {
        Set<String> keys = new LinkedHashSet<>();
        addDestinationKeys(keys, decision.destination());
        addDestinationKeys(keys, decision.country());
        return keys.stream().limit(6).toList();
    }

    private void addDestinationKeys(Set<String> keys, String value) {
        if (TravelState.isBlank(value)) {
            return;
        }
        String trimmed = value.trim();
        String normalized = trimmed.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            return;
        }

        keys.add(normalized);
        keys.addAll(DESTINATION_ALIASES.getOrDefault(normalized, List.of()));

        // LLMs commonly return a display value such as "Paris, France" while
        // the vector metadata uses the city key "paris". Add each comma-separated
        // component so display names and canonical metadata keys both match.
        if (trimmed.contains(",")) {
            for (String part : trimmed.split(",")) {
                String component = part.toLowerCase().trim()
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", "");
                if (!component.isBlank()) {
                    keys.add(component);
                    keys.addAll(DESTINATION_ALIASES.getOrDefault(component, List.of()));
                }
            }
        }
    }

    private static final Map<String, List<String>> DESTINATION_ALIASES = Map.ofEntries(
            Map.entry("japan", List.of("japan", "tokyo", "osaka")),
            Map.entry("tokyo", List.of("japan", "tokyo")),
            Map.entry("singapore", List.of("singapore")),
            Map.entry("dubai", List.of("dubai")),
            Map.entry("thailand", List.of("thailand", "bangkok", "phuket")),
            Map.entry("bali", List.of("bali", "indonesia")),
            Map.entry("indonesia", List.of("bali", "indonesia")),
            Map.entry("malaysia", List.of("malaysia", "kuala-lumpur")),
            Map.entry("nepal", List.of("nepal", "kathmandu")),
            Map.entry("sri-lanka", List.of("sri-lanka", "colombo")),
            Map.entry("vietnam", List.of("vietnam", "hanoi", "danang")),
            Map.entry("usa", List.of("usa", "new-york", "los-angeles", "san-francisco")),
            Map.entry("australia", List.of("australia", "sydney", "melbourne")),
            Map.entry("europe", List.of("europe", "paris", "zurich", "rome"))
    );

    private String extractSource(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return "knowledge-base";
        try {
            return jsonSupport.readTree(metadataJson).map(n -> n.path("source").asString("knowledge-base"))
                    .orElse("knowledge-base");
        } catch (Exception ignored) {
            return metadataJson;
        }
    }

    private List<Document> rerank(TravelState state, String query, List<Document> candidates) {
        StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            String text = String.valueOf(candidates.get(i).getText());
            if (text.length() > MAX_RERANK_INPUT_CHARS) text = text.substring(0, MAX_RERANK_INPUT_CHARS);
            prompt.append("[CANDIDATE ").append(i + 1).append("] source=")
                    .append(candidates.get(i).getMetadata().getOrDefault("source", "knowledge-base"))
                    .append("\n").append(text).append("\n\n");
        }
        String raw = routedLlm.complete(AgentRole.EXTRACT,
                """
                You are a strict retrieval reranker. Rank passages only by how directly they help answer
                the user's durable travel-knowledge question. Do not add facts. Prefer precise, relevant evidence.
                Return JSON only: {"ranked":[{"index":1,"score":0.95}, {"index":2,"score":0.70}]}
                Include only useful candidates, highest score first.
                """,
                "USER REQUEST:\n%s\n\nRETRIEVAL QUERY:\n%s\n\n%s".formatted(state.userRequest(), query, prompt));

        List<Integer> indices = new ArrayList<>();
        jsonSupport.readTree(raw).ifPresent(node -> {
            var ranked = node.path("ranked");
            if (ranked.isArray()) {
                ranked.forEach(item -> {
                    int index = item.path("index").asInt(-1) - 1;
                    if (index >= 0 && index < candidates.size() && !indices.contains(index)) indices.add(index);
                });
            }
        });
        if (indices.isEmpty()) return candidates.stream().limit(topK).toList();
        List<Document> result = new ArrayList<>();
        for (Integer index : indices) result.add(candidates.get(index));
        return result.stream().limit(topK).toList();
    }

    private String compressGroundedContext(TravelState state, String query, List<Document> evidence) {
        String rawEvidence = formatEvidence(evidence);
        if (rawEvidence.length() > MAX_CONTEXT_CHARS * 2) rawEvidence = rawEvidence.substring(0, MAX_CONTEXT_CHARS * 2);
        String raw = routedLlm.complete(AgentRole.EXTRACT,
                """
                You are a grounded context compressor.
                Extract only facts explicitly supported by the supplied passages.
                Do not add general knowledge, guesses, recommendations, or live data.
                Preserve source labels in [Source: filename] form so downstream agents can cite them.
                Return JSON only: {"context":"2-6 concise factual bullets or short paragraphs"}
                """,
                "USER REQUEST:\n%s\n\nQUERY:\n%s\n\nPASSAGES:\n%s".formatted(state.userRequest(), query, rawEvidence));
        String context = jsonSupport.readTree(raw).map(n -> n.path("context").asString("")).orElse("").trim();
        return context.isBlank() ? rawEvidence : context;
    }

    private Evaluation evaluateEvidence(TravelState state, String query, String context, List<Document> evidence) {
        String raw = routedLlm.complete(AgentRole.EXTRACT,
                """
                Evaluate whether the retrieved evidence is sufficient for the user's durable knowledge need.
                Sufficient means the passages contain enough directly relevant information to answer without guessing.
                Return JSON only: {"sufficient":true|false,"nextQuery":"","reason":"short reason"}
                """,
                "USER REQUEST:\n%s\n\nQUERY:\n%s\n\nGROUNDED CONTEXT:\n%s\n\nSOURCES:\n%s"
                        .formatted(state.userRequest(), query, context, sourceNames(evidence)));
        return new Evaluation(
                jsonSupport.readTree(raw).map(n -> n.path("sufficient").asBoolean(false)).orElse(false),
                jsonSupport.readTree(raw).map(n -> n.path("nextQuery").asString("")).orElse(""),
                jsonSupport.readTree(raw).map(n -> n.path("reason").asString("")).orElse(""));
    }

    private String refineQuery(TravelState state, String query, String feedback) {
        String raw = routedLlm.complete(AgentRole.EXTRACT,
                """
                Rewrite a semantic query for a travel knowledge base.
                Focus on durable knowledge and the missing information only.
                Return JSON only: {"query":"..."}
                """,
                "User request=%s\nCurrent query=%s\nFeedback=%s".formatted(state.userRequest(), query, feedback));
        return jsonSupport.readTree(raw).map(n -> n.path("query").asString("")).orElse("");
    }

    private String requestContext(TravelState state) {
        return """
                User request: %s
                Origin: %s
                Destination: %s
                Dates: %s to %s
                Travelers: %s
                Travel style: %s
                Budget: %s
                """.formatted(state.userRequest(), state.origin(), state.destination(),
                state.departureDate(), state.returnDate(), state.travelers(), state.travelStyle(), state.budgetLabel());
    }

    private String formatEvidence(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Document document : documents) {
            String source = String.valueOf(document.getMetadata().getOrDefault("source", "knowledge-base"));
            sb.append("[Source: ").append(source).append("]\n")
                    .append(String.valueOf(document.getText())).append("\n\n");
            if (++index > topK + 1) break;
        }
        return sb.toString().trim();
    }

    private List<String> sourceNames(List<Document> documents) {
        return documents.stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault("source", "knowledge-base")))
                .distinct().toList();
    }

    private double evidenceScore(String query, String context, Set<String> sources) {
        if (context == null || context.isBlank()) {
            return 0.0;
        }
        Set<String> queryTerms = terms(query);
        Set<String> contextTerms = terms(context);
        if (queryTerms.isEmpty()) {
            return sources.isEmpty() ? 0.0 : 1.0;
        }
        Set<String> overlap = new LinkedHashSet<>(queryTerms);
        overlap.retainAll(contextTerms);
        return Math.round((double) overlap.size() / queryTerms.size() * 1000d) / 1000d;
    }

    private Set<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String token : text.toLowerCase().split("\\W+")) {
            if (token.length() >= 3) {
                terms.add(token);
            }
        }
        return terms;
    }

    private record Decision(boolean retrieve, String query, String reason, String destination, String country, List<String> topics) {}
    private record Evaluation(boolean sufficient, String nextQuery, String reason) {}
    private record Retrieval(List<Document> documents, int candidateCount) {}
    private static final class RankedDocument {
        private final Document document;
        private double rrfScore;
        private RankedDocument(Document document, double rrfScore) { this.document = document; this.rrfScore = rrfScore; }
        void add(double score) { this.rrfScore += score; }
        Document document() { return document; }
        double rrfScore() { return rrfScore; }
    }

    public record RagResult(boolean used, String decision, String query, String context,
                             List<String> sources, int iterations, boolean sufficient,
                             String retrievalMethod, int candidateCount, int rerankedCount,
                             int contextChars, double evidenceScore, String destination,
                             String country, List<String> topics) {
        static RagResult skipped(String decision) {
            return new RagResult(false, decision, "", "", List.of(), 0, false,
                    "none", 0, 0, 0, 0.0, "", "", List.of());
        }
    }
}
