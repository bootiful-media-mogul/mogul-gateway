package com.joshlong.mogul.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "mogul")
record GatewayProperties(Amqp amqp, Gateway gateway) {

	record Amqp(String settingsEvents) {
	}

	record Gateway(String apiPrefix, URI api, String uiPrefix, URI ui) {
	}
}
