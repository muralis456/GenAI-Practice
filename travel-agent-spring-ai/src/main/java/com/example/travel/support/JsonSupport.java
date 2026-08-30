package com.example.travel.support;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JsonSupport {

    private static final Logger log = LoggerFactory.getLogger(JsonSupport.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> read(String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String json = unwrap(raw);
        try {
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception first) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return Optional.ofNullable(objectMapper.readValue(json.substring(start, end + 1), type));
                } catch (Exception ignored) {
                    log.debug("Could not parse {} from model output", type.getSimpleName());
                }
            }
            return Optional.empty();
        }
    }

    private String unwrap(String raw) {
        Matcher matcher = JSON_BLOCK.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return raw.trim();
    }
}
