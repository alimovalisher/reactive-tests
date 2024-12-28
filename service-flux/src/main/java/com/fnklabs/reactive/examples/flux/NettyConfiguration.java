package com.fnklabs.reactive.examples.flux;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.resources.LoopResources;

import java.util.function.Function;

@Configuration
public class NettyConfiguration {
    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyPoolCustomizer(@Value("${server.netty.server.threads:#{T(java.lang.Runtime).getRuntime().availableProcessors()*2}}") int threads) {
        return factory -> factory.addServerCustomizers(
                server -> server.runOn(LoopResources.create("netty-server-epoll-%d", threads, false), true)
                                .metrics(true, Function.identity(), Function.identity())
        );
    }
}
