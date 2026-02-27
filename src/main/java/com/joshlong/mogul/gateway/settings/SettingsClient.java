package com.joshlong.mogul.gateway.settings;

import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * read settings from the mogul-service
 */
public class SettingsClient {

	private final HttpGraphQlClient graphQlClient;

	public SettingsClient(WebClient.Builder webClientBuilder, String graphqlUrl) {
		var webClient = webClientBuilder.baseUrl(graphqlUrl).build();
		this.graphQlClient = HttpGraphQlClient.builder(webClient).build();
	}

	private HttpGraphQlClient authenticatedGraphQlClient(String bearerToken) {
		return this.graphQlClient.mutate()
				.header("Authorization", "Bearer " + bearerToken)
				.build();
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

		return this.authenticatedGraphQlClient(bearerToken) //
			.document(query) //
			.retrieve("settings") //
			.toEntityList(SettingsPage.class) //
			.flatMapMany(Flux::fromIterable);
	}

}
