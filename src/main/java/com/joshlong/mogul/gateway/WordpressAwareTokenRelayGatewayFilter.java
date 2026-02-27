package com.joshlong.mogul.gateway;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * propagates a WordPress OAuth token as a separate header.
 */
class WordpressAwareTokenRelayGatewayFilter implements GatewayFilter {

	private static final String CLIENT_REGISTRATION_ID = "wordpress";

	private final ServerOAuth2AuthorizedClientRepository clientRepository;

	WordpressAwareTokenRelayGatewayFilter(ServerOAuth2AuthorizedClientRepository clientRepository) {
		this.clientRepository = clientRepository;
	}

	@Override
	public @NonNull Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return exchange //
			.getPrincipal() //
			.filter(p -> p instanceof OAuth2AuthenticationToken) //
			.cast(OAuth2AuthenticationToken.class)//
			.flatMap(auth -> clientRepository.loadAuthorizedClient(CLIENT_REGISTRATION_ID, auth, exchange) //
				.flatMap(client -> {
					var token = client.getAccessToken().getTokenValue();
					var modified = exchange.getRequest() //
						.mutate() //
						.header("X-WordPress-Token", token) //
						.build();//
					return chain.filter(exchange.mutate().request(modified).build());
				}) //
			) //
			.switchIfEmpty(chain.filter(exchange));
	}

}
