package com.pythonburp.bridge;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.Objects;

import static burp.api.montoya.http.message.requests.HttpRequest.httpRequestFromUrl;

public final class MontoyaHttpBridge implements HttpBridge {
    private final MontoyaApi api;

    public MontoyaHttpBridge(MontoyaApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public HttpResult send(String method, String url, String body) {
        HttpRequest request = httpRequestFromUrl(url)
            .withMethod(method)
            .withBody(body == null ? "" : body)
            .withDefaultHeaders();
        HttpRequestResponse requestResponse = api.http().sendRequest(request);
        HttpResponse response = requestResponse.response();
        if (response == null) {
            return new HttpResult(0, "");
        }
        return new HttpResult(response.statusCode(), response.bodyToString());
    }
}
