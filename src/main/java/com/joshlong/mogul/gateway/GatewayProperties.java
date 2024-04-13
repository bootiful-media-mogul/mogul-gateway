package com.joshlong.mogul.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "mogul.gateway")
public record GatewayProperties(String apiPrefix, URI api, String uiPrefix, URI ui) {
}
