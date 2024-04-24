package com.joshlong.mogul.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@SpringBootApplication
@Controller
@ResponseBody
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routes(GatewayProperties gatewayProperties) {

		var wildcard = "**";

		var route = route("routes");

		// API
		var apiPrefix = gatewayProperties.apiPrefix();
		var apiHost = gatewayProperties.api();

		for (var m : HttpMethod.values()) {
			route = route//
				.add(RouterFunctions.route(
						RequestPredicates.method(m).and(RequestPredicates.path(apiPrefix + wildcard)), http(apiHost)))
				.before(rewritePath(apiPrefix + "(?<segment>.*)", "/${segment}"))
				.filter(tokenRelay());
		}

		// UI
		var uiPrefix = gatewayProperties.uiPrefix();
		var uiHost = gatewayProperties.ui();
		route = route.GET(uiPrefix + wildcard, http(uiHost));

		return route.build();
	}

	@Bean
	SecurityFilterChain mySecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
		return httpSecurity//
			.authorizeHttpRequests(a -> a //
				.requestMatchers(EndpointRequest.toAnyEndpoint())
				.permitAll()//
				.anyRequest()
				.authenticated()//
			)//
			.csrf(AbstractHttpConfigurer::disable)//
			.cors(AbstractHttpConfigurer::disable)//
			.oauth2Login(Customizer.withDefaults()) //
			.oauth2Client(Customizer.withDefaults())//
			.build();
	}

}