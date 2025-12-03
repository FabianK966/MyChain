package org.fintech;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Random;

public class StringUtil {

    public static String applySha256(String input) {
        String toHash = (input == null) ? "" : input;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toHash.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Base58 für  Bitcoin-Adressen
    public static String base58Encode(byte[] input) {
        String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        StringBuilder result = new StringBuilder();
        java.math.BigInteger num = new java.math.BigInteger(1, input);
        while (num.compareTo(java.math.BigInteger.ZERO) > 0) {
            java.math.BigInteger[] divRem = num.divideAndRemainder(java.math.BigInteger.valueOf(58));
            num = divRem[0];
            result.insert(0, ALPHABET.charAt(divRem[1].intValue()));
        }
        for (byte b : input) {
            if (b != 0) break;
            result.insert(0, '1');
        }
        return result.toString();
    }

    // 🌟 HINZUGEFÜGT: Methode zur Generierung eines einfachen Zufallspassworts
    public static String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(10);
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}