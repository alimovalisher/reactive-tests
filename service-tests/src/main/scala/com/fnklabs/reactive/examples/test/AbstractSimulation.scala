package com.fnklabs.reactive.examples.test

import io.gatling.core.Predef.{jsonPath, _}
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.duration.{DurationInt, FiniteDuration}

abstract class AbstractSimulation extends Simulation {
  val timeout: FiniteDuration = 100.millis

  val minUsersCount: Int = System.getenv().getOrDefault("MIN_USERS", "10").toInt
  val maxUsersCount: Int = System.getenv().getOrDefault("MAX_USERS", "100").toInt
  val repeats: Int = System.getenv().getOrDefault("REPEATS", "10").toInt
  val duration: FiniteDuration = System.getenv().getOrDefault("DURATION", "60").toInt.seconds


  var host = System.getenv().getOrDefault("HOST", "localhost")
  var port = System.getenv().getOrDefault("PORT", "9000")
  val url: String = System.getenv().getOrDefault("URL", "http://localhost:8080")

  val counterApiUrl = s"${url}/counter";
  val messagesApiUrl = s"${url}/messages";

  println(s"Endpoint: ${url}")


}
