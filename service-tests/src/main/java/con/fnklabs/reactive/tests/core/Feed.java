package con.fnklabs.reactive.tests.core;


import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class Feed implements Supplier<Message> {
    public static final AtomicLong counter = new AtomicLong(0);
    private final int users;

    public Feed(int users) {
        this.users = users;
    }


    @Override
    public Message get() {
        long counter = Feed.counter.getAndIncrement();

        return new Message(UUID.randomUUID(), UUID.randomUUID() + " - " + counter, "author-" + counter % users, ZonedDateTime.now());
    }
}
