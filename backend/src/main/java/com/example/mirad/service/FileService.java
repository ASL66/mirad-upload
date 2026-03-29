package com.example.mirad.service;

import com.example.mirad.config.AppProperties;
import com.example.mirad.model.FileInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class FileService {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final DataSize MAX_FILE_SIZE = DataSize.ofMegabytes(50);

    private final AppProperties appProperties;

    public FileService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public List<String> saveFiles(String username, List<MultipartFile> files) throws IOException {
        Path userDir = ensureUserDir(username);
        List<String> savedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_SIZE.toBytes()) {
                throw new IllegalArgumentException("File exceeds the single-file size limit");
            }

            String cleanFileName = sanitizeFileName(file.getOriginalFilename());
            if (cleanFileName.isBlank()) {
                throw new IllegalArgumentException("File name is invalid");
            }

            Path targetFile = uniqueTarget(userDir, cleanFileName);
            Files.write(targetFile, file.getBytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            savedFiles.add(targetFile.getFileName().toString());
        }

        return savedFiles;
    }

    public List<FileInfo> listFiles(String username) throws IOException {
        Path userDir = ensureUserDir(username);
        List<FileInfo> files = new ArrayList<>();

        try (var stream = Files.list(userDir)) {
            stream.filter(path -> !Files.isDirectory(path))
                    .sorted(Comparator.comparing(this::lastModifiedSafely).reversed())
                    .forEach(path -> files.add(toFileInfo(path)));
        }

        return files;
    }

    public Path resolveFile(String username, String rawFileName) throws IOException {
        Path userDir = ensureUserDir(username);
        String cleanFileName = sanitizeFileName(rawFileName);
        if (cleanFileName.isBlank()) {
            throw new SecurityException("Invalid file path");
        }

        Path target = userDir.resolve(cleanFileName).toAbsolutePath().normalize();
        if (!target.startsWith(userDir)) {
            throw new SecurityException("Invalid file path");
        }
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new FileNotFoundException(cleanFileName);
        }

        return target;
    }

    public boolean deleteFile(String username, String rawFileName) throws IOException {
        Path target = resolveFile(username, rawFileName);
        return Files.deleteIfExists(target);
    }

    public String detectContentType(Path file) throws IOException {
        String contentType = Files.probeContentType(file);
        if (contentType != null) {
            return contentType;
        }

        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".txt") || fileName.endsWith(".log") || fileName.endsWith(".md")) {
            return "text/plain; charset=UTF-8";
        }
        if (fileName.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private FileInfo toFileInfo(Path file) {
        try {
            long modifiedAt = Files.getLastModifiedTime(file).toMillis();
            return new FileInfo(
                    file.getFileName().toString(),
                    Files.size(file),
                    modifiedAt,
                    DATE_FORMAT.format(Instant.ofEpochMilli(modifiedAt))
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file metadata", exception);
        }
    }

    private long lastModifiedSafely(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private Path ensureUserDir(String username) throws IOException {
        Path userDir = appProperties.userUploadDir(username);
        if (!userDir.startsWith(appProperties.getUploadRoot())) {
            throw new SecurityException("Invalid upload directory");
        }
        Files.createDirectories(userDir);
        return userDir;
    }

    private Path uniqueTarget(Path userDir, String cleanFileName) {
        Path candidate = userDir.resolve(cleanFileName).toAbsolutePath().normalize();
        if (!Files.exists(candidate)) {
            return candidate;
        }

        int dotIndex = cleanFileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? cleanFileName.substring(0, dotIndex) : cleanFileName;
        String extension = dotIndex > 0 ? cleanFileName.substring(dotIndex) : "";
        int counter = 1;

        while (true) {
            Path retry = userDir.resolve(baseName + "-" + counter + extension).toAbsolutePath().normalize();
            if (!Files.exists(retry)) {
                return retry;
            }
            counter++;
        }
    }

    private String sanitizeFileName(String rawFileName) {
        if (rawFileName == null) {
            return "";
        }

        String clean = rawFileName.replace('\\', '/');
        int slashIndex = clean.lastIndexOf('/');
        if (slashIndex >= 0) {
            clean = clean.substring(slashIndex + 1);
        }

        clean = clean.trim().replaceAll("[\\p{Cntrl}]", "_");
        clean = clean.replaceAll("[^\\p{L}\\p{N}._()\\- \\[\\]]", "_");
        while (clean.contains("..")) {
            clean = clean.replace("..", ".");
        }
        if (clean.startsWith(".")) {
            clean = "_" + clean.substring(1);
        }
        if (clean.length() > 120) {
            int dotIndex = clean.lastIndexOf('.');
            if (dotIndex > 0) {
                String extension = clean.substring(dotIndex);
                String baseName = clean.substring(0, dotIndex);
                clean = baseName.substring(0, Math.min(baseName.length(), 100)) + extension;
            } else {
                clean = clean.substring(0, 120);
            }
        }

        return clean;
    }
}
