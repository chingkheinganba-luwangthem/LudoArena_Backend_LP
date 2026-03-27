package com.ludoarena.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

/**
 * Security Configuration - Full JWT + CORS setup.
 *
 * THREAD ARCHITECTURE:
 * - SecurityFilterChain is a singleton, but each request gets its own SecurityContext
 *   stored in ThreadLocal via SecurityContextHolder.
 * - BCryptPasswordEncoder is thread-safe (can be shared across all request threads).
 * - JwtAuthFilter runs on the same Tomcat thread as the HTTP request (OncePerRequestFilter).
 * - The filter chain order:
 *   CORS → JwtAuthFilter → UsernamePasswordAuthenticationFilter → Controller
 *
 * Public endpoints (no JWT required):
 *   - /api/auth/** (login, signup, guest, OAuth)
 *   - /ws/** (WebSocket endpoint - has its own auth)
 *   - /api/public/** (any public APIs)
 *
 * Protected endpoints (JWT required):
 *   - Everything else (/api/rooms/**, /api/games/**, /api/users/**)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * BCryptPasswordEncoder - Thread-safe password hashing.
     * BCrypt internally uses SecureRandom which is thread-safe.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager bean - needed for programmatic authentication.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Main security filter chain configuration.
     *
     * THREAD: Each HTTP request from Tomcat's thread pool passes through this
     * filter chain sequentially on the same thread.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF - we use stateless JWT tokens instead
            .csrf(csrf -> csrf.disable())
            // Disable OAuth2 login - we use JWT tokens, not server-side OAuth flows
            .oauth2Login(oauth2 -> oauth2.disable())
            // Enable CORS for React frontend
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Stateless sessions - no server-side session storage
            // THREAD: No HttpSession means no session-based thread contention
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no JWT required
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            // Add JWT filter BEFORE Spring's default auth filter
            // THREAD: JwtAuthFilter runs on the same Tomcat thread,
            // setting ThreadLocal SecurityContext before controller execution
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * CORS configuration for React frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000", 
            "http://localhost:5173", 
            "https://ludo-arena-frontend-murex.vercel.app"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
