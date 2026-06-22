package com.virtuehire.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "https://admin.virtuehire.in",
            "https://backend.virtuehire.in",
            "http://localhost:*"
    };

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/ws-assessment")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .withSockJS();

        registry.addEndpoint("/api/ws-assessment")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);

        registry.addEndpoint("/ws-assessment")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .withSockJS();
        
        registry.addEndpoint("/ws-assessment")
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    }
}
