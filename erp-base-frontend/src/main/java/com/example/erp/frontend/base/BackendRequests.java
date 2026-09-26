package com.example.erp.frontend.base;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

public final class BackendRequests {
    static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private BackendRequests() { }

    public static HttpRequest.Builder newBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15));
        String token = TOKEN.get();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder;
    }
}