package com.hmdp.service;

import java.util.function.Consumer;

public interface IAiCustomerService {

    String chat(String message);

    default String chat(String message, String conversationId) { return chat(message); }

    void stream(String message, Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError);

    default void stream(String message, String conversationId, Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
        stream(message, onToken, onComplete, onError);
    }
}
