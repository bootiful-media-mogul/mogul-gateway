package com.joshlong.mogul.gateway;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * use to obtain the current access token for the current authenticated
 * {@link java.security.Principal}
 */
@Component
class CurrentToken {

	private final OAuth2AuthorizedClientService authorizedClientService;

	CurrentToken(OAuth2AuthorizedClientService authorizedClientService) {
		this.authorizedClientService = authorizedClientService;
	}

	@Nullable
	String getAccessToken(OAuth2AuthenticationToken authentication) {
		var client = this.authorizedClientService //
			.loadAuthorizedClient(authentication.getAuthorizedClientRegistrationId(), authentication.getName());
		return client == null ? null : client.getAccessToken().getTokenValue();
	}

}
