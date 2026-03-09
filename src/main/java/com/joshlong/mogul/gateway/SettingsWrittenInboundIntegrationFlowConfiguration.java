package com.joshlong.mogul.gateway;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// todo why is this required? if i don't have it, this {@code IntegrationFlow} doesnt trigger
@EnableIntegration
@Configuration
class SettingsWrittenInboundIntegrationFlowConfiguration {

	@Bean
	IntegrationFlow settingsWrittenInboundIntegrationFlow(
			MogulSettingsAwareReactiveClientRegistrationRepository registrationRepository, ObjectMapper objectMapper,
			ConnectionFactory connectionFactory, GatewayProperties gatewayProperties) {
		return IntegrationFlow //
			.from(Amqp.inboundAdapter(connectionFactory, gatewayProperties.amqp().settingsEvents())) //
			.transform((GenericTransformer<String, String>) source -> { //
				var jsonNode = objectMapper.readValue(source, JsonNode.class);
				return jsonNode.get("authenticationName").asString();
			}) //
			.handle(String.class, (payload, headers) -> {
				registrationRepository.invalidateMogulSettingsCache(payload);
				return null;
			})
			.get();
	}

}
