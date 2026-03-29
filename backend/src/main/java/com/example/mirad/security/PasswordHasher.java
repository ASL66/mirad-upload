package com.example.mirad.security;

import com.example.mirad.model.StoredUser;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class PasswordHasher {
    private static final String DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public StoredUser hash(String username, char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derived = derive(password, DEFAULT_ALGORITHM, DEFAULT_ITERATIONS, salt);
        return StoredUser.modern(
                username,
                DEFAULT_ALGORITHM,
                DEFAULT_ITERATIONS,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derived)
        );
    }

    public boolean matches(char[] password, StoredUser storedUser) {
        if (storedUser.isLegacy()) {
            return MessageDigest.isEqual(
                    storedUser.getLegacyHashHex().getBytes(StandardCharsets.UTF_8),
                    legacySha256(password).getBytes(StandardCharsets.UTF_8)
            );
        }

        byte[] expected = Base64.getDecoder().decode(storedUser.getHashBase64());
        byte[] salt = Base64.getDecoder().decode(storedUser.getSaltBase64());
        byte[] actual = derive(password, storedUser.getAlgorithm(), storedUser.getIterations(), salt);
        boolean matched = MessageDigest.isEqual(expected, actual);
        Arrays.fill(actual, (byte) 0);
        return matched;
    }

    public void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private byte[] derive(char[] password, String algorithm, int iterations, byte[] salt) {
        try {
            PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
            try {
                SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
                return secretKeyFactory.generateSecret(keySpec).getEncoded();
            } finally {
                keySpec.clearPassword();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash password", exception);
        }
    }

    private String legacySha256(char[] password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer passwordBytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
            byte[] input = new byte[passwordBytes.remaining()];
            passwordBytes.get(input);
            byte[] hash = digest.digest(input);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            Arrays.fill(input, (byte) 0);
            Arrays.fill(hash, (byte) 0);
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
