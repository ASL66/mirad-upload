package com.example.mirad.util;

import com.example.mirad.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public final class RequestUtils {
    private RequestUtils() {
    }

    public static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String sessionUsername(HttpServletRequest request) {
        Object username = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute("username");
        return username == null ? null : username.toString();
    }

    public static String requireSessionUsername(HttpServletRequest request) {
        String username = sessionUsername(request);
        if (username == null || username.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in first");
        }
        return username;
    }
}
