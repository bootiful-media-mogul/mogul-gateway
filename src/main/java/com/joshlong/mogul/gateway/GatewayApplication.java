package com.joshlong.mogul.gateway;

import com.joshlong.mogul.gateway.settings.SettingsClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

	private static final String API_PROPERTY_NAME = "${mogul.gateway.api}";

	private static final String UI_PROPERTY_NAME = "${mogul.gateway.ui}";

	static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	// do i need the following?
	@Bean
	ReactiveOAuth2AuthorizedClientService authorizedClientService(
			ReactiveClientRegistrationRepository clientRegistrationRepository) {
		return new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
	}

	@Bean
	SimpleAsyncTaskScheduler taskScheduler() {
		return new SimpleAsyncTaskSchedulerBuilder()//
			.virtualThreads(true) //
			.build();
	}

	@Bean
	MogulSettingsAwareReactiveClientRegistrationRepository mogulSettingsAwareReactiveClientRegistrationRepository(
			ObjectProvider<CurrentToken> token, SettingsClient settingsClient, Environment environment) {
		return new MogulSettingsAwareReactiveClientRegistrationRepository(token, environment, settingsClient);
	}

	@Bean
	SettingsClient settingsClient(WebClient.Builder webClientBuilder, @Value(API_PROPERTY_NAME) String apiEndpointUrl) {
		return new SettingsClient(webClientBuilder, apiEndpointUrl + "/graphql");
	}

	@Bean
	RouteLocator gateway(WordpressAwareTokenRelayGatewayFilter wordPressTokenRelayFilter, RouteLocatorBuilder rlb,
			TokenEnrichingTokenRelayGatewayFilter tokenRelay, @Value(UI_PROPERTY_NAME) String ui,
			@Value(API_PROPERTY_NAME) String api) {
		var apiPrefix = "/api/";
		var retries = 5;
		return rlb//
			.routes()
			.route(rs -> rs //
				.path(apiPrefix + "**") //
				.filters(f -> f //
					.retry(retries) //
					.filter(tokenRelay)
					.filter(wordPressTokenRelayFilter)
					.rewritePath(apiPrefix + "(?<segment>.*)", "/$\\{segment}")//
				)
				.uri(api) //
			)//
			.route(rs -> rs//
				.path("/**") //
				.filters(f -> f.retry(retries).filter(tokenRelay)) //
				.uri(ui) //
			) //
			.build();
	}

	@Bean
	WordpressAwareTokenRelayGatewayFilter wordpressAwareTokenRelayGatewayFilter(
			ServerOAuth2AuthorizedClientRepository serverOAuth2AuthorizedClientRepository) {
		return new WordpressAwareTokenRelayGatewayFilter(serverOAuth2AuthorizedClientRepository);
	}

	@Bean
	TokenEnrichingTokenRelayGatewayFilter tokenEnrichingTokenRelayGatewayFilter(
			ReactiveOAuth2AuthorizedClientManager clientManager) {
		return new TokenEnrichingTokenRelayGatewayFilter(clientManager);
	}

	// make sure there's only one login mechanism, no matter how many OAuth clients we
	// register.
	@Bean
	RouterFunction<ServerResponse> loginRoutes() {
		var location = URI.create("/oauth2/authorization/auth0");
		return route() //
			.GET("/login", _ -> ServerResponse.temporaryRedirect(location).build()) //
			.build();
	}

}
