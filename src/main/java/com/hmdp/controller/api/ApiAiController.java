package com.hmdp.controller.api;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.dto.AiChatRequest;
import com.hmdp.dto.AiReservationRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IAiCustomerService;
import com.hmdp.service.impl.AiBusinessTools;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/ai")
public class ApiAiController {

    @Resource
    private IAiCustomerService aiCustomerService;
    @Resource
    private AiBusinessTools aiBusinessTools;

    @PostMapping("/chat")
    @RateLimit(key = "api:ai:chat", limit = 20, windowSeconds = 60, type = RateLimitType.IP)
    public Result chat(@RequestBody AiChatRequest request) {
        return Result.ok(aiCustomerService.chat(request.getMessage()));
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "api:ai:chat:stream", limit = 20, windowSeconds = 60, type = RateLimitType.IP)
    public SseEmitter stream(@RequestParam("message") String message) {
        SseEmitter emitter = new SseEmitter(60_000L);
        aiCustomerService.stream(message, chunk -> send(emitter, "message", chunk), () -> {
            send(emitter, "done", "[DONE]");
            emitter.complete();
        }, emitter::completeWithError);
        return emitter;
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @PostMapping("/reservations/preview")
    public Result previewReservation(@RequestBody AiReservationRequest request) {
        return aiBusinessTools.previewReservation(request);
    }

    @PostMapping("/reservations/confirm")
    public Result confirmReservation(@RequestBody AiReservationRequest request) {
        return aiBusinessTools.confirmReservation(request);
    }
}
