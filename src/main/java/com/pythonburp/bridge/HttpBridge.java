package com.pythonburp.bridge;

public interface HttpBridge {
    HttpResult send(String method, String url, String body);

    record HttpResult(int statusCode, String body) {
    }

    static HttpBridge unavailable() {
        return (method, url, body) -> new HttpResult(0, "HTTP bridge is not connected to Burp yet");
    }
}
