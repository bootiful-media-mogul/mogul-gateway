package com.joshlong.mogul.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joshlong.mogul.gateway.settings.SettingsClient;
import com.joshlong.mogul.gateway.settings.SettingsPage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
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
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

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
 * table and can dynamically register OAuth client registrations based on those settings.
 *
 * @author Josh Long
 */
class MogulSettingsAwareReactiveClientRegistrationRepository implements ReactiveClientRegistrationRepository {

	private static final String WORDPRESS_CONSTANT = "wordpress";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ObjectProvider<CurrentToken> token;

	private final SettingsClient settings;

	private final ClientRegistration auth0ClientRegistration;

	private final boolean traceEnabled = this.log.isTraceEnabled();

	private final Cache<String, ClientRegistration> clientRegistrationCache = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofSeconds(30))
		.maximumSize(10_000)
		.build();

	MogulSettingsAwareReactiveClientRegistrationRepository(ObjectProvider<CurrentToken> token, Environment environment,
			SettingsClient settings) {
		Assert.notNull(token, "token cannot be null");
		Assert.notNull(environment, "environment cannot be null");
		Assert.notNull(settings, "settings cannot be null");
		this.token = token;
		this.settings = settings;
		this.auth0ClientRegistration = this.registerAuth0Client(environment);
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
					return currentToken.getAccessToken(auth2AuthenticationToken)
						.map(access -> new PrincipalTokenRegistrationId(auth2AuthenticationToken, access,
								auth2AuthenticationToken.getAuthorizedClientRegistrationId()));
				} //
				return Mono.error(new IllegalStateException(String.format(
						"no token available for authentication %s while attempting to look up secondary %s", auth,
						ClientRegistration.class.getName())));
			})//
			.flatMap(this::getClientRegistration);
	}

	private Mono<ClientRegistration> getClientRegistration(PrincipalTokenRegistrationId accessToken) {
		var key = this.buildValidCacheKey(accessToken);
		var cached = this.clientRegistrationCache.getIfPresent(key);
		if (cached != null) {
			if (this.traceEnabled) {
				this.log.trace("found cached client registration for {}: {}", key, cached.getRegistrationId());
			}
			return Mono.just(cached);
		}
		if (this.traceEnabled) {
			this.log.trace("no cached client registration for {}. loading from settings.", key);
		}
		return this.settings.getSettings(accessToken.accessToken())
			.filter(sp -> sp.category().equals(WORDPRESS_CONSTANT))
			.singleOrEmpty()
			.flatMap(this::registerWordpressClient)
			.doOnNext(cr -> this.clientRegistrationCache.put(key, cr));
	}

	private record PrincipalTokenRegistrationId(OAuth2AuthenticationToken authentication, String accessToken,
			String registrationId) {
	}

	private String buildValidCacheKey(PrincipalTokenRegistrationId token) {
		return this.buildValidCacheKey(token.authentication(), token.registrationId());
	}

	private String buildValidCacheKey(Authentication authentication, String registrationId) {
		Assert.notNull(authentication, "authentication cannot be null");
		Assert.hasText(registrationId, "registrationId cannot be null");
		return registrationId + "|" + authentication.getName();
	}

	/**
	 * registers an OAuth client for WordPress, whose definition we can only derive from
	 * the downstream {@code settings} table, accessed via the GraphQL settings API.
	 */
	private Mono<ClientRegistration> registerWordpressClient(SettingsPage settingsPage) {
		var authorizationUri = this.getSettingsValueForKey(settingsPage, "authorizationUri");
		var tokenUri = this.getSettingsValueForKey(settingsPage, "tokenUri");
		var clientId = this.getSettingsValueForKey(settingsPage, "clientId");
		var clientSecret = this.getSettingsValueForKey(settingsPage, "clientSecret");
		var good = StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)
				&& StringUtils.hasText(authorizationUri) && StringUtils.hasText(tokenUri);
		if (!good) {
			return Mono.error(new IllegalArgumentException(
					"missing required settings for [" + WORDPRESS_CONSTANT + "] client registration"));
		}
		return Mono.just(ClientRegistration.withRegistrationId(WORDPRESS_CONSTANT)//
			.clientId(clientId)//
			.clientSecret(clientSecret)//
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)//
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
			.redirectUri("{baseUrl}/oauth2/code/{registrationId}") //
			.scope("global") //
			.authorizationUri(authorizationUri) //
			.tokenUri(tokenUri) //
			.clientName(WORDPRESS_CONSTANT) //
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

	private String getSettingsValueForKey(SettingsPage settingsPage, String k) {
		Assert.notNull(settingsPage, "the settingsPage must not be null");
		Assert.notNull(k, "the key must not be null");
		for (var setting : settingsPage.settings()) {
			if (setting.name().equals(k)) {
				return setting.value();
			}
		}
		return null;
	}

}
