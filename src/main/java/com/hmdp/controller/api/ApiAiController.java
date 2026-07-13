package com.hmdp.controller.api;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.dto.AiChatRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IAiCustomerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;

@RestController
@RequestMapping("/api/ai")
public class ApiAiController {

    @Resource
    private IAiCustomerService aiCustomerService;

    @PostMapping("/chat")
    @RateLimit(key = "api:ai:chat", limit = 20, windowSeconds = 60, type = RateLimitType.IP)
    public Result chat(@RequestBody AiChatRequest request) {
        return Result.ok(aiCustomerService.chat(request.getMessage()));
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "api:ai:chat:stream", limit = 20, windowSeconds = 60, type = RateLimitType.IP)
    public SseEmitter stream(@RequestParam("message") String message) throws IOException {
        SseEmitter emitter = new SseEmitter(30_000L);
        String answer = aiCustomerService.chat(message);
        emitter.send(SseEmitter.event().name("message").data(answer));
        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        emitter.complete();
        return emitter;
    }
}
