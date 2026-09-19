package com.example.travel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GraphProgressHub {

    private static final Logger log = LoggerFactory.getLogger(GraphProgressHub.class);
    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L;

    private final ConcurrentHashMap<String, HubSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public GraphProgressHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void open(String threadId) {
        hubSession(threadId);
    }

    public SseEmitter subscribe(String threadId) {
        HubSession hubSession = hubSession(threadId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        hubSession.addEmitter(emitter);
        emitter.onCompletion(() -> hubSession.removeEmitter(emitter));
        emitter.onTimeout(() -> {
            hubSession.removeEmitter(emitter);
            log.debug("SSE timeout threadId={}", threadId);
        });
        emitter.onError(error -> {
            hubSession.removeEmitter(emitter);
            log.debug("SSE transport error threadId={} message={}", threadId,
                    error == null ? "unknown" : error.getMessage());
        });
        for (Map<String, Object> event : hubSession.copyReplay()) {
            send(hubSession, emitter, event);
        }
        return emitter;
    }

    public void emit(String threadId, String type, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("ts", System.currentTimeMillis());
        event.putAll(payload);
        HubSession hubSession = hubSession(threadId);
        hubSession.appendReplay(event);
        for (SseEmitter emitter : hubSession.copyEmitters()) {
            send(hubSession, emitter, event);
        }
        if ("complete".equals(type) || "failed".equals(type)) {
            for (SseEmitter emitter : hubSession.copyEmitters()) {
                completeQuietly(hubSession, emitter);
            }
        }
    }

    public void close(String threadId) {
        HubSession hubSession = sessions.get(threadId);
        if (hubSession == null) {
            return;
        }
        for (SseEmitter emitter : hubSession.copyEmitters()) {
            completeQuietly(hubSession, emitter);
        }
    }

    private HubSession hubSession(String threadId) {
        return sessions.computeIfAbsent(threadId, key -> new HubSession());
    }

    private void send(HubSession session, SseEmitter emitter, Map<String, Object> event) {
        try {
            String name = String.valueOf(event.getOrDefault("type", "node"));
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().name(name).data(json));
        } catch (Exception ex) {
            log.debug("SSE send failed; removing emitter: {}", ex.getMessage());
            session.removeEmitter(emitter);
            // Once send() fails, the servlet response is no longer usable. Remove
            // the emitter immediately and never call complete() on this failed
            // response. Spring may otherwise raise AsyncRequestNotUsableException
            // while trying to complete an already-broken async response.
            // The completion callback will also remove it, making this idempotent.
        }
    }

    private static void completeQuietly(HubSession session, SseEmitter emitter) {
        session.removeEmitter(emitter);
        if (!session.markClosed(emitter)) {
            return;
        }
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // The client may already have disconnected. The emitter is already removed.
        }
    }

    private static final class HubSession {
        private final List<Map<String, Object>> replay = new ArrayList<>();
        private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private final Map<SseEmitter, AtomicBoolean> closedEmitters = new ConcurrentHashMap<>();

        private void addEmitter(SseEmitter emitter) {
            emitters.add(emitter);
            closedEmitters.put(emitter, new AtomicBoolean(false));
        }

        private void removeEmitter(SseEmitter emitter) {
            emitters.remove(emitter);
            // Keep the closed marker briefly for idempotent completion. It is
            // removed when a new emitter is created for the same object only.
        }

        private boolean markClosed(SseEmitter emitter) {
            return closedEmitters.computeIfAbsent(emitter, ignored -> new AtomicBoolean(false))
                    .compareAndSet(false, true);
        }

        private List<SseEmitter> copyEmitters() {
            return List.copyOf(emitters);
        }

        private List<Map<String, Object>> copyReplay() {
            synchronized (replay) {
                return List.copyOf(replay);
            }
        }

        private void appendReplay(Map<String, Object> event) {
            synchronized (replay) {
                replay.add(event);
                if (replay.size() > 200) {
                    replay.remove(0);
                }
            }
        }
    }
}
