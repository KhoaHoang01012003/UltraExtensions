package com.pythonburp.bridge;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Objects;

import static burp.api.montoya.http.message.requests.HttpRequest.httpRequestFromUrl;

public final class MontoyaRepeaterBridge implements RepeaterBridge {
    private final MontoyaApi api;

    public MontoyaRepeaterBridge(MontoyaApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public void send(String method, String url, String body, String tabName) {
        HttpRequest request = httpRequestFromUrl(url)
            .withMethod(method)
            .withBody(body == null ? "" : body)
            .withDefaultHeaders();
        if (tabName == null || tabName.isBlank()) {
            api.repeater().sendToRepeater(request);
            return;
        }
        api.repeater().sendToRepeater(request, tabName);
    }
}
