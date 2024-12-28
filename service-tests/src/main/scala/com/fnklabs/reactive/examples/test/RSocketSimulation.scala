package com.fnklabs.reactive.examples.test

import com.fnklabs.reactive.examples.test.rsocket.RSocketActionBuilder
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import reactor.core.publisher.Flux

import java.nio.file.{Files, Path}
import scala.io.Source
import scala.jdk.CollectionConverters.IterableHasAsJava


class RSocketSimulation extends AbstractSimulation {

  val counterGetScenario: ScenarioBuilder = scenario(s"counter get")
    .repeat(repeats) {
      exec(
        new RSocketActionBuilder(host, port.toInt, "counterGet", a => a, response => response.retrieveMono(classOf[Long]))
      ).exitHereIfFailed
    }

  val counterIncrementScenario: ScenarioBuilder = scenario(s"counter increment")
    .repeat(repeats) {
      exec(
        new RSocketActionBuilder(host, port.toInt, "counterIncrement", a => a, response => response.retrieveMono(classOf[Long]))
      )
    }

  val messagesAddScenario: ScenarioBuilder = scenario(s"messages add")
    .repeat(repeats) {
      exec(
        new RSocketActionBuilder(host, port.toInt, "messagesAdd", a => {
          try {
            val strings = Source.fromResource("messages-add-request.txt").getLines().toList
            a.data(Flux.fromIterable(strings.asJava))
          } catch {
            case e: Exception => {
              e.printStackTrace()
              a.data(Flux.empty)
            }
          }

        }, response => response.retrieveFlux(classOf[String]).`then`())
      )
    }

  val messagesGetScenario: ScenarioBuilder = scenario(s"messages get")
    .repeat(repeats) {
      new RSocketActionBuilder(host, port.toInt, "messagesGet", a => a, response => response.retrieveFlux(classOf[String]).`then`())
    }


  setUp(
    counterIncrementScenario.inject(
        rampConcurrentUsers(minUsersCount).to(maxUsersCount).during(duration),
        constantConcurrentUsers(maxUsersCount).during(duration)
      )
      .andThen(
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
  )
    .assertions(
      global.successfulRequests.percent.gt(99)
    )
}
