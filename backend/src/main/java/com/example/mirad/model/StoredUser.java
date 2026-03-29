package com.example.mirad.model;

import java.util.HashMap;
import java.util.Map;

public final class StoredUser {
    private final String username;
    private final String algorithm;
    private final int iterations;
    private final String saltBase64;
    private final String hashBase64;
    private final String legacyHashHex;

    private StoredUser(
            String username,
            String algorithm,
            int iterations,
            String saltBase64,
            String hashBase64,
            String legacyHashHex
    ) {
        this.username = username;
        this.algorithm = algorithm;
        this.iterations = iterations;
        this.saltBase64 = saltBase64;
        this.hashBase64 = hashBase64;
        this.legacyHashHex = legacyHashHex;
    }

    public static StoredUser modern(
            String username,
            String algorithm,
            int iterations,
            String saltBase64,
            String hashBase64
    ) {
        return new StoredUser(username, algorithm, iterations, saltBase64, hashBase64, null);
    }

    public static StoredUser legacy(String username, String legacyHashHex) {
        return new StoredUser(username, null, 0, null, null, legacyHashHex.toLowerCase());
    }

    public static StoredUser parse(String username, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.matches("^[0-9a-fA-F]{64}$")) {
            return legacy(username, trimmed);
        }

        Map<String, String> values = new HashMap<>();
        for (String line : trimmed.split("\\R")) {
            String item = line.trim();
            if (item.isEmpty() || item.startsWith("#")) {
                continue;
            }
            int separatorIndex = item.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = item.substring(0, separatorIndex).trim();
            String value = item.substring(separatorIndex + 1).trim();
            values.put(key, value);
        }

        String algorithm = values.get("algorithm");
        String iterations = values.get("iterations");
        String salt = values.get("salt");
        String hash = values.get("hash");
        if (algorithm == null || iterations == null || salt == null || hash == null) {
            throw new IllegalArgumentException("Invalid user file format for " + username);
        }

        return modern(username, algorithm, Integer.parseInt(iterations), salt, hash);
    }

    public String serialize() {
        if (isLegacy()) {
            return legacyHashHex + System.lineSeparator();
        }

        return String.join(
                System.lineSeparator(),
                "algorithm=" + algorithm,
                "iterations=" + iterations,
                "salt=" + saltBase64,
                "hash=" + hashBase64
        ) + System.lineSeparator();
    }

    public boolean isLegacy() {
        return legacyHashHex != null;
    }

    public String getUsername() {
        return username;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getIterations() {
        return iterations;
    }

    public String getSaltBase64() {
        return saltBase64;
    }

    public String getHashBase64() {
        return hashBase64;
    }

    public String getLegacyHashHex() {
        return legacyHashHex;
    }
}
