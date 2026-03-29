package com.example.mirad.util;

import com.example.mirad.model.FileInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiResponses {
    private ApiResponses() {
    }

    public static Map<String, Object> success(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        return body;
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    public static Map<String, Object> loginState(boolean loggedIn, String username) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loggedIn", loggedIn);
        if (loggedIn && username != null) {
            body.put("username", username);
        }
        return body;
    }

    public static Map<String, Object> uploadResult(List<String> fileNames) {
        Map<String, Object> body = success("Upload completed");
        body.put("files", fileNames);
        return body;
    }

    public static Map<String, Object> fileList(List<FileInfo> files) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("files", files);
        return body;
    }
}
