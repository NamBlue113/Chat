package com.messenger.shared.util;

import org.mindrot.jbcrypt.BCrypt;

public final class BCryptUtil {

    private static final int WORKLOAD = 12;

    private BCryptUtil() {
        throw new UnsupportedOperationException("BCryptUtil is a utility class and cannot be instantiated");
    }

    public static String hashPassword(String plainTextPassword) {
        if (plainTextPassword == null || plainTextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        String salt = BCrypt.gensalt(WORKLOAD);
        return BCrypt.hashpw(plainTextPassword, salt);
    }

    public static boolean checkPassword(String plainTextPassword, String storedHash) {
        if (plainTextPassword == null || storedHash == null) {
            return false;
        }
        if (!storedHash.startsWith("$2a$") && !storedHash.startsWith("$2b$") && !storedHash.startsWith("$2y$")) {
            throw new IllegalArgumentException("Stored hash is not a valid BCrypt hash");
        }
        return BCrypt.checkpw(plainTextPassword, storedHash);
    }
}
