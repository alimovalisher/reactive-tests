package com.fnklabs.reactive.examples.test.rsocket

import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.{Action, ExitableAction, RequestAction}
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper, Session}
import io.gatling.core.stats.StatsEngine
import org.reactivestreams.Publisher
import org.springframework.core.codec.{Decoder, Encoder}
import org.springframework.http.codec.cbor.{Jackson2CborDecoder, Jackson2CborEncoder}
import org.springframework.messaging.rsocket.{DefaultMetadataExtractor, RSocketRequester, RSocketStrategies}
import org.springframework.messaging.rsocket.RSocketRequester.{RequestSpec, RetrieveSpec}
import reactor.core.publisher.Mono

import java.util
import java.util.Properties

class RSocketAction(val name: String,
                    val statsEngine: StatsEngine,
                    val clock: Clock,
                    val next: Action,
                    val host: String,
                    val port: Int,
                    val route: String,
                    val bodyFunc: Function[RequestSpec, RetrieveSpec],
                    val retrieveFunc: Function[RetrieveSpec, Mono[_]]
                   ) extends RequestAction with ExitableAction {

  override def requestName: Expression[String] = route.expressionSuccess

  override def sendRequest(session: Session): Validation[Unit] = {
    val begin = clock.nowMillis

    val requester: RSocketRequester = createRequester
    try {

      val requestSpec = requester.route(route)


      val requestBegin = clock.nowMillis
      statsEngine.logUserStart(session.scenario)

      val result = bodyFunc.andThen(retrieveFunc).apply(requestSpec)

      result.block()

      val end = clock.nowMillis

      statsEngine.logUserEnd(session.scenario)
      statsEngine.logResponse(session.scenario, session.groups, route, requestBegin, end, OK, Option.empty, Option.empty)


      Success("OK")
    } catch {
      case e: Exception =>
        val end = clock.nowMillis

        statsEngine.logUserEnd(session.scenario)
        statsEngine.logResponse(session.scenario, session.groups, route, begin, end, KO, Option.empty, Option.apply(e.getMessage))

        Failure(e.getMessage)
    } finally {
      requester.dispose()
      next.!(session)
    }

  }

  private def createRequester = {
    val connectBegin = clock.nowMillis
    val requester = RSocketRequester.builder
      .rsocketStrategies(
        RSocketStrategies.builder
          .metadataExtractor(
            new DefaultMetadataExtractor)
          .encoders((encoders: util.List[Encoder[_]]) => encoders.add(new Jackson2CborEncoder))
          .decoders((decoders: util.List[Decoder[_]]) => decoders.add(new Jackson2CborDecoder))
          .build)
      .tcp(host, port)

    val connectEnd = clock.nowMillis

    statsEngine.logTcpConnect(host, connectBegin, connectEnd, Option.empty)

    requester
  }
}
