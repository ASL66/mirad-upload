package com.example.mirad.controller;

import com.example.mirad.config.AppProperties;
import com.example.mirad.security.RateLimiter;
import com.example.mirad.service.FileService;
import com.example.mirad.util.ApiResponses;
import com.example.mirad.util.RequestUtils;
import com.example.mirad.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private static final Duration UPLOAD_WINDOW = Duration.ofMinutes(5);

    private final AppProperties appProperties;
    private final FileService fileService;
    private final RateLimiter rateLimiter;

    public FileController(AppProperties appProperties, FileService fileService, RateLimiter rateLimiter) {
        this.appProperties = appProperties;
        this.fileService = fileService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/list")
    public Map<String, Object> list(HttpServletRequest request) throws IOException {
        String username = RequestUtils.requireSessionUsername(request);
        return ApiResponses.fileList(fileService.listFiles(username));
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request
    ) throws IOException {
        String username = RequestUtils.requireSessionUsername(request);
        String rateLimitKey = RequestUtils.clientIp(request) + ":" + username;
        if (!rateLimiter.allow("upload", rateLimitKey, 30, UPLOAD_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many upload requests, please retry later");
        }

        if (files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file found in upload request");
        }
        if (files.size() > appProperties.getMaxFilesPerUpload()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many files in one upload request");
        }

        return ApiResponses.uploadResult(fileService.saveFiles(username, files));
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(
            @RequestParam("file") String fileName,
            @RequestParam(name = "preview", defaultValue = "false") boolean preview,
            HttpServletRequest request
    ) throws IOException {
        String username = RequestUtils.requireSessionUsername(request);

        Path file;
        try {
            file = fileService.resolveFile(username, fileName);
        } catch (FileNotFoundException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        } catch (SecurityException exception) {
            throw new ApiException(HttpStatus.FORBIDDEN, exception.getMessage());
        }

        String contentType = fileService.detectContentType(file);
        String encodedName = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8).replace("+", "%20");

        if (!preview) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        ContentDisposition disposition = ContentDisposition.builder(preview ? "inline" : "attachment")
                .filename(file.getFileName().toString(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(Files.size(file))
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(Files.newInputStream(file)));
    }

    @DeleteMapping("/delete")
    public Map<String, Object> delete(
            @RequestParam("file") String fileName,
            HttpServletRequest request
    ) throws IOException {
        String username = RequestUtils.requireSessionUsername(request);

        try {
            if (!fileService.deleteFile(username, fileName)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
            }
        } catch (FileNotFoundException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        } catch (SecurityException exception) {
            throw new ApiException(HttpStatus.FORBIDDEN, exception.getMessage());
        }

        return ApiResponses.success("File deleted");
    }
}
