package com.pythonburp.bridge;

public final class BurpBridge {
    private final EncoderBridge encoder = new EncoderBridge();
    private final CryptoBridge crypto = new CryptoBridge();

    public EncoderBridge encoder() {
        return encoder;
    }

    public CryptoBridge crypto() {
        return crypto;
    }
}
