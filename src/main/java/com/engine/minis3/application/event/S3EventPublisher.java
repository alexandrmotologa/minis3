package com.engine.minis3.application.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publishes real-time activity events to connected SSE (Server-Sent Events) clients.
 */
@Component
public class S3EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(S3EventPublisher.class);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Subscribes a new SSE listener for live telemetry.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(3600_000L); // 1 hour timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            S3ActivityEvent initial = new S3ActivityEvent(
                    UUID.randomUUID().toString(),
                    "CONNECTED",
                    null,
                    null,
                    "MiniS3 Real-Time SSE Stream Connected",
                    Instant.now()
            );
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .id(initial.id())
                    .data(initial));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts an activity event to all active clients.
     */
    public void publish(String type, String bucket, String key, String details) {
        S3ActivityEvent event = new S3ActivityEvent(
                UUID.randomUUID().toString(),
                type,
                bucket,
                key,
                details,
                Instant.now()
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(type)
                        .id(event.id())
                        .data(event));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
