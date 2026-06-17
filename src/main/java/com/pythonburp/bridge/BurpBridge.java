package com.pythonburp.bridge;

public final class BurpBridge {
    private final EncoderBridge encoder = new EncoderBridge();
    private final CryptoBridge crypto = new CryptoBridge();
    private final HttpBridge http;
    private final RepeaterBridge repeater;

    public BurpBridge() {
        this(HttpBridge.unavailable(), RepeaterBridge.unavailable());
    }

    public BurpBridge(HttpBridge http) {
        this(http, RepeaterBridge.unavailable());
    }

    public BurpBridge(HttpBridge http, RepeaterBridge repeater) {
        this.http = http;
        this.repeater = repeater;
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

    public RepeaterBridge repeater() {
        return repeater;
    }
}
