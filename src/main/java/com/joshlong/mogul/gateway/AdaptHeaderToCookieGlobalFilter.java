package com.joshlong.mogul.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * this is meant to take incoming requests with a particular header, and use that header
 * to set the cookie for requests into the reactive web application
 */
// @Component
class AdaptHeaderToCookieGlobalFilter implements GlobalFilter, Ordered {

	private final String XSESSIONID = "X-Mogul-Session-ID";

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		var request = exchange.getRequest();
		var sessionId = request.getHeaders().getFirst(XSESSIONID);

		if (sessionId != null) {
			var modifiedRequest = request.mutate() //
				.headers(httpHeaders -> {
					// this.log.debug("setting the cookie header to {}", sessionId);
					var existingCookies = httpHeaders.getFirst(HttpHeaders.COOKIE);
					var newCookieHeader = existingCookies != null ? existingCookies + "; SESSION=" + sessionId
							: "SESSION=" + sessionId;

					httpHeaders.set(HttpHeaders.COOKIE, newCookieHeader);
				}) //
				.build();
			var modifiedExchange = exchange.mutate().request(modifiedRequest).build();
			return chain.filter(modifiedExchange);
		}

		return chain.filter(exchange);
	}

	/*
	 * private String buildCookieHeader(String existingCookies, String sessionId) { var
	 * cookieBuilder = new StringBuilder(); if (existingCookies != null &&
	 * !existingCookies.isEmpty()) { var cookies = existingCookies.split(";"); for (var
	 * cookie : cookies) { var trimmed = cookie.trim(); if
	 * (!trimmed.startsWith("JSESSIONID=")) { if (!cookieBuilder.isEmpty()) {
	 * cookieBuilder.append("; "); } cookieBuilder.append(trimmed); } } } if
	 * (!cookieBuilder.isEmpty()) { cookieBuilder.append("; "); }
	 * cookieBuilder.append("JSESSIONID=").append(sessionId);
	 *
	 * return cookieBuilder.toString(); }
	 *
	 */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
