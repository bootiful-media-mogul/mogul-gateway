package com.joshlong.mogul.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.server.SecurityWebFilterChain;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

	public static void main(String[] args) {
		if (System.getenv("DEBUG") != null && System.getenv("DEBUG").equals("true"))
			System.getenv().forEach((k, v) -> System.out.println(k + "=" + v));
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb, @Value("${mogul.gateway.ui}") String ui,
			@Value("${mogul.gateway.api}") String api) {
		var apiPrefix = "/api/";
		var retries = 5;
		return rlb//
			.routes()
			.route(rs -> rs.path(apiPrefix + "**")
				.filters(f -> f //
					.tokenRelay()
					.retry(retries) //
					.rewritePath(apiPrefix + "(?<segment>.*)", "/$\\{segment}")//
				)
				.uri(api))
			.route(rs -> rs.path("/**").filters(f -> f.retry(retries)).uri(ui))
			.build();
	}

}
/*
 * class TokenEnrichingTokenRelayGatewayFilter implements GatewayFilter {
 *
 * private final Logger log = LoggerFactory.getLogger(getClass());
 *
 * private final ReactiveOAuth2AuthorizedClientManager clientManager;
 *
 * private final ObjectMapper objectMapper;
 *
 * TokenEnrichingTokenRelayGatewayFilter(ReactiveOAuth2AuthorizedClientManager
 * clientManager, ObjectMapper objectMapper) { this.clientManager = clientManager;
 * this.objectMapper = objectMapper; }
 *
 * private String json(Object o) { try { return this.objectMapper.writeValueAsString(o); }
 * // catch (Throwable throwable) { throw new RuntimeException(throwable); } }
 *
 * @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain
 * chain) { return exchange.getPrincipal().flatMap(principal -> { if (principal instanceof
 * OAuth2AuthenticationToken authentication) { var clientRegistrationId =
 * authentication.getAuthorizedClientRegistrationId(); var auth2AuthorizeRequest =
 * OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
 * .principal(authentication) .build(); var map = new HashMap<String, Object>(); if
 * (auth2AuthorizeRequest.getPrincipal() instanceof OAuth2AuthenticationToken token) { var
 * fullProfile = token.getPrincipal().getAttributes(); var desired = Set.of("given_name",
 * "picture", "email", "family_name"); var desiredMapToSendOnward = fullProfile.entrySet()
 * .stream() .filter(entry -> desired.contains(entry.getKey()) && entry.getValue() !=
 * null) .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
 * map.putAll(desiredMapToSendOnward); } else Assert.state(false,
 * "the principal must be an OAuth2AuthenticationToken.");
 *
 * return clientManager.authorize(auth2AuthorizeRequest)
 * .map(OAuth2AuthorizedClient::getAccessToken) .map(token -> exchange.mutate().request(r
 * -> r.headers(h -> { h.setBearerAuth(token.getTokenValue()); h.add("X-Auth-Details",
 * json(map)); })
 *
 * ).build()); } return Mono.empty();
 *
 * }).defaultIfEmpty(exchange).flatMap(chain::filter); }
 *
 * }
 */

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
	SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		return http//
			.authorizeExchange((authorize) -> authorize//
				.matchers(EndpointRequest.toAnyEndpoint())
				.permitAll()//
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