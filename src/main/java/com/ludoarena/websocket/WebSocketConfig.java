package com.ludoarena.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration using STOMP protocol.
 *
 * THREAD ARCHITECTURE:
 * - Spring's WebSocket support uses TWO thread pools by default:
 *   1. clientInboundChannel threads  → receive messages FROM clients
 *   2. clientOutboundChannel threads → send messages TO clients
 * - The SimpleBroker uses its own broker thread to route messages.
 *
 * Message Flow:
 *   Client → WebSocket → clientInboundChannel (thread pool)
 *     → @MessageMapping handler → GameEngine (with ReentrantLock)
 *     → SimpMessagingTemplate → SimpleBroker (broker thread)
 *     → clientOutboundChannel (thread pool) → WebSocket → All Clients
 *
 * Endpoints:
 *   /ws                           → WebSocket connection endpoint
 *   /app/game/{id}/roll-dice      → Send dice roll
 *   /app/game/{id}/move           → Send move
 *   /app/game/{id}/chat           → Send chat message
 *   /topic/game/{id}              → Subscribe to game updates
 *   /topic/game/{id}/chat         → Subscribe to chat messages
 *   /topic/room/{roomCode}        → Subscribe to room updates
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure the STOMP message broker.
     *
     * - /topic: broadcast messages (game state, chat) to all subscribers
     * - /queue: user-specific messages (personal notifications)
     * - /app: prefix for messages FROM client → server @MessageMapping handlers
     *
     * THREAD: The simple broker runs on its own thread for message dispatch.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Register STOMP WebSocket endpoint.
     *
     * - Clients connect to /ws via SockJS (fallback for browsers that don't support WS)
     * - Allows connections from React frontend at localhost:3000
     *
     * THREAD: Each WebSocket connection is maintained by the Tomcat WebSocket thread.
     * Messages are dispatched to the clientInboundChannel thread pool.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
