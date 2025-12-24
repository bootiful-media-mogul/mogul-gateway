package com.joshlong.mogul.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.reactive.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
