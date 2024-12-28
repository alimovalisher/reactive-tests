package con.fnklabs.reactive.tests;

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import con.fnklabs.reactive.tests.core.Feed;
import con.fnklabs.reactive.tests.core.Message;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.cbor.Jackson2CborDecoder;
import org.springframework.http.codec.cbor.Jackson2CborEncoder;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@ShellComponent
public class TestCommand {
    private static final Logger log = LoggerFactory.getLogger(TestCommand.class);
    public static final String DEFAULT_USERS = "500";
    public static final String DEFAULT_THREADS = "8";
    public static final String DEFAULT_MESSAGES = "10";
    public static final String TEST_TYPE_WEB = "web";
    public static final String TEST_TYPE_FLUX = "flux";
    public static final String DEFAULT_URL = "http://localhost:8080";
    public static final String DEFAULT_DURATION = "30s";
    public static final String DEFAULT_TYPE = TEST_TYPE_WEB;
    public static final String TEST_TYPE_RSOCKET = "rsocket";


    private final WebClient webClient;
    private final MeterRegistry meterRegistry;


    private final AtomicReference<AutoCloseable> activeTestContext = new AtomicReference<>();

    @Autowired
    public TestCommand(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;


        webClient = WebClient.builder()
                             .clientConnector(new ReactorClientHttpConnector(
                                     HttpClient.create(
                                                       ConnectionProvider.builder("test")
                                                                         .maxConnections(1000)
                                                                         .pendingAcquireMaxCount(10_000)
                                                                         .pendingAcquireTimeout(Duration.of(30, ChronoUnit.SECONDS))
                                                                         .maxIdleTime(Duration.of(5, ChronoUnit.SECONDS))
                                                                         .build()
                                               )
                                               .runOn(LoopResources.create("http-client"))
                                               .metrics(true, Function.identity())
                                               .responseTimeout(Duration.of(60, ChronoUnit.HOURS))
                                               .keepAlive(true)
                             ))
                             .codecs(config -> {
                                         config.defaultCodecs()
                                               .maxInMemorySize(512_1024);
                                     }
                             )
                             .build();


    }

    @ShellMethod(value = "stop testing")
    public void stop() {
        addNewContext(null);

        log.info("stopped");
    }

