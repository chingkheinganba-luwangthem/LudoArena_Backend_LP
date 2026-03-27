package com.ludoarena.security;

import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT Authentication Filter - Intercepts every HTTP request to validate JWT tokens.
 *
 * THREAD ARCHITECTURE:
 * - This filter extends OncePerRequestFilter, guaranteeing it runs EXACTLY ONCE
 *   per request, even if the request is forwarded internally.
 * - It runs on the SAME Tomcat HTTP thread that handles the request.
 * - The SecurityContextHolder uses ThreadLocal storage, meaning each thread
 *   has its own isolated SecurityContext. This ensures concurrent requests
 *   don't interfere with each other's authentication state.
 *
 * Flow:
 * 1. Extract JWT from Authorization header
 * 2. Validate token signature and expiration
 * 3. Load user from database
 * 4. Set authentication in SecurityContext (ThreadLocal)
 * 5. Continue filter chain → reaches the controller
 * 6. After response, Spring clears the ThreadLocal SecurityContext
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtTokenProvider tokenProvider, @org.springframework.context.annotation.Lazy UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // Step 1: Extract JWT token from "Authorization: Bearer <token>" header
            String jwt = getJwtFromRequest(request);

            // Step 2: Validate token
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                // Step 3: Extract user ID and load user from DB
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                User user = userRepository.findById(userId).orElse(null);

                if (user != null) {
                    // Step 4: Create authentication token and set in SecurityContext
                    // THREAD: SecurityContextHolder stores this in ThreadLocal,
                    // so it's isolated to the current HTTP thread
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user, null, Collections.emptyList());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            // Log but don't throw - let the request proceed as unauthenticated
            logger.error("Could not set user authentication in security context", ex);
        }

        // Step 5: Continue the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from the Authorization header.
     * Expects format: "Bearer eyJhbGciOi..."
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
