package com.joshlong.mogul.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// todo does retrying the request in this filter fix things?
class TokenEnrichingTokenRelayGatewayFilter implements GatewayFilter {

	private final ReactiveOAuth2AuthorizedClientManager clientManager;

	TokenEnrichingTokenRelayGatewayFilter(ReactiveOAuth2AuthorizedClientManager clientManager) {
		this.clientManager = clientManager;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return exchange //
			.getPrincipal() //
			.flatMap(principal -> { //
				if (principal instanceof OAuth2AuthenticationToken authentication) {
					var clientRegistrationId = authentication.getAuthorizedClientRegistrationId();
					var auth2AuthorizeRequest = OAuth2AuthorizeRequest//
						.withClientRegistrationId(clientRegistrationId)//
						.principal(authentication)//
						.build();
					var tokenMono = clientManager//
						.authorize(auth2AuthorizeRequest)//
						.map(OAuth2AuthorizedClient::getAccessToken)//
						.map(AbstractOAuth2Token::getTokenValue);
					return tokenMono
						.map(token -> exchange.mutate().request(r -> r.headers(h -> h.setBearerAuth(token))).build())
						.retry(3);

				}
				return Mono.empty();

			}) //
			.defaultIfEmpty(exchange) //
			.flatMap(chain::filter);
	}

}
