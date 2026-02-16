package com.joshlong.mogul.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }


    @Bean
    RouteLocator gateway(
            WordpressAwareTokenRelayGatewayFilter wordPressTokenRelayFilter,
            RouteLocatorBuilder rlb,
            TokenEnrichingTokenRelayGatewayFilter tokenRelay,
            @Value("${mogul.gateway.ui}") String ui,
            @Value("${mogul.gateway.api}") String api
    ) {
        var apiPrefix = "/api/";
        var retries = 5;
        return rlb//
                .routes()
                .route(rs -> rs //
                        .path(apiPrefix + "**") //
                        .filters(f -> f //
                                .retry(retries) //
                                .filter(tokenRelay)
                                .filter(wordPressTokenRelayFilter)
                                .rewritePath(apiPrefix + "(?<segment>.*)", "/$\\{segment}")//
                        )
                        .uri(api) //
                )//
                .route(rs -> rs//
                        .path("/**") //
                        .filters(f -> f.retry(retries).filter(tokenRelay)) //
                        .uri(ui) //
                ) //
                .build();
    }

    @Bean
    WordpressAwareTokenRelayGatewayFilter wordpressAwareTokenRelayGatewayFilter(
            ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository) {
        return new WordpressAwareTokenRelayGatewayFilter(serverOAuth2AuthorizedClientRepository);
    }

    @Bean
    TokenEnrichingTokenRelayGatewayFilter tokenEnrichingTokenRelayGatewayFilter(
            ReactiveOAuth2AuthorizedClientManager clientManager) {
        return new TokenEnrichingTokenRelayGatewayFilter(clientManager);
    }

    @Bean
    RouterFunction<ServerResponse> loginRoutes() {
        return route() //
                .GET("/login", _ -> ServerResponse.temporaryRedirect(URI.create("/oauth2/authorization/auth0")).build()) //
                .build();
    }


}
