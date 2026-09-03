package com.example.travel.support;

import com.example.travel.tool.ToolErrorCode;

public final class ToolFailureClassifier {

    private ToolFailureClassifier() {
    }

    public static ToolErrorCode fromHttp(int status, String body) {
        String text = body == null ? "" : body.toLowerCase();
        if (status == 429 || text.contains("rate limit")) {
            return ToolErrorCode.RATE_LIMITED;
        }
        if (status == 401) {
            return ToolErrorCode.UNAUTHORIZED;
        }
        if (status == 403 || text.contains("function_access_restricted")) {
            return ToolErrorCode.FORBIDDEN;
        }
        if (status == 404) {
            return ToolErrorCode.NOT_FOUND;
        }
        if (status == 400 || status == 422) {
            return ToolErrorCode.INVALID_INPUT;
        }
        if (status >= 500) {
            return ToolErrorCode.RETRYABLE;
        }
        return ToolErrorCode.UNKNOWN;
    }

    public static ToolErrorCode fromException(Throwable throwable) {
        if (throwable == null) {
            return ToolErrorCode.UNKNOWN;
        }
        String name = throwable.getClass().getSimpleName();
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase();
        if (name.contains("Timeout") || message.contains("timed out") || message.contains("timeout")) {
            return ToolErrorCode.TIMEOUT;
        }
        if (throwable instanceof org.springframework.web.client.ResourceAccessException) {
            return ToolErrorCode.TIMEOUT;
        }
        return ToolErrorCode.UNKNOWN;
    }
}
