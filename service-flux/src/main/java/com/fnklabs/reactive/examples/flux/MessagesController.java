package com.fnklabs.reactive.examples.flux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

@RestController
@RequestMapping("/messages")
public class MessagesController {

    private static final Logger log = LoggerFactory.getLogger(MessagesController.class);
    private final R2dbcEntityTemplate r2dbcTemplate;

    @Autowired
    public MessagesController(R2dbcEntityTemplate jdbcTemplate) {
        this.r2dbcTemplate = jdbcTemplate;
    }


    @GetMapping
    public Flux<Message> getMessages(@RequestParam("author") String author) {
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
                            .all();
    }

    private ZonedDateTime getCreatedAt(io.r2dbc.spi.Readable row) {
        try {
            return row.get("created_at", Date.class).toInstant().atZone(ZoneOffset.UTC);
        } catch (Exception e) {
            log.error("error: {}", row.get("created_at", String.class), e);

            return null;
        }
    }


    @PostMapping
    public Mono<Void> addMessages(@RequestBody Flux<Message> messages) {
        return messages.flatMap(message -> r2dbcTemplate.insert(Message.class).using(message))
                       .then();

    }
}
