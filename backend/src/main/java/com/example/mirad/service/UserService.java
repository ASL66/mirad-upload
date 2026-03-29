package com.example.mirad.service;

import com.example.mirad.config.AppProperties;
import com.example.mirad.model.StoredUser;
import com.example.mirad.repository.UserRepository;
import com.example.mirad.security.PasswordHasher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(AppProperties appProperties, UserRepository userRepository, PasswordHasher passwordHasher) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public void register(String rawUsername, String rawPassword) throws IOException {
        String username = normalizeUsername(rawUsername);
        String password = rawPassword == null ? "" : rawPassword;

        validateUsername(username);
        validatePassword(password);

        if (userRepository.exists(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        char[] passwordChars = password.toCharArray();
        try {
            StoredUser storedUser = passwordHasher.hash(username, passwordChars);
            userRepository.save(storedUser);
            Files.createDirectories(appProperties.userUploadDir(username));
        } finally {
            passwordHasher.clear(passwordChars);
        }
    }

    public boolean authenticate(String rawUsername, String rawPassword) throws IOException {
        String username = normalizeUsername(rawUsername);
        String password = rawPassword == null ? "" : rawPassword;

        if (username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("Username and password are required");
        }

        Optional<StoredUser> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            return false;
        }

        StoredUser storedUser = optionalUser.get();
        char[] passwordChars = password.toCharArray();
        try {
            boolean matched = passwordHasher.matches(passwordChars, storedUser);
            if (!matched) {
                return false;
            }

            if (storedUser.isLegacy()) {
                userRepository.save(passwordHasher.hash(username, passwordChars));
            }
            Files.createDirectories(appProperties.userUploadDir(username));
            return true;
        } finally {
            passwordHasher.clear(passwordChars);
        }
    }

    private String normalizeUsername(String rawUsername) {
        return rawUsername == null ? "" : rawUsername.trim();
    }

    private void validateUsername(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3-20 chars and contain only letters, numbers, or underscore");
        }
    }

    private void validatePassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password is too long");
        }
    }
}
