package com.example.erp.users;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private PasswordHasher() { }

    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return format(ITERATIONS, salt, derive(password.toCharArray(), salt, ITERATIONS));
    }

    static boolean matches(String password, String storedHash) {
        try {
            String[] values = storedHash.split(":");
            if (values.length != 3) return false;
            int iterations = Integer.parseInt(values[0]);
            byte[] salt = Base64.getDecoder().decode(values[1]);
            byte[] expected = Base64.getDecoder().decode(values[2]);
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, HASH_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            specification.clearPassword();
        }
    }

    private static String format(int iterations, byte[] salt, byte[] hash) {
        return iterations + ":" + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(hash);
    }
}