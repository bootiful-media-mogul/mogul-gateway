package com.joshlong.mogul.gateway;

import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * propagates a WordPress OAuth token as a separate header.
 */
class WordpressAwareTokenRelayFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

	private static final String CLIENT_REGISTRATION_ID = "wordpress";

	private static final String TOKEN_HEADER = "X-WordPress-Token";

	private final OAuth2AuthorizedClientRepository clientRepository;

	WordpressAwareTokenRelayFilterFunction(OAuth2AuthorizedClientRepository clientRepository) {
		this.clientRepository = clientRepository;
	}

	@Override
	public @NonNull ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
			throws Exception {
		if (request.servletRequest().getUserPrincipal() instanceof OAuth2AuthenticationToken authentication) {
			var client = this.clientRepository.loadAuthorizedClient(CLIENT_REGISTRATION_ID, authentication,
					request.servletRequest());
			if (client != null) {
				var token = client.getAccessToken().getTokenValue();
				return next.handle(ServerRequest.from(request).header(TOKEN_HEADER, token).build());
			}
		}
		return next.handle(request);
	}

}
