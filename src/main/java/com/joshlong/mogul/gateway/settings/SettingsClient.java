package com.joshlong.mogul.gateway.settings;

import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * read settings from the mogul-service
 */
public class SettingsClient {

	private final HttpSyncGraphQlClient graphQlClient;

	public SettingsClient(RestClient.Builder restClientBuilder, String graphqlUrl) {
		var restClient = restClientBuilder.baseUrl(graphqlUrl).build();
		this.graphQlClient = HttpSyncGraphQlClient.builder(restClient).build();
	}

	private HttpSyncGraphQlClient authenticatedGraphQlClient(String bearerToken) {
		return this.graphQlClient.mutate().header("Authorization", "Bearer " + bearerToken).build();
	}

	public List<SettingsPage> getSettings(String bearerToken) {
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
			.retrieveSync("settings") //
			.toEntityList(SettingsPage.class);
	}

}
