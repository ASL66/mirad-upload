package com.example.mirad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mirad")
public class AppProperties {
    private Path dataDir = Paths.get("data");
    private int maxFilesPerUpload = 10;
    private List<String> corsAllowedOrigins = new ArrayList<>();

    public Path getDataDir() {
        return dataDir.toAbsolutePath().normalize();
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public int getMaxFilesPerUpload() {
        return maxFilesPerUpload;
    }

    public void setMaxFilesPerUpload(int maxFilesPerUpload) {
        this.maxFilesPerUpload = maxFilesPerUpload;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public Path getUploadRoot() {
        return getDataDir().resolve("uploads").toAbsolutePath().normalize();
    }

    public Path getUserRoot() {
        return getDataDir().resolve("users").toAbsolutePath().normalize();
    }

    public Path userUploadDir(String username) {
        return getUploadRoot().resolve(username).toAbsolutePath().normalize();
    }

    public void ensureDirectories() {
        try {
            Files.createDirectories(getDataDir());
            Files.createDirectories(getUploadRoot());
            Files.createDirectories(getUserRoot());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize Mirad data directories", exception);
        }
    }
}
