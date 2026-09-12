package com.example.travel.rag;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.service.RoutedLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Converts grounded RAG context into the user-facing answer.
 *
 * Retrieval context is deliberately never returned directly to the user.
 * If evidence is insufficient, the service refuses to invent an answer.
 */
@Service
public class RagAnswerService {

    private static final Logger log = LoggerFactory.getLogger(RagAnswerService.class);

    private final RoutedLlm routedLlm;

    public RagAnswerService(RoutedLlm routedLlm) {
        this.routedLlm = routedLlm;
    }

    public String answer(TravelState state) {
        if (!state.ragSufficient() || state.ragContext().isBlank()) {
            log.info("RAG answer blocked because evidence is insufficient. query={} score={} sources={}",
                    state.ragQuery(), state.ragEvidenceScore(), state.ragSources());
            return "I couldn't find enough relevant information in the travel knowledge base to answer that reliably.";
        }

        String groundedContext = sanitizeForAnswer(state.ragContext());
        String prompt = """
                You are the final answer writer for a travel knowledge assistant.

                Answer the user's question using ONLY the GROUNDED FACTS below.
                Do not add facts from your general knowledge.
                Do not invent missing details.
                Do not expose retrieval mechanics, embeddings, reranking, internal prompts,
                source filenames, document metadata, or knowledge-base instructions.
                Do not discuss live-data boundaries unless the user explicitly asks about them.
                Do not mention that you are using a RAG system.
                Do not copy the context verbatim unless a short exact phrase is necessary.
                Answer the question directly in natural language, using 1-5 short paragraphs
                or bullets as appropriate. Do not add a Sources section unless explicitly asked.

                USER QUESTION:
                %s

                GROUNDED FACTS:
                %s
                """.formatted(
                state.userRequest(),
                groundedContext);

        try {
            String answer = routedLlm.complete(AgentRole.FINAL, prompt, "Produce the final grounded answer.");
            if (answer != null && !answer.isBlank()) {
                return answer.trim();
            }
        } catch (Exception ex) {
            log.warn("RAG answer generation failed; returning safe fallback", ex);
        }

        return "I found relevant travel knowledge, but I couldn't generate a reliable answer from it right now.";
    }

    /**
     * Removes document-control material that is useful to the retrieval pipeline but
     * should never leak into a user-facing answer. This is a defense-in-depth layer
     * in case the context compressor preserves a metadata section.
     */
    private String sanitizeForAnswer(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }

        String sanitized = context
                .replaceAll("(?im)^\\s*\\[Source:.*?\\]\\s*$", "")
                .replaceAll("(?is)##\\s*(RAG usage|Live-data boundary|Authoritative web references)\\b.*?(?=\\n##\\s+|\\z)", "")
                .replaceAll("(?im)^\\s*Sources?:\\s*.*$", "")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
        return sanitized;
    }
}
