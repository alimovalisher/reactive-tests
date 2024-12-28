package com.fnklabs.reactive.examples.reactive;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.cbor.Jackson2CborDecoder;
import org.springframework.http.codec.cbor.Jackson2CborEncoder;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

class RSocketControllerTest {
    private static final Logger log = LoggerFactory.getLogger(RSocketControllerTest.class);

    @Test
    void name() {
        RSocketRequester requester = RSocketRequester.builder()
                                                     .rsocketStrategies(
                                                             RSocketStrategies.builder()
                                                                              .metadataExtractor(new DefaultMetadataExtractor())
                                                                              .encoders(encoders -> encoders.add(new Jackson2CborEncoder()))
                                                                              .decoders(decoders -> decoders.add(new Jackson2CborDecoder()))
                                                                              .build()
                                                     )
                                                     .tcp("localhost", 9000);

        Flux<Void> sendMessageMono = requester.route("messages.add")
                                              .data(Flux.fromIterable(List.of(new Message(UUID.randomUUID().toString(), "", "author-1", ZonedDateTime.now()))), Message.class)
                                              .retrieveFlux(Void.class);

        System.out.println(sendMessageMono.then().block());

        Mono<List<Message>> messagesList = requester.route("messages.get")
                                                    .data("author-1")
                                                    .retrieveFlux(Message.class)
                                                    .collectList();
        System.out.println(messagesList.block());


    }
}