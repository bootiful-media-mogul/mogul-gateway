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
					.tokenRelay() //
					.retry(retries) //
					.rewritePath(apiPrefix + "(?<segment>.*)", "/$\\{segment}")//
				)
				.uri(api))
			.route(rs -> rs.path("/**").filters(f -> f.retry(retries)).uri(ui))
			.build();
	}

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