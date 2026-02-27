package com.joshlong.mogul.gateway.settings;

import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * read settings from the mogul-service
 */
public class SettingsClient {

	private final WebClient.Builder webClientBuilder;

	private final String graphqlUrl;

	public SettingsClient(WebClient.Builder webClientBuilder, String graphqlUrl) {
		this.webClientBuilder = webClientBuilder;
		this.graphqlUrl = graphqlUrl;
	}

	private HttpGraphQlClient authenticatedGraphQlClient(String bearerToken) {
		// todo this can be factored out as an ExchangeFilterFunction on the WebClient
		// itself
		var webClient = webClientBuilder.defaultHeaders(h -> h.setBearerAuth(bearerToken)).baseUrl(graphqlUrl).build();

		return HttpGraphQlClient.builder(webClient).build();
	}

	public Flux<SettingsPage> getSettings(String bearerToken) {
		String query = """
				query {
				    settings {
				        valid
				        category
				        settings {
				            name
				            value
				            valid
				        }
				    }
				}
				""";

		return authenticatedGraphQlClient(bearerToken) //
			.document(query) //
			.retrieve("settings") //
			.toEntityList(SettingsPage.class) //
			.flatMapMany(Flux::fromIterable);
	}

}
