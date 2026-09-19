package com.joshlong.mogul.gateway;

import org.springframework.amqp.core.*;
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

	/**
	 * has to match the producer's derivation of the same name, in
	 * {@code RabbitMqConfiguration} over in mogul-service. the base name is already a
	 * direct exchange from the topology this replaces, and an exchange cannot change type
	 * in place.
	 */
	private static String settingsEventsExchangeName(String destination) {
		return destination + "-fanout";
	}

	@Bean
	FanoutExchange settingsEventsExchange(GatewayProperties gatewayProperties) {
		var name = settingsEventsExchangeName(gatewayProperties.amqp().settingsEvents());
		return ExchangeBuilder.fanoutExchange(name).durable(true).build();
	}

	/**
	 * a queue per instance, not a queue per cluster. the point of the message is to tell
	 * this JVM that the {@code ClientRegistration} it is holding is stale, so every
	 * instance needs its own copy; sharing one queue would make the instances competing
	 * consumers and deliver each change to exactly one of them.
	 * <p>
	 * anonymous, so it is non-durable, exclusive and auto-deleted with the connection --
	 * a queue named after the instance would outlive the pod and quietly accumulate
	 * messages nobody will ever read. the cost is that a change published during a
	 * connection blip is missed by that instance, which leaves it serving a stale
	 * registration until the five-minute expiry in
	 * {@link MogulSettingsAwareClientRegistrationRepository} catches it.
	 */
	@Bean
	Queue settingsEventsQueue() {
		return new AnonymousQueue();
	}

	@Bean
	Binding settingsEventsBinding(Queue settingsEventsQueue, FanoutExchange settingsEventsExchange) {
		return BindingBuilder.bind(settingsEventsQueue).to(settingsEventsExchange);
	}

	@Bean
	IntegrationFlow settingsWrittenInboundIntegrationFlow(
			MogulSettingsAwareClientRegistrationRepository registrationRepository, ObjectMapper objectMapper,
			ConnectionFactory connectionFactory, Queue settingsEventsQueue) {
		return IntegrationFlow //
			.from(Amqp.inboundAdapter(connectionFactory, settingsEventsQueue)) //
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
