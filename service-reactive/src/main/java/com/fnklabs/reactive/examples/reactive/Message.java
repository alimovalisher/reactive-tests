
package com.fnklabs.reactive.examples.reactive;

import java.time.ZonedDateTime;

public record Message(String id, String text, String author, ZonedDateTime createdAt) {
}
