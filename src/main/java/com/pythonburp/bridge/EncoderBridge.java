package com.pythonburp.bridge;

import java.util.Base64;

public final class EncoderBridge {
    public String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public byte[] base64Decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
