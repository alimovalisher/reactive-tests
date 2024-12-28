
package com.fnklabs.reactive.examples.flux;

import java.time.ZonedDateTime;

public record Message(String id, String text, String author, ZonedDateTime createdAt) {
}
