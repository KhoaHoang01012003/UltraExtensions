package com.pythonburp.bridge;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CryptoBridge {
    public String sha256Hex(byte[] data) {
        return digestHex("SHA-256", data);
    }

    public String sha1Hex(byte[] data) {
        return digestHex("SHA-1", data);
    }

    private String digestHex(String algorithm, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is unavailable", e);
        }
    }
}