    @ShellMethod(key = "messages:create", value = "run testing for messages create")
    public void create(
            @ShellOption(value = "type", defaultValue = DEFAULT_TYPE) String type,
            @ShellOption(value = "users", defaultValue = DEFAULT_USERS) Integer usersCount,
            @ShellOption(value = "threads", defaultValue = DEFAULT_THREADS) Integer threads,
            @ShellOption(value = "messages", defaultValue = DEFAULT_MESSAGES) Integer messagesBatchSize,
            @ShellOption(value = "address", defaultValue = DEFAULT_URL) String uri,
            @ShellOption(value = "duration", defaultValue = DEFAULT_DURATION) Duration duration) {

        Scheduler scheduler = createScheduler(threads);


        Feed feed = new Feed(usersCount);

        Timer timer = createMessagePostTimer(
                type,
                Tag.of("users", String.valueOf(usersCount)),
                Tag.of("threads", String.valueOf(threads)),
                Tag.of("messages", String.valueOf(messagesBatchSize))
        );

        ParallelFlux<List<Message>> generateMsgFlux = Flux.<Message>create(sink -> {
                                                              for (int j = 0; j < messagesBatchSize * usersCount; j++) {
                                                                  sink.next(feed.get());
                                                              }
                                                              sink.complete();
                                                          })
                                                          .buffer(messagesBatchSize)
                                                          .parallel(threads)
                                                          .runOn(scheduler);

        List<Disposable> disposables = new ArrayList<>();

        ParallelFlux<Void> sendMsgFlux = switch (type) {
            case TEST_TYPE_WEB -> generateMsgFlux.flatMap(msg -> {
                        long begin = System.currentTimeMillis();

                        return webClient.post()
                                        .uri(uri, uriBuilder -> {
                                            return uriBuilder.path("/messages")
                                                             .build();
                                        })
                                        .bodyValue(msg)
                                        .retrieve()
                                        .bodyToMono(Void.class)
                                        .doFinally(s -> {
                                            timer.record(System.currentTimeMillis() - begin, TimeUnit.MILLISECONDS);
                                        });
                    }
            );
            case TEST_TYPE_FLUX -> generateMsgFlux.flatMap(msg -> {

                        long begin = System.currentTimeMillis();

                        return webClient.post()
                                        .uri(uri, uriBuilder -> {
                                            return uriBuilder.path("/messages")
                                                             .build();
                                        })
                                        .contentType(MediaType.APPLICATION_NDJSON)
                                        .body(Flux.fromIterable(msg), Message.class)
                                        .retrieve()
                                        .bodyToMono(Void.class)
                                        .doFinally(s -> {
                                            timer.record(System.currentTimeMillis() - begin, TimeUnit.MILLISECONDS);
                                        });
                    }
            );
            case TEST_TYPE_RSOCKET -> {
                RSocketRequester requester = createRSocketRequester(uri);

                disposables.add(requester);

                yield generateMsgFlux.flatMap(msg -> {
                    long begin = System.currentTimeMillis();

                    return requester.route("messages.add")
                                    .data(Flux.fromIterable(msg), Message.class)
                                    .retrieveFlux(Void.class)
                                    .doFinally(s -> {
                                        timer.record(System.currentTimeMillis() - begin, TimeUnit.MILLISECONDS);
                                    });
                });
            }
            default -> ParallelFlux.from(Flux.empty());
        };

        Disposable subscribed = sendMsgFlux.then()
                                           .onErrorContinue(Exception.class, (r, e) -> {
                                               log.warn("error", r);
                                           })
                                           .repeat()
                                           .doOnComplete(() -> log.info("send flux completed"))
                                           .subscribeOn(scheduler)
                                           .subscribe();


        disposables.add(subscribed);

        TestContext context = new TestContext(Disposables.composite(subscribed), scheduler);

        addNewContext(context);

        try {
            Thread.sleep(duration.toMillis());
        } catch (Exception e) {

        } finally {
            addNewContext(null);
        }
    }

    @ShellMethod(key = "messages:get", value = "run testing for message get")
    public void get(
            @ShellOption(value = "type", defaultValue = DEFAULT_TYPE) String type,
            @ShellOption(value = "users", defaultValue = DEFAULT_USERS) Integer usersCount,
            @ShellOption(value = "threads", defaultValue = DEFAULT_THREADS) Integer threads,
            @ShellOption(value = "address", defaultValue = DEFAULT_URL) String uri,
            @ShellOption(value = "duration", defaultValue = DEFAULT_DURATION) Duration duration) {


        Scheduler scheduler = createScheduler(threads);

        Timer messageGetTimer = createMessageGetTimer(type,
                Tag.of("users", String.valueOf(usersCount)),
                Tag.of("threads", String.valueOf(threads))
        );

        Counter messageGauge = createMessageGauge(type, Tag.of("users", String.valueOf(usersCount)), Tag.of("threads", String.valueOf(threads)));

        AtomicLong counter = new AtomicLong(0);

        ParallelFlux<Long> usersFlux = Flux.<Long>create(sink -> {
                                               for (int j = 0; j < usersCount; j++) {
                                                   sink.next(counter.get() % usersCount);

                                                   counter.incrementAndGet();
                                               }
                                               sink.complete();
                                           })
                                           .parallel(threads)
                                           .runOn(scheduler);

        List<Disposable> disposables = new ArrayList<>();

        ParallelFlux<Void> getMessagesFlux = switch (type) {
            case TEST_TYPE_WEB -> usersFlux.flatMap(userId -> {

                long begin = System.currentTimeMillis();

                return webClient.get()
                                .uri(uri, uriBuilder -> {
                                    return uriBuilder.path("/messages")
                                                     .queryParam("author", "author-%d".formatted(userId))
                                                     .build();
                                })
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<List<Message>>() {
                                })
                                .doOnNext(msg -> messageGauge.increment(msg.size()))
                                .doFinally(s -> {
                                    messageGetTimer.record(System.currentTimeMillis() - begin, TimeUnit.MILLISECONDS);
                                })
                                .then();
            });
            case TEST_TYPE_FLUX -> usersFlux.flatMap(userId -> {

                long begin = System.currentTimeMillis();

                return webClient.get()
                                .uri(uri, uriBuilder -> {
                                    return uriBuilder.path("/messages")
                                                     .queryParam("author", "author-%d".formatted(userId))
                                                     .build();
                                })
                                .accept(MediaType.APPLICATION_NDJSON)
                                .retrieve()
                                .bodyToFlux(Message.class)
                                .count()
                                .doOnNext(messageGauge::increment)
                                .doFinally(s -> {
                                    messageGetTimer.record(System.currentTimeMillis() - begin, TimeUnit.MILLISECONDS);
                                })
                                .then();
            });


            case TEST_TYPE_RSOCKET -> {
                RSocketRequester requester = createRSocketRequester(uri);

                disposables.add(requester);

                yield usersFlux.flatMap(userId -> {
                    messageGauge.increment();
                    long begin = System.currentTimeMillis();

                    return requester.route("messages.get")
                                    .data("author-%d".formatted(userId))
                                    .retrieveFlux(Message.class)
                                    .count()
                                    .doOnNext(messageGauge::increment)
                                    .onErrorResume(Exception.class, e -> {
                                        log.warn("can't get messages", e);

                                        return Mono.empty();
                                    })
                                    .doFinally(s -> {
                                        messageGetTimer.record(System.currentTimeMillis() - begin, TimeUnit.MILLISECONDS);
                                    })
                                    .then();
                });
            }
            default -> ParallelFlux.from(Flux.empty());
        };

