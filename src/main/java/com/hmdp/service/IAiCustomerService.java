package com.hmdp.service;

import java.util.function.Consumer;

public interface IAiCustomerService {

    String chat(String message);

    void stream(String message, Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError);
}
