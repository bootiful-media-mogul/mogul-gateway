package com.joshlong.mogul.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb, TokenEnrichingTokenRelayGatewayFilter tokenRelay,
			@Value("${mogul.gateway.ui}") String ui, @Value("${mogul.gateway.api}") String api) {
		var apiPrefix = "/api/";
		var retries = 5;
		return rlb//
			.routes()
			.route(rs -> rs //
				.path(apiPrefix + "**") //
				.filters(f -> f //
					.retry(retries) //
					.filter(tokenRelay)
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
	TokenEnrichingTokenRelayGatewayFilter tokenEnrichingTokenRelayGatewayFilter(
			ReactiveOAuth2AuthorizedClientManager clientManager) {
		return new TokenEnrichingTokenRelayGatewayFilter(clientManager);
	}

}
