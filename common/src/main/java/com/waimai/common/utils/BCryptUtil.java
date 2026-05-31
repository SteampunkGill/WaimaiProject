package com.waimai.common.utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt 安全工具——包装 jBCrypt，对非法哈希格式（如明文密码）返回错误而非崩溃
 */
public final class BCryptUtil {

    private BCryptUtil() {}

    /**
     * 校验明文密码与哈希值，哈希格式非法时返回 false 而不抛异常
     */
    public static boolean checkPassword(String plaintext, String hashed) {
        if (hashed == null || !hashed.startsWith("$2")) {
            return false;
        }
        try {
            return BCrypt.checkpw(plaintext, hashed);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String hashPassword(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt());
    }
}
