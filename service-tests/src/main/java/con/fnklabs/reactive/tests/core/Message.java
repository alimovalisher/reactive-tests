package con.fnklabs.reactive.tests.core;

import java.time.ZonedDateTime;
import java.util.UUID;

public record Message(UUID id, String text, String author, ZonedDateTime createdAt) {
}
