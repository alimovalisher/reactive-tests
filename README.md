# About

Tests for classic rest application, spring reactive application and service on RSocket

# Description

Each application implemented API

## Accept message

Receive list/stream of messages and save it into DB

```json
{
  "id": Long,
  "text": String,
  "author": String,
  "createdAt": ZonedDateTime
}
```

Then return empty response

```json

```

## List messages by author

Return all messages by author

```
?username=String
```

Return list/stream of messages filtered by author

## Tests

* All tests should use same data feed but could use different approaches to send/receive data
* All tests should measure RPS

