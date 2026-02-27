package com.joshlong.mogul.gateway;

import com.joshlong.mogul.gateway.settings.SettingsClient;
import com.joshlong.mogul.gateway.settings.SettingsPage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Objects;

class Tokens {

}

@Component
class CurrentToken {

	private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

	CurrentToken(ReactiveOAuth2AuthorizedClientService authorizedClientService) {
		this.authorizedClientService = authorizedClientService;
	}

	@NonNull
	Mono<String> getAccessToken(OAuth2AuthenticationToken authentication) {
		return authorizedClientService //
			.loadAuthorizedClient(authentication.getAuthorizedClientRegistrationId(), authentication.getName()) //
			.map(client -> client.getAccessToken().getTokenValue());
	}

}

/**
 * A reactive client registration repository that is aware of the tenant {@code settings}
 * table and can dynamically register client registrations based on those settings.
 *
 * @author Josh Long
 */
class MogulSettingsAwareReactiveClientRegistrationRepository implements ReactiveClientRegistrationRepository {

	private final ObjectProvider<CurrentToken> token;

	private final SettingsClient settings;

	private final Logger log = LoggerFactory.getLogger(getClass());

	private static final String WORDPRESS_CONSTANT = "wordpress";

	private final ClientRegistration auth0ClientRegistration;

	private final boolean traceEnabled = this.log.isTraceEnabled();

	MogulSettingsAwareReactiveClientRegistrationRepository(ObjectProvider<CurrentToken> token, Environment environment,
			SettingsClient settings) {

		Assert.notNull(token, "token cannot be null");
		Assert.notNull(environment, "environment cannot be null");
		Assert.notNull(settings, "settings cannot be null");

		this.token = token;
		this.settings = settings;
		this.auth0ClientRegistration = this.registerAuth0Client(environment);

	}

	private String valueFor(SettingsPage settingsPage, String k) {
		Assert.notNull(settingsPage, "the settingsPage must not be null");
		Assert.notNull(k, "the key must not be null");

		for (var setting : settingsPage.settings()) {
			if (setting.name().equals(k)) {
				if (this.traceEnabled) {
					this.log.trace("setting: {}={}", k, setting.value());
				}
				return setting.value();
			}
		}
		if (this.traceEnabled) {
			this.log.trace("couldn't find {}.", k);
		}
		return null;
	}

	/**
	 * registers an OAuth client for WordPress, whose definition we can only derive from
	 * the downstream {@code settings} table, accessed via the GraphQL settings API.
	 */
	private Mono<ClientRegistration> registerWordpressClient(SettingsPage settingsPage) {
		var name = WORDPRESS_CONSTANT;
		var authorizationUri = this.valueFor(settingsPage, "authorizationUri");
		var tokenUri = this.valueFor(settingsPage, "tokenUri");
		var clientId = this.valueFor(settingsPage, "clientId");
		var clientSecret = this.valueFor(settingsPage, "clientSecret");
		return Mono.just(ClientRegistration.withRegistrationId(name)
			.clientId(clientId)
			.clientSecret(clientSecret)
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/oauth2/code/{registrationId}")
			.scope("global")
			.authorizationUri(authorizationUri)
			.tokenUri(tokenUri)
			.clientName(name)
			.build());
	}

	private ClientRegistration registerAuth0Client(Environment environment) {
		var name = "auth0";
		return ClientRegistrations
			.fromOidcIssuerLocation(Objects.requireNonNull(environment.getProperty("auth0.domain")))
			.registrationId(name)
			.clientId(environment.getProperty("AUTH0_CLIENT_ID"))
			.clientSecret(environment.getProperty("AUTH0_CLIENT_SECRET"))
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.scope("profile", "openid", "email")
			.clientName(name)
			.build();
	}

	@Override
	public Mono<ClientRegistration> findByRegistrationId(String registrationId) {

		// fast path.
		if (registrationId.equals(auth0ClientRegistration.getRegistrationId())) {
			return Mono.just(auth0ClientRegistration);
		}

		// otherwise load settings
		return ReactiveSecurityContextHolder //
			.getContext() //
			.flatMap(sc -> {
				var auth = Objects.requireNonNull(sc.getAuthentication());
				if (auth instanceof OAuth2AuthenticationToken auth2AuthenticationToken) {
					var currentToken = this.token.getIfAvailable();
					Assert.notNull(currentToken, "currentToken cannot be null");
					return currentToken.getAccessToken(auth2AuthenticationToken);
				} //
				return Mono.error(new IllegalStateException(String.format(
						"no token available for authentication %s while attempting to look up secondary %s", auth,
						ClientRegistration.class.getName())));
			})//
			.flatMap(accessToken -> this.settings.getSettings(accessToken)
				.filter(sp -> sp.category().equals(WORDPRESS_CONSTANT))
				.singleOrEmpty())//
			.flatMap(this::registerWordpressClient);
	}

}
