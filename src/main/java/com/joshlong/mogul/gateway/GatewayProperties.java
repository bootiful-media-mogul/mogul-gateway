package com.joshlong.mogul.gateway;

import java.net.URI;
import org.springframework.boot.context.properties.*;

@ConfigurationProperties(prefix = "mogul.gateway")
record GatewayProperties(String apiPrefix, URI api, String uiPrefix, URI ui) {
}
