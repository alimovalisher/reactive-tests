package com.fnklabs.reactive.examples.reactive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.annotation.ConnectMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class RSocketController {
    private static final Logger log = LoggerFactory.getLogger(RSocketController.class);
    private final R2dbcEntityTemplate r2dbcTemplate;


    @Autowired
    public RSocketController(R2dbcEntityTemplate r2dbcTemplate) {
        this.r2dbcTemplate = r2dbcTemplate;
    }

    @MessageMapping("messages.add")
    public Flux<Void> addMessages(Flux<Message> messages) {
        return messages.flatMap(message -> r2dbcTemplate.insert(Message.class).using(message))
                       .thenMany(Flux.empty());

    }

    @MessageMapping("messages.get")
    public Flux<Message> getMessages(String author) {
        return r2dbcTemplate.getDatabaseClient()
                            .sql("select id, text,  author, created_at from message where author = :author")
                            .bind("author", author)
                            .map(row -> new Message(
                                            row.get("id", String.class),
                                            row.get("text", String.class),
                                            row.get("author", String.class),
                                            getCreatedAt(row)

                                    )
                            )
                            .all()
                            .onErrorResume(Exception.class, e -> {
                                log.error("error: {}", e.getMessage(), e);
                                return Flux.empty();
                            });
    }

    private ZonedDateTime getCreatedAt(io.r2dbc.spi.Readable row) {
        try {
            return row.get("created_at", Date.class).toInstant().atZone(ZoneOffset.UTC);
        } catch (Exception e) {
            log.error("error: {}", row.get("created_at", String.class), e);

            return null;
        }
    }
}
