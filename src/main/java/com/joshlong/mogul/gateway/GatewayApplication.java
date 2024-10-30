package com.joshlong.mogul.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

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
					.filter(tokenRelay)
					.retry(retries) //
					.rewritePath(apiPrefix + "(?<segment>.*)", "/$\\{segment}")//
				)
				.uri(api) //
			)//
			.route(rs -> rs//
				.path("/**") //
				.filters(f -> f.retry(retries)) //
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

@ConfigurationProperties(prefix = "mogul.gateway")
record GatewayProperties(String apiPrefix, URI api, String uiPrefix, URI ui) {
}

/**
 * a useful fix from <a href="https://github.com/okta/okta-spring-boot/issues/596">Matt
 * Raible</a>
 */
@Configuration
class SecurityConfiguration {

	private final String audience;

	private final ReactiveClientRegistrationRepository clientRegistrationRepository;

	SecurityConfiguration(ReactiveClientRegistrationRepository clientRegistrationRepository,
			@Value("${auth0.audience}") String audience) {
		this.clientRegistrationRepository = clientRegistrationRepository;
		this.audience = audience;
	}

	@Bean
	@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	SecurityWebFilterChain securityWebFilterChain(GatewayProperties properties, ServerHttpSecurity http) {

		var apiPrefix = properties.apiPrefix().endsWith("/") ? properties.apiPrefix() : properties.apiPrefix() + "/";
		return http//
			.authorizeExchange((authorize) -> authorize//
				.matchers(EndpointRequest.toAnyEndpoint())
				.permitAll()//
				.pathMatchers(apiPrefix + "feeds/**")
				.permitAll()
				.anyExchange()
				.authenticated()//
			)//
			.oauth2Login(oauth2 -> oauth2
				.authorizationRequestResolver(this.authorizationRequestResolver(this.clientRegistrationRepository)))
			.csrf(ServerHttpSecurity.CsrfSpec::disable)//
			.oauth2Login(Customizer.withDefaults())//
			.oauth2Client(Customizer.withDefaults())//
			.build();
	}

	private ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver(
			ReactiveClientRegistrationRepository clientRegistrationRepository) {
		var authorizationRequestResolver = new DefaultServerOAuth2AuthorizationRequestResolver(
				clientRegistrationRepository);
		authorizationRequestResolver.setAuthorizationRequestCustomizer(
				customizer -> customizer.additionalParameters(params -> params.put("audience", audience)));
		return authorizationRequestResolver;
	}

}