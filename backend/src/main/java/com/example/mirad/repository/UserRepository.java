package com.example.mirad.repository;

import com.example.mirad.config.AppProperties;
import com.example.mirad.model.StoredUser;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

@Repository
public class UserRepository {
    private final Path userRoot;

    public UserRepository(AppProperties appProperties) {
        this.userRoot = appProperties.getUserRoot();
    }

    public Optional<StoredUser> findByUsername(String username) throws IOException {
        Path userFile = resolveUserFile(username);
        if (!Files.exists(userFile)) {
            return Optional.empty();
        }

        String raw = Files.readString(userFile, StandardCharsets.UTF_8);
        return Optional.of(StoredUser.parse(username, raw));
    }

    public boolean exists(String username) throws IOException {
        return Files.exists(resolveUserFile(username));
    }

    public void save(StoredUser storedUser) throws IOException {
        Path targetFile = resolveUserFile(storedUser.getUsername());
        Files.createDirectories(targetFile.getParent());

        Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");
        Files.writeString(
                tempFile,
                storedUser.serialize(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        try {
            Files.move(
                    tempFile,
                    targetFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveUserFile(String username) {
        Path userFile = userRoot.resolve(username + ".user").toAbsolutePath().normalize();
        if (!userFile.startsWith(userRoot)) {
            throw new SecurityException("Invalid username path");
        }
        return userFile;
    }
}
