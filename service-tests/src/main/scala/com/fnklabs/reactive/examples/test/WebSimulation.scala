package com.fnklabs.reactive.examples.test

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.structure.{PopulationBuilder, ScenarioBuilder}

class WebSimulation extends AbstractSimulation {

  val counterGetScenario: ScenarioBuilder = scenario(s"counter get")
    .repeat(repeats) {
      exec(
        http("counter get")
          .get(s"${counterApiUrl}")
          .header(HttpHeaderNames.Accept, HttpHeaderValues.ApplicationJson)
          .check(status.is(200))
      )
    }

  val counterIncrementScenario: ScenarioBuilder = scenario(s"counter increment")
    .repeat(repeats) {
      exec(
        http("counter Increment")
          .post(s"${counterApiUrl}")
          .header(HttpHeaderNames.ContentType, HttpHeaderValues.ApplicationJson)
          .header(HttpHeaderNames.Accept, HttpHeaderValues.ApplicationJson)
          .check(status.is(200))
      )
    }

  val messagesAddScenario: ScenarioBuilder = scenario(s"messages add")
    .repeat(repeats) {
      exec(
        http("messages add")
          .post(s"${messagesApiUrl}")
          .body(ElFileBody("messages-add-request.json"))
          .header(HttpHeaderNames.ContentType, HttpHeaderValues.ApplicationJson)
          .header(HttpHeaderNames.Accept, HttpHeaderValues.ApplicationJson)
          .check(status.is(200))
      )
    }

  val messagesGetScenario: ScenarioBuilder = scenario(s"messages get")
    .repeat(repeats) {
      exec(
        http("messages get")
          .get(s"${messagesApiUrl}")
          .header(HttpHeaderNames.Accept, HttpHeaderValues.ApplicationJson)
          .check(status.is(200))
      )
    }


  setUp(
    counterIncrementScenario.inject(
        rampConcurrentUsers(minUsersCount).to(maxUsersCount).during(duration),
        constantConcurrentUsers(maxUsersCount).during(duration)
      ).andThen(
        counterGetScenario.inject(
          rampConcurrentUsers(minUsersCount).to(maxUsersCount).during(duration),
          constantConcurrentUsers(maxUsersCount).during(duration)
        )
      )
      .andThen(
        messagesAddScenario.inject(
          rampConcurrentUsers(minUsersCount).to(maxUsersCount).during(duration),
          constantConcurrentUsers(maxUsersCount).during(duration)
        )
      )
      .andThen(
        messagesGetScenario.inject(
          rampConcurrentUsers(minUsersCount).to(maxUsersCount).during(duration),
          constantConcurrentUsers(maxUsersCount).during(duration)
        )
      )
      .protocols(http.disableCaching.baseUrl(url))
  )
    .assertions(
      global.successfulRequests.percent.gt(99)
    )
}
