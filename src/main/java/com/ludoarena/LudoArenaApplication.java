package com.ludoarena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * LudoArena Main Application Entry Point
 *
 * THREAD ARCHITECTURE:
 * - @SpringBootApplication: Bootstraps the embedded Tomcat with a default thread pool
 *   (200 threads) for handling HTTP requests concurrently.
 * - @EnableAsync: Enables Spring's asynchronous method execution, creating a separate
 *   thread pool (configured in application.properties) for tasks like AI player moves.
 *
 * Thread Pools in this application:
 * 1. Tomcat HTTP Thread Pool   → handles REST API requests
 * 2. WebSocket Broker Threads  → handles STOMP message routing
 * 3. Async Task Executor       → handles @Async methods (AI moves, notifications)
 * 4. Scheduler Thread Pool     → handles @Scheduled tasks (game timeouts)
 */
@SpringBootApplication
@EnableAsync
public class LudoArenaApplication {

    public static void main(String[] args) {
        SpringApplication.run(LudoArenaApplication.class, args);
    }
}
