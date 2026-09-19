package com.joshlong.mogul.gateway;

import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * preserves valueless query parameters on the way to the backend.
 * <p>
 * the stock proxy builds the outbound URL by re-rendering the request's <em>parsed</em>
 * parameters, and a valueless parameter parses to an empty string, so it comes back out
 * with an {@code =} welded on: vite asks for
 * {@code /src/ui/Icon.vue?vue&type=style&index=0&lang.css} and the backend is handed
 * {@code ?vue=&type=style&index=0&lang.css=}. vite decides a request is CSS by testing
 * whether the id ends in {@code .css}, so {@code lang.css=} misses, and it returns raw
 * CSS where the browser imported a javascript module -- "Uncaught SyntaxError: Invalid or
 * unexpected token". the webflux gateway passed the query through untouched.
 * <p>
 * only kicks in when the original query actually has a valueless parameter, so the stock
 * behaviour (and filters like {@code addRequestParameter}) is left alone everywhere else.
 */
class RawQueryPreservingProxyExchange implements ProxyExchange {

	private final ProxyExchange delegate;

	RawQueryPreservingProxyExchange(ProxyExchange delegate) {
		this.delegate = delegate;
	}

	@Override
	public ServerResponse exchange(Request request) {
		var target = request.getUri();
		var rawQuery = request.getServerRequest().uri().getRawQuery();
		if (target == null || !hasValuelessParameter(rawQuery)) {
			return this.delegate.exchange(request);
		}
		var corrected = UriComponentsBuilder.fromUri(target).replaceQuery(rawQuery).build(true).toUri();
		return this.delegate.exchange(this.copyWithUri(request, corrected));
	}

	private Request copyWithUri(Request request, URI uri) {
		var builder = this.delegate.request(request.getServerRequest())//
			.method(request.getMethod())//
			.headers(request.getHeaders())//
			.uri(uri);
		request.getResponseConsumers().forEach(builder::responseConsumer);
		return builder.build();
	}

	private boolean hasValuelessParameter(String rawQuery) {
		if (rawQuery == null || rawQuery.isEmpty()) {
			return false;
		}
		for (var pair : rawQuery.split("&")) {
			if (!pair.isEmpty() && pair.indexOf('=') < 0) {
				return true;
			}
		}
		return false;
	}

}
