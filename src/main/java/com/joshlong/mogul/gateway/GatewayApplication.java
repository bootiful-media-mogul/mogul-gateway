package com.joshlong.mogul.gateway;

import com.joshlong.mogul.gateway.settings.SettingsClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.function.Function;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

	private static final String API_PROPERTY_NAME = "${mogul.gateway.api}";

	private static final String UI_PROPERTY_NAME = "${mogul.gateway.ui}";

	private static final String BASE_URL_HEADER = "X-Mogul-Base-Url";

	private static final String API_PREFIX = "/api/";

	private static final int RETRIES = 5;

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	/**
	 * stamp the caller's absolute base URL (scheme + host[:port]) onto every proxied
	 * request so the backend can build absolute links back into the app (e.g. to a newly
	 * created blog post) without hard-coding a host.
	 */
	private static Function<ServerRequest, ServerRequest> baseUrlHeader() {
		return request -> {
			var uri = request.uri();
			var baseUrl = uri.getScheme() + "://" + uri.getAuthority();
			return ServerRequest.from(request).header(BASE_URL_HEADER, baseUrl).build();
		};
	}

	// todo: do i need the following?
	@Bean
	OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
		return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
	}

	/**
	 * the {@code tokenRelay()} filter function looks this up from the context to trade
	 * the logged-in principal for a live access token. nothing auto-configures it.
	 */
	@Bean
	OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizedClientRepository authorizedClientRepository) {
		return new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
	}

	/**
	 * the proxy talks to the UI and the API over cleartext http, and the JDK's
	 * {@link HttpClient} defaults to {@link HttpClient.Version#HTTP_2}, which means it
	 * opens every one of those requests with an h2c upgrade ({@code Connection: Upgrade},
	 * {@code Upgrade: h2c}). the vite dev server registers an {@code upgrade} handler for
	 * its HMR websocket and simply swallows an upgrade it doesn't recognize, so the
	 * request never gets a response and the browser hangs forever with nothing in any
	 * log. reactor netty never did this, so it is a webflux-to-mvc regression, not a vite
	 * bug. pin the proxy to HTTP/1.1.
	 */
	@Bean
	ClientHttpRequestFactory gatewayClientHttpRequestFactory() {
		var httpClient = HttpClient.newBuilder() //
			.version(HttpClient.Version.HTTP_1_1) //
			.build();
		return new JdkClientHttpRequestFactory(httpClient);
	}

	/**
	 * wraps the stock proxy so valueless query parameters survive the hop to the backend;
	 * see {@link RawQueryPreservingProxyExchange}.
	 */
	@Bean
	ProxyExchange proxyExchange(RestClient.Builder restClientBuilder, GatewayMvcProperties gatewayMvcProperties) {
		var restClientProxyExchange = new RestClientProxyExchange(restClientBuilder.build(), gatewayMvcProperties);
		return new RawQueryPreservingProxyExchange(restClientProxyExchange);
	}

	@Bean
	SimpleAsyncTaskScheduler taskScheduler() {
		return new SimpleAsyncTaskSchedulerBuilder()//
			.virtualThreads(true) //
			.build();
	}

	@Bean
	MogulSettingsAwareClientRegistrationRepository mogulSettingsAwareClientRegistrationRepository(
			ObjectProvider<CurrentToken> token, SettingsClient settingsClient, Environment environment) {
		return new MogulSettingsAwareClientRegistrationRepository(token, environment, settingsClient);
	}

	@Bean
	SettingsClient settingsClient(RestClient.Builder restClientBuilder,
			@Value(API_PROPERTY_NAME) String apiEndpointUrl) {
		return new SettingsClient(restClientBuilder, apiEndpointUrl + "/graphql");
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	RouterFunction<ServerResponse> apiRoute(WordpressAwareTokenRelayFilterFunction wordPressTokenRelayFilter,
			@Value(API_PROPERTY_NAME) String api) {
		return route("api") //
			.route(path(API_PREFIX + "**"), http()) //
			.filter(retry(RETRIES)) //
			.filter(tokenRelay()) //
			.filter(wordPressTokenRelayFilter) //
			.before(baseUrlHeader()) //
			.before(uri(api)) //
			.before(rewritePath(API_PREFIX + "(?<segment>.*)", "/$\\{segment}")) //
			.build();
	}

	/**
	 * the catch-all: anything that isn't the API is the UI, so this has to be the last
	 * {@link RouterFunction} the {@code DispatcherServlet} consults.
	 */
	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	RouterFunction<ServerResponse> uiRoute(@Value(UI_PROPERTY_NAME) String ui) {
		return route("ui") //
			.route(path("/**"), http()) //
			.filter(retry(RETRIES)) //
			.filter(tokenRelay()) //
			.before(baseUrlHeader()) //
			.before(uri(ui)) //
			.build();
	}

	@Bean
	WordpressAwareTokenRelayFilterFunction wordpressAwareTokenRelayFilterFunction(
			OAuth2AuthorizedClientRepository authorizedClientRepository) {
		return new WordpressAwareTokenRelayFilterFunction(authorizedClientRepository);
	}

	// make sure there's only one login mechanism, no matter how many OAuth clients we
	// register.
	@Bean
	@Order(0)
	RouterFunction<ServerResponse> loginRoutes() {
		var location = URI.create("/oauth2/authorization/auth0");
		return RouterFunctions.route() //
			.GET("/login", _ -> ServerResponse.temporaryRedirect(location).build()) //
			.build();
	}

}
