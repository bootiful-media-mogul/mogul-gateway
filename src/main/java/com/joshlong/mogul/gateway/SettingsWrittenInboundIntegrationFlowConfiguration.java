package com.joshlong.mogul.gateway;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.event.outbound.ApplicationEventPublishingMessageHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// todo why do i need this?
@EnableIntegration
@Configuration
class SettingsWrittenInboundIntegrationFlowConfiguration {

	@Bean
	ApplicationEventPublishingMessageHandler applicationEventPublishingMessageHandler() {
		var messageHandler = new ApplicationEventPublishingMessageHandler();
		messageHandler.setPublishPayload(true);
		return messageHandler;
	}

	@Bean
	IntegrationFlow settingsWrittenInboundIntegrationFlow(ObjectMapper objectMapper,
			ApplicationEventPublishingMessageHandler applicationEventPublishingMessageHandler,
			ConnectionFactory connectionFactory, GatewayProperties gatewayProperties) {
		return IntegrationFlow.from(Amqp.inboundAdapter(connectionFactory, gatewayProperties.amqp().settingsEvents()))
			.transform((GenericTransformer<String, String>) source -> {
				var jsonNode = objectMapper.readValue(source, JsonNode.class);
				return jsonNode.get("authenticationName").asString();
			})
			.transform(MogulSettingsCacheInvalidationEvent::new)
			.handle(applicationEventPublishingMessageHandler)
			.get();
	}

}
