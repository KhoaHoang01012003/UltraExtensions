package com.pythonburp.bridge;

public final class BurpBridge {
    private final EncoderBridge encoder = new EncoderBridge();
    private final CryptoBridge crypto = new CryptoBridge();
    private final HttpBridge http;

    public BurpBridge() {
        this(HttpBridge.unavailable());
    }

    public BurpBridge(HttpBridge http) {
        this.http = http;
    }

    public EncoderBridge encoder() {
        return encoder;
    }

    public CryptoBridge crypto() {
        return crypto;
    }

    public HttpBridge http() {
        return http;
    }
}
