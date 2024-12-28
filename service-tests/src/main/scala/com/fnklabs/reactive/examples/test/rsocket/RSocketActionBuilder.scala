package com.fnklabs.reactive.examples.test.rsocket

import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import org.springframework.core.codec.{Decoder, Encoder}
import org.springframework.http.codec.cbor.{Jackson2CborDecoder, Jackson2CborEncoder}
import org.springframework.messaging.rsocket.RSocketRequester.{RequestSpec, RetrieveSpec}
import org.springframework.messaging.rsocket.{DefaultMetadataExtractor, RSocketRequester, RSocketStrategies}
import org.springframework.web.client.RestClient.ResponseSpec
import reactor.core.publisher.Mono

import java.util
import java.util.{List, UUID}
import java.util.concurrent.Flow.Publisher

class RSocketActionBuilder(host: String, port: Int,
                           route: String,
                           bodyFunc: Function[RequestSpec, RetrieveSpec],
                           retrieveFunc: Function[RetrieveSpec, Mono[_]]) extends ActionBuilder {

  def connect(host: String, port: Int): RSocketActionBuilder = {
    return new RSocketActionBuilder(host, port, null, null, null);
  };

  def route(route: String): RSocketActionBuilder = {
    return new RSocketActionBuilder(host, port, route, null, null);
  }

  def body(function: Function[RequestSpec, RetrieveSpec]): RSocketActionBuilder = {
    new RSocketActionBuilder(host, port, route, function, null);
  }

  def retrieve(function: Function[RetrieveSpec, Mono[_]]): RSocketActionBuilder = {
    new RSocketActionBuilder(host, port, route, bodyFunc, function);
  }

  override def build(ctx: ScenarioContext, next: Action): Action = {
    return new RSocketAction(route, ctx.coreComponents.statsEngine, ctx.coreComponents.clock, next, host, port, route, bodyFunc, retrieveFunc)
  }


}
