package com.joshlong.mogul.gateway;

import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * use to obtain the current access token for the current authenticated
 * {@link java.security.Principal}
 */
@Component
class CurrentToken {

	private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

	CurrentToken(ReactiveOAuth2AuthorizedClientService authorizedClientService) {
		this.authorizedClientService = authorizedClientService;
	}

	@NonNull
	Mono<String> getAccessToken(OAuth2AuthenticationToken authentication) {
		return this.authorizedClientService //
			.loadAuthorizedClient(authentication.getAuthorizedClientRegistrationId(), authentication.getName()) //
			.map(client -> client.getAccessToken().getTokenValue());
	}

}