        Disposable subscribed = getMessagesFlux.then()
                                               .onErrorContinue(Exception.class, (e, r) -> {
                                                   log.warn("error on executing request {}", e.getMessage());
                                               })
                                               .repeat()
                                               .doOnComplete(() -> log.info("completed"))
                                               .subscribe();


        disposables.add(subscribed);

        TestContext context = new TestContext(Disposables.composite(disposables), scheduler);

        addNewContext(context);

        try {
            Thread.sleep(duration.toMillis());
        } catch (Exception _) {

        } finally {
            addNewContext(null);
        }
    }

    private RSocketRequester createRSocketRequester(String uri) {
        HostAndPort hostAndPort = HostAndPort.fromString(uri);
        RSocketRequester requester = RSocketRequester.builder()
                                                     .rsocketStrategies(
                                                             RSocketStrategies.builder()
                                                                              .metadataExtractor(new DefaultMetadataExtractor())
                                                                              .encoders(encoders -> encoders.add(new Jackson2CborEncoder()))
                                                                              .decoders(decoders -> decoders.add(new Jackson2CborDecoder()))
                                                                              .build()
                                                     )

                                                     .tcp(hostAndPort.getHost(), hostAndPort.getPort());
        return requester;
    }

    private void addNewContext(TestContext context) {
        activeTestContext.getAndUpdate(existing -> {
            if (existing != null) {
                try {
                    existing.close();
                } catch (Exception _) {
                }
            }
            return context;
        });
    }

    private Scheduler createScheduler(Integer threads) {
        return Schedulers.newParallel(threads, new ThreadFactoryBuilder().setNameFormat("test").build());
    }

    private Counter createMessageGauge(String type, Tag... tags) {
        return Counter.builder("messages-get-size")
                      .tag("type", type)
                      .tags(List.of(tags))
                      .register(meterRegistry);
    }

    private Timer createMessagePostTimer(String type, Tag... tags) {
        return Timer.builder("messages-post")
                    .tag("type", type)
                    .tags(List.of(tags))
                    .register(meterRegistry);
    }

    private Timer createMessageGetTimer(String type, Tag... tags) {

        return Timer.builder("messages-get")
                    .tag("type", type)
                    .tags(List.of(tags))
                    .register(meterRegistry);
    }


    private record TestContext(Disposable dispose,
                               Scheduler scheduler) implements AutoCloseable {
        @Override
        public void close() {
            try {
                log.info("closing test context...");


                dispose.dispose();
                while (!dispose.isDisposed()) {
                    Thread.sleep(1_000);
                }

                scheduler.disposeGracefully().toFuture().get();

                log.info("context closed");
            } catch (Exception e) {
                log.warn("error on closing test context", e);
            }
        }
    }
}
